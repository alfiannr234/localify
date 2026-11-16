<script lang="ts">
  import Artwork from '../components/Artwork.svelte';
  import Icon from '../components/Icon.svelte';
  import { library } from '../lib/library.svelte';
  import { nav } from '../lib/nav.svelte';
  import { player } from '../lib/player.svelte';

  let filter = $state('');
  let albums = $derived.by(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return library.albums;
    return library.albums.filter(
      (a) => a.name.toLowerCase().includes(q) || a.artist.toLowerCase().includes(q)
    );
  });
</script>

<div class="albums-page scroll-thin">
  <div class="head">
    <div>
      <h1>Albums</h1>
      <p class="sub tabular">{albums.length} albums</p>
    </div>
    <input class="input filter-input" placeholder="Filter albums..." bind:value={filter} />
  </div>

  {#if albums.length === 0}
    <div class="empty">No matching albums found.</div>
  {:else}
    <div class="grid">
      {#each albums as album (album.key)}
        <div
          class="album-card"
          role="button"
          tabindex="0"
          onclick={() => nav.go({ kind: 'album', name: album.name, artist: album.artist })}
          onkeydown={(e) => {
            if (e.key === 'Enter') nav.go({ kind: 'album', name: album.name, artist: album.artist });
          }}
        >
          <div class="art-box">
            <Artwork hash={album.artHash} alt={album.name} size={172} radius={10} />
            <button
              type="button"
              class="play-fab"
              aria-label={`Play ${album.name}`}
              onclick={(e) => {
                e.stopPropagation();
                if (album.tracks.length > 0) {
                  void player.playTrack(album.tracks[0], album.tracks);
                }
              }}
            >
              <Icon name="play" size={18} />
            </button>
          </div>
          <div class="a-title truncate">{album.name}</div>
          <div class="a-sub truncate">
            {album.artist}{album.year ? ` / ${album.year}` : ''}
          </div>
        </div>
      {/each}
    </div>
  {/if}
</div>

<style>
  .albums-page {
    height: 100%;
    overflow-y: auto;
    padding: 20px 28px 36px;
    display: flex;
    flex-direction: column;
    gap: 18px;
  }
  .head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
  }
  .head h1 {
    margin: 0;
    font-size: 22px;
    font-weight: 700;
  }
  .sub {
    margin: 2px 0 0;
    font-size: 12.5px;
    color: var(--text-2);
  }
  .filter-input {
    width: 220px;
  }
  .grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(186px, 1fr));
    gap: 18px;
  }
  .album-card {
    padding: 10px;
    border-radius: var(--r-md);
    border: 1px solid var(--border);
    background: var(--surface);
    display: flex;
    flex-direction: column;
    gap: 8px;
    transition: background 140ms var(--ease), transform 140ms var(--ease);
  }
  .album-card:hover {
    background: var(--surface-2);
    transform: translateY(-2px);
  }
  .art-box {
    position: relative;
    display: flex;
    justify-content: center;
  }
  .play-fab {
    position: absolute;
    right: 8px;
    bottom: 8px;
    width: 38px;
    height: 38px;
    border-radius: 999px;
    border: none;
    background: var(--accent);
    color: var(--accent-fg);
    display: flex;
    align-items: center;
    justify-content: center;
    opacity: 0;
    transform: translateY(6px);
    box-shadow: var(--shadow-sm);
    transition: opacity 150ms var(--ease), transform 150ms var(--ease);
  }
  .album-card:hover .play-fab {
    opacity: 1;
    transform: translateY(0);
  }
  .a-title {
    font-size: 13.5px;
    font-weight: 600;
  }
  .a-sub {
    font-size: 12px;
    color: var(--text-2);
    margin-top: -4px;
  }
  .empty {
    margin: auto;
    color: var(--text-3);
  }
</style>
