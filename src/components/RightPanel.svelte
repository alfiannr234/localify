<script lang="ts">
  import { artUrl } from '../lib/api';
  import { player } from '../lib/player.svelte';
  import { quranStore } from '../lib/quran.svelte';
  import Icon from './Icon.svelte';
  import LyricsView from './LyricsView.svelte';
  import QueueList from './QueueList.svelte';
  import QuranView from './QuranView.svelte';

  let track = $derived(player.currentTrack);
  let bgArt = $derived(track ? artUrl(track.artHash, false) : null);
</script>

{#if player.rightPanel}
  <aside
    class="right-panel"
    class:has-art={Boolean(bgArt)}
    aria-label={player.rightPanel === 'lyrics' ? 'Lyrics' : 'Playing queue'}
  >
    {#if bgArt}
      <div class="backdrop" aria-hidden="true">
        <img src={bgArt} alt="" />
      </div>
    {/if}

    <div class="tabs">
      <div class="tab-group">
        <button
          type="button"
          class="tab"
          class:active={player.rightPanel === 'lyrics'}
          onclick={() => (player.rightPanel = 'lyrics')}
        >
          {quranStore.rec ? "Qur'an" : 'Lyrics'}
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
      {#if player.rightPanel === 'queue'}
        <QueueList onBackdrop={Boolean(bgArt)} />
      {:else if quranStore.rec}
        <QuranView onOverlay={Boolean(bgArt)} />
      {:else}
        <LyricsView onBackdrop={Boolean(bgArt)} />
      {/if}
    </div>
  </aside>
{/if}

<style>
  .right-panel {
    position: relative;
    width: var(--rightpanel-w);
    height: 100%;
    background: var(--bg-elev);
    color: var(--text);
    border-left: 1px solid var(--border);
    display: flex;
    flex-direction: column;
    flex-shrink: 0;
    overflow: hidden;
  }
  .right-panel.has-art {
    color: #fff;
    background: #10131d;
  }
  .backdrop {
    position: absolute;
    inset: -18%;
    pointer-events: none;
    z-index: 0;
  }
  .backdrop img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    filter: blur(56px) saturate(1.5) brightness(0.48);
    transform: scale(1.22);
  }
  .backdrop::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, rgba(10, 14, 24, 0.34), rgba(8, 12, 22, 0.62));
  }
  .tabs {
    position: relative;
    z-index: 1;
    height: var(--topbar-h);
    padding: 0 12px;
    border-bottom: 1px solid var(--border);
    display: flex;
    align-items: center;
    justify-content: space-between;
    flex-shrink: 0;
  }
  .has-art .tabs {
    border-bottom-color: rgba(255, 255, 255, 0.1);
  }
  .tab-group {
    display: flex;
    gap: 6px;
  }
  .tab {
    height: 28px;
    padding: 0 12px;
    border-radius: var(--r-sm);
    border: none;
    background: transparent;
    color: var(--text-2);
    font-weight: 600;
    font-size: 12.5px;
  }
  .tab:hover {
    background: var(--surface-2);
    color: var(--text);
  }
  .tab.active {
    background: var(--accent);
    color: var(--accent-fg);
  }
  .has-art .tab {
    border-radius: 999px;
    color: rgba(255, 255, 255, 0.72);
  }
  .has-art .tab:hover {
    background: rgba(255, 255, 255, 0.12);
    color: #fff;
  }
  .has-art .tab.active {
    background: rgba(255, 255, 255, 0.92);
    color: #111318;
  }
  .close-panel {
    width: 28px;
    height: 28px;
  }
  .has-art .close-panel {
    color: rgba(255, 255, 255, 0.74);
  }
  .has-art .close-panel:hover {
    background: rgba(255, 255, 255, 0.12);
    color: #fff;
  }
  .body {
    position: relative;
    z-index: 1;
    flex: 1;
    min-height: 0;
    overflow: hidden;
  }
  @media (prefers-reduced-transparency: reduce) {
    .backdrop {
      display: none;
    }
    .right-panel.has-art {
      background: #10131d;
    }
  }
</style>
