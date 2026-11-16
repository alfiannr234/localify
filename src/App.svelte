<script lang="ts">
  import { listen } from '@tauri-apps/api/event';
  import ContextMenu from './components/ContextMenu.svelte';
  import MiniPlayer from './components/MiniPlayer.svelte';
  import NowPlaying from './components/NowPlaying.svelte';
  import PlayerBar from './components/PlayerBar.svelte';
  import RightPanel from './components/RightPanel.svelte';
  import Sidebar from './components/Sidebar.svelte';
  import TagEditor from './components/TagEditor.svelte';
  import TopBar from './components/TopBar.svelte';
  import { api } from './lib/api';
  import { applyAppearance } from './lib/appearance.svelte';
  import { eqStore } from './lib/eq.svelte';
  import { library } from './lib/library.svelte';
  import { nav } from './lib/nav.svelte';
  import { player } from './lib/player.svelte';
  import { settings } from './lib/settings.svelte';
  import AlbumGrid from './views/AlbumGrid.svelte';
  import AlbumView from './views/AlbumView.svelte';
  import Artists from './views/Artists.svelte';
  import EqView from './views/EqView.svelte';
  import Genres from './views/Genres.svelte';
  import Home from './views/Home.svelte';
  import PlaylistView from './views/PlaylistView.svelte';
  import Search from './views/Search.svelte';
  import Settings from './views/Settings.svelte';
  import Songs from './views/Songs.svelte';

  let ready = $state(false);

  $effect(() => {
    let cleanupFns: Array<() => void> = [];

    void (async () => {
      await settings.load();
      await eqStore.load();
      applyAppearance(null);
      await library.init();
      await player.initListeners();

      if (settings.outputDevice) {
        await api.audioSetDevice(settings.outputDevice).catch(() => {});
      }

      const u1 = await listen<{ current: number; total: number }>('scan-progress', (ev) => {
        library.scanCurrent = ev.payload.current;
        library.scanTotal = ev.payload.total;
      });
      const u2 = await listen<void>('library-updated', () => {
        void library.refreshFromDb();
      });
      cleanupFns = [u1, u2];

      const launchFiles = await api.getLaunchFiles();
      if (launchFiles.length > 0) {
        const first = launchFiles[0];
        const found = library.trackMap.get(first);
        if (found) {
          void player.playTrack(found, library.tracks);
        } else {
          void player.playPaths(launchFiles, 0);
        }
      }
      ready = true;
    })();

    return () => {
      for (const fn of cleanupFns) fn();
      player.destroy();
    };
  });

  function onGlobalKeyDown(e: KeyboardEvent): void {
    const tag = (e.target as HTMLElement | null)?.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return;

    if (e.code === 'Space') {
      e.preventDefault();
      void player.togglePlay();
    } else if (e.ctrlKey && e.key === 'ArrowRight') {
      e.preventDefault();
      void player.next(true);
    } else if (e.ctrlKey && e.key === 'ArrowLeft') {
      e.preventDefault();
      void player.prev();
    } else if (e.key.toLowerCase() === 'm' && !e.ctrlKey) {
      player.toggleMute();
    } else if (e.key.toLowerCase() === 'f' && !e.ctrlKey && player.currentTrack) {
      player.nowPlayingOpen = !player.nowPlayingOpen;
    }
  }
</script>

<svelte:window onkeydown={onGlobalKeyDown} />

{#if !ready && library.loading}
  <div class="boot-screen">
    <img src="/localify.png" alt="Localify" class="boot-logo" />
  </div>
{:else if player.miniMode}
  <MiniPlayer />
{:else}
  <div class="shell">
    <Sidebar />
    <div class="workspace">
      <TopBar />
      <main class="main-content">
        {#if player.error}
          <div class="play-err" role="alert">
            <span>{player.error}</span>
            <button type="button" class="btn-icon" aria-label="Dismiss" onclick={() => (player.error = null)}>
              x
            </button>
          </div>
        {/if}

        <div class="view-container">
          {#if nav.current.kind === 'home'}
            <Home />
          {:else if nav.current.kind === 'songs'}
            <Songs />
          {:else if nav.current.kind === 'albums'}
            <AlbumGrid />
          {:else if nav.current.kind === 'album'}
            <AlbumView name={nav.current.name} artist={nav.current.artist} />
          {:else if nav.current.kind === 'artists'}
            <Artists selected={nav.current.selected} />
          {:else if nav.current.kind === 'genres'}
            <Genres selected={nav.current.selected} />
          {:else if nav.current.kind === 'liked'}
            <PlaylistView likedOnly={true} />
          {:else if nav.current.kind === 'playlist'}
            <PlaylistView playlistId={nav.current.id} />
          {:else if nav.current.kind === 'search'}
            <Search />
          {:else if nav.current.kind === 'eq'}
            <EqView />
          {:else if nav.current.kind === 'settings'}
            <Settings />
          {/if}
        </div>
      </main>
    </div>
    <RightPanel />
  </div>

  <PlayerBar />
{/if}

<NowPlaying />
<ContextMenu />
<TagEditor />

<style>
  .boot-screen {
    height: 100vh;
    width: 100vw;
    display: flex;
    align-items: center;
    justify-content: center;
    background: var(--bg);
  }
  .boot-logo {
    width: 56px;
    height: 56px;
    border-radius: 12px;
  }
  .shell {
    height: calc(100vh - var(--playerbar-h));
    width: 100vw;
    display: flex;
    overflow: hidden;
  }
  .workspace {
    flex: 1;
    min-width: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
    background: var(--bg);
  }
  .main-content {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
  .view-container {
    flex: 1;
    min-height: 0;
    overflow: hidden;
  }
  .play-err {
    margin: 10px 24px 0;
    padding: 8px 14px;
    border-radius: var(--r-sm);
    background: rgba(248, 113, 113, 0.14);
    color: var(--danger);
    display: flex;
    align-items: center;
    justify-content: space-between;
    font-size: 13px;
  }
</style>
