import { listen } from '@tauri-apps/api/event';
import { api } from './api';
import { library } from './library.svelte';
import { settings } from './settings.svelte';

export type DownloadPreset = 'audioOriginal' | 'audioMp3' | 'video1080' | 'videoBest';

export interface PresetOption {
  id: DownloadPreset;
  label: string;
  hint: string;
  needsFfmpeg: boolean;
}

export const DOWNLOAD_PRESETS: PresetOption[] = [
  {
    id: 'audioOriginal',
    label: 'Audio (Original)',
    hint: 'Best audio as published without re-encoding (Opus / M4A)',
    needsFfmpeg: false,
  },
  {
    id: 'audioMp3',
    label: 'Audio (MP3)',
    hint: 'Converts to high-quality MP3 with embedded cover art and metadata',
    needsFfmpeg: true,
  },
  {
    id: 'video1080',
    label: 'Video (1080p)',
    hint: 'Up to 1080p MP4 with audio, cover art, and subtitles when ffmpeg is ready',
    needsFfmpeg: false,
  },
  {
    id: 'videoBest',
    label: 'Video (Best)',
    hint: 'Highest available resolution merged into MP4',
    needsFfmpeg: false,
  },
];

export interface DownloadJob {
  id: number;
  urls: string[];
  preset: DownloadPreset;
  dest: string;
  status: 'queued' | 'running' | 'done' | 'failed' | 'cancelled';
  percent: number | null;
  eta: string;
  title: string;
  files: string[];
  error: string;
}

class DownloadsStore {
  tools = $state<{ ytdlp: string | null; ffmpeg: string | null } | null>(null);
  installing = $state<'ytdlp' | 'ffmpeg' | null>(null);
  toolDone = $state(0);
  toolTotal = $state(0);
  defaultFolder = $state('');
  jobs = $state<DownloadJob[]>([]);
  error = $state('');
  private listening = false;

  async init(): Promise<void> {
    if (!this.listening) {
      this.listening = true;
      await listen<{ tool: 'ytdlp' | 'ffmpeg'; done: number; total: number }>(
        'tool-progress',
        (ev) => {
          this.toolDone = ev.payload.done;
          this.toolTotal = ev.payload.total;
        }
      );
      await listen<{
        id: number;
        status: DownloadJob['status'];
        percent?: number;
        eta?: string;
        title?: string;
        file?: string;
        error?: string;
      }>('download-progress', (ev) => {
        const p = ev.payload;
        this.jobs = this.jobs.map((j) => {
          if (j.id !== p.id) return j;
          const files = p.file && !j.files.includes(p.file) ? [...j.files, p.file] : j.files;
          return {
            ...j,
            status: p.status,
            percent: p.percent !== undefined ? p.percent : j.percent,
            eta: p.eta !== undefined ? p.eta : j.eta,
            title: p.title !== undefined ? p.title : j.title,
            files,
            error: p.error !== undefined ? p.error : j.error,
          };
        });
      });
    }

    const [t, defFolder] = await Promise.all([api.toolsStatus(), api.downloadDefaultFolder()]);
    this.tools = t;
    this.defaultFolder = defFolder;
  }

  async refreshTools(): Promise<void> {
    this.tools = await api.toolsStatus();
  }

  async installTool(tool: 'ytdlp' | 'ffmpeg'): Promise<void> {
    this.error = '';
    this.installing = tool;
    this.toolDone = 0;
    this.toolTotal = 0;
    try {
      await api.toolInstall(tool);
      await this.refreshTools();
    } catch (e) {
      this.error = e instanceof Error ? e.message : String(e);
    } finally {
      this.installing = null;
    }
  }

  async updateYtdlp(): Promise<void> {
    this.error = '';
    try {
      await api.ytdlpUpdate();
      await this.refreshTools();
    } catch (e) {
      this.error = e instanceof Error ? e.message : String(e);
    }
  }

  async startDownload(urls: string[], preset: DownloadPreset, dest: string, addToLibrary: boolean): Promise<boolean> {
    this.error = '';
    try {
      settings.downloadFolder = dest;
      settings.downloadPreset = preset;
      settings.downloadToLibrary = addToLibrary;
      settings.save('download_folder', dest);
      settings.save('download_preset', preset);
      settings.save('download_to_library', String(addToLibrary));

      const id = await api.downloadAdd({ urls, preset, dest, addToLibrary });
      this.jobs = [
        {
          id,
          urls,
          preset,
          dest,
          status: 'queued',
          percent: null,
          eta: '',
          title: '',
          files: [],
          error: '',
        },
        ...this.jobs,
      ];
      if (addToLibrary && !library.folders.includes(dest)) {
        library.folders = [...library.folders, dest];
      }
      return true;
    } catch (e) {
      this.error = e instanceof Error ? e.message : String(e);
      return false;
    }
  }

  async cancel(id: number): Promise<void> {
    await api.downloadCancel(id);
  }

  clearFinished(): void {
    this.jobs = this.jobs.filter((j) => j.status === 'queued' || j.status === 'running');
  }
}

export const downloadsStore = new DownloadsStore();
