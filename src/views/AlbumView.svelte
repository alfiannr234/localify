<script lang="ts">
  import Artwork from '../components/Artwork.svelte';
  import Icon from '../components/Icon.svelte';
  import TrackRow from '../components/TrackRow.svelte';
  import { formatTotalDuration } from '../lib/format';
  import { library } from '../lib/library.svelte';
  import { nav } from '../lib/nav.svelte';
  import { player } from '../lib/player.svelte';

  interface Props {
    name: string;
    artist: string;
  }

  let { name, artist }: Props = $props();

  let album = $derived(
    library.albums.find(
      (a) => a.name.toLowerCase() === name.toLowerCase() && a.artist.toLowerCase() === artist.toLowerCase()
    ) ?? library.albums.find((a) => a.name.toLowerCase() === name.toLowerCase())
  );
</script>

<div class="album-view scroll-thin">
  {#if !album}
    <div class="empty">Album not found.</div>
  {:else}
    <div class="banner">
      <Artwork hash={album.artHash} alt={album.name} size={172} thumb={false} radius={12} />
      <div class="banner-meta">
        <span class="kicker">ALBUM</span>
        <h1>{album.name}</h1>
        <p class="subtitle">
          <button
            type="button"
            class="artist-link"
            onclick={() => nav.go({ kind: 'artists', selected: album.artist })}
          >
            {album.artist}
          </button>
          {#if album.year}
            <span class="tabular"> / {album.year}</span>
          {/if}
          <span class="tabular"> / {album.tracks.length} tracks / {formatTotalDuration(album.duration)}</span>
        </p>
        <div class="actions">
          <button
            type="button"
            class="btn btn-primary"
            onclick={() => {
              player.shuffle = false;
              if (album.tracks[0]) void player.playTrack(album.tracks[0], album.tracks);
            }}
          >
            <Icon name="play" size={16} />
            <span>Play</span>
          </button>
          <button
            type="button"
            class="btn"
            onclick={() => {
              player.shuffle = true;
              const pick = album.tracks[Math.floor(Math.random() * album.tracks.length)];
              if (pick) void player.playTrack(pick, album.tracks);
            }}
          >
            <Icon name="shuffle" size={16} />
            <span>Shuffle</span>
          </button>
          <button type="button" class="btn" onclick={() => player.appendToQueue(album.tracks)}>
            <Icon name="queue" size={16} />
            <span>Add to Queue</span>
          </button>
        </div>
      </div>
    </div>

    <div class="tracklist">
      {#each album.tracks as track, idx (track.path)}
        <TrackRow {track} index={idx} contextTracks={album.tracks} showAlbum={false} />
      {/each}
    </div>
  {/if}
</div>

<style>
  .album-view {
    height: 100%;
    overflow-y: auto;
    padding: 24px 28px 36px;
    display: flex;
    flex-direction: column;
    gap: 22px;
  }
  .banner {
    display: flex;
    gap: 22px;
    align-items: flex-end;
    padding-bottom: 18px;
    border-bottom: 1px solid var(--border);
  }
  .banner-meta {
    display: flex;
    flex-direction: column;
    gap: 8px;
    min-width: 0;
  }
  .kicker {
    font-size: 11px;
    font-weight: 700;
    letter-spacing: 0.08em;
    color: var(--accent);
  }
  .banner-meta h1 {
    margin: 0;
    font-size: 26px;
    font-weight: 700;
  }
  .subtitle {
    margin: 0;
    color: var(--text-2);
    font-size: 13.5px;
  }
  .artist-link {
    border: none;
    background: transparent;
    padding: 0;
    color: var(--text);
    font-weight: 600;
  }
  .artist-link:hover {
    text-decoration: underline;
    color: var(--accent);
  }
  .actions {
    display: flex;
    gap: 10px;
    margin-top: 6px;
  }
  .tracklist {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .empty {
    margin: auto;
    color: var(--text-3);
  }
</style>
