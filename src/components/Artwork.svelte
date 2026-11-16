<script lang="ts">
  import { artUrl } from '../lib/api';
  import Icon from './Icon.svelte';

  interface Props {
    hash: string | null | undefined;
    alt?: string;
    size?: number;
    thumb?: boolean;
    radius?: number;
    class?: string;
  }

  let { hash, alt = 'Album art', size = 44, thumb = true, radius = 8, class: cls = '' }: Props = $props();
  let failed = $state(false);
  let url = $derived(!failed ? artUrl(hash, thumb) : null);

  $effect(() => {
    void hash;
    failed = false;
  });
</script>

<div
  class="art {cls}"
  style="width: {size}px; height: {size}px; border-radius: {radius}px;"
>
  {#if url}
    <img src={url} {alt} loading="lazy" decoding="async" onerror={() => (failed = true)} />
  {:else}
    <div class="fallback">
      <Icon name="disc" size={Math.max(16, Math.round(size * 0.48))} />
    </div>
  {/if}
</div>

<style>
  .art {
    position: relative;
    overflow: hidden;
    flex-shrink: 0;
    background: var(--surface-2);
    border: 1px solid var(--border);
  }
  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
    display: block;
  }
  .fallback {
    width: 100%;
    height: 100%;
    display: flex;
    align-items: center;
    justify-content: center;
    color: var(--text-3);
  }
</style>
