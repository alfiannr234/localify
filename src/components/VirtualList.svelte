<script lang="ts" generics="T">
  import type { Snippet } from 'svelte';

  interface Props {
    items: T[];
    itemHeight?: number;
    overscan?: number;
    row: Snippet<[T, number]>;
    header?: Snippet;
  }

  let { items, itemHeight = 52, overscan = 8, row, header }: Props = $props();

  let viewportEl: HTMLDivElement | undefined = $state();
  let scrollTop = $state(0);
  let viewportHeight = $state(600);
  let headerEl: HTMLDivElement | undefined = $state();
  let headerHeight = $state(0);

  $effect(() => {
    if (!viewportEl) return;
    const ro = new ResizeObserver(() => {
      if (viewportEl) viewportHeight = viewportEl.clientHeight;
      if (headerEl) headerHeight = headerEl.offsetHeight;
    });
    ro.observe(viewportEl);
    if (headerEl) ro.observe(headerEl);
    return () => ro.disconnect();
  });

  let startIdx = $derived.by(() => {
    const relScroll = Math.max(0, scrollTop - headerHeight);
    return Math.max(0, Math.floor(relScroll / itemHeight) - overscan);
  });

  let endIdx = $derived.by(() => {
    const relScroll = Math.max(0, scrollTop - headerHeight);
    return Math.min(items.length, Math.ceil((relScroll + viewportHeight) / itemHeight) + overscan);
  });

  let visible = $derived.by(() => {
    const out: Array<{ item: T; index: number }> = [];
    for (let i = startIdx; i < endIdx; i++) {
      out.push({ item: items[i], index: i });
    }
    return out;
  });

  export function scrollToIndex(index: number): void {
    if (!viewportEl) return;
    viewportEl.scrollTo({
      top: Math.max(0, headerHeight + index * itemHeight - 12),
      behavior: 'smooth',
    });
  }
</script>

<div
  bind:this={viewportEl}
  class="vlist scroll-thin"
  onscroll={(e) => (scrollTop = (e.currentTarget as HTMLDivElement).scrollTop)}
>
  {#if header}
    <div bind:this={headerEl} class="vheader">
      {@render header()}
    </div>
  {/if}
  <div class="vtrack" style="height: {items.length * itemHeight}px;">
    <div class="vslice" style="transform: translateY({startIdx * itemHeight}px);">
      {#each visible as entry (entry.index)}
        <div class="vrow" style="height: {itemHeight}px;">
          {@render row(entry.item, entry.index)}
        </div>
      {/each}
    </div>
  </div>
</div>

<style>
  .vlist {
    height: 100%;
    width: 100%;
    overflow-y: auto;
    overflow-x: hidden;
  }
  .vtrack {
    position: relative;
    width: 100%;
  }
  .vslice {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    will-change: transform;
  }
  .vrow {
    width: 100%;
  }
</style>
