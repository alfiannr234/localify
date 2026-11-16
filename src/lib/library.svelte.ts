import { api, setCachedArtDir } from './api';
import type { PlaylistSummary, Track } from './types';

export interface AlbumGroup {
  key: string;
  name: string;
  artist: string;
  year: number | null;
  artHash: string | null;
  artColor: string | null;
  tracks: Track[];
  duration: number;
}

export interface ArtistGroup {
  name: string;
  tracks: Track[];
  albums: AlbumGroup[];
  artHash: string | null;
}

class LibraryStore {
  tracks = $state<Track[]>([]);
  folders = $state<string[]>([]);
  playlists = $state<PlaylistSummary[]>([]);
  recentPaths = $state<string[]>([]);
  playlistTracksMap = $state<Record<number, string[]>>({});
  loading = $state(true);
  scanning = $state(false);
  scanCurrent = $state(0);
  scanTotal = $state(0);
  error = $state<string | null>(null);

  trackMap = $derived.by(() => {
    const map = new Map<string, Track>();
    for (const t of this.tracks) map.set(t.path, t);
    return map;
  });

  likedTracks = $derived.by(() => this.tracks.filter((t) => t.liked));

  recentlyPlayed = $derived.by(() => {
    const map = this.trackMap;
    return this.recentPaths.map((p) => map.get(p)).filter((t): t is Track => Boolean(t));
  });

  recentlyAdded = $derived.by(() =>
    [...this.tracks].sort((a, b) => b.addedAt - a.addedAt).slice(0, 16)
  );

  albums = $derived.by(() => {
    const groups = new Map<string, AlbumGroup>();
    for (const t of this.tracks) {
      const artist = t.albumArtist || t.artist || 'Unknown Artist';
      const name = t.album || 'Unknown Album';
      const key = `${name.toLowerCase()}::${artist.toLowerCase()}`;
      let g = groups.get(key);
      if (!g) {
        g = {
          key,
          name,
          artist,
          year: t.year,
          artHash: t.artHash,
          artColor: t.artColor,
          tracks: [],
          duration: 0,
        };
        groups.set(key, g);
      }
      g.tracks.push(t);
      g.duration += t.duration;
      if (!g.artHash && t.artHash) {
        g.artHash = t.artHash;
        g.artColor = t.artColor;
      }
      if (!g.year && t.year) g.year = t.year;
    }
    for (const g of groups.values()) {
      g.tracks.sort(
        (a, b) => (a.discNo ?? 1) - (b.discNo ?? 1) || (a.trackNo ?? 999) - (b.trackNo ?? 999)
      );
    }
    return [...groups.values()].sort((a, b) => a.name.localeCompare(b.name));
  });

  artists = $derived.by(() => {
    const map = new Map<string, Track[]>();
    for (const t of this.tracks) {
      const name = t.albumArtist || t.artist || 'Unknown Artist';
      const list = map.get(name);
      if (list) list.push(t);
      else map.set(name, [t]);
    }
    const out: ArtistGroup[] = [];
    for (const [name, list] of map.entries()) {
      const sorted = [...list].sort((a, b) => a.album.localeCompare(b.album));
      const albumSet = this.albums.filter((al) => al.artist === name);
      out.push({
        name,
        tracks: sorted,
        albums: albumSet,
        artHash: sorted.find((t) => t.artHash)?.artHash ?? null,
      });
    }
    return out.sort((a, b) => a.name.localeCompare(b.name));
  });

  genres = $derived.by(() => {
    const map = new Map<string, Track[]>();
    for (const t of this.tracks) {
      const g = t.genre.trim() || 'Unspecified';
      const list = map.get(g);
      if (list) list.push(t);
      else map.set(g, [t]);
    }
    return [...map.entries()]
      .map(([name, tracks]) => ({ name, tracks }))
      .sort((a, b) => b.tracks.length - a.tracks.length);
  });

  async init(): Promise<void> {
    this.loading = true;
    this.error = null;
    try {
      const [artDir, tracks, folders, playlists, recent] = await Promise.all([
        api.getArtDir(),
        api.getLibrary(),
        api.getFolders(),
        api.getPlaylists(),
        api.getRecentlyPlayed(24),
      ]);
      setCachedArtDir(artDir);
      this.tracks = tracks;
      this.folders = folders;
      this.playlists = playlists;
      this.recentPaths = recent;
    } catch (e) {
      this.error = e instanceof Error ? e.message : String(e);
    } finally {
      this.loading = false;
    }
  }

  async refreshFromDb(): Promise<void> {
    const [tracks, recent] = await Promise.all([api.getLibrary(), api.getRecentlyPlayed(24)]);
    this.tracks = tracks;
    this.recentPaths = recent;
  }

  async addFolder(path: string): Promise<void> {
    this.scanning = true;
    try {
      this.tracks = await api.addFolder(path);
      this.folders = await api.getFolders();
    } finally {
      this.scanning = false;
    }
  }

  async removeFolder(path: string): Promise<void> {
    this.tracks = await api.removeFolder(path);
    this.folders = await api.getFolders();
  }

  async rescan(): Promise<void> {
    this.scanning = true;
    try {
      this.tracks = await api.scanFolders();
    } finally {
      this.scanning = false;
    }
  }

  async toggleLike(path: string): Promise<boolean> {
    const liked = await api.toggleLike(path);
    this.tracks = this.tracks.map((t) => (t.path === path ? { ...t, liked } : t));
    return liked;
  }

  async loadPlaylists(): Promise<void> {
    this.playlists = await api.getPlaylists();
  }

  async loadPlaylistTracks(id: number): Promise<Track[]> {
    const paths = await api.getPlaylistTracks(id);
    this.playlistTracksMap = { ...this.playlistTracksMap, [id]: paths };
    const map = this.trackMap;
    return paths.map((p) => map.get(p)).filter((t): t is Track => Boolean(t));
  }

  async createPlaylist(name: string): Promise<PlaylistSummary> {
    const pl = await api.createPlaylist(name);
    await this.loadPlaylists();
    return pl;
  }

  async deletePlaylist(id: number): Promise<void> {
    await api.deletePlaylist(id);
    await this.loadPlaylists();
  }

  async addToPlaylist(playlistId: number, paths: string[]): Promise<void> {
    await api.addTracksToPlaylist(playlistId, paths);
    await this.loadPlaylists();
    await this.loadPlaylistTracks(playlistId);
  }

  async markPlayed(path: string): Promise<void> {
    try {
      const count = await api.recordPlay(path);
      this.tracks = this.tracks.map((t) => (t.path === path ? { ...t, playCount: count } : t));
      this.recentPaths = [path, ...this.recentPaths.filter((p) => p !== path)].slice(0, 24);
    } catch {
      // ignore play history write failures
    }
  }

  searchTracks(query: string): Track[] {
    const q = query.trim().toLowerCase();
    if (!q) return [];
    const tokens = q.split(/\s+/);
    return this.tracks.filter((t) => {
      const hay = `${t.title} ${t.artist} ${t.album} ${t.genre}`.toLowerCase();
      return tokens.every((tok) => hay.includes(tok));
    });
  }
}

export const library = new LibraryStore();
