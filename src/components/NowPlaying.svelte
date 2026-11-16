<script lang="ts">
  import { artUrl } from '../lib/api';
  import { formatTime } from '../lib/format';
  import { player } from '../lib/player.svelte';
  import { quranStore } from '../lib/quran.svelte';
  import Artwork from './Artwork.svelte';
  import Icon from './Icon.svelte';
  import LyricsView from './LyricsView.svelte';
  import QueueList from './QueueList.svelte';
  import QuranView from './QuranView.svelte';
  import Slider from './Slider.svelte';

  let track = $derived(player.currentTrack);
  let bgArt = $derived(track ? artUrl(track.artHash, false) : null);
  let scrubbing = $state(false);
  let scrubVal = $state(0);
  let displayPos = $derived(scrubbing ? scrubVal : player.smoothPosition);
  let totalDur = $derived(player.duration || track?.duration || 0);
  let remaining = $derived(Math.max(0, totalDur - displayPos));

  let sideMode = $state<'lyrics' | 'queue'>('lyrics');
  let showSleep = $state(false);
  const SLEEP_OPTS = [15, 30, 45, 60];

  function onKeyDown(e: KeyboardEvent): void {
    if (e.key === 'Escape' && player.nowPlayingOpen) {
      player.nowPlayingOpen = false;
    }
  }
</script>

<svelte:window onkeydown={onKeyDown} />

{#if player.nowPlayingOpen && track}
  <div class="np-sheet" role="dialog" aria-modal="true" aria-label="Now Playing">
    <div class="backdrop" aria-hidden="true">
      {#if bgArt}
        <img src={bgArt} alt="" />
      {/if}
    </div>

    <button
      type="button"
      class="btn-icon close-btn"
      aria-label="Close Now Playing"
      onclick={() => (player.nowPlayingOpen = false)}
    >
      <Icon name="chevron-down" size={22} />
    </button>

    <div class="stage">
      <div class="player-col">
        <div class="art-wrap" class:paused={!player.playing}>
          <div class="art-box">
            <Artwork hash={track.artHash} alt={track.album} size={340} thumb={false} radius={14} />
          </div>
        </div>

        <div class="titles">
          <h1 class="truncate">{track.title}</h1>
          <p class="truncate">
            {track.artist}{track.album ? ` - ${track.album}` : ''}
          </p>
        </div>

        <div class="scrub">
          <Slider
            value={displayPos}
            min={0}
            max={Math.max(1, totalDur)}
            step={0.1}
            label="Seek"
            onInput={(v) => {
              scrubbing = true;
              scrubVal = v;
            }}
            onChange={(v) => {
              scrubbing = false;
              void player.seek(v);
            }}
          />
          <div class="times tabular">
            <span>{formatTime(displayPos)}</span>
            <span>-{formatTime(remaining)}</span>
          </div>
        </div>

        <div class="transport">
          <button
            type="button"
            class="btn-icon t-btn"
            class:on={player.shuffle}
            aria-label="Shuffle"
            onclick={() => player.toggleShuffle()}
          >
            <Icon name="shuffle" size={18} />
          </button>
          <button type="button" class="btn-icon t-btn" aria-label="Previous" onclick={() => player.prev()}>
            <Icon name="prev" size={26} />
          </button>
          <button
            type="button"
            class="btn-icon play-btn"
            aria-label={player.playing ? 'Pause' : 'Play'}
            onclick={() => player.togglePlay()}
          >
            <Icon name={player.playing ? 'pause' : 'play'} size={36} />
          </button>
          <button type="button" class="btn-icon t-btn" aria-label="Next" onclick={() => player.next(true)}>
            <Icon name="next" size={26} />
          </button>
          <button
            type="button"
            class="btn-icon t-btn"
            class:on={player.repeat !== 'off'}
            aria-label="Repeat"
            onclick={() => player.cycleRepeat()}
          >
            <Icon name={player.repeat === 'one' ? 'repeat-one' : 'repeat'} size={18} />
          </button>
        </div>

        <div class="bottom-row">
          <div class="vol">
            <button type="button" class="btn-icon small" aria-label="Mute" onclick={() => player.toggleMute()}>
              <Icon name={player.muted || player.volume === 0 ? 'volume-mute' : 'volume-high'} size={16} />
            </button>
            <Slider
              value={player.muted ? 0 : player.volume}
              min={0}
              max={1}
              step={0.01}
              label="Volume"
              onInput={(v) => player.setVolume(v)}
            />
          </div>

          <div class="pop-wrap">
            <button
              type="button"
              class="btn-icon small"
              class:on={player.sleepEndAt !== null}
              aria-label="Sleep timer"
              onclick={() => (showSleep = !showSleep)}
            >
              <Icon name="moon" size={17} />
            </button>
            {#if showSleep}
              <div class="sleep-pop" role="menu">
                {#each SLEEP_OPTS as mins}
                  <button
                    type="button"
                    class="sp-item"
                    onclick={() => {
                      player.setSleepMinutes(mins);
                      showSleep = false;
                    }}
                  >
                    {mins}m
                  </button>
                {/each}
                {#if player.sleepEndAt !== null}
                  <button
                    type="button"
                    class="sp-item"
                    onclick={() => {
                      player.setSleepMinutes(null);
                      showSleep = false;
                    }}
                  >
                    Off
                  </button>
                {/if}
              </div>
            {/if}
          </div>

          <button
            type="button"
            class="btn-icon small"
            class:on={sideMode === 'lyrics'}
            aria-label="Lyrics"
            onclick={() => (sideMode = 'lyrics')}
          >
            <Icon name="lyrics" size={17} />
          </button>
          <button
            type="button"
            class="btn-icon small"
            class:on={sideMode === 'queue'}
            aria-label="Playing next"
            onclick={() => (sideMode = 'queue')}
          >
            <Icon name="list" size={18} />
          </button>
        </div>
      </div>

      <!-- Borderless clean side area aligned top-to-bottom with the player column -->
      <div class="side-pane">
        <div class="side-inner">
          {#if sideMode === 'queue'}
            <QueueList />
          {:else if quranStore.rec}
            <QuranView onOverlay={true} />
          {:else}
            <LyricsView expanded={true} />
          {/if}
        </div>
      </div>
    </div>
  </div>
{/if}

<style>
  .np-sheet {
    position: fixed;
    inset: 0;
    z-index: 80;
    color: #fff;
    background: #10131d;
    overflow: hidden;
  }
  .backdrop {
    position: absolute;
    inset: -10%;
    pointer-events: none;
  }
  .backdrop img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    filter: blur(72px) saturate(1.6) brightness(0.58);
    transform: scale(1.28);
  }
  .backdrop::after {
    content: '';
    position: absolute;
    inset: 0;
    background: linear-gradient(180deg, rgba(8, 12, 22, 0.42), rgba(6, 10, 18, 0.68));
  }
  .close-btn {
    position: absolute;
    top: 14px;
    left: 16px;
    z-index: 5;
    width: 36px;
    height: 36px;
    color: rgba(255, 255, 255, 0.85);
  }
  .close-btn:hover {
    background: rgba(255, 255, 255, 0.14);
    color: #fff;
  }
  .stage {
    position: relative;
    z-index: 2;
    height: 100%;
    max-width: 1240px;
    margin: 0 auto;
    padding: clamp(36px, 5.5vh, 52px) 5vw clamp(20px, 3.5vh, 34px);
    display: grid;
    grid-template-columns: minmax(260px, 370px) minmax(0, 1fr);
    gap: 5vw;
    align-content: center;
    align-items: stretch;
  }
  .player-col {
    display: flex;
    flex-direction: column;
    gap: clamp(10px, 1.8vh, 16px);
    min-width: 0;
  }
  .art-wrap {
    align-self: center;
    box-shadow: 0 24px 48px rgba(0, 0, 0, 0.48);
    border-radius: 14px;
    transition: transform 320ms var(--ease);
  }
  .art-box {
    width: clamp(160px, 33vh, 300px);
    aspect-ratio: 1;
  }
  .art-box :global(.art) {
    width: 100% !important;
    height: 100% !important;
  }
  .art-wrap.paused {
    transform: scale(0.86);
  }
  .titles h1 {
    margin: 0;
    font-size: clamp(18px, 2.4vh, 21px);
    font-weight: 700;
    color: #fff;
  }
  .titles p {
    margin: 2px 0 0;
    font-size: clamp(13.5px, 1.8vh, 15.5px);
    color: rgba(255, 255, 255, 0.72);
  }
  .scrub :global(.rail) {
    background: rgba(255, 255, 255, 0.22);
  }
  .scrub :global(.fill) {
    background: rgba(255, 255, 255, 0.9);
  }
  .times {
    display: flex;
    justify-content: space-between;
    font-size: 11px;
    color: rgba(255, 255, 255, 0.68);
    margin-top: 2px;
  }
  .transport {
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .t-btn {
    color: rgba(255, 255, 255, 0.82);
    width: 44px;
    height: 44px;
    border-radius: 50%;
  }
  .t-btn.on {
    color: #fff;
    background: rgba(255, 255, 255, 0.16);
  }
  .play-btn {
    width: 56px;
    height: 56px;
    border-radius: 50%;
    color: #fff;
  }
  .play-btn:hover,
  .t-btn:hover {
    background: rgba(255, 255, 255, 0.14);
    color: #fff;
  }
  .bottom-row {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .vol {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .vol :global(.rail) {
    background: rgba(255, 255, 255, 0.22);
  }
  .vol :global(.fill) {
    background: rgba(255, 255, 255, 0.9);
  }
  .bottom-row .btn-icon {
    color: rgba(255, 255, 255, 0.78);
    width: 34px;
    height: 34px;
  }
  .bottom-row .btn-icon.on {
    color: #fff;
    background: rgba(255, 255, 255, 0.16);
  }
  .pop-wrap {
    position: relative;
  }
  .sleep-pop {
    position: absolute;
    bottom: 36px;
    right: 0;
    background: rgba(24, 28, 40, 0.92);
    border: 1px solid rgba(255, 255, 255, 0.16);
    border-radius: 10px;
    padding: 4px;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .sp-item {
    border: none;
    background: transparent;
    color: #fff;
    padding: 5px 12px;
    border-radius: 6px;
    font-size: 12px;
  }
  .sp-item:hover {
    background: rgba(255, 255, 255, 0.14);
  }
  .side-pane {
    position: relative;
    align-self: stretch;
    min-height: 0;
    width: 100%;
    max-width: 660px;
    justify-self: center;
    background: transparent;
    border: none;
  }
  .side-inner {
    position: absolute;
    inset: 0;
    overflow: hidden;
  }
  @media (max-width: 740px) {
    .stage {
      grid-template-columns: 1fr;
      align-content: start;
      overflow-y: auto;
      gap: 20px;
    }
    .art-box {
      width: clamp(120px, 24vh, 180px);
    }
    .side-pane {
      height: clamp(240px, 42vh, 340px);
    }
  }
</style>
