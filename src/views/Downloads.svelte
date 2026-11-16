<script lang="ts">
  import { open } from '@tauri-apps/plugin-dialog';
  import Icon from '../components/Icon.svelte';
  import Toggle from '../components/Toggle.svelte';
  import { api } from '../lib/api';
  import { DOWNLOAD_PRESETS, downloadsStore } from '../lib/downloads.svelte';
  import { settings } from '../lib/settings.svelte';

  let text = $state('');

  $effect(() => {
    void downloadsStore.init();
  });

  let dest = $derived(settings.downloadFolder || downloadsStore.defaultFolder);
  let urls = $derived(text.split(/\s+/).filter((u) => /^https?:\/\//i.test(u)));
  let hasFfmpeg = $derived(Boolean(downloadsStore.tools?.ffmpeg));
  let needsFfmpeg = $derived(
    Boolean(DOWNLOAD_PRESETS.find((p) => p.id === settings.downloadPreset)?.needsFfmpeg && !hasFfmpeg)
  );
  const mb = (n: number) => `${(n / 1048576).toFixed(0)} MB`;

  async function pickFolder(): Promise<void> {
    const picked = await open({ directory: true, multiple: false, title: 'Save downloads into folder' });
    if (picked && !Array.isArray(picked)) {
      settings.downloadFolder = picked;
      settings.save('download_folder', picked);
    }
  }

  async function submit(): Promise<void> {
    if (!urls.length || !dest || needsFfmpeg) return;
    const ok = await downloadsStore.startDownload(
      urls,
      settings.downloadPreset,
      dest,
      settings.downloadToLibrary
    );
    if (ok) text = '';
  }
</script>

<div class="dl-page scroll-thin">
  <div class="dl-head">
    <h1>Downloads</h1>
    <p class="lede">
      Paste video, song, playlist, or channel links to save audio or video into your library folder.
      Download only content you have the right to keep.
    </p>
  </div>

  {#if !downloadsStore.tools}
    <div class="card-sec">Checking download tools...</div>
  {:else if !downloadsStore.tools.ytdlp}
    <section class="card-sec">
      <h2>Install yt-dlp</h2>
      <p class="hint">
        Localify downloads the official Windows binary directly from yt-dlp's GitHub release and
        verifies it against its published SHA-256 checksum before running.
      </p>
      <div class="actions">
        <button
          type="button"
          class="btn btn-primary"
          disabled={Boolean(downloadsStore.installing)}
          onclick={() => downloadsStore.installTool('ytdlp')}
        >
          <Icon name="download" size={16} />
          <span>
            {downloadsStore.installing === 'ytdlp'
              ? `Downloading ${downloadsStore.toolTotal ? Math.round((downloadsStore.toolDone / downloadsStore.toolTotal) * 100) : 0}%`
              : 'Get yt-dlp (GitHub Release)'}
          </span>
        </button>
      </div>
    </section>
  {:else}
    <section class="card-sec">
      <textarea
        class="input link-area scroll-thin"
        rows="4"
        placeholder="Paste links, one per line: videos, songs, playlists, or channels"
        aria-label="Links to download"
        spellcheck="false"
        bind:value={text}
      ></textarea>

      <div class="presets" role="radiogroup" aria-label="Download format">
        {#each DOWNLOAD_PRESETS as p (p.id)}
          <button
            type="button"
            class="preset-chip"
            class:active={settings.downloadPreset === p.id}
            role="radio"
            aria-checked={settings.downloadPreset === p.id}
            onclick={() => {
              settings.downloadPreset = p.id;
              settings.save('download_preset', p.id);
            }}
          >
            {p.label}
          </button>
        {/each}
      </div>

      <p class="hint">
        {DOWNLOAD_PRESETS.find((p) => p.id === settings.downloadPreset)?.hint}
        {#if !hasFfmpeg && settings.downloadPreset !== 'audioOriginal'}
          Without ffmpeg, MP3 conversion is disabled and video stays single-stream without metadata embedding.
        {/if}
      </p>

      <div class="dest-row">
        <span class="truncate">Save to <b>{dest || '...'}</b></span>
        <div class="dest-btns">
          {#if settings.downloadFolder}
            <button
              type="button"
              class="btn"
              onclick={() => {
                settings.downloadFolder = '';
                settings.save('download_folder', '');
              }}
            >
              Use Default
            </button>
          {/if}
          <button type="button" class="btn" onclick={pickFolder}>
            <Icon name="folder" size={15} />
            <span>Change...</span>
          </button>
        </div>
      </div>

      <div class="toggle-row">
        <div>
          <div class="t-title">Add folder to library</div>
          <div class="hint">Finished audio files are indexed automatically with your music</div>
        </div>
        <Toggle
          checked={settings.downloadToLibrary}
          label="Add download folder to library"
          onChange={(v) => {
            settings.downloadToLibrary = v;
            settings.save('download_to_library', String(v));
          }}
        />
      </div>

      <div class="go-row">
        <button
          type="button"
          class="btn btn-primary"
          disabled={urls.length === 0 || !dest || needsFfmpeg}
          onclick={submit}
        >
          <Icon name="download" size={16} />
          <span>{urls.length > 1 ? `Download ${urls.length} Links` : 'Download'}</span>
        </button>
        {#if needsFfmpeg}
          <span class="warn-txt">MP3 preset requires ffmpeg (install below).</span>
        {/if}
      </div>
    </section>

    {#if downloadsStore.jobs.length > 0}
      <section class="jobs-sec">
        <div class="jobs-head">
          <h2>Queue ({downloadsStore.jobs.length})</h2>
          <button type="button" class="btn" onclick={() => downloadsStore.clearFinished()}>
            Clear Finished
          </button>
        </div>

        <ul class="jobs-list">
          {#each downloadsStore.jobs as j (j.id)}
            <li class="job-row">
              <div class="job-main">
                <div class="job-title truncate">
                  {j.title || j.urls[0]}{j.urls.length > 1 ? ` (+${j.urls.length - 1})` : ''}
                </div>
                <div class="job-sub tabular">
                  {#if j.status === 'queued'}
                    Waiting in queue
                  {:else if j.status === 'running'}
                    {j.percent !== null ? `${j.percent.toFixed(0)}%` : 'Starting'}
                    {j.eta ? ` / ${j.eta} left` : ''}
                    {j.files.length ? ` / ${j.files.length} saved` : ''}
                  {:else if j.status === 'done'}
                    Saved {j.files.length || 1} {j.files.length === 1 ? 'file' : 'files'}
                  {:else if j.status === 'cancelled'}
                    Cancelled
                  {:else}
                    {j.error}
                  {/if}
                </div>
                {#if j.status === 'running'}
                  <div class="prog-bar">
                    <div class="prog-fill" style="width: {j.percent ?? 0}%;"></div>
                  </div>
                {/if}
              </div>

              {#if j.status === 'done'}
                <span class="ok-ic"><Icon name="check-circle" size={18} /></span>
              {:else if j.status === 'failed'}
                <span class="bad-ic"><Icon name="warning-circle" size={18} /></span>
              {/if}

              {#if j.files.length > 0}
                <button
                  type="button"
                  class="btn-icon"
                  title="Show in Explorer"
                  aria-label="Show file in Explorer"
                  onclick={() => api.revealInExplorer(j.files[j.files.length - 1])}
                >
                  <Icon name="folder" size={16} />
                </button>
              {/if}

              {#if j.status === 'queued' || j.status === 'running'}
                <button
                  type="button"
                  class="btn-icon"
                  aria-label="Cancel download"
                  onclick={() => downloadsStore.cancel(j.id)}
                >
                  <Icon name="close" size={15} />
                </button>
              {/if}
            </li>
          {/each}
        </ul>
      </section>
    {/if}

    <section class="card-sec tools-card">
      <h2>Verification & Tools</h2>
      <div class="tool-line">
        <div>
          <div class="t-title">yt-dlp</div>
          <div class="hint tabular">Version {downloadsStore.tools.ytdlp} (SHA-256 verified)</div>
        </div>
        <button type="button" class="btn" onclick={() => downloadsStore.updateYtdlp()}>
          <Icon name="refresh" size={15} />
          <span>Update yt-dlp</span>
        </button>
      </div>

      <div class="tool-line">
        <div class="min-w-0">
          <div class="t-title">ffmpeg</div>
          <div class="hint truncate">
            {downloadsStore.tools.ffmpeg ?? 'Not found on PATH: required for MP3 conversion and metadata embedding'}
          </div>
        </div>
        {#if !hasFfmpeg}
          <button
            type="button"
            class="btn"
            disabled={Boolean(downloadsStore.installing)}
            onclick={() => downloadsStore.installTool('ffmpeg')}
          >
            {downloadsStore.installing === 'ffmpeg'
              ? `Downloading ${downloadsStore.toolTotal ? `${mb(downloadsStore.toolDone)} / ${mb(downloadsStore.toolTotal)}` : ''}`
              : 'Get ffmpeg (SHA-256)'}
          </button>
        {/if}
      </div>
    </section>
  {/if}

  {#if downloadsStore.error}
    <div class="err-box" role="alert">{downloadsStore.error}</div>
  {/if}
</div>

<style>
  .dl-page {
    height: 100%;
    overflow-y: auto;
    padding: 24px 28px 48px;
    max-width: 860px;
    display: flex;
    flex-direction: column;
    gap: 20px;
  }
  .dl-head h1 {
    margin: 0;
    font-size: 26px;
    font-weight: 700;
  }
  .lede {
    margin: 6px 0 0;
    color: var(--text-2);
    line-height: 1.5;
  }
  .card-sec {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--r-lg);
    padding: 18px 20px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }
  .card-sec h2 {
    margin: 0;
    font-size: 15px;
    font-weight: 700;
    color: var(--accent);
  }
  .link-area {
    width: 100%;
    height: auto;
    padding: 10px 12px;
    resize: vertical;
    font-family: var(--font-mono);
    font-size: 12.5px;
  }
  .presets {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .preset-chip {
    height: 30px;
    padding: 0 14px;
    border-radius: var(--r-full);
    border: 1px solid var(--border);
    background: var(--surface-2);
    color: var(--text-2);
    font-size: 12.5px;
    font-weight: 600;
  }
  .preset-chip.active {
    background: var(--accent);
    color: var(--accent-fg);
    border-color: transparent;
  }
  .hint {
    margin: 0;
    font-size: 12px;
    color: var(--text-2);
    line-height: 1.45;
  }
  .dest-row,
  .toggle-row,
  .tool-line {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 14px;
  }
  .dest-btns {
    display: flex;
    gap: 8px;
    flex-shrink: 0;
  }
  .t-title {
    font-size: 13.5px;
    font-weight: 600;
    color: var(--text);
  }
  .go-row {
    display: flex;
    align-items: center;
    gap: 12px;
  }
  .warn-txt {
    font-size: 12px;
    color: var(--danger);
  }
  .jobs-sec {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .jobs-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }
  .jobs-head h2 {
    margin: 0;
    font-size: 16px;
    font-weight: 700;
  }
  .jobs-list {
    list-style: none;
    margin: 0;
    padding: 0;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .job-row {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--r-md);
    padding: 12px 14px;
    display: flex;
    align-items: center;
    gap: 10px;
  }
  .job-main {
    flex: 1;
    min-width: 0;
  }
  .job-title {
    font-size: 13.5px;
    font-weight: 600;
  }
  .job-sub {
    font-size: 12px;
    color: var(--text-2);
    margin-top: 2px;
  }
  .prog-bar {
    height: 4px;
    background: var(--surface-2);
    border-radius: 999px;
    margin-top: 6px;
    overflow: hidden;
  }
  .prog-fill {
    height: 100%;
    background: var(--accent);
    transition: width 240ms ease;
  }
  .ok-ic {
    color: var(--success);
  }
  .bad-ic {
    color: var(--danger);
  }
  .err-box {
    padding: 10px 14px;
    border-radius: var(--r-sm);
    background: rgba(248, 113, 113, 0.14);
    color: var(--danger);
    font-size: 13px;
  }
  .min-w-0 {
    min-width: 0;
  }
</style>
