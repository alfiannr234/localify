<script lang="ts">
  import { api } from '../lib/api';
  import { library } from '../lib/library.svelte';
  import { menuStore } from '../lib/menu.svelte';
  import { player } from '../lib/player.svelte';
  import Icon from './Icon.svelte';

  let menu = $derived(menuStore.state);
  let showPlaylists = $state(false);
</script>

<svelte:window
  onclick={() => {
    showPlaylists = false;
    menuStore.close();
  }}
  onkeydown={(e) => {
    if (e.key === 'Escape') menuStore.close();
  }}
/>

{#if menu}
  {@const first = menu.tracks[0]}
  {@const many = menu.tracks.length > 1}
  <div
    class="ctx-menu"
    style="left: {menu.x}px; top: {menu.y}px;"
    role="menu"
    tabindex="-1"
    onclick={(e) => e.stopPropagation()}
    onkeydown={(e) => {
      if (e.key === 'Escape') menuStore.close();
    }}
  >
    <button
      type="button"
      class="ctx-item"
      onclick={() => {
        void player.playTrack(first, menu.tracks.length > 1 ? menu.tracks : library.tracks);
        menuStore.close();
      }}
    >
      <Icon name="play" size={15} />
      <span>{many ? `Play ${menu.tracks.length} tracks` : 'Play now'}</span>
    </button>

    {#if !many}
      <button
        type="button"
        class="ctx-item"
        onclick={() => {
          player.playNextInQueue(first);
          menuStore.close();
        }}
      >
        <Icon name="next" size={15} />
        <span>Play next</span>
      </button>
    {/if}

    <button
      type="button"
      class="ctx-item"
      onclick={() => {
        player.appendToQueue(menu.tracks);
        menuStore.close();
      }}
    >
      <Icon name="queue" size={15} />
      <span>Add to queue</span>
    </button>

    <div class="divider"></div>

    <div class="sub-wrap">
      <button
        type="button"
        class="ctx-item"
        onclick={() => (showPlaylists = !showPlaylists)}
      >
        <Icon name="playlists" size={15} />
        <span>Add to playlist</span>
        <Icon name="chevron-right" size={13} class="ml-auto" />
      </button>
      {#if showPlaylists}
        <div class="submenu">
          {#if library.playlists.length === 0}
            <div class="sub-empty">No playlists yet</div>
          {:else}
            {#each library.playlists as pl (pl.id)}
              <button
                type="button"
                class="ctx-item"
                onclick={() => {
                  void library.addToPlaylist(pl.id, menu.tracks.map((t) => t.path));
                  menuStore.close();
                }}
              >
                <span class="truncate">{pl.name}</span>
              </button>
            {/each}
          {/if}
        </div>
      {/if}
    </div>

    {#if !many}
      <button
        type="button"
        class="ctx-item"
        onclick={() => {
          void library.toggleLike(first.path);
          menuStore.close();
        }}
      >
        <Icon name={first.liked ? 'heart-fill' : 'heart'} size={15} />
        <span>{first.liked ? 'Remove from favorites' : 'Add to favorites'}</span>
      </button>

      <div class="divider"></div>

      <button
        type="button"
        class="ctx-item"
        onclick={() => menuStore.openTagEditor(first.path)}
      >
        <Icon name="edit" size={15} />
        <span>Edit tags & file info</span>
      </button>

      <button
        type="button"
        class="ctx-item"
        onclick={() => {
          void api.revealInExplorer(first.path);
          menuStore.close();
        }}
      >
        <Icon name="folder" size={15} />
        <span>Show in Explorer</span>
      </button>
    {/if}
  </div>
{/if}

<style>
  .ctx-menu {
    position: fixed;
    z-index: 120;
    min-width: 210px;
    padding: 6px;
    border-radius: var(--r-md);
    background: var(--surface);
    border: 1px solid var(--border-strong);
    box-shadow: var(--shadow-lg);
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .ctx-item {
    border: none;
    background: transparent;
    color: var(--text);
    padding: 7px 10px;
    border-radius: var(--r-sm);
    display: flex;
    align-items: center;
    gap: 9px;
    font-size: 13px;
    text-align: left;
  }
  .ctx-item:hover {
    background: var(--surface-2);
  }
  .ctx-item :global(.ml-auto) {
    margin-left: auto;
    color: var(--text-3);
  }
  .divider {
    height: 1px;
    background: var(--border);
    margin: 4px 6px;
  }
  .sub-wrap {
    position: relative;
  }
  .submenu {
    position: absolute;
    left: 100%;
    top: 0;
    margin-left: 6px;
    min-width: 170px;
    max-height: 220px;
    overflow-y: auto;
    padding: 6px;
    border-radius: var(--r-md);
    background: var(--surface);
    border: 1px solid var(--border-strong);
    box-shadow: var(--shadow-lg);
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .sub-empty {
    padding: 8px 10px;
    font-size: 12px;
    color: var(--text-3);
  }
</style>
