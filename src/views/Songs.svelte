<script lang="ts">
  import Icon from '../components/Icon.svelte';
  import TrackRow from '../components/TrackRow.svelte';
  import VirtualList from '../components/VirtualList.svelte';
  import { sortLetterKey } from '../lib/format';
  import { library } from '../lib/library.svelte';
  import { menuStore } from '../lib/menu.svelte';
  import { player } from '../lib/player.svelte';
  import type { Track } from '../lib/types';

  type SortKey = 'title' | 'artist' | 'album' | 'duration' | 'addedAt';
  let sortKey = $state<SortKey>('title');
  let sortAsc = $state(true);
  let selectedPaths = $state<Set<string>>(new Set());
  let lastClickIdx = $state<number | null>(null);
  let vlist: { scrollToIndex: (i: number) => void } | undefined = $state();

  let sorted = $derived.by(() => {
    const arr = [...library.tracks];
    const dir = sortAsc ? 1 : -1;
    arr.sort((a, b) => {
      if (sortKey === 'duration') return (a.duration - b.duration) * dir;
      if (sortKey === 'addedAt') return (a.addedAt - b.addedAt) * dir;
      return a[sortKey].localeCompare(b[sortKey]) * dir;
    });
    return arr;
  });

  let letters = $derived.by(() => {
    if (sortKey !== 'title' && sortKey !== 'artist') return [];
    const seen = new Map<string, number>();
    for (let i = 0; i < sorted.length; i++) {
      const k = sortLetterKey(sorted[i][sortKey]);
      if (!seen.has(k)) seen.set(k, i);
    }
    return [...seen.entries()];
  });

  let selectedTracks = $derived(sorted.filter((t) => selectedPaths.has(t.path)));

  function handleSelect(e: MouseEvent, track: Track, idx: number): void {
    const next = new Set(e.ctrlKey || e.metaKey ? selectedPaths : []);
    if (e.shiftKey && lastClickIdx !== null) {
      const lo = Math.min(lastClickIdx, idx);
      const hi = Math.max(lastClickIdx, idx);
      for (let i = lo; i <= hi; i++) next.add(sorted[i].path);
    } else if (e.ctrlKey || e.metaKey) {
      if (next.has(track.path)) next.delete(track.path);
      else next.add(track.path);
      lastClickIdx = idx;
    } else {
      next.add(track.path);
      lastClickIdx = idx;
    }
    selectedPaths = next;
  }
</script>

<div class="songs-page">
  <div class="page-head">
    <div>
      <h1>Songs</h1>
      <p class="sub tabular">{sorted.length} tracks</p>
    </div>

    <div class="controls">
      {#if selectedTracks.length > 1}
        <button
          type="button"
          class="btn btn-primary"
          onclick={() => player.playTrack(selectedTracks[0], selectedTracks)}
        >
          <Icon name="play" size={15} />
          <span>Play {selectedTracks.length} selected</span>
        </button>
        <button
          type="button"
          class="btn"
          onclick={(e) => menuStore.openForTracks(e, selectedTracks)}
        >
          Actions
        </button>
      {/if}

      <button
        type="button"
        class="btn"
        disabled={sorted.length === 0}
        onclick={() => {
          player.shuffle = true;
          const pick = sorted[Math.floor(Math.random() * sorted.length)];
          void player.playTrack(pick, sorted);
        }}
      >
        <Icon name="shuffle" size={15} />
        <span>Shuffle</span>
      </button>

      <select class="input sort-sel" aria-label="Sort songs by" bind:value={sortKey}>
        <option value="title">Title</option>
        <option value="artist">Artist</option>
        <option value="album">Album</option>
        <option value="duration">Duration</option>
        <option value="addedAt">Date Added</option>
      </select>

      <button
        type="button"
        class="btn-icon"
        aria-label={sortAsc ? 'Sort descending' : 'Sort ascending'}
        onclick={() => (sortAsc = !sortAsc)}
      >
        <Icon name="sort" size={16} />
      </button>
    </div>
  </div>

  {#if sorted.length === 0}
    <div class="empty-state">No songs in library. Add a music folder in Home or Settings.</div>
  {:else}
    <div class="list-area">
      <VirtualList bind:this={vlist} items={sorted} itemHeight={52}>
        {#snippet row(track, idx)}
          <TrackRow
            {track}
            index={idx}
            contextTracks={sorted}
            selected={selectedPaths.has(track.path)}
            onSelect={(e) => handleSelect(e, track, idx)}
          />
        {/snippet}
      </VirtualList>

      {#if letters.length > 3}
        <div class="az-rail" aria-label="Jump to letter">
          {#each letters as [char, targetIdx] (char)}
            <button
              type="button"
              class="az-btn"
              onclick={() => vlist?.scrollToIndex(targetIdx)}
            >
              {char}
            </button>
          {/each}
        </div>
      {/if}
    </div>
  {/if}
</div>

<style>
  .songs-page {
    height: 100%;
    display: flex;
    flex-direction: column;
    padding: 18px 20px 0 28px;
    min-height: 0;
  }
  .page-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding-bottom: 14px;
    border-bottom: 1px solid var(--border);
    flex-shrink: 0;
  }
  .page-head h1 {
    margin: 0;
    font-size: 22px;
    font-weight: 700;
  }
  .sub {
    margin: 2px 0 0;
    font-size: 12.5px;
    color: var(--text-2);
  }
  .controls {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .sort-sel {
    height: 32px;
    font-size: 12.5px;
  }
  .list-area {
    position: relative;
    flex: 1;
    min-height: 0;
    display: flex;
    padding-right: 18px;
  }
  .az-rail {
    position: absolute;
    right: 2px;
    top: 8px;
    bottom: 8px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 1px;
    z-index: 10;
  }
  .az-btn {
    border: none;
    background: transparent;
    color: var(--text-3);
    font-size: 10px;
    font-weight: 700;
    width: 16px;
    height: 14px;
    padding: 0;
    line-height: 1;
    border-radius: 3px;
  }
  .az-btn:hover {
    color: var(--accent);
    background: var(--accent-soft);
  }
  .empty-state {
    margin: auto;
    color: var(--text-3);
  }
</style>
