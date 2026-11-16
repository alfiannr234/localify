<script lang="ts">
  import { fly } from 'svelte/transition';
  import { cubicOut } from 'svelte/easing';
  import { learnStore } from '../lib/learn.svelte';
  import { player } from '../lib/player.svelte';
  import { SURAHS } from '../lib/quran';
  import {
    BASMALA,
    ISTIADHA,
    QURAN_TRANSLATIONS,
    quranStore,
    type QAyah,
    type QWord,
    type TranslationId,
  } from '../lib/quran.svelte';
  import { settings } from '../lib/settings.svelte';
  import { vocabId } from '../lib/vocab';
  import Icon from './Icon.svelte';

  interface Props {
    onOverlay?: boolean;
  }

  let { onOverlay = false }: Props = $props();

  let scrollerEl: HTMLDivElement | undefined = $state();
  let manualScrolling = $state(false);
  let manualTimer: ReturnType<typeof setTimeout> | null = null;
  let card = $state<{ unit: number; ayah: QAyah; word: QWord } | null>(null);

  let now = $derived(player.smoothPosition);
  let activeIdx = $derived(quranStore.currentUnitIndex(now + 0.1));
  let rec = $derived(quranStore.rec);
  let surahs = $derived(rec?.surahs.map((n) => SURAHS[n - 1]) ?? []);
  let title = $derived(
    !rec
      ? ''
      : rec.surahs.length > 1
        ? rec.surahs[0] === 78
          ? "Juz 'Amma"
          : 'Juz Tabarak'
        : surahs[0].en
  );
  let riwayah = $derived(rec?.riwayah === 'shubah' ? "Shu'bah 'an 'Asim" : "Hafs 'an 'Asim");
  let shownTranslations = $derived(
    QURAN_TRANSLATIONS.filter((t) => settings.quranTranslations.includes(t.id))
  );

  $effect(() => {
    const idx = activeIdx;
    if (idx < 0 || manualScrolling || !scrollerEl) return;
    const el = scrollerEl.querySelector<HTMLElement>(`[data-q-unit="${idx}"]`);
    if (el) {
      const targetTop = Math.max(0, el.offsetTop - scrollerEl.clientHeight * 0.28);
      scrollerEl.scrollTo({ top: targetTop, behavior: 'smooth' });
    }
  });

  function onUserWheel(): void {
    manualScrolling = true;
    if (manualTimer) clearTimeout(manualTimer);
    manualTimer = setTimeout(() => {
      manualScrolling = false;
    }, 3000);
  }

  function toggleTr(id: TranslationId): void {
    const cur = settings.quranTranslations;
    const next = cur.includes(id) ? cur.filter((x) => x !== id) : [...cur, id];
    if (next.length === 0) return;
    settings.quranTranslations = next;
    settings.save('quran_translations', JSON.stringify(next));
    if (player.currentTrack) void quranStore.loadTrack(player.currentTrack);
  }

  function startOf(i: number): number | undefined {
    return quranStore.starts?.[i] ?? quranStore.anchors[i];
  }

  async function seekAndPlay(secs: number | undefined): Promise<void> {
    if (secs === undefined) return;
    await player.seek(secs);
    if (!player.playing) await player.togglePlay();
  }

  function tapUnit(i: number): void {
    if (quranStore.fixing) void quranStore.markStart(i, player.position);
    else void seekAndPlay(startOf(i));
  }

  function tapWord(e: MouseEvent, unit: number, ayah: QAyah, word: QWord): void {
    e.stopPropagation();
    if (quranStore.fixing) {
      void quranStore.markStart(unit, player.position);
      return;
    }
    card = { unit, ayah, word };
  }

  let cardId = $derived(card ? vocabId('ar', card.word.ar) : '');
  let saved = $derived(Boolean(cardId && learnStore.vocabLoaded && learnStore.isSaved(cardId)));

  function toggleSave(): void {
    if (!card) return;
    if (saved) {
      learnStore.removeWord(cardId);
      return;
    }
    const { ayah, word, unit } = card;
    const t = player.currentTrack;
    learnStore.saveWordDirect({
      id: cardId,
      word: word.ar,
      reading: '',
      romaji: word.tr,
      pos: '',
      meanings: [word.mean].filter(Boolean),
      lang: 'ar',
      line: ayah.ar,
      translation: shownTranslations.map((s) => ayah.tr[s.id]).find(Boolean) ?? '',
      path: t?.path ?? '',
      title: `${SURAHS[ayah.surah - 1].en} ${ayah.surah}:${ayah.n}`,
      artist: t?.artist ?? '',
      t: word.seg?.[0] ?? startOf(unit) ?? 0,
    });
  }

  function onKeys(e: KeyboardEvent): void {
    if (e.key !== 'Escape' || (!card && !quranStore.fixing)) return;
    e.stopPropagation();
    card = null;
    quranStore.fixing = false;
  }

  const arNum = (n: number) => n.toLocaleString('ar-EG');
  const translit = (a: QAyah) => a.words?.map((w) => w.tr).filter(Boolean).join(' ') ?? '';
</script>

<svelte:window onkeydown={onKeys} />

<div class="quran" class:overlay={onOverlay}>
  <header class="q-header">
    <div class="q-title">
      <span class="q-name" lang="ar">
        {rec && rec.surahs.length === 1
          ? `سورة ${surahs[0].ar}`
          : rec?.surahs[0] === 78
            ? 'جزء عمّ'
            : 'جزء تبارك'}
      </span>
      <span class="q-sub">{title} / {riwayah}</span>
    </div>

    <div class="q-tools">
      {#each QURAN_TRANSLATIONS as t (t.id)}
        <button
          type="button"
          class="q-chip"
          class:on={settings.quranTranslations.includes(t.id)}
          aria-pressed={settings.quranTranslations.includes(t.id)}
          onclick={() => toggleTr(t.id)}
        >
          {t.short}
        </button>
      {/each}
      <button
        type="button"
        class="q-chip"
        class:on={settings.quranTranslit}
        aria-pressed={settings.quranTranslit}
        onclick={() => {
          settings.quranTranslit = !settings.quranTranslit;
          settings.save('quran_translit', String(settings.quranTranslit));
        }}
      >
        Latin
      </button>
      <button
        type="button"
        class="q-chip"
        class:on={quranStore.fixing}
        aria-pressed={quranStore.fixing}
        title="Tap the ayah currently being recited to sync"
        onclick={() => (quranStore.fixing = !quranStore.fixing)}
      >
        <Icon name="crosshair" size={13} />
        <span>Fix sync</span>
      </button>
      {#if Object.keys(quranStore.anchors).length > 0}
        <button
          type="button"
          class="q-chip"
          aria-label="Reset ayah sync anchors"
          onclick={() => quranStore.clearMarks()}
        >
          <Icon name="reset" size={13} />
        </button>
      {/if}
    </div>

    {#if quranStore.fixing}
      <p class="q-note active">Tap the ayah currently being recited. Esc cancels.</p>
    {:else if quranStore.status === 'loading'}
      <p class="q-note">Loading surah text and meanings...</p>
    {:else if quranStore.status === 'analysing'}
      <p class="q-note">Aligning ayahs to recitation pauses (cached per track)...</p>
    {:else if quranStore.status === 'error'}
      <p class="q-note err">{quranStore.error}</p>
    {:else if rec?.partial && Object.keys(quranStore.anchors).length === 0}
      <p class="q-note">Partial recitation: tap Fix sync on any ayah while listening.</p>
    {/if}
  </header>

  <div bind:this={scrollerEl} class="q-scroll" class:manual={manualScrolling} onwheel={onUserWheel}>
    <div class="q-inner">
      {#each quranStore.units as u, i (i)}
        {@const dist = Math.min(3, Math.abs(i - Math.max(0, activeIdx)))}
        {#if u.kind === 'istiadha'}
          <button
            type="button"
            data-q-unit={i}
            class="q-unit q-open"
            class:on={i === activeIdx}
            style:--d={dist}
            onclick={() => tapUnit(i)}
          >
            <span lang="ar" dir="rtl">{ISTIADHA}</span>
          </button>
        {:else if u.kind === 'basmala'}
          <button
            type="button"
            data-q-unit={i}
            class="q-unit q-open"
            class:on={i === activeIdx}
            style:--d={dist}
            onclick={() => tapUnit(i)}
          >
            {#if rec && rec.surahs.length > 1}
              <span class="q-surah-div">
                <span lang="ar">سورة {SURAHS[u.surah - 1].ar}</span> / {SURAHS[u.surah - 1].en}
              </span>
            {/if}
            <span lang="ar" dir="rtl">{BASMALA}</span>
          </button>
        {:else}
          {@const a = u.ayah}
          <div
            data-q-unit={i}
            class="q-unit q-ayah"
            class:on={i === activeIdx}
            style:--d={dist}
            role="button"
            tabindex="0"
            onclick={() => tapUnit(i)}
            onkeydown={(e) => e.key === 'Enter' && tapUnit(i)}
          >
            <p class="q-ar" lang="ar" dir="rtl">
              {#if a.words}
                {#each a.words as w, k (k)}
                  <button
                    type="button"
                    class="q-w"
                    class:now={quranStore.exact && i === activeIdx && w.seg && now >= w.seg[0] && now < w.seg[1]}
                    onclick={(e) => tapWord(e, i, a, w)}
                  >
                    {w.ar}
                  </button>{' '}
                {/each}
              {:else}
                {a.ar}
              {/if}
              <span class="q-num" aria-label="Ayah {a.n}">{arNum(a.n)}</span>
            </p>
            {#if settings.quranTranslit && a.words}
              <p class="q-tr">{translit(a)}</p>
            {/if}
            {#each shownTranslations as trDef (trDef.id)}
              {#if a.tr[trDef.id]}
                <p class="q-mean" lang={trDef.lang}>
                  {a.tr[trDef.id]}
                  {#if shownTranslations.length > 1}
                    <span class="q-src">({trDef.short})</span>
                  {/if}
                </p>
              {/if}
            {/each}
          </div>
        {/if}
      {/each}
      <div class="q-bottom-pad"></div>
    </div>
  </div>

  {#if card}
    <div
      class="q-card"
      role="dialog"
      aria-label="Qur'an word meaning"
      transition:fly={{ y: 24, duration: 220, easing: cubicOut }}
    >
      <button type="button" class="btn-icon q-close" aria-label="Close" onclick={() => (card = null)}>
        <Icon name="close" size={15} />
      </button>
      <div class="qc-head" lang="ar" dir="rtl">{card.word.ar}</div>
      {#if card.word.tr}
        <div class="qc-say">{card.word.tr}</div>
      {/if}
      <div class="qc-mean">{card.word.mean || '-'}</div>
      <div class="qc-from">{SURAHS[card.ayah.surah - 1].en} {card.ayah.surah}:{card.ayah.n}</div>
      <div class="qc-actions">
        <button
          type="button"
          class="qc-btn"
          onclick={() => card && seekAndPlay(card.word.seg?.[0] ?? startOf(card.unit))}
        >
          <Icon name="play" size={13} />
          <span>{card.word.seg ? 'Hear Word' : 'Hear Ayah'}</span>
        </button>
        <button type="button" class="qc-btn" class:saved onclick={toggleSave}>
          <Icon name={saved ? 'star-fill' : 'star'} size={13} />
          <span>{saved ? 'Saved' : 'Save to Vocabulary'}</span>
        </button>
      </div>
    </div>
  {/if}
</div>

<style>
  .quran {
    position: relative;
    height: 100%;
    display: flex;
    flex-direction: column;
    color: var(--text);
  }
  .quran.overlay {
    color: #fff;
  }
  .q-header {
    flex: none;
    padding: 10px 14px 8px;
    display: flex;
    flex-direction: column;
    gap: 8px;
  }
  .q-title {
    display: flex;
    align-items: baseline;
    gap: 10px;
    min-width: 0;
  }
  .q-name {
    font-size: 22px;
    font-weight: 600;
  }
  .q-sub {
    font-size: 12px;
    opacity: 0.68;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }
  .q-tools {
    display: flex;
    flex-wrap: wrap;
    gap: 6px;
  }
  .q-chip {
    display: inline-flex;
    align-items: center;
    gap: 4px;
    height: 24px;
    padding: 0 10px;
    border-radius: 999px;
    border: none;
    font-size: 11.5px;
    font-weight: 600;
    background: rgba(130, 140, 160, 0.2);
    color: inherit;
    opacity: 0.85;
  }
  .overlay .q-chip {
    background: rgba(255, 255, 255, 0.14);
    color: rgba(255, 255, 255, 0.85);
  }
  .q-chip.on {
    background: var(--accent);
    color: var(--accent-fg);
    opacity: 1;
  }
  .overlay .q-chip.on {
    background: rgba(255, 255, 255, 0.92);
    color: #111318;
  }
  .q-note {
    margin: 0;
    font-size: 12px;
    opacity: 0.72;
  }
  .q-note.active {
    color: var(--accent);
    opacity: 1;
  }
  .q-note.err {
    color: var(--danger);
    opacity: 1;
  }
  .q-scroll {
    flex: 1;
    overflow-y: auto;
    scrollbar-width: none;
    mask-image: linear-gradient(transparent, #000 8%, #000 84%, transparent);
    padding: 0 10px;
  }
  .q-scroll::-webkit-scrollbar {
    display: none;
  }
  .q-inner {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .q-unit {
    display: block;
    width: 100%;
    text-align: center;
    border: none;
    background: transparent;
    padding: 10px 12px;
    border-radius: 14px;
    color: inherit;
    opacity: 0.42;
    filter: blur(calc(var(--d, 0) * 0.35px));
    transition: opacity 280ms ease, filter 280ms ease, background 280ms ease;
  }
  .q-scroll.manual .q-unit {
    filter: none;
    opacity: 0.75;
  }
  .q-unit.on {
    opacity: 1;
    filter: none;
    background: rgba(255, 255, 255, 0.06);
  }
  .q-unit:hover {
    filter: none;
  }
  .q-open {
    font-size: 22px;
    line-height: 2;
  }
  .q-surah-div {
    display: block;
    font-size: 12px;
    opacity: 0.65;
    margin-bottom: 4px;
  }
  .q-ar {
    margin: 0;
    font-size: 28px;
    line-height: 2.15;
  }
  .q-w {
    font: inherit;
    color: inherit;
    border: none;
    background: transparent;
    padding: 0 2px;
    border-radius: 6px;
  }
  .q-w:hover {
    background: rgba(255, 255, 255, 0.14);
  }
  .q-w.now {
    color: var(--accent);
    text-shadow: 0 0 12px rgba(245, 130, 62, 0.45);
  }
  .q-num {
    display: inline-grid;
    place-items: center;
    min-width: 1.55em;
    height: 1.55em;
    margin-inline-start: 4px;
    border: 1.5px solid currentColor;
    border-radius: 50%;
    font-size: 0.48em;
    line-height: 1;
    vertical-align: middle;
    opacity: 0.7;
  }
  .q-tr {
    margin: 2px 0 0;
    font-size: 13.5px;
    font-style: italic;
    opacity: 0.78;
  }
  .q-mean {
    margin: 6px 0 0;
    font-size: 14px;
    line-height: 1.45;
    opacity: 0.86;
    text-align: start;
  }
  .q-src {
    font-size: 11px;
    opacity: 0.6;
  }
  .q-bottom-pad {
    height: 180px;
  }
  .q-card {
    position: absolute;
    left: 10px;
    right: 10px;
    bottom: 10px;
    z-index: 25;
    padding: 16px 18px 14px;
    border-radius: 16px;
    color: #fff;
    background: rgba(24, 26, 34, 0.88);
    backdrop-filter: blur(28px);
    box-shadow: 0 16px 40px rgba(0, 0, 0, 0.45);
  }
  .q-close {
    position: absolute;
    top: 8px;
    right: 8px;
    width: 26px;
    height: 26px;
    color: rgba(255, 255, 255, 0.7);
  }
  .qc-head {
    font-size: 36px;
    line-height: 1.6;
    text-align: center;
  }
  .qc-say {
    text-align: center;
    font-size: 14px;
    font-style: italic;
    color: rgba(255, 255, 255, 0.76);
  }
  .qc-mean {
    margin-top: 6px;
    text-align: center;
    font-size: 16px;
    font-weight: 600;
  }
  .qc-from {
    margin-top: 3px;
    text-align: center;
    font-size: 12px;
    color: rgba(255, 255, 255, 0.56);
  }
  .qc-actions {
    display: flex;
    justify-content: center;
    gap: 8px;
    margin-top: 12px;
  }
  .qc-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 30px;
    padding: 0 12px;
    border-radius: 8px;
    border: none;
    background: rgba(255, 255, 255, 0.16);
    color: #fff;
    font-size: 12.5px;
    font-weight: 600;
  }
  .qc-btn.saved {
    color: #ffd60a;
  }
</style>
