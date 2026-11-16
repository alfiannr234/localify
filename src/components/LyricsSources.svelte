<script lang="ts">
  import { tick } from 'svelte';
  import Toggle from './Toggle.svelte';
  import { LYRICS_SOURCES } from '../lib/lyricsources';
  import { settings } from '../lib/settings.svelte';
  import Icon from './Icon.svelte';

  let listEl = $state<HTMLOListElement>();
  let dragFrom = $state<number | null>(null);
  let overIdx = $state<number | null>(null);

  function moveItem(from: number, to: number): void {
    const arr = [...settings.lyricsSources];
    const [moved] = arr.splice(from, 1);
    arr.splice(to, 0, moved);
    settings.lyricsSources = arr;
    settings.save('lyrics_sources', JSON.stringify(arr));
  }

  async function reorder(from: number, to: number, refocus = false): Promise<void> {
    if (to < 0 || to >= settings.lyricsSources.length || from === to) return;
    moveItem(from, to);
    if (refocus) {
      await tick();
      listEl?.children[to]?.querySelector<HTMLElement>('.grip')?.focus();
    }
  }

  function toggleSource(idx: number, on: boolean): void {
    const next = settings.lyricsSources.map((s, i) => (i === idx ? { ...s, on } : s));
    settings.lyricsSources = next;
    settings.save('lyrics_sources', JSON.stringify(next));
  }
</script>

<ol bind:this={listEl} class="sources" aria-label="Lyrics source priority order">
  {#each settings.lyricsSources as s, i (s.id)}
    <li
      class="src-row"
      class:off={!s.on}
      class:over={overIdx === i && dragFrom !== i}
      draggable="true"
      ondragstart={(e) => {
        dragFrom = i;
        if (e.dataTransfer) e.dataTransfer.effectAllowed = 'move';
      }}
      ondragover={(e) => {
        e.preventDefault();
        overIdx = i;
      }}
      ondragleave={() => overIdx === i && (overIdx = null)}
      ondrop={(e) => {
        e.preventDefault();
        if (dragFrom !== null) {
          void reorder(dragFrom, dragFrom < i ? i - 1 : i);
        }
        dragFrom = null;
        overIdx = null;
      }}
      ondragend={() => {
        dragFrom = null;
        overIdx = null;
      }}
    >
      <button
        type="button"
        class="grip"
        aria-label="Move {LYRICS_SOURCES[s.id].label} ({i + 1} of {settings.lyricsSources.length})"
        title="Drag, or press ArrowUp / ArrowDown"
        onkeydown={(e) => {
          if (e.key !== 'ArrowUp' && e.key !== 'ArrowDown') return;
          e.preventDefault();
          void reorder(i, e.key === 'ArrowUp' ? i - 1 : i + 1, true);
        }}
      >
        <Icon name="dots-six-vertical" size={14} />
      </button>
      <span class="ord tabular">{i + 1}</span>
      <div class="src-txt">
        <div class="src-name">{LYRICS_SOURCES[s.id].label}</div>
        <div class="src-hint">{LYRICS_SOURCES[s.id].hint}</div>
      </div>
      <Toggle
        checked={s.on}
        label="Use {LYRICS_SOURCES[s.id].label}"
        onChange={(v) => toggleSource(i, v)}
      />
    </li>
  {/each}
</ol>

<style>
  .sources {
    list-style: none;
    margin: 6px 0 0;
    padding: 0;
    border-radius: var(--r-md);
    border: 1px solid var(--border);
    background: var(--bg-elev);
    overflow: hidden;
  }
  .src-row {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 9px 12px 9px 8px;
    border-bottom: 1px solid var(--border);
    border-top: 2px solid transparent;
    cursor: grab;
  }
  .src-row:last-child {
    border-bottom: none;
  }
  .src-row.over {
    border-top-color: var(--accent);
  }
  .grip {
    border: none;
    background: transparent;
    color: var(--text-3);
    width: 24px;
    height: 28px;
    border-radius: var(--r-sm);
    display: flex;
    align-items: center;
    justify-content: center;
    cursor: grab;
  }
  .grip:hover {
    background: var(--surface-2);
    color: var(--text);
  }
  .ord {
    width: 16px;
    text-align: center;
    font-size: 12px;
    color: var(--text-3);
    font-weight: 600;
  }
  .src-txt {
    flex: 1;
    min-width: 0;
  }
  .src-name {
    font-size: 13px;
    font-weight: 600;
    color: var(--text);
  }
  .off .src-name {
    color: var(--text-2);
  }
  .src-hint {
    font-size: 11.5px;
    color: var(--text-2);
  }
</style>
