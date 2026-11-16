<script lang="ts">
  import { library } from '../lib/library.svelte';
  import { player } from '../lib/player.svelte';
  import Artwork from './Artwork.svelte';
  import Icon from './Icon.svelte';
  import Slider from './Slider.svelte';

  let track = $derived(player.currentTrack);
  let scrubbing = $state(false);
  let scrubVal = $state(0);
  let displayPos = $derived(scrubbing ? scrubVal : player.smoothPosition);
  let totalDur = $derived(player.duration || track?.duration || 0);
</script>

<div class="mini-root">
  <div class="mini-top" data-tauri-drag-region>
    <div class="mini-art" data-tauri-drag-region>
      <Artwork hash={track?.artHash} alt={track?.album ?? 'Album'} size={76} radius={0} />
    </div>

    <div class="mini-info" data-tauri-drag-region>
      <div class="mini-title truncate" data-tauri-drag-region>{track?.title ?? 'No track playing'}</div>
      <div class="mini-artist truncate" data-tauri-drag-region>{track?.artist ?? 'Select a song'}</div>
    </div>

    <div class="mini-side">
      <button
        type="button"
        class="btn-icon side-btn"
        aria-label="Restore full window"
        title="Restore full window"
        onclick={() => player.toggleMiniMode()}
      >
        <Icon name="fullscreen" size={14} />
      </button>
      <button
        type="button"
        class="btn-icon side-btn"
        class:liked={Boolean(track?.liked)}
        disabled={!track}
        aria-label={track?.liked ? 'Remove from favorites' : 'Add to favorites'}
        title={track?.liked ? 'Remove from favorites' : 'Add to favorites'}
        onclick={() => track && library.toggleLike(track.path)}
      >
        <Icon name={track?.liked ? 'heart-fill' : 'heart'} size={14} />
      </button>
    </div>
  </div>

  <div class="mini-bottom">
    <div class="mini-btns">
      <button
        type="button"
        class="btn-icon m-btn"
        class:on={player.shuffle}
        aria-label="Shuffle"
        onclick={() => player.toggleShuffle()}
      >
        <Icon name="shuffle" size={14} />
      </button>
      <button
        type="button"
        class="btn-icon m-btn"
        disabled={!track}
        aria-label="Previous"
        onclick={() => player.prev()}
      >
        <Icon name="prev" size={16} />
      </button>
      <button
        type="button"
        class="mini-play"
        aria-label={player.playing ? 'Pause' : 'Play'}
        onclick={() => player.togglePlay()}
      >
        <Icon name={player.playing ? 'pause' : 'play'} size={15} />
      </button>
      <button
        type="button"
        class="btn-icon m-btn"
        disabled={!track}
        aria-label="Next"
        onclick={() => player.next(true)}
      >
        <Icon name="next" size={16} />
      </button>
      <button
        type="button"
        class="btn-icon m-btn"
        class:on={player.repeat !== 'off'}
        aria-label="Repeat"
        onclick={() => player.cycleRepeat()}
      >
        <Icon name={player.repeat === 'one' ? 'repeat-one' : 'repeat'} size={14} />
      </button>
    </div>

    <div class="mini-scrub">
      <Slider
        value={displayPos}
        min={0}
        max={Math.max(1, totalDur)}
        step={0.1}
        label="Playback position"
        height={3}
        disabled={!track}
        onInput={(v) => {
          scrubbing = true;
          scrubVal = v;
        }}
        onChange={(v) => {
          scrubbing = false;
          void player.seek(v);
        }}
      />
    </div>
  </div>
</div>

<style>
  .mini-root {
    height: 100vh;
    width: 100vw;
    background: var(--bg);
    border: 1px solid var(--border-strong);
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
  .mini-top {
    height: clamp(56px, 53vh, 76px);
    display: flex;
    align-items: center;
    background: var(--surface);
    cursor: grab;
    flex-shrink: 0;
  }
  .mini-art {
    width: clamp(56px, 53vh, 76px);
    height: clamp(56px, 53vh, 76px);
    flex-shrink: 0;
  }
  .mini-art :global(.art) {
    width: 100% !important;
    height: 100% !important;
    border: none;
  }
  .mini-info {
    flex: 1;
    min-width: 0;
    padding: 0 8px 0 11px;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .mini-title {
    font-size: 13px;
    font-weight: 700;
    color: var(--text);
  }
  .mini-artist {
    font-size: 11.5px;
    color: var(--text-2);
  }
  .mini-side {
    display: flex;
    flex-direction: column;
    justify-content: center;
    align-items: center;
    gap: 4px;
    padding: 0 6px;
    flex-shrink: 0;
  }
  .side-btn {
    width: 26px;
    height: 26px;
    color: var(--text-2);
  }
  .side-btn:hover:not(:disabled) {
    color: var(--text);
  }
  .side-btn.liked {
    color: var(--accent);
  }
  .mini-bottom {
    flex: 1;
    min-height: 0;
    background: var(--bg-elev);
    border-top: 1px solid var(--border);
    padding: 4px 10px 3px;
    display: flex;
    flex-direction: column;
    justify-content: center;
    gap: 1px;
  }
  .mini-btns {
    display: flex;
    align-items: center;
    justify-content: space-between;
    padding: 0 4px;
  }
  .m-btn {
    width: 28px;
    height: 28px;
    color: var(--text-2);
  }
  .m-btn:hover:not(:disabled) {
    color: var(--text);
  }
  .m-btn.on {
    color: var(--accent);
  }
  .mini-play {
    width: 32px;
    height: 32px;
    border-radius: 999px;
    border: 1.5px solid var(--text-2);
    background: transparent;
    color: var(--text);
    display: flex;
    align-items: center;
    justify-content: center;
    transition: border-color 140ms var(--ease), background 140ms var(--ease), color 140ms var(--ease);
  }
  .mini-play:hover {
    border-color: var(--accent);
    background: var(--accent);
    color: var(--accent-fg);
  }
  .mini-scrub {
    width: 100%;
  }
</style>
