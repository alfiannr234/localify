<script lang="ts">
  import Artwork from '../components/Artwork.svelte';
  import Icon from '../components/Icon.svelte';
  import TrackRow from '../components/TrackRow.svelte';
  import { library } from '../lib/library.svelte';
  import { nav } from '../lib/nav.svelte';
  import { player } from '../lib/player.svelte';

  interface Props {
    selected?: string;
  }

  let { selected }: Props = $props();
  let filter = $state('');
  let activeName = $state('');

  $effect(() => {
    if (selected) {
      activeName = selected;
    } else if (!activeName && library.artists.length > 0) {
      activeName = library.artists[0].name;
    }
  });

  let filteredArtists = $derived.by(() => {
    const q = filter.trim().toLowerCase();
    if (!q) return library.artists;
    return library.artists.filter((a) => a.name.toLowerCase().includes(q));
  });

  let current = $derived(
    library.artists.find((a) => a.name === activeName) ?? filteredArtists[0] ?? null
  );
</script>

<div class="artists-split">
  <div class="left-pane">
    <div class="left-head">
      <input class="input" placeholder="Filter artists..." bind:value={filter} />
    </div>
    <div class="artist-list scroll-thin">
      {#each filteredArtists as ar (ar.name)}
        <button
          type="button"
          class="ar-btn"
          class:active={current?.name === ar.name}
          onclick={() => (activeName = ar.name)}
        >
          <Artwork hash={ar.artHash} alt={ar.name} size={34} radius={999} />
          <div class="ar-meta">
            <span class="ar-name truncate">{ar.name}</span>
            <span class="ar-count tabular">{ar.tracks.length} songs</span>
          </div>
        </button>
      {/each}
    </div>
  </div>

  <div class="right-pane scroll-thin">
    {#if !current}
      <div class="empty">No artists found.</div>
    {:else}
      <div class="ar-hero">
        <div>
          <h1>{current.name}</h1>
          <p class="tabular">{current.albums.length} albums / {current.tracks.length} tracks</p>
        </div>
        <button
          type="button"
          class="btn btn-primary"
          onclick={() => current.tracks[0] && player.playTrack(current.tracks[0], current.tracks)}
        >
          <Icon name="play" size={16} />
          <span>Play All</span>
        </button>
      </div>

      {#if current.albums.length > 0}
        <div class="album-strip">
          {#each current.albums as al (al.key)}
            <button
              type="button"
              class="al-chip"
              onclick={() => nav.go({ kind: 'album', name: al.name, artist: al.artist })}
            >
              <Artwork hash={al.artHash} alt={al.name} size={118} radius={8} />
              <span class="al-name truncate">{al.name}</span>
              {#if al.year}
                <span class="al-yr tabular">{al.year}</span>
              {/if}
            </button>
          {/each}
        </div>
      {/if}

      <div class="tracks-sec">
        {#each current.tracks as track, idx (track.path)}
          <TrackRow {track} index={idx} contextTracks={current.tracks} />
        {/each}
      </div>
    {/if}
  </div>
</div>

<style>
  .artists-split {
    height: 100%;
    display: grid;
    grid-template-columns: 248px 1fr;
    min-height: 0;
  }
  .left-pane {
    border-right: 1px solid var(--border);
    display: flex;
    flex-direction: column;
    min-height: 0;
  }
  .left-head {
    padding: 12px;
    border-bottom: 1px solid var(--border);
  }
  .left-head .input {
    width: 100%;
  }
  .artist-list {
    flex: 1;
    overflow-y: auto;
    padding: 8px;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .ar-btn {
    border: none;
    background: transparent;
    padding: 7px 8px;
    border-radius: var(--r-sm);
    display: flex;
    align-items: center;
    gap: 10px;
    text-align: left;
  }
  .ar-btn:hover {
    background: var(--surface-2);
  }
  .ar-btn.active {
    background: var(--accent-soft);
  }
  .ar-meta {
    min-width: 0;
    display: flex;
    flex-direction: column;
  }
  .ar-name {
    font-size: 13px;
    font-weight: 600;
    color: var(--text);
  }
  .ar-btn.active .ar-name {
    color: var(--accent);
  }
  .ar-count {
    font-size: 11px;
    color: var(--text-3);
  }
  .right-pane {
    padding: 22px 26px 36px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 20px;
  }
  .ar-hero {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
  }
  .ar-hero h1 {
    margin: 0;
    font-size: 24px;
    font-weight: 700;
  }
  .ar-hero p {
    margin: 3px 0 0;
    color: var(--text-2);
    font-size: 12.5px;
  }
  .album-strip {
    display: flex;
    gap: 14px;
    overflow-x: auto;
    padding-bottom: 6px;
  }
  .al-chip {
    width: 134px;
    flex-shrink: 0;
    border: 1px solid var(--border);
    background: var(--surface);
    border-radius: var(--r-md);
    padding: 8px;
    display: flex;
    flex-direction: column;
    gap: 5px;
    text-align: left;
  }
  .al-chip:hover {
    background: var(--surface-2);
  }
  .al-name {
    font-size: 12.5px;
    font-weight: 600;
    color: var(--text);
    width: 100%;
  }
  .al-yr {
    font-size: 11px;
    color: var(--text-3);
  }
  .tracks-sec {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .empty {
    margin: auto;
    color: var(--text-3);
  }
</style>
