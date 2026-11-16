<script lang="ts">
  import { library } from '../lib/library.svelte';
  import { nav } from '../lib/nav.svelte';
  import { player } from '../lib/player.svelte';
  import Icon from './Icon.svelte';

  let searchInput: HTMLInputElement | undefined = $state();

  function onSearchInput(e: Event): void {
    const val = (e.currentTarget as HTMLInputElement).value;
    nav.searchQuery = val;
    if (val.trim().length > 0 && nav.current.kind !== 'search') {
      nav.go({ kind: 'search' });
    }
  }

  function handleWindowKey(e: KeyboardEvent): void {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'k') {
      e.preventDefault();
      searchInput?.focus();
      searchInput?.select();
    }
  }
</script>

<svelte:window onkeydown={handleWindowKey} />

<header class="topbar">
  <div class="nav-arrows">
    <button
      type="button"
      class="btn-icon"
      aria-label="Back"
      disabled={!nav.canBack}
      onclick={() => nav.back()}
    >
      <Icon name="chevron-left" size={17} />
    </button>
    <button
      type="button"
      class="btn-icon"
      aria-label="Forward"
      disabled={!nav.canForward}
      onclick={() => nav.forward()}
    >
      <Icon name="chevron-right" size={17} />
    </button>
  </div>

  <div class="search-wrap">
    <Icon name="search" size={15} class="search-ic" />
    <input
      bind:this={searchInput}
      type="text"
      class="search-input"
      placeholder="Search songs, artists, albums (Ctrl+K)"
      value={nav.searchQuery}
      oninput={onSearchInput}
      onfocus={() => {
        if (nav.searchQuery.trim() && nav.current.kind !== 'search') {
          nav.go({ kind: 'search' });
        }
      }}
    />
    {#if nav.searchQuery}
      <button
        type="button"
        class="btn-icon clear-btn"
        aria-label="Clear search"
        onclick={() => {
          nav.searchQuery = '';
        }}
      >
        <Icon name="close" size={14} />
      </button>
    {/if}
  </div>

  <div class="top-actions">
    <button
      type="button"
      class="btn-icon"
      title="Rescan folders"
      aria-label="Rescan music folders"
      disabled={library.scanning}
      onclick={() => library.rescan()}
    >
      <Icon name="refresh" size={17} class={library.scanning ? 'spin' : ''} />
    </button>
    <button
      type="button"
      class="btn-icon"
      title="Mini player"
      aria-label="Switch to mini player"
      onclick={() => player.toggleMiniMode()}
    >
      <Icon name="pip" size={17} />
    </button>
  </div>
</header>

<style>
  .topbar {
    height: var(--topbar-h);
    padding: 0 18px;
    display: flex;
    align-items: center;
    gap: 14px;
    background: var(--surface-glass);
    backdrop-filter: blur(24px);
    -webkit-backdrop-filter: blur(24px);
    border-bottom: 1px solid var(--border);
    flex-shrink: 0;
    z-index: 20;
  }
  .nav-arrows {
    display: flex;
    align-items: center;
    gap: 4px;
  }
  .search-wrap {
    position: relative;
    flex: 1;
    max-width: 440px;
    display: flex;
    align-items: center;
  }
  .search-wrap :global(.search-ic) {
    position: absolute;
    left: 11px;
    color: var(--text-3);
    pointer-events: none;
  }
  .search-input {
    width: 100%;
    height: 33px;
    padding: 0 32px 0 33px;
    border-radius: var(--r-full);
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--text);
    font-size: 13px;
    transition: border-color 140ms var(--ease), background 140ms var(--ease);
  }
  .search-input:focus {
    outline: none;
    border-color: var(--accent);
    background: var(--surface-2);
  }
  .clear-btn {
    position: absolute;
    right: 4px;
    width: 24px;
    height: 24px;
  }
  .top-actions {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 4px;
  }
</style>
