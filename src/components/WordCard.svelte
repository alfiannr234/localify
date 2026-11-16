<script lang="ts">
  import { fly } from 'svelte/transition';
  import { cubicOut } from 'svelte/easing';
  import { buildFurigana } from '../lib/kana';
  import { learnStore } from '../lib/learn.svelte';
  import { lyricsStore } from '../lib/lyrics.svelte';
  import { player } from '../lib/player.svelte';
  import { translateStore } from '../lib/translate.svelte';
  import Icon from './Icon.svelte';

  let card = $derived(learnStore.card);
  let line = $derived(lyricsStore.lines[card.line]);
  let saved = $derived(learnStore.isSaved(learnStore.cardId()));
  let head = $derived(buildFurigana(card.text, card.reading));
  let lang = $derived(learnStore.japanese ? 'ja' : undefined);

  async function playLine(): Promise<void> {
    if (!line) return;
    await player.seek(line.time);
    if (!player.playing) await player.togglePlay();
  }

  function onKeyCapture(e: KeyboardEvent): void {
    if (e.key !== 'Escape' || !card.open) return;
    e.stopPropagation();
    learnStore.closeCard();
  }
</script>

<svelte:window onkeydowncapture={onKeyCapture} />

{#if card.open}
  <div
    class="word-card"
    role="dialog"
    aria-label="Word details: {card.baseWord || card.text}"
    transition:fly={{ y: 24, duration: 220, easing: cubicOut }}
  >
    <button
      type="button"
      class="btn-icon close-card"
      aria-label="Close word details"
      onclick={() => learnStore.closeCard()}
    >
      <Icon name="close" size={14} />
    </button>

    <div class="wc-head">
      <span class="wc-word" {lang}>
        {#each head as seg, idx (idx)}
          {#if seg.rt}
            <ruby>{seg.text}<rt>{seg.rt}</rt></ruby>
          {:else}
            {seg.text}
          {/if}
        {/each}
      </span>
      {#if card.pos}
        <span class="wc-pos">{card.pos}</span>
      {/if}
    </div>

    {#if (card.baseWord && card.baseWord !== card.text) || card.romaji || card.reading}
      <p class="wc-form">
        {#if card.baseWord && card.baseWord !== card.text}
          <span class="wc-label">Base:</span> <span {lang}>{card.baseWord}</span>
        {/if}
        {#if card.reading && card.reading !== card.text}
          <span {lang}>{card.reading}</span>
        {/if}
        {#if card.romaji}
          <i>{card.romaji}</i>
        {/if}
      </p>
    {/if}

    <div class="wc-meaning">
      {#if card.note}
        <p>{card.note}</p>
      {:else if card.busy}
        <p class="dim">Looking up meaning...</p>
      {:else if card.error}
        <p class="dim">{card.error}</p>
      {:else if card.lookup?.senses.length}
        {#each card.lookup.senses.slice(0, 3) as s, k (k)}
          <p><span class="wc-pos">{s.pos}</span> {s.terms.slice(0, 5).join(', ')}</p>
        {/each}
      {:else if card.lookup?.translation}
        <p>{card.lookup.translation}</p>
      {/if}
    </div>

    {#if line}
      <div class="wc-ctx">
        <p {lang}>
          {#each learnStore.pieces?.[card.line] ?? [] as p, j (j)}
            {#if j === card.piece}
              <b>{p.text}</b>
            {:else}
              {p.text}
            {/if}
          {/each}
        </p>
        {#if translateStore.lines?.[card.line]}
          <p class="dim">{translateStore.lines[card.line]}</p>
        {/if}
      </div>
    {/if}

    <div class="wc-actions">
      {#if line && lyricsStore.synced}
        <button type="button" class="wc-btn" onclick={playLine}>
          <Icon name="play" size={13} />
          <span>Play Line</span>
        </button>
      {/if}
      <button
        type="button"
        class="wc-btn"
        class:saved
        disabled={!learnStore.vocabLoaded}
        onclick={() => learnStore.toggleSaveCard()}
      >
        <Icon name={saved ? 'star-fill' : 'star'} size={14} />
        <span>{saved ? 'Saved' : 'Save Word'}</span>
      </button>
    </div>
  </div>
{/if}

<style>
  .word-card {
    position: absolute;
    left: 10px;
    right: 10px;
    bottom: 10px;
    z-index: 30;
    padding: 16px 18px 14px;
    border-radius: 16px;
    color: #fff;
    background: rgba(24, 26, 34, 0.86);
    backdrop-filter: blur(28px) saturate(1.5);
    -webkit-backdrop-filter: blur(28px) saturate(1.5);
    border: 1px solid rgba(255, 255, 255, 0.14);
    box-shadow: 0 14px 40px rgba(0, 0, 0, 0.45);
  }
  .close-card {
    position: absolute;
    top: 10px;
    right: 10px;
    width: 26px;
    height: 26px;
    color: rgba(255, 255, 255, 0.7);
  }
  .wc-head {
    display: flex;
    align-items: baseline;
    gap: 10px;
    padding-right: 28px;
  }
  .wc-word {
    font-size: 28px;
    font-weight: 700;
    line-height: 1.25;
  }
  rt {
    font-size: 0.42em;
    font-weight: 600;
    color: rgba(255, 255, 255, 0.78);
  }
  .wc-pos {
    font-size: 11px;
    font-weight: 600;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: rgba(255, 255, 255, 0.6);
  }
  .wc-form {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    align-items: baseline;
    margin: 4px 0 0;
    font-size: 14px;
    color: rgba(255, 255, 255, 0.88);
  }
  .wc-form i {
    font-style: normal;
    color: rgba(255, 255, 255, 0.65);
  }
  .wc-label {
    font-size: 11px;
    color: rgba(255, 255, 255, 0.55);
  }
  .wc-meaning {
    margin: 10px 0 0;
    font-size: 14.5px;
    line-height: 1.4;
  }
  .wc-meaning p {
    margin: 0 0 3px;
  }
  .wc-ctx {
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px solid rgba(255, 255, 255, 0.14);
    font-size: 13px;
    line-height: 1.4;
  }
  .wc-ctx p {
    margin: 0 0 2px;
  }
  .dim {
    color: rgba(255, 255, 255, 0.6);
  }
  .wc-actions {
    display: flex;
    gap: 8px;
    margin-top: 12px;
  }
  .wc-btn {
    display: inline-flex;
    align-items: center;
    gap: 6px;
    height: 30px;
    padding: 0 12px;
    border-radius: 8px;
    border: none;
    background: rgba(255, 255, 255, 0.15);
    color: #fff;
    font-size: 12.5px;
    font-weight: 600;
  }
  .wc-btn:hover:not(:disabled) {
    background: rgba(255, 255, 255, 0.24);
  }
  .wc-btn.saved {
    color: #ffd60a;
  }
</style>
