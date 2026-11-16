<script lang="ts">
  import { open } from '@tauri-apps/plugin-dialog';
  import { api } from '../lib/api';
  import { formatFileSize, formatTime } from '../lib/format';
  import { library } from '../lib/library.svelte';
  import { menuStore } from '../lib/menu.svelte';
  import type { FullTags } from '../lib/types';
  import Icon from './Icon.svelte';

  let path = $derived(menuStore.tagEditorPath);
  let track = $derived(path ? library.trackMap.get(path) ?? null : null);
  let form = $state<FullTags | null>(null);
  let coverPath = $state<string | null>(null);
  let tab = $state<'tags' | 'info'>('tags');
  let saving = $state(false);
  let error = $state<string | null>(null);

  $effect(() => {
    const p = path;
    if (!p) {
      form = null;
      coverPath = null;
      error = null;
      return;
    }
    tab = 'tags';
    error = null;
    coverPath = null;
    void api
      .readTags(p)
      .then((res) => (form = res))
      .catch((e) => (error = e instanceof Error ? e.message : String(e)));
  });

  async function pickCover(): Promise<void> {
    const picked = await open({
      multiple: false,
      filters: [{ name: 'Images', extensions: ['jpg', 'jpeg', 'png', 'webp'] }],
    });
    if (picked && !Array.isArray(picked)) {
      coverPath = picked;
    }
  }

  async function saveTags(e: Event): Promise<void> {
    e.preventDefault();
    if (!form) return;
    saving = true;
    error = null;
    try {
      const updated = await api.writeTags({
        ...form,
        coverPath,
      });
      library.tracks = library.tracks.map((t) => (t.path === updated.path ? updated : t));
      menuStore.closeTagEditor();
    } catch (err) {
      error = err instanceof Error ? err.message : String(err);
    } finally {
      saving = false;
    }
  }
</script>

{#if path}
  <div class="modal-backdrop" role="presentation" onclick={() => menuStore.closeTagEditor()}>
    <div
      class="modal"
      role="dialog"
      tabindex="-1"
      aria-modal="true"
      aria-label="Edit tags and file info"
      onclick={(e) => e.stopPropagation()}
      onkeydown={(e) => {
        if (e.key === 'Escape') menuStore.closeTagEditor();
      }}
    >
      <div class="modal-head">
        <div class="tabs">
          <button
            type="button"
            class="tab"
            class:active={tab === 'tags'}
            onclick={() => (tab = 'tags')}
          >
            Edit Tags
          </button>
          <button
            type="button"
            class="tab"
            class:active={tab === 'info'}
            onclick={() => (tab = 'info')}
          >
            Technical Info
          </button>
        </div>
        <button
          type="button"
          class="btn-icon"
          aria-label="Close modal"
          onclick={() => menuStore.closeTagEditor()}
        >
          <Icon name="close" size={16} />
        </button>
      </div>

      {#if error}
        <div class="err-banner" role="alert">{error}</div>
      {/if}

      {#if tab === 'tags'}
        {#if !form}
          <div class="loading">Reading audio file tags...</div>
        {:else}
          <form class="form-body scroll-thin" onsubmit={saveTags}>
            <div class="grid-2">
              <label class="field">
                <span>Title</span>
                <input class="input" bind:value={form.title} />
              </label>
              <label class="field">
                <span>Artist</span>
                <input class="input" bind:value={form.artist} />
              </label>
              <label class="field">
                <span>Album</span>
                <input class="input" bind:value={form.album} />
              </label>
              <label class="field">
                <span>Album Artist</span>
                <input class="input" bind:value={form.albumArtist} />
              </label>
              <label class="field">
                <span>Genre</span>
                <input class="input" bind:value={form.genre} />
              </label>
              <div class="grid-3">
                <label class="field">
                  <span>Year</span>
                  <input class="input tabular" type="number" bind:value={form.year} />
                </label>
                <label class="field">
                  <span>Track</span>
                  <input class="input tabular" type="number" bind:value={form.trackNo} />
                </label>
                <label class="field">
                  <span>Disc</span>
                  <input class="input tabular" type="number" bind:value={form.discNo} />
                </label>
              </div>
            </div>

            <div class="cover-row">
              <button type="button" class="btn" onclick={pickCover}>Replace Cover Image</button>
              <span class="cover-hint truncate">{coverPath ?? 'Keep embedded artwork'}</span>
            </div>

            <label class="field">
              <span>Embedded Lyrics</span>
              <textarea class="input lrc-area scroll-thin" rows="5" bind:value={form.lyrics}></textarea>
            </label>

            <div class="modal-foot">
              <button type="button" class="btn" onclick={() => menuStore.closeTagEditor()}>Cancel</button>
              <button type="submit" class="btn btn-primary" disabled={saving}>
                {saving ? 'Saving...' : 'Save Changes'}
              </button>
            </div>
          </form>
        {/if}
      {:else if track}
        <div class="info-body scroll-thin">
          <div class="info-row"><span>Location</span><span class="mono">{track.path}</span></div>
          <div class="info-row"><span>Codec</span><span>{track.codec ?? 'Audio'}</span></div>
          <div class="info-row"><span>Duration</span><span class="tabular">{formatTime(track.duration)}</span></div>
          <div class="info-row"><span>Sample rate</span><span class="tabular">{track.sampleRate ? `${track.sampleRate} Hz` : '-'}</span></div>
          <div class="info-row"><span>Bit depth</span><span class="tabular">{track.bitDepth ? `${track.bitDepth}-bit` : '-'}</span></div>
          <div class="info-row"><span>Bitrate</span><span class="tabular">{track.bitrate ? `${track.bitrate} kbps` : '-'}</span></div>
          <div class="info-row"><span>Channels</span><span class="tabular">{track.channels ?? '-'}</span></div>
          <div class="info-row"><span>File size</span><span class="tabular">{formatFileSize(track.fileSize)}</span></div>
          <div class="info-row"><span>Play count</span><span class="tabular">{track.playCount}</span></div>
        </div>
      {/if}
    </div>
  </div>
{/if}

<style>
  .modal-backdrop {
    position: fixed;
    inset: 0;
    z-index: 150;
    background: rgba(8, 9, 13, 0.72);
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 20px;
  }
  .modal {
    width: 100%;
    max-width: 600px;
    max-height: 86vh;
    background: var(--surface);
    border: 1px solid var(--border-strong);
    border-radius: var(--r-lg);
    box-shadow: var(--shadow-lg);
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
  .modal-head {
    padding: 12px 16px;
    border-bottom: 1px solid var(--border);
    display: flex;
    align-items: center;
    justify-content: space-between;
  }
  .tabs {
    display: flex;
    gap: 6px;
  }
  .tab {
    height: 30px;
    padding: 0 12px;
    border: none;
    border-radius: var(--r-sm);
    background: transparent;
    color: var(--text-2);
    font-weight: 600;
  }
  .tab.active {
    background: var(--accent);
    color: var(--accent-fg);
  }
  .err-banner {
    margin: 10px 16px 0;
    padding: 8px 12px;
    border-radius: var(--r-sm);
    background: rgba(248, 113, 113, 0.14);
    color: var(--danger);
    font-size: 12.5px;
  }
  .form-body {
    padding: 16px;
    overflow-y: auto;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }
  .grid-2 {
    display: grid;
    grid-template-columns: 1fr 1fr;
    gap: 12px;
  }
  .grid-3 {
    display: grid;
    grid-template-columns: 1fr 1fr 1fr;
    gap: 8px;
  }
  .field {
    display: flex;
    flex-direction: column;
    gap: 5px;
    font-size: 12px;
    color: var(--text-2);
  }
  .lrc-area {
    height: auto;
    padding: 8px 10px;
    resize: vertical;
    font-family: var(--font-mono);
    font-size: 12px;
  }
  .cover-row {
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .cover-hint {
    font-size: 12px;
    color: var(--text-3);
  }
  .modal-foot {
    display: flex;
    justify-content: flex-end;
    gap: 8px;
    padding-top: 6px;
  }
  .loading {
    padding: 36px;
    text-align: center;
    color: var(--text-3);
  }
  .info-body {
    padding: 16px;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .info-row {
    display: flex;
    justify-content: space-between;
    gap: 16px;
    font-size: 13px;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--border);
  }
  .info-row span:first-child {
    color: var(--text-2);
    flex-shrink: 0;
  }
  .mono {
    font-family: var(--font-mono);
    font-size: 11.5px;
    word-break: break-all;
    text-align: right;
  }
</style>
