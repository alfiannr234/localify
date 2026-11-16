<script lang="ts">
  import { formatTime } from '../lib/format';
  import { library } from '../lib/library.svelte';
  import { nav } from '../lib/nav.svelte';
  import { player } from '../lib/player.svelte';
  import Artwork from './Artwork.svelte';
  import Icon from './Icon.svelte';
  import Slider from './Slider.svelte';

  let track = $derived(player.currentTrack);
  let scrubbing = $state(false);
  let scrubVal = $state(0);
  let displayPos = $derived(scrubbing ? scrubVal : player.smoothPosition);
  let totalDur = $derived(player.duration || track?.duration || 0);

  let showSleepMenu = $state(false);
  let showSpeedMenu = $state(false);

  const SPEEDS = [0.75, 0.9, 1.0, 1.1, 1.25, 1.5, 2.0];
  const SLEEP_OPTS = [15, 30, 45, 60];
</script>

<footer class="playerbar" aria-label="Playback controls">
  <div class="left">
    {#if track}
      <button
        type="button"
        class="cover-btn"
        aria-label="Open full-screen now playing"
        onclick={() => (player.nowPlayingOpen = true)}
      >
        <Artwork hash={track.artHash} alt={track.album} size={48} radius={8} />
      </button>
      <div class="meta">
        <button
          type="button"
          class="meta-title truncate"
          onclick={() => nav.go({ kind: 'album', name: track.album, artist: track.albumArtist || track.artist })}
        >
          {track.title}
        </button>
        <button
          type="button"
          class="meta-artist truncate"
          onclick={() => nav.go({ kind: 'artists', selected: track.albumArtist || track.artist })}
        >
          {track.artist}
        </button>
      </div>
      <button
        type="button"
        class="btn-icon"
        class:active={track.liked}
        aria-label={track.liked ? 'Remove from favorites' : 'Add to favorites'}
        onclick={() => library.toggleLike(track.path)}
      >
        <Icon name={track.liked ? 'heart-fill' : 'heart'} size={17} />
      </button>
    {:else}
      <div class="idle-meta">Select a track to play</div>
    {/if}
  </div>

  <div class="center">
    <div class="transport">
      <button
        type="button"
        class="btn-icon"
        class:active={player.shuffle}
        aria-label="Shuffle"
        aria-pressed={player.shuffle}
        onclick={() => player.toggleShuffle()}
      >
        <Icon name="shuffle" size={17} />
      </button>
      <button
        type="button"
        class="btn-icon"
        aria-label="Previous track"
        disabled={!track}
        onclick={() => player.prev()}
      >
        <Icon name="prev" size={18} />
      </button>
      <button
        type="button"
        class="play-circle"
        aria-label={player.playing ? 'Pause' : 'Play'}
        onclick={() => player.togglePlay()}
      >
        <Icon name={player.playing ? 'pause' : 'play'} size={18} />
      </button>
      <button
        type="button"
        class="btn-icon"
        aria-label="Next track"
        disabled={!track}
        onclick={() => player.next(true)}
      >
        <Icon name="next" size={18} />
      </button>
      <button
        type="button"
        class="btn-icon"
        class:active={player.repeat !== 'off'}
        aria-label="Cycle repeat mode"
        onclick={() => player.cycleRepeat()}
      >
        <Icon name={player.repeat === 'one' ? 'repeat-one' : 'repeat'} size={17} />
      </button>
    </div>

    <div class="progress-row">
      <span class="time tabular">{formatTime(displayPos)}</span>
      <Slider
        value={displayPos}
        min={0}
        max={Math.max(1, totalDur)}
        step={0.1}
        label="Playback position"
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
      <span class="time right tabular">{formatTime(totalDur)}</span>
    </div>
  </div>

  <div class="right">
    <button
      type="button"
      class="btn-icon"
      class:active={player.rightPanel === 'lyrics'}
      title="Lyrics panel"
      aria-label="Toggle lyrics panel"
      onclick={() => (player.rightPanel = player.rightPanel === 'lyrics' ? null : 'lyrics')}
    >
      <Icon name="lyrics" size={17} />
    </button>
    <button
      type="button"
      class="btn-icon"
      class:active={player.rightPanel === 'queue'}
      title="Up Next queue"
      aria-label="Toggle queue panel"
      onclick={() => (player.rightPanel = player.rightPanel === 'queue' ? null : 'queue')}
    >
      <Icon name="queue" size={17} />
    </button>

    <div class="popover-anchor">
      <button
        type="button"
        class="btn-icon"
        class:active={player.sleepEndAt !== null}
        title="Sleep timer"
        aria-label="Sleep timer"
        onclick={() => {
          showSleepMenu = !showSleepMenu;
          showSpeedMenu = false;
        }}
      >
        <Icon name="timer" size={17} />
      </button>
      {#if showSleepMenu}
        <div class="popover" role="menu">
          <div class="pop-head">
            <span>Sleep timer</span>
            {#if player.sleepRemainingSecs > 0}
              <span class="tabular accent-text">{formatTime(player.sleepRemainingSecs)}</span>
            {/if}
          </div>
          {#each SLEEP_OPTS as mins}
            <button
              type="button"
              class="pop-item"
              onclick={() => {
                player.setSleepMinutes(mins);
                showSleepMenu = false;
              }}
            >
              {mins} minutes
            </button>
          {/each}
          {#if player.sleepEndAt !== null}
            <button
              type="button"
              class="pop-item danger"
              onclick={() => {
                player.setSleepMinutes(null);
                showSleepMenu = false;
              }}
            >
              Cancel timer
            </button>
          {/if}
        </div>
      {/if}
    </div>

    <div class="popover-anchor">
      <button
        type="button"
        class="speed-pill tabular"
        class:active={player.speed !== 1.0}
        aria-label="Playback speed"
        onclick={() => {
          showSpeedMenu = !showSpeedMenu;
          showSleepMenu = false;
        }}
      >
        {player.speed.toFixed(2).replace(/\.00$/, '')}x
      </button>
      {#if showSpeedMenu}
        <div class="popover" role="menu">
          {#each SPEEDS as sp}
            <button
              type="button"
              class="pop-item tabular"
              class:selected={player.speed === sp}
              onclick={() => {
                player.setSpeed(sp);
                showSpeedMenu = false;
              }}
            >
              {sp.toFixed(2).replace(/\.00$/, '')}x
            </button>
          {/each}
        </div>
      {/if}
    </div>

    <div class="vol-group">
      <button
        type="button"
        class="btn-icon"
        aria-label={player.muted ? 'Unmute' : 'Mute'}
        onclick={() => player.toggleMute()}
      >
        <Icon
          name={player.muted || player.volume === 0 ? 'volume-mute' : player.volume < 0.45 ? 'volume-low' : 'volume-high'}
          size={17}
        />
      </button>
      <div class="vol-slider">
        <Slider
          value={player.muted ? 0 : player.volume}
          min={0}
          max={1}
          step={0.01}
          label="Volume"
          onInput={(v) => player.setVolume(v)}
        />
      </div>
    </div>

    <button
      type="button"
      class="btn-icon"
      title="Now Playing view"
      aria-label="Expand now playing"
      disabled={!track}
      onclick={() => (player.nowPlayingOpen = true)}
    >
      <Icon name="fullscreen" size={16} />
    </button>
  </div>
</footer>

<style>
  .playerbar {
    height: var(--playerbar-h);
    padding: 0 18px;
    display: grid;
    grid-template-columns: minmax(220px, 1fr) minmax(300px, 1.5fr) minmax(240px, 1fr);
    align-items: center;
    gap: 16px;
    background: var(--surface-glass);
    backdrop-filter: blur(28px);
    -webkit-backdrop-filter: blur(28px);
    border-top: 1px solid var(--border);
    flex-shrink: 0;
    z-index: 30;
  }
  .left {
    display: flex;
    align-items: center;
    gap: 11px;
    min-width: 0;
  }
  .cover-btn {
    border: none;
    padding: 0;
    background: transparent;
    border-radius: 8px;
    flex-shrink: 0;
  }
  .meta {
    display: flex;
    flex-direction: column;
    min-width: 0;
  }
  .meta-title,
  .meta-artist {
    border: none;
    padding: 0;
    background: transparent;
    text-align: left;
  }
  .meta-title {
    font-weight: 600;
    font-size: 13.5px;
    color: var(--text);
  }
  .meta-title:hover,
  .meta-artist:hover {
    text-decoration: underline;
  }
  .meta-artist {
    font-size: 12px;
    color: var(--text-2);
  }
  .idle-meta {
    font-size: 12.5px;
    color: var(--text-3);
  }
  .center {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 4px;
    min-width: 0;
  }
  .transport {
    display: flex;
    align-items: center;
    gap: 8px;
  }
  .play-circle {
    width: 36px;
    height: 36px;
    border-radius: 999px;
    border: none;
    background: var(--accent);
    color: var(--accent-fg);
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background 140ms var(--ease), transform 100ms var(--ease);
  }
  .play-circle:hover {
    background: var(--accent-hover);
    transform: scale(1.04);
  }
  .play-circle:active {
    transform: scale(0.96);
  }
  .progress-row {
    width: 100%;
    display: flex;
    align-items: center;
    gap: 9px;
  }
  .time {
    font-size: 11.5px;
    color: var(--text-2);
    min-width: 36px;
  }
  .time.right {
    text-align: right;
  }
  .right {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 4px;
  }
  .vol-group {
    display: flex;
    align-items: center;
    gap: 4px;
  }
  .vol-slider {
    width: 82px;
  }
  .speed-pill {
    height: 26px;
    padding: 0 7px;
    border-radius: var(--r-sm);
    border: 1px solid var(--border);
    background: transparent;
    color: var(--text-2);
    font-size: 11.5px;
    font-weight: 600;
  }
  .speed-pill:hover,
  .speed-pill.active {
    background: var(--surface-2);
    color: var(--accent);
  }
  .popover-anchor {
    position: relative;
  }
  .popover {
    position: absolute;
    bottom: 38px;
    right: 0;
    min-width: 152px;
    padding: 6px;
    border-radius: var(--r-md);
    background: var(--surface);
    border: 1px solid var(--border-strong);
    box-shadow: var(--shadow-lg);
    display: flex;
    flex-direction: column;
    gap: 2px;
    z-index: 60;
  }
  .pop-head {
    display: flex;
    justify-content: space-between;
    padding: 5px 8px;
    font-size: 11.5px;
    color: var(--text-3);
    font-weight: 600;
  }
  .accent-text {
    color: var(--accent);
  }
  .pop-item {
    border: none;
    background: transparent;
    border-radius: var(--r-sm);
    padding: 6px 8px;
    text-align: left;
    font-size: 12.5px;
    color: var(--text);
  }
  .pop-item:hover,
  .pop-item.selected {
    background: var(--surface-2);
  }
  .pop-item.selected {
    color: var(--accent);
    font-weight: 600;
  }
  .pop-item.danger {
    color: var(--danger);
  }
</style>
