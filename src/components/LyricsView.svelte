<script lang="ts">
  import { open } from '@tauri-apps/plugin-dialog';
  import { formatTime } from '../lib/format';
  import { formatOffsetSeconds, parseOffsetInput } from '../lib/lyrics';
  import { lyricsStore } from '../lib/lyrics.svelte';
  import { player } from '../lib/player.svelte';
  import { settings } from '../lib/settings.svelte';
  import Icon from './Icon.svelte';

  interface Props {
    expanded?: boolean;
  }

  let { expanded = false }: Props = $props();

  let track = $derived(player.currentTrack);
  let active = $derived(lyricsStore.activeAt(player.smoothPosition));
  let scrollerEl: HTMLDivElement | undefined = $state();
  let manualScrolling = $state(false);
  let manualTimer: ReturnType<typeof setTimeout> | null = null;
  let searchOpen = $state(false);

  let offsetDraft = $state('0.00');
  let offsetFocused = $state(false);

  $effect(() => {
    if (!offsetFocused) {
      offsetDraft = formatOffsetSeconds(lyricsStore.trackOffset);
    }
  });

  $effect(() => {
    const idx = active.lineIndex;
    if (idx < 0 || manualScrolling || !scrollerEl) return;
    const lineEl = scrollerEl.querySelector<HTMLElement>(`[data-line="${idx}"]`);
    if (lineEl) {
      const targetTop =
        lineEl.offsetTop - scrollerEl.clientHeight * (expanded ? 0.36 : 0.38) + lineEl.offsetHeight / 2;
      scrollerEl.scrollTo({
        top: Math.max(0, targetTop),
        behavior: 'smooth',
      });
    }
  });

  function onUserWheel(): void {
    manualScrolling = true;
    if (manualTimer) clearTimeout(manualTimer);
    manualTimer = setTimeout(() => {
      manualScrolling = false;
    }, 3200);
  }

  function recenter(): void {
    manualScrolling = false;
    if (scrollerEl && active.lineIndex >= 0) {
      const lineEl = scrollerEl.querySelector<HTMLElement>(`[data-line="${active.lineIndex}"]`);
      if (lineEl) {
        scrollerEl.scrollTo({
          top: Math.max(
            0,
            lineEl.offsetTop - scrollerEl.clientHeight * 0.36 + lineEl.offsetHeight / 2
          ),
          behavior: 'smooth',
        });
      }
    }
  }

  function commitOffset(): void {
    offsetFocused = false;
    const parsed = parseOffsetInput(offsetDraft);
    if (parsed !== null) {
      void lyricsStore.setOffset(parsed);
    } else {
      offsetDraft = formatOffsetSeconds(lyricsStore.trackOffset);
    }
  }

  async function pickLocalLyrics(): Promise<void> {
    if (!track) return;
    const picked = await open({
      multiple: false,
      filters: [{ name: 'Lyrics files', extensions: ['lrc', 'ttml', 'krc', 'txt'] }],
    });
    if (!picked || Array.isArray(picked)) return;
    await lyricsStore.assignCustomFile(track.path, picked);
  }

  async function openOnlineSearch(): Promise<void> {
    if (!track) return;
    searchOpen = !searchOpen;
    if (searchOpen && lyricsStore.searchResults.length === 0) {
      await lyricsStore.searchManual(track);
    }
  }
</script>

<div class="lyrics-shell" class:expanded>
  <div class="toolbar">
    {#if lyricsStore.hasRomanizable}
      <button
        type="button"
        class="tool-chip"
        class:active={settings.romanizeLyrics}
        onclick={() => {
          settings.romanizeLyrics = !settings.romanizeLyrics;
          settings.save('romanize_lyrics', String(settings.romanizeLyrics));
        }}
      >
        Romaji
      </button>
    {/if}

    <!-- Strict user design rule: ONLY editable text input + [-0.1s] & [+0.1s] buttons, no save toast popup -->
    <div class="offset-group" aria-label="Sinkronisasi lirik">
      <button
        type="button"
        class="step-btn tabular"
        onclick={() => lyricsStore.adjustOffset(-0.1)}
      >
        -0.1s
      </button>
      <input
        type="text"
        class="offset-input tabular"
        aria-label="Lyrics offset in seconds"
        bind:value={offsetDraft}
        onfocus={() => (offsetFocused = true)}
        onblur={commitOffset}
        onkeydown={(e) => {
          if (e.key === 'Enter') {
            (e.currentTarget as HTMLInputElement).blur();
          }
        }}
      />
      <button
        type="button"
        class="step-btn tabular"
        onclick={() => lyricsStore.adjustOffset(0.1)}
      >
        +0.1s
      </button>
    </div>

    <div class="tool-right">
      <button
        type="button"
        class="btn-icon small"
        title="Search online lyrics"
        aria-label="Search online lyrics"
        disabled={!track}
        onclick={openOnlineSearch}
      >
        <Icon name="search" size={15} />
      </button>
      <button
        type="button"
        class="btn-icon small"
        title="Load .lrc or .ttml file"
        aria-label="Load local lyrics file"
        disabled={!track}
        onclick={pickLocalLyrics}
      >
        <Icon name="file-text" size={15} />
      </button>
      <button
        type="button"
        class="btn-icon small"
        title="Refresh lyrics from providers"
        aria-label="Refresh online lyrics"
        disabled={!track || lyricsStore.loading}
        onclick={() => track && lyricsStore.refreshOnline(track)}
      >
        <Icon name="refresh" size={15} class={lyricsStore.loading ? 'spin' : ''} />
      </button>
    </div>
  </div>

  {#if searchOpen && track}
    <div class="cand-drawer scroll-thin">
      <div class="cand-head">
        <span>Online Lyrics Versions</span>
        <button type="button" class="btn-icon small" aria-label="Close" onclick={() => (searchOpen = false)}>
          <Icon name="close" size={14} />
        </button>
      </div>
      {#if lyricsStore.searching}
        <div class="cand-empty">Searching LRCLIB, NetEase, and Kugou...</div>
      {:else if lyricsStore.searchResults.length === 0}
        <div class="cand-empty">No matching lyrics found online.</div>
      {:else}
        {#each lyricsStore.searchResults as cand (cand.id)}
          <button
            type="button"
            class="cand-row"
            onclick={() => {
              void lyricsStore.applyCandidate(track.path, cand);
              searchOpen = false;
            }}
          >
            <div class="cand-top">
              <span class="cand-title truncate">{cand.title} - {cand.artist}</span>
              <span class="cand-meta tabular">
                {cand.provider.toUpperCase()}
                {cand.wordSynced ? ' / WORD' : cand.synced ? ' / SYNC' : ''}
                {cand.duration > 0 ? ` / ${formatTime(cand.duration)}` : ''}
              </span>
            </div>
            <div class="cand-prev truncate">{cand.preview}</div>
          </button>
        {/each}
      {/if}
    </div>
  {/if}

  <div
    bind:this={scrollerEl}
    class="lines scroll-thin"
    role="region"
    aria-label="Synchronized lyrics"
    style="--lrc-size: {expanded ? Math.max(24, settings.lyricsFontSize + 5) : settings.lyricsFontSize}px;"
    onwheel={onUserWheel}
    ontouchstart={onUserWheel}
  >
    {#if !track}
      <div class="empty-box">Play a track to display synchronized lyrics.</div>
    {:else if lyricsStore.loading}
      <div class="empty-box">Loading lyrics...</div>
    {:else if lyricsStore.lines.length === 0}
      <div class="empty-box">
        <p>No lyrics available for this track.</p>
        <div class="empty-actions">
          <button type="button" class="btn" onclick={openOnlineSearch}>Search Online</button>
          <button type="button" class="btn" onclick={pickLocalLyrics}>Load LRC File</button>
        </div>
      </div>
    {:else}
      <div class="spacer"></div>
      {#each lyricsStore.lines as line, idx (idx)}
        {@const isCurrent = lyricsStore.synced && idx === active.lineIndex}
        {@const isPast = lyricsStore.synced && idx < active.lineIndex}
        <button
          type="button"
          data-line={idx}
          class="line-btn"
          class:current={isCurrent}
          class:past={isPast}
          class:bg-vocal={line.bg}
          disabled={!lyricsStore.synced}
          onclick={() => lyricsStore.synced && player.seek(line.time - lyricsStore.trackOffset)}
        >
          {#if isCurrent && line.words.length > 0}
            <span class="line-text">
              {#each line.words as word, wIdx (wIdx)}
                {@const wp =
                  wIdx < active.wordIndex
                    ? 100
                    : wIdx === active.wordIndex
                      ? Math.round(active.wordProgress * 100)
                      : 0}
                <span
                  class="word-karaoke"
                  style="background-image: linear-gradient(90deg, var(--accent) {wp}%, var(--text-2) {wp}%);"
                >{word.text}</span>
              {/each}
            </span>
          {:else}
            <span class="line-text">{line.text}</span>
          {/if}

          {#if settings.romanizeLyrics && lyricsStore.romanizedLines[idx]}
            <span class="romaji">{lyricsStore.romanizedLines[idx]}</span>
          {/if}
        </button>
      {/each}
      <div class="spacer"></div>
    {/if}
  </div>

  {#if manualScrolling && lyricsStore.synced}
    <button type="button" class="recenter-btn" onclick={recenter}>Recenter</button>
  {/if}
</div>

<style>
  .lyrics-shell {
    position: relative;
    height: 100%;
    display: flex;
    flex-direction: column;
    min-height: 0;
  }
  .toolbar {
    display: flex;
    align-items: center;
    gap: 6px;
    padding: 8px 12px;
    border-bottom: 1px solid var(--border);
    flex-shrink: 0;
  }
  .tool-chip {
    height: 26px;
    padding: 0 9px;
    border-radius: var(--r-sm);
    border: 1px solid var(--border);
    background: transparent;
    color: var(--text-2);
    font-size: 11.5px;
    font-weight: 600;
  }
  .tool-chip.active {
    background: var(--accent-soft);
    color: var(--accent);
    border-color: transparent;
  }
  .offset-group {
    display: inline-flex;
    align-items: center;
    gap: 4px;
  }
  .step-btn {
    height: 26px;
    padding: 0 8px;
    border-radius: var(--r-sm);
    border: 1px solid var(--border);
    background: var(--surface-2);
    color: var(--text);
    font-size: 11.5px;
    font-weight: 600;
  }
  .step-btn:hover {
    background: var(--surface-hover);
    border-color: var(--border-strong);
  }
  .offset-input {
    width: 56px;
    height: 26px;
    text-align: center;
    border-radius: var(--r-sm);
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--text);
    font-size: 12px;
    font-weight: 600;
  }
  .offset-input:focus {
    outline: none;
    border-color: var(--accent);
  }
  .tool-right {
    margin-left: auto;
    display: flex;
    align-items: center;
    gap: 2px;
  }
  .btn-icon.small {
    width: 28px;
    height: 28px;
  }
  .cand-drawer {
    max-height: 220px;
    overflow-y: auto;
    border-bottom: 1px solid var(--border);
    background: var(--bg-elev);
    padding: 8px 10px;
    display: flex;
    flex-direction: column;
    gap: 6px;
    flex-shrink: 0;
  }
  .cand-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 12px;
    font-weight: 600;
    color: var(--text-2);
  }
  .cand-empty {
    font-size: 12px;
    color: var(--text-3);
    padding: 8px 2px;
  }
  .cand-row {
    border: 1px solid var(--border);
    background: var(--surface);
    border-radius: var(--r-sm);
    padding: 7px 9px;
    text-align: left;
    display: flex;
    flex-direction: column;
    gap: 3px;
  }
  .cand-row:hover {
    border-color: var(--accent);
    background: var(--surface-2);
  }
  .cand-top {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    font-size: 12px;
    font-weight: 600;
    color: var(--text);
  }
  .cand-meta {
    font-size: 10.5px;
    color: var(--accent);
    flex-shrink: 0;
  }
  .cand-prev {
    font-size: 11px;
    color: var(--text-3);
  }
  .lines {
    flex: 1;
    overflow-y: auto;
    padding: 0 18px;
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .spacer {
    height: 36%;
    flex-shrink: 0;
  }
  .line-btn {
    border: none;
    background: transparent;
    text-align: left;
    padding: 6px 8px;
    border-radius: var(--r-md);
    display: flex;
    flex-direction: column;
    gap: 3px;
    transition: transform 240ms var(--ease), color 200ms var(--ease), opacity 200ms var(--ease);
    transform-origin: left center;
  }
  .line-btn:disabled {
    cursor: default;
    opacity: 0.92;
  }
  .line-btn:hover:not(:disabled) {
    background: var(--surface-2);
  }
  .line-text {
    font-size: var(--lrc-size, 22px);
    font-weight: 700;
    line-height: 1.32;
    color: var(--text-3);
    transition: color 220ms var(--ease);
  }
  .line-btn.bg-vocal .line-text {
    font-size: calc(var(--lrc-size, 22px) * 0.82);
    font-style: italic;
  }
  .line-btn.past .line-text {
    color: var(--text-2);
    opacity: 0.6;
  }
  .line-btn.current {
    transform: scale(1.03);
  }
  .line-btn.current .line-text {
    color: var(--text);
  }
  .word-karaoke {
    -webkit-background-clip: text;
    background-clip: text;
    color: transparent;
    white-space: pre-wrap;
  }
  .romaji {
    font-size: 12.5px;
    font-weight: 500;
    color: var(--text-2);
    opacity: 0.78;
  }
  .line-btn.current .romaji {
    color: var(--accent);
    opacity: 1;
  }
  .empty-box {
    margin: auto;
    text-align: center;
    color: var(--text-3);
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    padding: 24px;
  }
  .empty-actions {
    display: flex;
    gap: 8px;
  }
  .recenter-btn {
    position: absolute;
    bottom: 14px;
    right: 16px;
    height: 28px;
    padding: 0 12px;
    border-radius: 999px;
    border: 1px solid var(--border-strong);
    background: var(--surface);
    color: var(--text);
    font-size: 12px;
    font-weight: 600;
    box-shadow: var(--shadow-sm);
  }
  .recenter-btn:hover {
    border-color: var(--accent);
    color: var(--accent);
  }
</style>
