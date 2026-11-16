<script lang="ts">
  import { open } from '@tauri-apps/plugin-dialog';
  import { api } from '../lib/api';
  import { learnStore } from '../lib/learn.svelte';
  import { library } from '../lib/library.svelte';
  import { nav } from '../lib/nav.svelte';
  import { dueVocabItems } from '../lib/vocab';
  import type { ViewRoute } from '../lib/types';
  import Icon from './Icon.svelte';

  let dueCount = $derived(dueVocabItems(learnStore.vocab).length);

  let creating = $state(false);
  let newName = $state('');

  function isActive(kind: ViewRoute['kind'], playlistId?: number): boolean {
    const cur = nav.current;
    if (kind === 'playlist' && cur.kind === 'playlist') {
      return cur.id === playlistId;
    }
    return cur.kind === kind;
  }

  async function submitCreate(): Promise<void> {
    const name = newName.trim();
    if (!name) {
      creating = false;
      return;
    }
    const created = await library.createPlaylist(name);
    newName = '';
    creating = false;
    nav.go({ kind: 'playlist', id: created.id });
  }

  async function handleImportM3u(): Promise<void> {
    const selected = await open({
      multiple: false,
      filters: [{ name: 'Playlists', extensions: ['m3u8', 'm3u'] }],
    });
    if (!selected || Array.isArray(selected)) return;
    const base = selected.split(/[\\/]/).pop()?.replace(/\.(m3u8|m3u)$/i, '') || 'Imported';
    const pl = await api.importM3u8(base, selected);
    await library.loadPlaylists();
    nav.go({ kind: 'playlist', id: pl.id });
  }
</script>

<aside class="sidebar" aria-label="Primary navigation">
  <div class="brand">
    <img src="/localify.png" alt="Localify" class="brand-logo" />
    <span class="brand-name">Localify</span>
  </div>

  <nav class="nav-list scroll-thin">
    <div class="nav-group">
      <button
        type="button"
        class="nav-item"
        class:active={isActive('home')}
        onclick={() => nav.go({ kind: 'home' })}
      >
        <Icon name="home" size={18} />
        <span>Home</span>
      </button>
      <button
        type="button"
        class="nav-item"
        class:active={isActive('songs')}
        onclick={() => nav.go({ kind: 'songs' })}
      >
        <Icon name="songs" size={18} />
        <span>Songs</span>
        {#if library.tracks.length > 0}
          <span class="count tabular">{library.tracks.length}</span>
        {/if}
      </button>
      <button
        type="button"
        class="nav-item"
        class:active={isActive('albums') || isActive('album')}
        onclick={() => nav.go({ kind: 'albums' })}
      >
        <Icon name="albums" size={18} />
        <span>Albums</span>
      </button>
      <button
        type="button"
        class="nav-item"
        class:active={isActive('artists')}
        onclick={() => nav.go({ kind: 'artists' })}
      >
        <Icon name="artists" size={18} />
        <span>Artists</span>
      </button>
      <button
        type="button"
        class="nav-item"
        class:active={isActive('genres')}
        onclick={() => nav.go({ kind: 'genres' })}
      >
        <Icon name="genres" size={18} />
        <span>Genres</span>
      </button>
      <button
        type="button"
        class="nav-item"
        class:active={isActive('liked')}
        onclick={() => nav.go({ kind: 'liked' })}
      >
        <Icon name="heart" size={18} />
        <span>Favorites</span>
      </button>
      <button
        type="button"
        class="nav-item"
        class:active={isActive('eq')}
        onclick={() => nav.go({ kind: 'eq' })}
      >
        <Icon name="eq" size={18} />
        <span>Equalizer</span>
      </button>
      <button
        type="button"
        class="nav-item"
        class:active={isActive('downloads')}
        onclick={() => nav.go({ kind: 'downloads' })}
      >
        <Icon name="download" size={18} />
        <span>Downloads</span>
      </button>
      <button
        type="button"
        class="nav-item"
        class:active={isActive('vocabulary')}
        onclick={() => nav.go({ kind: 'vocabulary' })}
      >
        <Icon name="book-open" size={18} />
        <span>Vocabulary</span>
        {#if dueCount > 0}
          <span class="count tabular">{dueCount}</span>
        {/if}
      </button>
    </div>

    <div class="section-head">
      <span>PLAYLISTS</span>
      <div class="section-actions">
        <button
          type="button"
          class="btn-icon tiny"
          title="Import M3U8 playlist"
          aria-label="Import M3U8 playlist"
          onclick={handleImportM3u}
        >
          <Icon name="upload" size={14} />
        </button>
        <button
          type="button"
          class="btn-icon tiny"
          title="New playlist"
          aria-label="New playlist"
          onclick={() => (creating = !creating)}
        >
          <Icon name="plus" size={14} />
        </button>
      </div>
    </div>

    {#if creating}
      <form
        class="new-pl-form"
        onsubmit={(e) => {
          e.preventDefault();
          void submitCreate();
        }}
      >
        <input
          type="text"
          class="input pl-input"
          placeholder="Playlist name"
          bind:value={newName}
          onkeydown={(e) => e.key === 'Escape' && (creating = false)}
        />
      </form>
    {/if}

    <div class="nav-group">
      {#each library.playlists as pl (pl.id)}
        <button
          type="button"
          class="nav-item"
          class:active={isActive('playlist', pl.id)}
          onclick={() => nav.go({ kind: 'playlist', id: pl.id })}
        >
          <Icon name="playlists" size={17} />
          <span class="truncate">{pl.name}</span>
          <span class="count tabular">{pl.trackCount}</span>
        </button>
      {/each}
    </div>
  </nav>

  {#if library.scanning}
    <div class="scan-bar" role="status">
      <Icon name="refresh" size={14} class="spin" />
      <span class="truncate">
        Scanning {library.scanTotal > 0 ? `${library.scanCurrent}/${library.scanTotal}` : 'library'}
      </span>
    </div>
  {/if}

  <div class="footer">
    <button
      type="button"
      class="nav-item"
      class:active={isActive('settings')}
      onclick={() => nav.go({ kind: 'settings' })}
    >
      <Icon name="settings" size={18} />
      <span>Settings</span>
    </button>
  </div>
</aside>

<style>
  .sidebar {
    width: var(--sidebar-w);
    height: 100%;
    background: var(--bg-elev);
    border-right: 1px solid var(--border);
    display: flex;
    flex-direction: column;
    flex-shrink: 0;
  }
  .brand {
    height: var(--topbar-h);
    padding: 0 16px;
    display: flex;
    align-items: center;
    gap: 10px;
    border-bottom: 1px solid var(--border);
  }
  .brand-logo {
    width: 26px;
    height: 26px;
    border-radius: 6px;
    object-fit: contain;
  }
  .brand-name {
    font-size: 16px;
    font-weight: 700;
    letter-spacing: -0.02em;
    color: var(--text);
  }
  .nav-list {
    flex: 1;
    overflow-y: auto;
    padding: 12px 10px;
  }
  .nav-group {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .nav-item {
    display: flex;
    align-items: center;
    gap: 10px;
    width: 100%;
    height: 35px;
    padding: 0 10px;
    border: none;
    border-radius: var(--r-sm);
    background: transparent;
    color: var(--text-2);
    font-weight: 500;
    text-align: left;
    transition: background 120ms var(--ease), color 120ms var(--ease);
  }
  .nav-item:hover {
    background: var(--surface-2);
    color: var(--text);
  }
  .nav-item.active {
    background: var(--accent-soft);
    color: var(--accent);
    font-weight: 600;
  }
  .count {
    margin-left: auto;
    font-size: 11px;
    color: var(--text-3);
  }
  .nav-item.active .count {
    color: var(--accent);
  }
  .section-head {
    margin: 18px 6px 6px 10px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 11px;
    font-weight: 600;
    letter-spacing: 0.06em;
    color: var(--text-3);
  }
  .section-actions {
    display: flex;
    align-items: center;
    gap: 2px;
  }
  .btn-icon.tiny {
    width: 24px;
    height: 24px;
  }
  .new-pl-form {
    margin: 0 4px 8px;
  }
  .pl-input {
    width: 100%;
    height: 30px;
    font-size: 12.5px;
  }
  .scan-bar {
    margin: 8px 10px 0;
    padding: 7px 10px;
    border-radius: var(--r-sm);
    background: var(--surface-2);
    color: var(--text-2);
    font-size: 12px;
    display: flex;
    align-items: center;
    gap: 8px;
  }
  :global(.spin) {
    animation: spin360 1.2s linear infinite;
  }
  @keyframes spin360 {
    from {
      transform: rotate(0deg);
    }
    to {
      transform: rotate(360deg);
    }
  }
  .footer {
    padding: 8px 10px 10px;
    border-top: 1px solid var(--border);
  }
</style>
