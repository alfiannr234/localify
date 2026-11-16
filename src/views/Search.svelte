<script lang="ts">
  import Artwork from '../components/Artwork.svelte';
  import Icon from '../components/Icon.svelte';
  import TrackRow from '../components/TrackRow.svelte';
  import { library } from '../lib/library.svelte';
  import { nav } from '../lib/nav.svelte';
  import { player } from '../lib/player.svelte';

  let query = $derived(nav.searchQuery);
  let matchedTracks = $derived(library.searchTracks(query));
  let matchedAlbums = $derived.by(() => {
    const q = query.trim().toLowerCase();
    if (!q) return [];
    return library.albums
      .filter((a) => a.name.toLowerCase().includes(q) || a.artist.toLowerCase().includes(q))
      .slice(0, 6);
  });
</script>

<div class="search-page scroll-thin">
  <div class="s-head">
    <div>
      <h1>Search Results</h1>
      <p class="sub">
        {#if query.trim()}
          <span class="tabular">{matchedTracks.length}</span> tracks matching "{query.trim()}"
        {:else}
          Type in the search bar above to search songs, artists, or albums.
        {/if}
      </p>
    </div>

    {#if matchedTracks.length > 0}
      <button
        type="button"
        class="btn"
        onclick={() => player.playTrack(matchedTracks[0], matchedTracks)}
      >
        <Icon name="play" size={15} />
        <span>Play Results Only</span>
      </button>
    {/if}
  </div>

  {#if !query.trim()}
    <div class="empty">Start typing to find tracks across your library.</div>
  {:else if matchedTracks.length === 0 && matchedAlbums.length === 0}
    <div class="empty">No songs or albums match "{query.trim()}".</div>
  {:else}
    {#if matchedAlbums.length > 0}
      <section class="sec">
        <h2>Albums</h2>
        <div class="album-row">
          {#each matchedAlbums as al (al.key)}
            <button
              type="button"
              class="al-card"
              onclick={() => nav.go({ kind: 'album', name: al.name, artist: al.artist })}
            >
              <Artwork hash={al.artHash} alt={al.name} size={110} radius={8} />
              <div class="al-name truncate">{al.name}</div>
              <div class="al-ar truncate">{al.artist}</div>
            </button>
          {/each}
        </div>
      </section>
    {/if}

    {#if matchedTracks.length > 0}
      <section class="sec">
        <h2>Tracks</h2>
        <div class="rows">
          <!-- Pass full library.tracks as context so Next continues playing the rest of the library cleanly -->
          {#each matchedTracks as track, idx (track.path)}
            <TrackRow
              {track}
              index={idx}
              contextTracks={library.tracks}
              onPlay={() => player.playTrack(track, library.tracks)}
            />
          {/each}
        </div>
      </section>
    {/if}
  {/if}
</div>

<style>
  .search-page {
    height: 100%;
    overflow-y: auto;
    padding: 22px 28px 36px;
    display: flex;
    flex-direction: column;
    gap: 22px;
  }
  .s-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    padding-bottom: 14px;
    border-bottom: 1px solid var(--border);
  }
  .s-head h1 {
    margin: 0;
    font-size: 22px;
    font-weight: 700;
  }
  .sub {
    margin: 3px 0 0;
    color: var(--text-2);
    font-size: 13px;
  }
  .sec h2 {
    margin: 0 0 10px;
    font-size: 15px;
    font-weight: 700;
  }
  .album-row {
    display: flex;
    gap: 12px;
    overflow-x: auto;
    padding-bottom: 4px;
  }
  .al-card {
    width: 126px;
    flex-shrink: 0;
    border: 1px solid var(--border);
    background: var(--surface);
    border-radius: var(--r-md);
    padding: 8px;
    text-align: left;
    display: flex;
    flex-direction: column;
    gap: 5px;
  }
  .al-card:hover {
    background: var(--surface-2);
  }
  .al-name {
    font-size: 12.5px;
    font-weight: 600;
    width: 100%;
  }
  .al-ar {
    font-size: 11.5px;
    color: var(--text-2);
    width: 100%;
  }
  .rows {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .empty {
    margin: auto;
    color: var(--text-3);
  }
</style>
