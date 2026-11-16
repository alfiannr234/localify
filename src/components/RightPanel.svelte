<script lang="ts">
  import { player } from '../lib/player.svelte';
  import Icon from './Icon.svelte';
  import LyricsView from './LyricsView.svelte';
  import QueueList from './QueueList.svelte';
</script>

{#if player.rightPanel}
  <aside class="right-panel" aria-label={player.rightPanel === 'lyrics' ? 'Lyrics' : 'Playing queue'}>
    <div class="tabs">
      <div class="tab-group">
        <button
          type="button"
          class="tab"
          class:active={player.rightPanel === 'lyrics'}
          onclick={() => (player.rightPanel = 'lyrics')}
        >
          Lyrics
        </button>
        <button
          type="button"
          class="tab"
          class:active={player.rightPanel === 'queue'}
          onclick={() => (player.rightPanel = 'queue')}
        >
          Playing next
        </button>
      </div>
      <button
        type="button"
        class="btn-icon close-panel"
        aria-label="Close side panel"
        onclick={() => (player.rightPanel = null)}
      >
        <Icon name="close" size={16} />
      </button>
    </div>

    <div class="body">
      {#if player.rightPanel === 'lyrics'}
        <LyricsView />
      {:else}
        <QueueList />
      {/if}
    </div>
  </aside>
{/if}

<style>
  .right-panel {
    width: var(--rightpanel-w);
    height: 100%;
    background: var(--bg-elev);
    border-left: 1px solid var(--border);
    display: flex;
    flex-direction: column;
    flex-shrink: 0;
  }
  .tabs {
    height: var(--topbar-h);
    padding: 0 12px;
    border-bottom: 1px solid var(--border);
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-shrink: 0;
  }
  .tab-group {
    display: flex;
    gap: 6px;
  }
  .tab {
    height: 30px;
    padding: 0 12px;
    border-radius: var(--r-sm);
    border: none;
    background: transparent;
    color: var(--text-2);
    font-weight: 600;
    font-size: 13px;
  }
  .tab:hover {
    background: var(--surface-2);
    color: var(--text);
  }
  .tab.active {
    background: var(--accent);
    color: var(--accent-fg);
  }
  .close-panel {
    width: 28px;
    height: 28px;
  }
  .body {
    flex: 1;
    min-height: 0;
    overflow: hidden;
  }
</style>
