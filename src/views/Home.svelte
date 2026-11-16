<script lang="ts">
  import { open } from '@tauri-apps/plugin-dialog';
  import Artwork from '../components/Artwork.svelte';
  import Icon from '../components/Icon.svelte';
  import TrackRow from '../components/TrackRow.svelte';
  import { formatTotalDuration } from '../lib/format';
  import { library } from '../lib/library.svelte';
  import { nav } from '../lib/nav.svelte';
  import { player } from '../lib/player.svelte';

  let totalDuration = $derived(library.tracks.reduce((acc, t) => acc + t.duration, 0));

  async function addMusicFolder(): Promise<void> {
    const picked = await open({ directory: true, multiple: false, title: 'Select music folder' });
    if (picked && !Array.isArray(picked)) {
      await library.addFolder(picked);
    }
  }
</script>

<div class="home scroll-thin">
  {#if library.loading}
    <div class="state-box">Loading your library...</div>
  {:else if library.error}
    <div class="state-box err">
      <p>Failed to load library: {library.error}</p>
      <button type="button" class="btn btn-primary" onclick={() => library.init()}>Retry</button>
    </div>
  {:else if library.tracks.length === 0}
    <div class="welcome">
      <img src="/localify.png" alt="Localify" class="w-logo" />
      <h1>Welcome to Localify</h1>
      <p>
        Point Localify to your music folder to index your lossless FLAC, MP3, WAV, M4A, and OGG files locally.
      </p>
      <div class="w-actions">
        <button type="button" class="btn btn-primary" onclick={addMusicFolder}>
          <Icon name="folder-plus" size={17} />
          <span>Add Music Folder</span>
        </button>
        <button type="button" class="btn" onclick={() => nav.go({ kind: 'settings' })}>
          Open Settings
        </button>
      </div>
    </div>
  {:else}
    <section class="hero">
      <div>
        <h1>Library</h1>
        <p class="stats tabular">
          {library.tracks.length} songs / {library.albums.length} albums / {library.artists.length} artists / {formatTotalDuration(totalDuration)}
        </p>
      </div>
      <div class="hero-actions">
        <button
          type="button"
          class="btn btn-primary"
          onclick={() => {
            if (library.tracks.length > 0) {
              player.shuffle = false;
              void player.playTrack(library.tracks[0], library.tracks);
            }
          }}
        >
          <Icon name="play" size={16} />
          <span>Play All</span>
        </button>
        <button
          type="button"
          class="btn"
          onclick={() => {
            if (library.tracks.length > 0) {
              player.shuffle = true;
              const rand = library.tracks[Math.floor(Math.random() * library.tracks.length)];
              void player.playTrack(rand, library.tracks);
            }
          }}
        >
          <Icon name="shuffle" size={16} />
          <span>Shuffle All</span>
        </button>
      </div>
    </section>

    {#if library.recentlyPlayed.length > 0}
      <section class="sec">
        <div class="sec-head">
          <h2>Recently Played</h2>
        </div>
        <div class="cards-grid">
          {#each library.recentlyPlayed.slice(0, 8) as t (t.path)}
            <button
              type="button"
              class="card"
              onclick={() => player.playTrack(t, library.recentlyPlayed)}
            >
              <Artwork hash={t.artHash} alt={t.album} size={146} radius={10} />
              <div class="card-title truncate">{t.title}</div>
              <div class="card-sub truncate">{t.artist}</div>
            </button>
          {/each}
        </div>
      </section>
    {/if}

    <section class="sec">
      <div class="sec-head">
        <h2>Recently Added</h2>
        <button type="button" class="link-btn" onclick={() => nav.go({ kind: 'songs' })}>
          View all songs
        </button>
      </div>
      <div class="rows-wrap">
        {#each library.recentlyAdded.slice(0, 12) as track, idx (track.path)}
          <TrackRow {track} index={idx} contextTracks={library.tracks} />
        {/each}
      </div>
    </section>
  {/if}
</div>

<style>
  .home {
    height: 100%;
    overflow-y: auto;
    padding: 24px 28px 40px;
    display: flex;
    flex-direction: column;
    gap: 28px;
  }
  .state-box {
    margin: auto;
    text-align: center;
    color: var(--text-2);
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 12px;
  }
  .state-box.err {
    color: var(--danger);
  }
  .welcome {
    margin: auto;
    max-width: 440px;
    text-align: center;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 14px;
  }
  .w-logo {
    width: 64px;
    height: 64px;
    border-radius: 14px;
  }
  .welcome h1 {
    margin: 0;
    font-size: 24px;
    font-weight: 700;
  }
  .welcome p {
    margin: 0;
    color: var(--text-2);
    line-height: 1.55;
  }
  .w-actions {
    display: flex;
    gap: 10px;
    margin-top: 6px;
  }
  .hero {
    display: flex;
    align-items: flex-end;
    justify-content: space-between;
    flex-wrap: wrap;
    gap: 16px;
  }
  .hero h1 {
    margin: 0;
    font-size: 26px;
    font-weight: 700;
    letter-spacing: -0.02em;
  }
  .stats {
    margin: 4px 0 0;
    color: var(--text-2);
    font-size: 13px;
  }
  .hero-actions {
    display: flex;
    gap: 10px;
  }
  .sec {
    display: flex;
    flex-direction: column;
    gap: 12px;
  }
  .sec-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .sec-head h2 {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
  }
  .link-btn {
    border: none;
    background: transparent;
    color: var(--accent);
    font-size: 12.5px;
    font-weight: 600;
    padding: 4px 8px;
    border-radius: var(--r-sm);
  }
  .link-btn:hover {
    background: var(--accent-soft);
  }
  .cards-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(162px, 1fr));
    gap: 14px;
  }
  .card {
    border: 1px solid var(--border);
    background: var(--surface);
    border-radius: var(--r-md);
    padding: 10px;
    text-align: left;
    display: flex;
    flex-direction: column;
    gap: 8px;
    transition: background 140ms var(--ease), border-color 140ms var(--ease), transform 140ms var(--ease);
  }
  .card:hover {
    background: var(--surface-2);
    border-color: var(--border-strong);
    transform: translateY(-2px);
  }
  .card-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--text);
    width: 100%;
  }
  .card-sub {
    font-size: 12px;
    color: var(--text-2);
    width: 100%;
    margin-top: -4px;
  }
  .rows-wrap {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
</style>
