<script lang="ts">
  import { formatTime } from '../lib/format';
  import { player } from '../lib/player.svelte';
  import Artwork from './Artwork.svelte';
  import Icon from './Icon.svelte';

  let dragFrom = $state<number | null>(null);
  let dragOver = $state<number | null>(null);

  let current = $derived(player.currentTrack);
  let upNext = $derived(player.upNextTracks);

  function clearUpNext(): void {
    if (!player.currentPath) return;
    player.queue = player.queue.slice(0, player.queueIndex + 1);
  }
</script>

<div class="queue-shell scroll-thin">
  {#if current}
    <div class="section-label">NOW PLAYING</div>
    <div class="q-item current">
      <Artwork hash={current.artHash} alt={current.album} size={40} radius={6} />
      <div class="q-meta">
        <div class="q-title truncate">{current.title}</div>
        <div class="q-artist truncate">{current.artist}</div>
      </div>
      <span class="q-dur tabular">{formatTime(current.duration)}</span>
    </div>
  {/if}

  <div class="section-head">
    <span>UP NEXT ({upNext.length})</span>
    {#if upNext.length > 0}
      <button type="button" class="clear-btn" onclick={clearUpNext}>Clear</button>
    {/if}
  </div>

  {#if upNext.length === 0}
    <div class="empty">Queue is empty. Play a song or album to fill Up Next.</div>
  {:else}
    <div class="q-list" role="list">
      {#each upNext as track, relIdx (track.path + '-' + relIdx)}
        {@const absIdx = player.queueIndex + 1 + relIdx}
        <div
          class="q-item"
          class:drag-target={dragOver === relIdx}
          role="listitem"
          draggable="true"
          ondragstart={() => (dragFrom = relIdx)}
          ondragover={(e) => {
            e.preventDefault();
            dragOver = relIdx;
          }}
          ondragleave={() => {
            if (dragOver === relIdx) dragOver = null;
          }}
          ondrop={(e) => {
            e.preventDefault();
            if (dragFrom !== null && dragFrom !== relIdx) {
              player.reorderUpNext(dragFrom, relIdx);
            }
            dragFrom = null;
            dragOver = null;
          }}
          ondragend={() => {
            dragFrom = null;
            dragOver = null;
          }}
        >
          <button
            type="button"
            class="q-main"
            ondblclick={() => player.jumpToQueueIndex(absIdx)}
            onclick={() => player.jumpToQueueIndex(absIdx)}
          >
            <Artwork hash={track.artHash} alt={track.album} size={38} radius={6} />
            <div class="q-meta">
              <div class="q-title truncate">{track.title}</div>
              <div class="q-artist truncate">{track.artist}</div>
            </div>
          </button>
          <span class="q-dur tabular">{formatTime(track.duration)}</span>
          <button
            type="button"
            class="btn-icon rm-btn"
            aria-label="Remove from queue"
            onclick={() => player.removeFromQueue(absIdx)}
          >
            <Icon name="close" size={14} />
          </button>
        </div>
      {/each}
    </div>
  {/if}
</div>

<style>
  .queue-shell {
    height: 100%;
    overflow-y: auto;
    padding: 12px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .section-label,
  .section-head {
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.06em;
    color: var(--text-3);
    padding: 4px 4px 2px;
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .clear-btn {
    border: none;
    background: transparent;
    color: var(--accent);
    font-size: 11.5px;
    font-weight: 600;
    padding: 2px 6px;
    border-radius: var(--r-sm);
  }
  .clear-btn:hover {
    background: var(--accent-soft);
  }
  .q-list {
    display: flex;
    flex-direction: column;
    gap: 3px;
  }
  .q-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 6px 8px;
    border-radius: var(--r-sm);
    transition: background 120ms ease, border-color 120ms ease;
    border: 1px solid transparent;
  }
  .q-item:hover {
    background: var(--surface-2);
  }
  .q-item.current {
    background: var(--accent-soft);
    border-color: transparent;
  }
  .q-item.drag-target {
    border-color: var(--accent);
  }
  .q-main {
    border: none;
    background: transparent;
    padding: 0;
    flex: 1;
    min-width: 0;
    display: flex;
    align-items: center;
    gap: 10px;
    text-align: left;
  }
  .q-meta {
    flex: 1;
    min-width: 0;
  }
  .q-title {
    font-size: 13px;
    font-weight: 600;
    color: var(--text);
  }
  .q-item.current .q-title {
    color: var(--accent);
  }
  .q-artist {
    font-size: 11.5px;
    color: var(--text-2);
  }
  .q-dur {
    font-size: 11.5px;
    color: var(--text-3);
  }
  .rm-btn {
    width: 24px;
    height: 24px;
    opacity: 0;
  }
  .q-item:hover .rm-btn {
    opacity: 1;
  }
  .empty {
    color: var(--text-3);
    font-size: 12.5px;
    padding: 20px 8px;
    text-align: center;
  }
</style>
