<script lang="ts">
  import Icon from '../components/Icon.svelte';
  import { buildFurigana } from '../lib/kana';
  import { learnStore } from '../lib/learn.svelte';
  import { dueVocabItems, type VocabItem } from '../lib/vocab';

  let queue = $state<string[] | null>(null);
  let revealed = $state(false);

  let due = $derived(dueVocabItems(learnStore.vocab));
  let current = $derived(
    queue && queue.length > 0 ? learnStore.vocab.find((v) => v.id === queue![0]) ?? null : null
  );

  function startReview(): void {
    queue = due.map((i) => i.id);
    revealed = false;
  }

  function answer(knew: boolean): void {
    if (!current || !queue) return;
    learnStore.gradeWord(current.id, knew);
    const [first, ...rest] = queue;
    queue = knew ? rest : [...rest, first];
    revealed = false;
  }

  function onKeys(e: KeyboardEvent): void {
    if (!current || (e.target as HTMLElement | null)?.closest('input, textarea, select')) return;
    if (e.key === 'Enter' && !revealed) {
      e.preventDefault();
      revealed = true;
    } else if (revealed && e.key === '1') {
      e.preventDefault();
      answer(false);
    } else if (revealed && e.key === '2') {
      e.preventDefault();
      answer(true);
    }
  }
</script>

<svelte:window onkeydown={onKeys} />

{#snippet wordHead(item: VocabItem, showFurigana: boolean)}
  <span class="v-word" lang={item.lang === 'auto' ? undefined : item.lang}>
    {#each buildFurigana(item.word, showFurigana ? item.reading : '') as seg, k (k)}
      {#if seg.rt}
        <ruby>{seg.text}<rt>{seg.rt}</rt></ruby>
      {:else}
        {seg.text}
      {/if}
    {/each}
  </span>
{/snippet}

<div class="vocab-page scroll-thin">
  {#if queue !== null}
    <div class="review-box">
      {#if current}
        <p class="due-lbl tabular">{queue.length} cards remaining</p>
        <div class="flashcard">
          {@render wordHead(current, revealed)}
          {#if revealed}
            <p class="reading">
              {#if current.romaji}{current.romaji}{/if}
              {#if current.pos}
                <span class="pos-tag">{current.pos}</span>
              {/if}
            </p>
            <p class="mean">{current.meanings.join(', ')}</p>
            {#if current.line}
              <div class="example">
                <button
                  type="button"
                  class="btn-icon"
                  aria-label="Play example line"
                  onclick={() => learnStore.playVocabLine(current.path, current.t)}
                >
                  <Icon name="play" size={14} />
                </button>
                <div class="ex-body">
                  <div>{current.line}</div>
                  {#if current.translation}
                    <div class="sub">{current.translation}</div>
                  {/if}
                  <div class="sub src">{current.title}{current.artist ? ` - ${current.artist}` : ''}</div>
                </div>
              </div>
            {/if}
          {/if}
        </div>
        <div class="rev-actions">
          {#if !revealed}
            <button type="button" class="btn btn-primary" onclick={() => (revealed = true)}>
              Show Answer (Enter)
            </button>
          {:else}
            <button type="button" class="btn" onclick={() => answer(false)}>Again (1)</button>
            <button type="button" class="btn btn-primary" onclick={() => answer(true)}>Got It (2)</button>
          {/if}
          <button type="button" class="btn btn-ghost" onclick={() => (queue = null)}>End Review</button>
        </div>
      {:else}
        <div class="flashcard">
          <h2>All caught up</h2>
          <p class="sub">Words you remember return in longer intervals: 1, 3, 7, 14, and 30 days.</p>
        </div>
        <div class="rev-actions">
          <button type="button" class="btn btn-primary" onclick={() => (queue = null)}>Done</button>
        </div>
      {/if}
    </div>
  {:else}
    <div class="v-head">
      <div>
        <h1>Vocabulary</h1>
        <p class="sub tabular">
          {learnStore.vocab.length} saved words / {due.length} due for review
        </p>
      </div>
      <button type="button" class="btn btn-primary" disabled={due.length === 0} onclick={startReview}>
        <Icon name="cards" size={16} />
        <span>Review ({due.length})</span>
      </button>
    </div>

    {#if learnStore.vocab.length === 0}
      <div class="empty">
        Open Lyrics in Now Playing or the side panel, turn on Learn mode (the graduation cap icon)
        or play a Qur'an surah, tap any word to inspect its meaning, and save it here for spaced review.
      </div>
    {:else}
      <div class="v-list">
        {#each learnStore.vocab as item (item.id)}
          <div class="v-card">
            <div class="vc-top">
              {@render wordHead(item, true)}
              {#if item.romaji}
                <span class="sub">{item.romaji}</span>
              {/if}
              {#if item.pos}
                <span class="pos-tag">{item.pos}</span>
              {/if}
              <button
                type="button"
                class="btn-icon rm-btn"
                aria-label="Remove {item.word}"
                onclick={() => learnStore.removeWord(item.id)}
              >
                <Icon name="close" size={14} />
              </button>
            </div>
            {#if item.meanings.length > 0}
              <div class="mean">{item.meanings.join(', ')}</div>
            {/if}
            {#if item.line}
              <div class="example">
                <button
                  type="button"
                  class="btn-icon"
                  aria-label="Play line"
                  onclick={() => learnStore.playVocabLine(item.path, item.t)}
                >
                  <Icon name="play" size={14} />
                </button>
                <div class="ex-body">
                  <div>{item.line}</div>
                  {#if item.translation}
                    <div class="sub">{item.translation}</div>
                  {/if}
                  <div class="sub src">{item.title}{item.artist ? ` - ${item.artist}` : ''}</div>
                </div>
              </div>
            {/if}
          </div>
        {/each}
      </div>
    {/if}
  {/if}
</div>

<style>
  .vocab-page {
    height: 100%;
    overflow-y: auto;
    padding: 24px 28px 48px;
    max-width: 860px;
    display: flex;
    flex-direction: column;
    gap: 18px;
  }
  .v-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
  }
  .v-head h1 {
    margin: 0;
    font-size: 26px;
    font-weight: 700;
  }
  .sub {
    margin: 2px 0 0;
    color: var(--text-2);
    font-size: 12.5px;
  }
  .empty {
    color: var(--text-2);
    max-width: 560px;
    line-height: 1.55;
  }
  .v-list {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .v-card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--r-lg);
    padding: 14px 16px;
  }
  .vc-top {
    display: flex;
    align-items: baseline;
    gap: 10px;
  }
  .v-word {
    font-size: 22px;
    font-weight: 700;
  }
  rt {
    font-size: 0.46em;
    font-weight: 600;
    color: var(--text-2);
  }
  .pos-tag {
    font-size: 10.5px;
    font-weight: 700;
    text-transform: uppercase;
    letter-spacing: 0.05em;
    color: var(--text-3);
  }
  .rm-btn {
    margin-left: auto;
    width: 26px;
    height: 26px;
  }
  .mean {
    margin-top: 4px;
    font-size: 14px;
  }
  .example {
    display: flex;
    align-items: flex-start;
    gap: 8px;
    margin-top: 10px;
    padding-top: 10px;
    border-top: 1px solid var(--border);
  }
  .ex-body {
    font-size: 13px;
    line-height: 1.4;
  }
  .review-box {
    max-width: 580px;
    margin: 32px auto 0;
    width: 100%;
    text-align: center;
    display: flex;
    flex-direction: column;
    gap: 16px;
  }
  .due-lbl {
    margin: 0;
    color: var(--text-2);
    font-size: 12.5px;
  }
  .flashcard {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--r-lg);
    padding: 36px 28px;
    text-align: center;
  }
  .flashcard .v-word {
    font-size: 42px;
  }
  .flashcard .example {
    text-align: left;
  }
  .reading {
    margin: 8px 0 0;
    font-size: 16px;
    color: var(--text-2);
  }
  .rev-actions {
    display: flex;
    justify-content: center;
    gap: 10px;
  }
</style>
