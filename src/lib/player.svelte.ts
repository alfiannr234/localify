import { listen } from '@tauri-apps/api/event';
import { getCurrentWindow, LogicalSize } from '@tauri-apps/api/window';
import { api } from './api';
import { applyAppearance } from './appearance.svelte';
import { library } from './library.svelte';
import { lyricsStore } from './lyrics.svelte';
import { buildQueueOrder, resolveNextIndex, resolvePrevIndex } from './queue';
import { quranStore } from './quran.svelte';
import { settings } from './settings.svelte';
import type { AudioTick, RepeatMode, Track } from './types';

class PlayerStore {
  currentPath = $state<string | null>(null);
  queue = $state<string[]>([]);
  queueIndex = $state(0);
  playing = $state(false);
  position = $state(0);
  smoothPosition = $state(0);
  duration = $state(0);
  volume = $state(0.85);
  muted = $state(false);
  speed = $state(1.0);
  shuffle = $state(false);
  repeat = $state<RepeatMode>('off');
  rightPanel = $state<'lyrics' | 'queue' | null>('lyrics');
  nowPlayingOpen = $state(false);
  miniMode = $state(false);
  error = $state<string | null>(null);

  sleepEndAt = $state<number | null>(null);
  sleepRemainingSecs = $state(0);

  private anchorPos = 0;
  private anchorPerf = 0;
  private rafId: number | null = null;
  private lastDiscordPush = 0;
  private savedWinSize: { width: number; height: number } | null = null;
  private sleepInterval: ReturnType<typeof setInterval> | null = null;

  currentTrack = $derived<Track | null>(
    this.currentPath ? (library.trackMap.get(this.currentPath) ?? null) : null
  );

  upNextTracks = $derived.by(() => {
    const map = library.trackMap;
    return this.queue
      .slice(this.queueIndex + 1)
      .map((p) => map.get(p))
      .filter((t): t is Track => Boolean(t));
  });

  async initListeners(): Promise<void> {
    this.volume = settings.volume;
    this.speed = settings.speed;
    void api.audioSetVolume(this.volume);
    void api.audioSetSpeed(this.speed);

    await listen<AudioTick>('audio-tick', (ev) => {
      const t = ev.payload;
      this.position = t.position;
      if (t.duration > 0) this.duration = t.duration;
      this.playing = t.playing;
      this.anchorPos = t.position;
      this.anchorPerf = performance.now();
      if (!t.playing) {
        this.smoothPosition = t.position;
      }
      const now = Date.now();
      if (t.playing && now - this.lastDiscordPush > 15000) {
        this.syncIntegrations();
      }
    });

    await listen<void>('track-ended', () => {
      void this.next(false);
    });

    await listen<string>('media-control', (ev) => {
      switch (ev.payload) {
        case 'play':
          if (!this.playing) void this.togglePlay();
          break;
        case 'pause':
          if (this.playing) void this.togglePlay();
          break;
        case 'toggle':
          void this.togglePlay();
          break;
        case 'next':
          void this.next(true);
          break;
        case 'previous':
          void this.prev();
          break;
        case 'stop':
          void this.stop();
          break;
      }
    });

    await listen<number>('media-seek', (ev) => {
      void this.seek(ev.payload);
    });

    this.startClockLoop();
  }

  private startClockLoop(): void {
    const tick = () => {
      if (this.playing && this.anchorPerf > 0) {
        const dt = ((performance.now() - this.anchorPerf) / 1000) * this.speed;
        const maxDur = this.duration > 0 ? this.duration : Number.MAX_SAFE_INTEGER;
        this.smoothPosition = Math.min(maxDur, Math.max(0, this.anchorPos + dt));
      }
      this.rafId = requestAnimationFrame(tick);
    };
    this.rafId = requestAnimationFrame(tick);
  }

  async playTrack(track: Track, contextTracks?: Track[]): Promise<void> {
    this.error = null;
    const list = contextTracks && contextTracks.length > 0 ? contextTracks : library.tracks;
    this.queue = buildQueueOrder(list, track.path, this.shuffle);
    const idx = this.queue.indexOf(track.path);
    this.queueIndex = idx >= 0 ? idx : 0;
    await this.loadIndex(this.queueIndex);
  }

  async playPaths(paths: string[], startIndex = 0): Promise<void> {
    if (paths.length === 0) return;
    this.queue = [...paths];
    this.queueIndex = Math.max(0, Math.min(startIndex, paths.length - 1));
    await this.loadIndex(this.queueIndex);
  }

  async jumpToQueueIndex(idx: number): Promise<void> {
    if (idx < 0 || idx >= this.queue.length) return;
    this.queueIndex = idx;
    await this.loadIndex(idx);
  }

  playNextInQueue(track: Track): void {
    if (this.queue.length === 0) {
      void this.playTrack(track);
      return;
    }
    const existingAfter = this.queue.slice(this.queueIndex + 1).filter((p) => p !== track.path);
    const before = this.queue.slice(0, this.queueIndex + 1);
    this.queue = [...before, track.path, ...existingAfter];
  }

  appendToQueue(tracks: Track[]): void {
    const existing = new Set(this.queue);
    const additions = tracks.map((t) => t.path).filter((p) => !existing.has(p));
    if (additions.length === 0) return;
    this.queue = [...this.queue, ...additions];
    if (!this.currentPath && this.queue.length > 0) {
      this.queueIndex = 0;
      void this.loadIndex(0);
    }
  }

  removeFromQueue(absIndex: number): void {
    if (absIndex <= this.queueIndex || absIndex >= this.queue.length) return;
    const copy = [...this.queue];
    copy.splice(absIndex, 1);
    this.queue = copy;
  }

  reorderUpNext(fromRel: number, toRel: number): void {
    const absFrom = this.queueIndex + 1 + fromRel;
    const absTo = this.queueIndex + 1 + toRel;
    if (
      absFrom <= this.queueIndex ||
      absTo <= this.queueIndex ||
      absFrom >= this.queue.length ||
      absTo >= this.queue.length ||
      absFrom === absTo
    ) {
      return;
    }
    const copy = [...this.queue];
    const [moved] = copy.splice(absFrom, 1);
    copy.splice(absTo, 0, moved);
    this.queue = copy;
  }

  private async loadIndex(index: number): Promise<void> {
    const path = this.queue[index];
    if (!path) return;
    const track = library.trackMap.get(path);
    this.currentPath = path;
    this.duration = track?.duration ?? 0;
    this.position = 0;
    this.smoothPosition = 0;
    this.anchorPos = 0;
    this.anchorPerf = performance.now();

    applyAppearance(track?.artColor ?? null);

    try {
      await api.audioPlay(path, 0);
      this.playing = true;
      this.anchorPerf = performance.now();
      if (track) {
        void library.markPlayed(track.path);
        void quranStore.loadTrack(track);
        void lyricsStore.loadForTrack(track);
      } else {
        void quranStore.loadTrack(null);
        void lyricsStore.loadForPath(path);
      }
      this.syncIntegrations();
    } catch (e) {
      this.playing = false;
      this.error = e instanceof Error ? e.message : String(e);
    }
  }

  async togglePlay(): Promise<void> {
    if (!this.currentPath) {
      if (library.tracks.length > 0) {
        await this.playTrack(library.tracks[0], library.tracks);
      }
      return;
    }
    const nextPlaying = !this.playing;
    await api.audioPause(!nextPlaying);
    this.playing = nextPlaying;
    this.anchorPos = this.smoothPosition;
    this.anchorPerf = performance.now();
    this.syncIntegrations();
  }

  async seek(secs: number): Promise<void> {
    const clamped = Math.max(0, Math.min(this.duration || secs, secs));
    this.position = clamped;
    this.smoothPosition = clamped;
    this.anchorPos = clamped;
    this.anchorPerf = performance.now();
    await api.audioSeek(clamped);
    this.syncIntegrations();
  }

  async stop(): Promise<void> {
    await api.audioStop();
    this.playing = false;
    this.position = 0;
    this.smoothPosition = 0;
    void api.discordClear();
    void api.smtcClear();
  }

  async next(manual = true): Promise<void> {
    const nextIdx = resolveNextIndex(this.queueIndex, this.queue.length, this.repeat, manual);
    if (nextIdx === null) {
      this.playing = false;
      this.position = 0;
      this.smoothPosition = 0;
      this.syncIntegrations();
      return;
    }
    if (nextIdx === this.queueIndex) {
      await this.seek(0);
      if (!this.playing) await this.togglePlay();
      return;
    }
    this.queueIndex = nextIdx;
    await this.loadIndex(nextIdx);
  }

  async prev(): Promise<void> {
    if (this.smoothPosition > 3.5) {
      await this.seek(0);
      return;
    }
    const prevIdx = resolvePrevIndex(this.queueIndex, this.queue.length, this.repeat);
    if (prevIdx === null) return;
    this.queueIndex = prevIdx;
    await this.loadIndex(prevIdx);
  }

  toggleShuffle(): void {
    this.shuffle = !this.shuffle;
    if (this.currentPath && this.queue.length > 1) {
      const rest = this.queue.filter((p) => p !== this.currentPath);
      if (this.shuffle) {
        for (let i = rest.length - 1; i > 0; i--) {
          const j = Math.floor(Math.random() * (i + 1));
          [rest[i], rest[j]] = [rest[j], rest[i]];
        }
        this.queue = [this.currentPath, ...rest];
        this.queueIndex = 0;
      }
    }
  }

  cycleRepeat(): void {
    this.repeat = this.repeat === 'off' ? 'all' : this.repeat === 'all' ? 'one' : 'off';
  }

  setVolume(vol: number): void {
    const v = Math.max(0, Math.min(1, vol));
    this.volume = v;
    if (v > 0 && this.muted) {
      this.muted = false;
      void api.audioSetMute(false);
    }
    void api.audioSetVolume(v);
    settings.volume = v;
    settings.save('volume', v.toFixed(2));
  }

  toggleMute(): void {
    this.muted = !this.muted;
    void api.audioSetMute(this.muted);
  }

  setSpeed(sp: number): void {
    const clamped = Math.max(0.5, Math.min(2.0, sp));
    this.speed = clamped;
    this.anchorPos = this.smoothPosition;
    this.anchorPerf = performance.now();
    void api.audioSetSpeed(clamped);
    settings.speed = clamped;
    settings.save('speed', clamped.toFixed(2));
  }

  setSleepMinutes(mins: number | null): void {
    if (this.sleepInterval) {
      clearInterval(this.sleepInterval);
      this.sleepInterval = null;
    }
    if (!mins || mins <= 0) {
      this.sleepEndAt = null;
      this.sleepRemainingSecs = 0;
      return;
    }
    this.sleepEndAt = Date.now() + mins * 60_000;
    this.sleepRemainingSecs = mins * 60;
    this.sleepInterval = setInterval(() => {
      if (!this.sleepEndAt) return;
      const rem = Math.max(0, Math.round((this.sleepEndAt - Date.now()) / 1000));
      this.sleepRemainingSecs = rem;
      if (rem <= 0) {
        this.setSleepMinutes(null);
        if (this.playing) void this.togglePlay();
      }
    }, 1000);
  }

  async toggleMiniMode(): Promise<void> {
    const win = getCurrentWindow();
    if (!this.miniMode) {
      const size = await win.innerSize();
      const sf = await win.scaleFactor();
      this.savedWinSize = {
        width: Math.round(size.width / sf),
        height: Math.round(size.height / sf),
      };
      await win.setMinSize(new LogicalSize(240, 132));
      await win.setSize(new LogicalSize(268, 140));
      await win.setDecorations(false);
      await win.setAlwaysOnTop(true);
      this.miniMode = true;
    } else {
      await win.setAlwaysOnTop(false);
      await win.setDecorations(true);
      await win.setMinSize(new LogicalSize(820, 580));
      const prev = this.savedWinSize ?? { width: 1220, height: 780 };
      await win.setSize(new LogicalSize(prev.width, prev.height));
      this.miniMode = false;
    }
  }

  syncIntegrations(): void {
    const t = this.currentTrack;
    if (!t) {
      void api.discordClear();
      void api.smtcClear();
      return;
    }
    this.lastDiscordPush = Date.now();
    void api.smtcUpdate({
      title: t.title,
      artist: t.artist,
      album: t.album,
      durationSecs: t.duration || this.duration,
      positionSecs: this.smoothPosition,
      playing: this.playing,
      artHash: t.artHash,
    });
    void api.discordUpdate({
      enabled: settings.discordEnabled,
      clientId: settings.discordClientId,
      title: t.title,
      artist: t.artist,
      album: t.album,
      positionSecs: this.smoothPosition,
      durationSecs: t.duration || this.duration,
      playing: this.playing,
    });
  }

  destroy(): void {
    if (this.rafId !== null) cancelAnimationFrame(this.rafId);
    if (this.sleepInterval) clearInterval(this.sleepInterval);
  }
}

export const player = new PlayerStore();
