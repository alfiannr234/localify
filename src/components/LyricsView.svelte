<script lang="ts">
  import { buildFurigana } from '../lib/kana';
  import { learnStore } from '../lib/learn.svelte';
  import { lyricsStore } from '../lib/lyrics.svelte';
  import { player } from '../lib/player.svelte';
  import { settings } from '../lib/settings.svelte';
  import { TRANSLATE_LANGUAGES, languageName } from '../lib/translate';
  import { translateStore, triggerTranslateSync } from '../lib/translate.svelte';
  import { formatTime } from '../lib/format';
  import Icon from './Icon.svelte';
  import WordCard from './WordCard.svelte';

  interface Props {
    expanded?: boolean;
    onBackdrop?: boolean;
  }

  let { expanded = false, onBackdrop = false }: Props = $props();

  let track = $derived(player.currentTrack);
  let active = $derived(lyricsStore.activeAt(player.smoothPosition));
  let scrollerEl: HTMLDivElement | undefined = $state();
  let manualScrolling = $state(false);
  let manualTimer: ReturnType<typeof setTimeout> | null = null;
  let searchOpen = $state(false);

  let learning = $derived(settings.learnLyrics && Boolean(learnStore.pieces));
  let withSub = $derived(
    learning ||
      (settings.translateLyrics && Boolean(translateStore.lines?.length)) ||
      (settings.romanizeLyrics && lyricsStore.hasRomanizable)
  );
  let gapStart = $derived.by(() => {
    const idx = active.lineIndex;
    const lines = lyricsStore.lines;
    if (!lyricsStore.synced || lines.length === 0) return null;
    if (idx < 0 && lines[0].time > 2.2) {
      return { start: 0, end: lines[0].time };
    }
    if (idx >= 0 && idx + 1 < lines.length) {
      const curEnd = lines[idx].end;
      const nextStart = lines[idx + 1].time;
      if (nextStart - curEnd > 4.0 && player.smoothPosition >= curEnd) {
        return { start: curEnd, end: nextStart };
      }
    }
    return null;
  });

  $effect(() => {
    const idx = active.lineIndex;
    void translateStore.lines;
    void learnStore.pieces;
    void withSub;
    if (idx < 0 || manualScrolling || learnStore.card.open || !scrollerEl) return;
    const lineEl = scrollerEl.querySelector<HTMLElement>(`[data-line="${idx}"]`);
    if (lineEl) {
      const sRect = scrollerEl.getBoundingClientRect();
      const lRect = lineEl.getBoundingClientRect();
      const topInScroller = lRect.top - sRect.top + scrollerEl.scrollTop;
      const ratio = expanded ? 0.36 : 0.34;
      const targetTop = topInScroller - scrollerEl.clientHeight * ratio + lRect.height / 2;
      scrollerEl.scrollTo({
        top: Math.max(0, targetTop),
        behavior: 'smooth',
      });
    }
  });

  function setTargetLanguage(code: string): void {
    settings.lyricsLanguage = code;
    settings.save('lyrics_language', code);
    triggerTranslateSync();
  }

  function onUserWheel(): void {
    manualScrolling = true;
    if (manualTimer) clearTimeout(manualTimer);
    manualTimer = setTimeout(() => {
      manualScrolling = false;
    }, 3000);
  }

  function toggleLearn(): void {
    settings.learnLyrics = !settings.learnLyrics;
    settings.save('learn_lyrics', String(settings.learnLyrics));
    void learnStore.analyze(lyricsStore.lines, settings.learnLyrics);
    triggerTranslateSync();
  }

  function toggleTranslate(): void {
    settings.translateLyrics = !settings.translateLyrics;
    settings.save('translate_lyrics', String(settings.translateLyrics));
    triggerTranslateSync();
  }

  async function toggleVersions(): Promise<void> {
    if (!track) return;
    searchOpen = !searchOpen;
    if (searchOpen && lyricsStore.searchResults.length === 0) {
      await lyricsStore.searchManual(track);
    }
  }
</script>

<div
  class="lyrics-pane"
  class:expanded
  class:backdrop={expanded || onBackdrop}
  class:learning
  class:with-sub={withSub}
  class:manual={manualScrolling}
>
  {#if track}
    <div class="tools-bar">
      <div class="floating-tools">
        {#if (settings.translateLyrics || settings.learnLyrics) && lyricsStore.lines.length > 0}
          <select
            class="lang-pick"
            aria-label="Translation target language"
            value={translateStore.targetLanguage()}
            onchange={(e) => setTargetLanguage((e.currentTarget as HTMLSelectElement).value)}
          >
            {#each TRANSLATE_LANGUAGES as [code, label] (code)}
              <option value={code}>{label}</option>
            {/each}
          </select>
        {/if}

        {#if lyricsStore.hasRomanizable && !learning}
          <button
            type="button"
            class="tool-chip"
            class:on={settings.romanizeLyrics}
            aria-pressed={settings.romanizeLyrics}
            title="Show or hide offline romanization"
            onclick={() => {
              settings.romanizeLyrics = !settings.romanizeLyrics;
              settings.save('romanize_lyrics', String(settings.romanizeLyrics));
            }}
          >
            Romaji
          </button>
        {/if}

        {#if lyricsStore.lines.length > 0}
          <button
            type="button"
            class="icon-tool"
            class:on={settings.learnLyrics}
            aria-pressed={settings.learnLyrics}
            aria-label="Learn mode"
            title="Learn mode: tap any word for meaning, furigana, and vocabulary"
            onclick={toggleLearn}
          >
            <Icon name="graduation-cap" size={18} />
          </button>
          {#if !settings.learnLyrics}
            <button
              type="button"
              class="icon-tool"
              class:on={settings.translateLyrics}
              class:busy={translateStore.busy}
              aria-pressed={settings.translateLyrics}
              aria-label="Translate lyrics"
              title="Translate lyrics to {languageName(translateStore.targetLanguage())}"
              onclick={toggleTranslate}
            >
              <Icon name="translate" size={18} />
            </button>
          {/if}
        {/if}

        <button
          type="button"
          class="icon-tool"
          class:on={searchOpen}
          aria-label="Choose online lyrics version"
          title="Search online lyrics versions"
          onclick={toggleVersions}
        >
          <Icon name="search" size={16} />
        </button>
      </div>
    </div>

    {#if (settings.translateLyrics || settings.learnLyrics) && (translateStore.error || translateStore.same)}
      <div class="tr-note" class:err={Boolean(translateStore.error)}>
        {#if translateStore.error}
          <span>{translateStore.error}</span>
        {:else if translateStore.same}
          <span>
            Lyrics match {languageName(translateStore.targetLanguage())}. Choose Indonesian or another language above.
          </span>
        {/if}
      </div>
    {/if}
  {/if}

  {#if searchOpen && track}
    <div class="cand-overlay scroll-thin">
      <div class="cand-head">
        <span>Online Lyrics Versions</span>
        <button
          type="button"
          class="btn-icon tiny"
          aria-label="Close search"
          onclick={() => (searchOpen = false)}
        >
          <Icon name="close" size={14} />
        </button>
      </div>
      {#if lyricsStore.searching}
        <div class="cand-msg">Searching LRCLIB, NetEase, and Kugou...</div>
      {:else if lyricsStore.searchResults.length === 0}
        <div class="cand-msg">No matching online lyrics found.</div>
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
            <span class="cand-top">
              <span class="truncate">{cand.title} - {cand.artist}</span>
              <span class="cand-src tabular">
                {cand.provider.toUpperCase()}{cand.wordSynced ? ' / WORD' : cand.synced ? ' / SYNC' : ''}{cand.duration > 0 ? ` / ${formatTime(cand.duration)}` : ''}
              </span>
            </span>
            <span class="cand-prev truncate">{cand.preview}</span>
          </button>
        {/each}
      {/if}
    </div>
  {/if}

  <div class="lyrics-stage">
    <div
      bind:this={scrollerEl}
      class="lines-scroll"
      role="region"
      aria-label="Synchronized lyrics"
      style="--lrc-size: {expanded ? 'clamp(20px, min(2.05vw, 3.3vh), 29px)' : `${settings.lyricsFontSize}px`};"
      onwheel={onUserWheel}
      ontouchstart={onUserWheel}
    >
      {#if !track}
        <div class="empty-msg">Play a song to follow along with lyrics.</div>
      {:else if lyricsStore.loading}
        <div class="empty-msg">Looking for lyrics...</div>
      {:else if lyricsStore.lines.length === 0}
        <div class="empty-msg">
          <p>No lyrics available for this song.</p>
          <button type="button" class="search-pill" onclick={toggleVersions}>Search Online</button>
        </div>
      {:else}
        <div class="spacer top"></div>

        {#if gapStart && active.lineIndex < 0}
          {@const pct = Math.min(1, Math.max(0, (player.smoothPosition - gapStart.start) / Math.max(0.1, gapStart.end - gapStart.start)))}
          <div class="dots" aria-label="Instrumental">
            {#each [0, 1, 2] as d (d)}
              <i style:opacity={0.3 + 0.7 * Math.min(1, Math.max(0, pct * 3 - d))}></i>
            {/each}
          </div>
        {/if}

        {#each lyricsStore.lines as line, idx (idx)}
          {@const isCurrent = lyricsStore.synced && idx === active.lineIndex}
          {@const dist = Math.min(4, Math.abs(idx - Math.max(0, active.lineIndex)))}
          {#if learning && learnStore.pieces?.[idx]}
            <div
              data-line={idx}
              class="line"
              class:on={isCurrent}
              class:past={lyricsStore.synced && idx < active.lineIndex}
              style:--d={0}
            >
              <span class="learn-words" lang={learnStore.japanese ? 'ja' : undefined}>
                {#each learnStore.pieces[idx] as p, j (j)}
                  {#if p.tap}
                    <button
                      type="button"
                      class="wd"
                      class:sel={learnStore.card.open && learnStore.card.line === idx && learnStore.card.piece === j}
                      onclick={() => learnStore.openWord(idx, j)}
                    >
                      {#if p.reading}
                        {#each buildFurigana(p.text, p.reading) as seg, k (k)}
                          {#if seg.rt}
                            <ruby>{seg.text}<rt>{seg.rt}</rt></ruby>
                          {:else}
                            {seg.text}
                          {/if}
                        {/each}
                      {:else}
                        {p.text}
                      {/if}
                    </button>
                  {:else}
                    {p.text}
                  {/if}
                {/each}
              </span>
              {#if learnStore.romajiLines?.[idx]}
                <span class="ro">{learnStore.romajiLines[idx]}</span>
              {/if}
              {#if translateStore.lines?.[idx]}
                <span class="tr">({translateStore.lines[idx]})</span>
              {/if}
            </div>
          {:else}
            <button
              type="button"
              data-line={idx}
              class="line"
              class:on={isCurrent}
              class:past={lyricsStore.synced && idx < active.lineIndex}
              class:bg-vocal={line.bg}
              style:--d={manualScrolling || !lyricsStore.synced ? 0 : dist}
              disabled={!lyricsStore.synced}
              onclick={() => lyricsStore.synced && player.seek(line.time - lyricsStore.trackOffset)}
            >
              <span class="line-txt">
                {#if isCurrent && line.words.length > 0}
                  {#each line.words as word, wIdx (wIdx)}
                    {@const wp =
                      wIdx < active.wordIndex
                        ? 100
                        : wIdx === active.wordIndex
                          ? Math.round(active.wordProgress * 100)
                          : 0}
                    <span class="word-sweep" style:--p="{wp}%">{word.text}</span>
                  {/each}
                {:else}
                  {line.text}
                {/if}
              </span>

              {#if settings.romanizeLyrics && lyricsStore.romanizedLines[idx]}
                <span class="ro">{lyricsStore.romanizedLines[idx]}</span>
              {/if}
              {#if translateStore.lines?.[idx]}
                <span class="tr">({translateStore.lines[idx]})</span>
              {/if}
            </button>

            {#if isCurrent && gapStart}
              {@const pct = Math.min(1, Math.max(0, (player.smoothPosition - gapStart.start) / Math.max(0.1, gapStart.end - gapStart.start)))}
              <div class="dots" aria-label="Instrumental">
                {#each [0, 1, 2] as d (d)}
                  <i style:opacity={0.3 + 0.7 * Math.min(1, Math.max(0, pct * 3 - d))}></i>
                {/each}
              </div>
            {/if}
          {/if}
        {/each}
        <div class="spacer bottom"></div>
      {/if}
    </div>
  </div>

  <WordCard />
</div>

<style>
  .lyrics-pane {
    position: relative;
    height: 100%;
    display: flex;
    flex-direction: column;
    overflow: hidden;
  }
  .tools-bar {
    flex: none;
    display: flex;
    justify-content: flex-end;
    padding: 8px 10px 4px;
    z-index: 10;
  }
  .expanded .tools-bar {
    padding: 0 4px 10px;
  }
  .floating-tools {
    display: flex;
    align-items: center;
    gap: 4px;
  }
  .icon-tool {
    width: 34px;
    height: 34px;
    border: none;
    border-radius: 8px;
    background: transparent;
    color: var(--text-2);
    opacity: 0.78;
    display: flex;
    align-items: center;
    justify-content: center;
    transition: background 140ms ease, opacity 140ms ease, color 140ms ease;
  }
  .backdrop .icon-tool {
    opacity: 1;
    color: rgba(255, 255, 255, 0.76);
  }
  .icon-tool:hover {
    opacity: 1;
    background: rgba(130, 140, 160, 0.18);
  }
  .backdrop .icon-tool:hover {
    background: rgba(255, 255, 255, 0.14);
    color: #fff;
  }
  .icon-tool.on {
    opacity: 1;
    color: var(--accent);
    background: var(--accent-soft);
  }
  .backdrop .icon-tool.on {
    color: #fff;
    background: rgba(255, 255, 255, 0.2);
  }
  .tool-chip {
    height: 28px;
    padding: 0 10px;
    border: 1px solid var(--border);
    border-radius: 999px;
    background: transparent;
    color: var(--text-2);
    font-size: 11.5px;
    font-weight: 600;
    transition: background 140ms ease, border-color 140ms ease, color 140ms ease;
  }
  .tool-chip:hover {
    background: rgba(130, 140, 160, 0.18);
    color: var(--text);
  }
  .tool-chip.on {
    border-color: var(--accent);
    background: var(--accent-soft);
    color: var(--accent);
  }
  .backdrop .tool-chip {
    border-color: rgba(255, 255, 255, 0.2);
    color: rgba(255, 255, 255, 0.78);
  }
  .backdrop .tool-chip:hover {
    background: rgba(255, 255, 255, 0.14);
    color: #fff;
  }
  .backdrop .tool-chip.on {
    border-color: rgba(255, 255, 255, 0.45);
    background: rgba(255, 255, 255, 0.2);
    color: #fff;
  }
  .lang-pick {
    height: 28px;
    max-width: 126px;
    padding: 0 8px;
    border-radius: 8px;
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--text);
    font-size: 11.5px;
    font-weight: 600;
    cursor: pointer;
  }
  .backdrop .lang-pick {
    border-color: rgba(255, 255, 255, 0.18);
    background: rgba(255, 255, 255, 0.12);
    color: #fff;
  }
  .lang-pick option {
    background: var(--surface);
    color: var(--text);
  }
  .backdrop .lang-pick option {
    background: #1a1e29;
    color: #fff;
  }
  .tr-note {
    flex: none;
    margin: 0 12px 6px;
    padding: 6px 10px;
    border-radius: 8px;
    font-size: 11.5px;
    text-align: center;
    background: rgba(130, 140, 160, 0.14);
    color: var(--text-2);
  }
  .backdrop .tr-note {
    background: rgba(255, 255, 255, 0.1);
    color: rgba(255, 255, 255, 0.82);
  }
  .tr-note.err {
    color: var(--danger);
  }
  .icon-tool.busy {
    animation: pulseOpacity 1.2s ease-in-out infinite;
  }
  @keyframes pulseOpacity {
    50% {
      opacity: 0.35;
    }
  }
  .lyrics-stage {
    flex: 1;
    min-height: 0;
    display: flex;
    flex-direction: column;
  }
  .expanded .lyrics-stage {
    justify-content: center;
  }
  .cand-overlay {
    position: absolute;
    top: 44px;
    left: 10px;
    right: 10px;
    max-height: 260px;
    overflow-y: auto;
    z-index: 20;
    background: rgba(24, 26, 34, 0.92);
    backdrop-filter: blur(24px);
    border: 1px solid rgba(255, 255, 255, 0.14);
    border-radius: 12px;
    padding: 10px;
    display: flex;
    flex-direction: column;
    gap: 6px;
    color: #fff;
  }
  .cand-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    font-size: 12px;
    font-weight: 600;
  }
  .cand-msg {
    font-size: 12.5px;
    opacity: 0.7;
    padding: 8px 4px;
  }
  .cand-row {
    border: none;
    background: rgba(255, 255, 255, 0.08);
    border-radius: 8px;
    padding: 8px 10px;
    color: #fff;
    text-align: left;
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .cand-row:hover {
    background: rgba(255, 255, 255, 0.16);
  }
  .cand-top {
    display: flex;
    justify-content: space-between;
    gap: 8px;
    font-size: 12.5px;
    font-weight: 600;
  }
  .cand-src {
    font-size: 11px;
    color: var(--accent);
    flex-shrink: 0;
  }
  .cand-prev {
    font-size: 11.5px;
    opacity: 0.65;
  }
  .lines-scroll {
    position: relative;
    height: 100%;
    overflow-y: auto;
    scrollbar-width: none;
    padding: 0 14px;
    mask-image: linear-gradient(transparent, #000 10%, #000 84%, transparent);
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .expanded .lines-scroll {
    height: min(100%, calc(var(--lrc-size, 26px) * 8.45));
    padding: 0 10px;
    gap: calc(var(--lrc-size, 26px) * 0.3);
    mask-image: linear-gradient(transparent 0%, #000 5%, #000 92%, transparent 100%);
  }
  .expanded.with-sub .lines-scroll {
    height: min(100%, calc(var(--lrc-size, 26px) * 13.5));
  }
  .lines-scroll::-webkit-scrollbar {
    display: none;
  }
  .spacer {
    height: 34%;
    flex-shrink: 0;
  }
  .expanded .spacer.top {
    height: 6px;
  }
  .expanded .spacer.bottom {
    height: 54%;
  }
  .line {
    display: block;
    width: 100%;
    border: none;
    background: transparent;
    text-align: left;
    padding: 9px 12px;
    border-radius: 12px;
    color: var(--text-3);
    filter: blur(calc(var(--d, 0) * 0.55px));
    transform: scale(0.97);
    transform-origin: left center;
    transition: color 300ms ease, filter 300ms ease, transform 380ms cubic-bezier(0.25, 1.2, 0.4, 1);
  }
  .backdrop .line {
    color: rgba(255, 255, 255, 0.5);
  }
  .expanded .line {
    padding: calc(var(--lrc-size, 26px) * 0.27) 12px;
  }
  .manual .line {
    filter: none;
  }
  .line:disabled {
    cursor: default;
  }
  .line:hover:not(:disabled) {
    filter: none;
    background: rgba(255, 255, 255, 0.06);
  }
  .line.on {
    color: var(--text);
    filter: none;
    transform: none;
  }
  .backdrop .line.on {
    color: #fff;
  }
  .line-txt {
    font-size: var(--lrc-size, 22px);
    font-weight: 700;
    line-height: 1.25;
    letter-spacing: -0.02em;
  }
  .line.bg-vocal .line-txt {
    font-size: calc(var(--lrc-size, 22px) * 0.8);
    font-style: italic;
  }
  .word-sweep {
    background: linear-gradient(90deg, var(--accent) var(--p), rgba(160, 167, 184, 0.45) var(--p));
    -webkit-background-clip: text;
    background-clip: text;
    color: transparent;
    white-space: pre-wrap;
  }
  .backdrop .word-sweep {
    background: linear-gradient(90deg, #fff var(--p), rgba(255, 255, 255, 0.36) var(--p));
    -webkit-background-clip: text;
    background-clip: text;
    color: transparent;
  }
  .tr {
    display: block;
    margin-top: 4px;
    font-size: clamp(12px, calc(var(--lrc-size, 22px) * 0.54), 18px);
    font-weight: 600;
    line-height: 1.35;
    opacity: 0.78;
  }
  .ro {
    display: block;
    margin-top: 3px;
    font-size: 12.5px;
    font-weight: 500;
    opacity: 0.78;
  }
  .line.on .ro {
    color: var(--accent);
    opacity: 1;
  }
  .backdrop .line.on .ro {
    color: rgba(255, 255, 255, 0.9);
    opacity: 1;
  }
  .learning .line {
    filter: none;
    cursor: default;
  }
  .learn-words {
    display: block;
    font-size: calc(var(--lrc-size, 22px) * 0.86);
    font-weight: 700;
    line-height: 1.5;
  }
  .wd {
    font: inherit;
    color: inherit;
    border: none;
    background: transparent;
    padding: 0 3px;
    border-radius: 6px;
  }
  .wd:hover {
    background: rgba(255, 255, 255, 0.14);
  }
  .wd.sel {
    background: rgba(255, 255, 255, 0.25);
    color: #fff;
  }
  rt {
    font-size: 0.44em;
    font-weight: 600;
    opacity: 0.85;
  }
  .dots {
    display: flex;
    gap: 10px;
    padding: 12px 14px;
  }
  .dots i {
    width: 10px;
    height: 10px;
    border-radius: 50%;
    background: currentColor;
    color: var(--text);
  }
  .backdrop .dots i {
    color: #fff;
  }
  .empty-msg {
    margin: auto;
    text-align: center;
    opacity: 0.65;
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
  }
  .search-pill {
    border: none;
    border-radius: 999px;
    height: 30px;
    padding: 0 14px;
    background: rgba(255, 255, 255, 0.16);
    color: inherit;
    font-size: 12.5px;
    font-weight: 600;
  }
</style>
