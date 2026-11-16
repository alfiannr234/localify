<script lang="ts">
  import { save } from '@tauri-apps/plugin-dialog';
  import Icon from '../components/Icon.svelte';
  import TrackRow from '../components/TrackRow.svelte';
  import { api } from '../lib/api';
  import { formatTotalDuration } from '../lib/format';
  import { library } from '../lib/library.svelte';
  import { nav } from '../lib/nav.svelte';
  import { player } from '../lib/player.svelte';
  import type { Track } from '../lib/types';

  interface Props {
    likedOnly?: boolean;
    playlistId?: number;
  }

  let { likedOnly = false, playlistId }: Props = $props();
  let tracks = $state<Track[]>([]);
  let loading = $state(false);
  let renaming = $state(false);
  let renameVal = $state('');

  let playlist = $derived(
    playlistId ? library.playlists.find((p) => p.id === playlistId) ?? null : null
  );

  let title = $derived(likedOnly ? 'Favorites' : (playlist?.name ?? 'Playlist'));

  $effect(() => {
    if (likedOnly) {
      tracks = library.likedTracks;
      return;
    }
    if (playlistId) {
      loading = true;
      void library
        .loadPlaylistTracks(playlistId)
        .then((res) => (tracks = res))
        .finally(() => (loading = false));
    }
  });

  let totalDur = $derived(tracks.reduce((a, t) => a + t.duration, 0));

  async function saveRename(): Promise<void> {
    if (!playlist || !renameVal.trim()) {
      renaming = false;
      return;
    }
    await api.renamePlaylist(playlist.id, renameVal.trim());
    await library.loadPlaylists();
    renaming = false;
  }

  async function handleExport(): Promise<void> {
    if (!playlist) return;
    const dest = await save({
      defaultPath: `${playlist.name}.m3u8`,
      filters: [{ name: 'M3U8 Playlist', extensions: ['m3u8'] }],
    });
    if (dest) {
      await api.exportM3u8(playlist.id, dest);
    }
  }

  async function handleDelete(): Promise<void> {
    if (!playlist) return;
    await library.deletePlaylist(playlist.id);
    nav.go({ kind: 'home' });
  }
</script>

<div class="pl-page scroll-thin">
  <div class="pl-head">
    <div class="pl-meta">
      <span class="kicker">{likedOnly ? 'COLLECTION' : 'PLAYLIST'}</span>
      {#if renaming}
        <form
          onsubmit={(e) => {
            e.preventDefault();
            void saveRename();
          }}
        >
          <input class="input" bind:value={renameVal} onblur={() => (renaming = false)} />
        </form>
      {:else}
        <h1>{title}</h1>
      {/if}
      <p class="sub tabular">{tracks.length} tracks / {formatTotalDuration(totalDur)}</p>
    </div>

    <div class="pl-actions">
      <button
        type="button"
        class="btn btn-primary"
        disabled={tracks.length === 0}
        onclick={() => {
          player.shuffle = false;
          if (tracks[0]) void player.playTrack(tracks[0], tracks);
        }}
      >
        <Icon name="play" size={16} />
        <span>Play</span>
      </button>
      <button
        type="button"
        class="btn"
        disabled={tracks.length === 0}
        onclick={() => {
          player.shuffle = true;
          const pick = tracks[Math.floor(Math.random() * tracks.length)];
          if (pick) void player.playTrack(pick, tracks);
        }}
      >
        <Icon name="shuffle" size={16} />
        <span>Shuffle</span>
      </button>

      {#if !likedOnly && playlist}
        <button
          type="button"
          class="btn"
          onclick={() => {
            renameVal = playlist.name;
            renaming = true;
          }}
        >
          <Icon name="edit" size={15} />
          <span>Rename</span>
        </button>
        <button type="button" class="btn" onclick={handleExport}>
          <Icon name="download" size={15} />
          <span>Export M3U8</span>
        </button>
        <button type="button" class="btn btn-danger" onclick={handleDelete}>
          <Icon name="trash" size={15} />
          <span>Delete</span>
        </button>
      {/if}
    </div>
  </div>

  {#if loading}
    <div class="empty">Loading tracks...</div>
  {:else if tracks.length === 0}
    <div class="empty">
      {likedOnly
        ? 'Heart tracks as you listen to collect your favorites here.'
        : 'Right-click any track to add it to this playlist.'}
    </div>
  {:else}
    <div class="track-list">
      {#each tracks as track, idx (track.path + '-' + idx)}
        <TrackRow {track} index={idx} contextTracks={tracks} />
      {/each}
    </div>
  {/if}
</div>

<style>
  .pl-page {
    height: 100%;
    overflow-y: auto;
    padding: 24px 28px 36px;
    display: flex;
    flex-direction: column;
    gap: 20px;
  }
  .pl-head {
    display: flex;
    justify-content: space-between;
    align-items: flex-end;
    flex-wrap: wrap;
    gap: 16px;
    padding-bottom: 16px;
    border-bottom: 1px solid var(--border);
  }
  .kicker {
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.08em;
    color: var(--accent);
  }
  .pl-meta h1 {
    margin: 4px 0 0;
    font-size: 26px;
    font-weight: 700;
  }
  .sub {
    margin: 4px 0 0;
    font-size: 13px;
    color: var(--text-2);
  }
  .pl-actions {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .btn-danger:hover:not(:disabled) {
    color: var(--danger);
    border-color: var(--danger);
  }
  .track-list {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .empty {
    margin: auto;
    color: var(--text-3);
    text-align: center;
  }
</style>
