<script lang="ts">
  import { formatTime } from '../lib/format';
  import { lyricsStore } from '../lib/lyrics.svelte';
  import { player } from '../lib/player.svelte';
  import Artwork from './Artwork.svelte';
  import Icon from './Icon.svelte';
  import Slider from './Slider.svelte';

  let track = $derived(player.currentTrack);
  let activeLyric = $derived.by(() => {
    const st = lyricsStore.activeAt(player.smoothPosition);
    if (st.lineIndex >= 0 && lyricsStore.lines[st.lineIndex]) {
      return lyricsStore.lines[st.lineIndex].text;
    }
    return '';
  });
</script>

<div class="mini-root">
  <div class="mini-drag" data-tauri-drag-region>
    <span class="mini-brand" data-tauri-drag-region>Localify</span>
    <button
      type="button"
      class="btn-icon tiny"
      aria-label="Restore full window"
      onclick={() => player.toggleMiniMode()}
    >
      <Icon name="fullscreen" size={14} />
    </button>
  </div>

  <div class="mini-main">
    <Artwork hash={track?.artHash} alt={track?.album ?? 'Album'} size={58} radius={8} />
    <div class="mini-info">
      <div class="mini-title truncate">{track?.title ?? 'No track playing'}</div>
      <div class="mini-artist truncate">{track?.artist ?? 'Select a song'}</div>
      {#if activeLyric}
        <div class="mini-lyric truncate">{activeLyric}</div>
      {/if}
    </div>
    <div class="mini-btns">
      <button type="button" class="btn-icon" aria-label="Previous" onclick={() => player.prev()}>
        <Icon name="prev" size={16} />
      </button>
      <button
        type="button"
        class="mini-play"
        aria-label={player.playing ? 'Pause' : 'Play'}
        onclick={() => player.togglePlay()}
      >
        <Icon name={player.playing ? 'pause' : 'play'} size={16} />
      </button>
      <button type="button" class="btn-icon" aria-label="Next" onclick={() => player.next(true)}>
        <Icon name="next" size={16} />
      </button>
    </div>
  </div>

  <div class="mini-progress">
    <span class="tabular">{formatTime(player.smoothPosition)}</span>
    <Slider
      value={player.smoothPosition}
      min={0}
      max={Math.max(1, player.duration)}
      step={0.1}
      label="Position"
      height={3}
      onChange={(v) => player.seek(v)}
    />
    <span class="tabular">{formatTime(player.duration)}</span>
  </div>
</div>

<style>
  .mini-root {
    height: 100vh;
    width: 100vw;
    background: var(--bg);
    border: 1px solid var(--border-strong);
    padding: 6px 12px 8px;
    display: flex;
    flex-direction: column;
    justify-content: space-between;
  }
  .mini-drag {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 22px;
    cursor: grab;
  }
  .mini-brand {
    font-size: 11px;
    font-weight: 700;
    color: var(--text-3);
    letter-spacing: 0.04em;
  }
  .btn-icon.tiny {
    width: 22px;
    height: 22px;
  }
  .mini-main {
    display: flex;
    align-items: center;
    gap: 10px;
    min-width: 0;
  }
  .mini-info {
    flex: 1;
    min-width: 0;
  }
  .mini-title {
    font-size: 13.5px;
    font-weight: 700;
    color: var(--text);
  }
  .mini-artist {
    font-size: 11.5px;
    color: var(--text-2);
  }
  .mini-lyric {
    font-size: 11px;
    color: var(--accent);
    margin-top: 2px;
  }
  .mini-btns {
    display: flex;
    align-items: center;
    gap: 2px;
  }
  .mini-play {
    width: 32px;
    height: 32px;
    border-radius: 999px;
    border: none;
    background: var(--accent);
    color: var(--accent-fg);
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .mini-progress {
    display: flex;
    align-items: center;
    gap: 8px;
    font-size: 10.5px;
    color: var(--text-3);
  }
</style>
