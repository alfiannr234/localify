<script lang="ts">
  import { formatTime } from '../lib/format';
  import { library } from '../lib/library.svelte';
  import { menuStore } from '../lib/menu.svelte';
  import { nav } from '../lib/nav.svelte';
  import { player } from '../lib/player.svelte';
  import type { Track } from '../lib/types';
  import Artwork from './Artwork.svelte';
  import Icon from './Icon.svelte';

  interface Props {
    track: Track;
    index: number;
    contextTracks: Track[];
    showAlbum?: boolean;
    selected?: boolean;
    onSelect?: (e: MouseEvent) => void;
    onPlay?: () => void;
  }

  let {
    track,
    index,
    contextTracks,
    showAlbum = true,
    selected = false,
    onSelect,
    onPlay,
  }: Props = $props();

  let isCurrent = $derived(player.currentPath === track.path);
  let isPlaying = $derived(isCurrent && player.playing);

  function triggerPlay(): void {
    if (onPlay) {
      onPlay();
    } else {
      void player.playTrack(track, contextTracks);
    }
  }
</script>

<div
  class="track-row"
  class:current={isCurrent}
  class:selected
  role="row"
  tabindex="0"
  onclick={(e) => onSelect?.(e)}
  ondblclick={triggerPlay}
  oncontextmenu={(e) => menuStore.openForTracks(e, [track])}
  onkeydown={(e) => {
    if (e.key === 'Enter') triggerPlay();
  }}
>
  <button
    type="button"
    class="idx-play"
    aria-label={isPlaying ? 'Pause' : `Play ${track.title}`}
    onclick={(e) => {
      e.stopPropagation();
      if (isCurrent) {
        void player.togglePlay();
      } else {
        triggerPlay();
      }
    }}
  >
    <span class="idx-num tabular">{index + 1}</span>
    <span class="idx-icon">
      <Icon name={isPlaying ? 'pause' : 'play'} size={14} />
    </span>
  </button>

  <Artwork hash={track.artHash} alt={track.album} size={38} radius={6} />

  <div class="main-col">
    <div class="title truncate">{track.title}</div>
    <button
      type="button"
      class="artist truncate"
      onclick={(e) => {
        e.stopPropagation();
        nav.go({ kind: 'artists', selected: track.albumArtist || track.artist });
      }}
    >
      {track.artist}
    </button>
  </div>

  {#if showAlbum}
    <button
      type="button"
      class="album-col truncate"
      onclick={(e) => {
        e.stopPropagation();
        nav.go({ kind: 'album', name: track.album, artist: track.albumArtist || track.artist });
      }}
    >
      {track.album}
    </button>
  {/if}

  <button
    type="button"
    class="btn-icon like-btn"
    class:active={track.liked}
    aria-label={track.liked ? 'Remove from favorites' : 'Add to favorites'}
    onclick={(e) => {
      e.stopPropagation();
      void library.toggleLike(track.path);
    }}
  >
    <Icon name={track.liked ? 'heart-fill' : 'heart'} size={15} />
  </button>

  <span class="dur tabular">{formatTime(track.duration)}</span>

  <button
    type="button"
    class="btn-icon more-btn"
    aria-label="Track options"
    onclick={(e) => {
      e.stopPropagation();
      menuStore.openForTracks(e, [track]);
    }}
  >
    <Icon name="more" size={16} />
  </button>
</div>

<style>
  .track-row {
    height: 52px;
    padding: 0 12px;
    border-radius: var(--r-sm);
    display: grid;
    grid-template-columns: 34px 38px minmax(180px, 1.4fr) minmax(120px, 1fr) 32px 48px 30px;
    align-items: center;
    gap: 12px;
    cursor: pointer;
    transition: background 110ms ease;
  }
  .track-row:hover {
    background: var(--surface-2);
  }
  .track-row.selected {
    background: var(--accent-soft);
  }
  .track-row.current .title {
    color: var(--accent);
  }
  .idx-play {
    width: 34px;
    height: 34px;
    border: none;
    background: transparent;
    color: var(--text-3);
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 0;
  }
  .idx-icon {
    display: none;
    color: var(--text);
  }
  .track-row:hover .idx-num,
  .track-row.current .idx-num {
    display: none;
  }
  .track-row:hover .idx-icon,
  .track-row.current .idx-icon {
    display: flex;
  }
  .track-row.current .idx-icon {
    color: var(--accent);
  }
  .main-col {
    min-width: 0;
    display: flex;
    flex-direction: column;
  }
  .title {
    font-weight: 600;
    font-size: 13.5px;
    color: var(--text);
  }
  .artist,
  .album-col {
    border: none;
    background: transparent;
    padding: 0;
    text-align: left;
    font-size: 12px;
    color: var(--text-2);
  }
  .artist:hover,
  .album-col:hover {
    text-decoration: underline;
    color: var(--text);
  }
  .like-btn {
    width: 28px;
    height: 28px;
    opacity: 0;
  }
  .track-row:hover .like-btn,
  .like-btn.active {
    opacity: 1;
  }
  .dur {
    font-size: 12px;
    color: var(--text-3);
    text-align: right;
  }
  .more-btn {
    width: 28px;
    height: 28px;
    opacity: 0;
  }
  .track-row:hover .more-btn {
    opacity: 1;
  }
</style>
