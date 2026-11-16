import type { Track } from './types';

export interface ContextMenuState {
  x: number;
  y: number;
  tracks: Track[];
}

class MenuStore {
  state = $state<ContextMenuState | null>(null);
  tagEditorPath = $state<string | null>(null);

  openForTracks(e: MouseEvent, tracks: Track[]): void {
    e.preventDefault();
    e.stopPropagation();
    if (tracks.length === 0) return;
    const x = Math.min(e.clientX, window.innerWidth - 230);
    const y = Math.min(e.clientY, window.innerHeight - 310);
    this.state = { x: Math.max(8, x), y: Math.max(8, y), tracks };
  }

  close(): void {
    this.state = null;
  }

  openTagEditor(path: string): void {
    this.close();
    this.tagEditorPath = path;
  }

  closeTagEditor(): void {
    this.tagEditorPath = null;
  }
}

export const menuStore = new MenuStore();
