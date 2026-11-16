<script lang="ts">
  import Icon from '../components/Icon.svelte';
  import TrackRow from '../components/TrackRow.svelte';
  import { library } from '../lib/library.svelte';
  import { player } from '../lib/player.svelte';

  interface Props {
    selected?: string;
  }

  let { selected }: Props = $props();
  let activeGenre = $state('');

  $effect(() => {
    if (selected) {
      activeGenre = selected;
    } else if (!activeGenre && library.genres.length > 0) {
      activeGenre = library.genres[0].name;
    }
  });

  let current = $derived(
    library.genres.find((g) => g.name === activeGenre) ?? library.genres[0] ?? null
  );
</script>

<div class="genres-page">
  <div class="chips-bar scroll-thin">
    {#each library.genres as g (g.name)}
      <button
        type="button"
        class="genre-chip"
        class:active={current?.name === g.name}
        onclick={() => (activeGenre = g.name)}
      >
        <span>{g.name}</span>
        <span class="cnt tabular">{g.tracks.length}</span>
      </button>
    {/each}
  </div>

  {#if !current}
    <div class="empty">No genres indexed yet.</div>
  {:else}
    <div class="g-head">
      <div>
        <h1>{current.name}</h1>
        <p class="sub tabular">{current.tracks.length} tracks</p>
      </div>
      <button
        type="button"
        class="btn btn-primary"
        onclick={() => current.tracks[0] && player.playTrack(current.tracks[0], current.tracks)}
      >
        <Icon name="play" size={16} />
        <span>Play Genre</span>
      </button>
    </div>

    <div class="g-tracks scroll-thin">
      {#each current.tracks as track, idx (track.path)}
        <TrackRow {track} index={idx} contextTracks={current.tracks} />
      {/each}
    </div>
  {/if}
</div>

<style>
  .genres-page {
    height: 100%;
    display: flex;
    flex-direction: column;
    padding: 18px 28px 0;
    min-height: 0;
    gap: 16px;
  }
  .chips-bar {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    max-height: 108px;
    overflow-y: auto;
    padding-bottom: 4px;
    flex-shrink: 0;
  }
  .genre-chip {
    height: 30px;
    padding: 0 11px;
    border-radius: var(--r-full);
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--text-2);
    font-size: 12.5px;
    font-weight: 500;
    display: inline-flex;
    align-items: center;
    gap: 6px;
  }
  .genre-chip:hover {
    background: var(--surface-2);
    color: var(--text);
  }
  .genre-chip.active {
    background: var(--accent);
    color: var(--accent-fg);
    border-color: transparent;
    font-weight: 600;
  }
  .cnt {
    font-size: 11px;
    opacity: 0.75;
  }
  .g-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding-bottom: 12px;
    border-bottom: 1px solid var(--border);
    flex-shrink: 0;
  }
  .g-head h1 {
    margin: 0;
    font-size: 22px;
  }
  .sub {
    margin: 2px 0 0;
    color: var(--text-2);
    font-size: 12.5px;
  }
  .g-tracks {
    flex: 1;
    overflow-y: auto;
    padding-bottom: 24px;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .empty {
    margin: auto;
    color: var(--text-3);
  }
</style>
