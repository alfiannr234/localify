<script lang="ts">
  import { artUrl } from '../lib/api';
  import { formatAudioSpecs, formatTime } from '../lib/format';
  import { library } from '../lib/library.svelte';
  import { player } from '../lib/player.svelte';
  import Artwork from './Artwork.svelte';
  import Icon from './Icon.svelte';
  import LyricsView from './LyricsView.svelte';
  import Slider from './Slider.svelte';

  let track = $derived(player.currentTrack);
  let bgArt = $derived(track ? artUrl(track.artHash, false) : null);
  let scrubbing = $state(false);
  let scrubVal = $state(0);
  let displayPos = $derived(scrubbing ? scrubVal : player.smoothPosition);
  let totalDur = $derived(player.duration || track?.duration || 0);

  function onKeyDown(e: KeyboardEvent): void {
    if (e.key === 'Escape' && player.nowPlayingOpen) {
      player.nowPlayingOpen = false;
    }
  }
</script>

<svelte:window onkeydown={onKeyDown} />

{#if player.nowPlayingOpen && track}
  <div class="np-overlay" role="dialog" aria-modal="true" aria-label="Now playing">
    {#if bgArt}
      <div class="np-bg" style="background-image: url('{bgArt}');"></div>
    {/if}
    <div class="np-scrim"></div>

    <div class="np-header">
      <div class="specs">{formatAudioSpecs(track)}</div>
      <button
        type="button"
        class="btn-icon close-np"
        aria-label="Close now playing"
        onclick={() => (player.nowPlayingOpen = false)}
      >
        <Icon name="minimize" size={20} />
      </button>
    </div>

    <div class="np-grid">
      <div class="np-left">
        <div class="np-art-wrap">
          <Artwork hash={track.artHash} alt={track.album} size={320} thumb={false} radius={16} />
        </div>

        <div class="np-title-row">
          <div class="np-titles">
            <h1 class="truncate">{track.title}</h1>
            <p class="truncate">{track.artist} - {track.album}</p>
          </div>
          <button
            type="button"
            class="btn-icon"
            class:active={track.liked}
            aria-label={track.liked ? 'Remove from favorites' : 'Add to favorites'}
            onclick={() => library.toggleLike(track.path)}
          >
            <Icon name={track.liked ? 'heart-fill' : 'heart'} size={22} />
          </button>
        </div>

        <div class="np-scrub">
          <Slider
            value={displayPos}
            min={0}
            max={Math.max(1, totalDur)}
            step={0.1}
            label="Playback position"
            onInput={(v) => {
              scrubbing = true;
              scrubVal = v;
            }}
            onChange={(v) => {
              scrubbing = false;
              void player.seek(v);
            }}
          />
          <div class="np-times tabular">
            <span>{formatTime(displayPos)}</span>
            <span>{formatTime(totalDur)}</span>
          </div>
        </div>

        <div class="np-controls">
          <button
            type="button"
            class="btn-icon"
            class:active={player.shuffle}
            aria-label="Shuffle"
            onclick={() => player.toggleShuffle()}
          >
            <Icon name="shuffle" size={20} />
          </button>
          <button type="button" class="btn-icon" aria-label="Previous" onclick={() => player.prev()}>
            <Icon name="prev" size={24} />
          </button>
          <button
            type="button"
            class="np-play"
            aria-label={player.playing ? 'Pause' : 'Play'}
            onclick={() => player.togglePlay()}
          >
            <Icon name={player.playing ? 'pause' : 'play'} size={24} />
          </button>
          <button type="button" class="btn-icon" aria-label="Next" onclick={() => player.next(true)}>
            <Icon name="next" size={24} />
          </button>
          <button
            type="button"
            class="btn-icon"
            class:active={player.repeat !== 'off'}
            aria-label="Repeat"
            onclick={() => player.cycleRepeat()}
          >
            <Icon name={player.repeat === 'one' ? 'repeat-one' : 'repeat'} size={20} />
          </button>
        </div>
      </div>

      <div class="np-right">
        <LyricsView expanded={true} />
      </div>
    </div>
  </div>
{/if}

<style>
  .np-overlay {
    position: fixed;
    inset: 0;
    z-index: 80;
    background: var(--bg);
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
  .np-bg {
    position: absolute;
    inset: -40px;
    background-size: cover;
    background-position: center;
    filter: blur(56px) saturate(1.25);
    transform: scale(1.1);
    opacity: 0.42;
    pointer-events: none;
  }
  .np-scrim {
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, rgba(14, 16, 22, 0.72), rgba(12, 14, 19, 0.88));
    pointer-events: none;
  }
  .np-header {
    position: relative;
    z-index: 2;
    height: 54px;
    padding: 0 24px;
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .specs {
    font-size: 12px;
    font-weight: 600;
    color: rgba(241, 243, 249, 0.78);
    letter-spacing: 0.03em;
  }
  .close-np {
    color: #f1f3f9;
  }
  .np-grid {
    position: relative;
    z-index: 2;
    flex: 1;
    min-height: 0;
    display: grid;
    grid-template-columns: minmax(320px, 420px) 1fr;
    gap: 40px;
    padding: 16px 48px 36px;
    max-width: 1280px;
    width: 100%;
    margin: 0 auto;
    align-items: center;
  }
  .np-left {
    display: flex;
    flex-direction: column;
    gap: 18px;
  }
  .np-art-wrap {
    display: flex;
    justify-content: center;
    box-shadow: var(--shadow-lg);
    border-radius: 16px;
    align-self: center;
  }
  .np-title-row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }
  .np-titles {
    min-width: 0;
  }
  .np-titles h1 {
    margin: 0;
    font-size: 22px;
    font-weight: 700;
    color: #f1f3f9;
  }
  .np-titles p {
    margin: 4px 0 0;
    font-size: 14px;
    color: rgba(241, 243, 249, 0.75);
  }
  .np-scrub {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .np-times {
    display: flex;
    justify-content: space-between;
    font-size: 12px;
    color: rgba(241, 243, 249, 0.72);
  }
  .np-controls {
    display: flex;
    align-items: center;
    justify-content: center;
    gap: 16px;
  }
  .np-controls .btn-icon {
    color: rgba(241, 243, 249, 0.85);
    width: 40px;
    height: 40px;
  }
  .np-play {
    width: 54px;
    height: 54px;
    border-radius: 999px;
    border: none;
    background: var(--accent);
    color: var(--accent-fg);
    display: flex;
    align-items: center;
    justify-content: center;
  }
  .np-play:hover {
    background: var(--accent-hover);
  }
  .np-right {
    height: 100%;
    min-height: 0;
    border-radius: var(--r-lg);
    background: rgba(17, 19, 24, 0.35);
    border: 1px solid rgba(255, 255, 255, 0.08);
    overflow: hidden;
  }
  @media (max-width: 860px) {
    .np-grid {
      grid-template-columns: 1fr;
      padding: 12px 20px 24px;
    }
    .np-art-wrap {
      display: none;
    }
  }
</style>
