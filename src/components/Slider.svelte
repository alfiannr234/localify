<script lang="ts">
  interface Props {
    value: number;
    min?: number;
    max: number;
    step?: number;
    label: string;
    onInput?: (val: number) => void;
    onChange?: (val: number) => void;
    height?: number;
    disabled?: boolean;
  }

  let {
    value,
    min = 0,
    max,
    step = 0.01,
    label,
    onInput,
    onChange,
    height = 4,
    disabled = false,
  }: Props = $props();

  let trackEl: HTMLDivElement | undefined = $state();
  let dragging = $state(false);
  let hover = $state(false);

  let pct = $derived.by(() => {
    const span = max - min;
    if (span <= 0) return 0;
    return Math.max(0, Math.min(100, ((value - min) / span) * 100));
  });

  function valueFromPointer(clientX: number): number {
    if (!trackEl) return value;
    const rect = trackEl.getBoundingClientRect();
    const ratio = rect.width > 0 ? (clientX - rect.left) / rect.width : 0;
    const clamped = Math.max(0, Math.min(1, ratio));
    const raw = min + clamped * (max - min);
    return step > 0 ? Math.round(raw / step) * step : raw;
  }

  function onPointerDown(e: PointerEvent): void {
    if (disabled || !trackEl) return;
    dragging = true;
    trackEl.setPointerCapture(e.pointerId);
    const v = valueFromPointer(e.clientX);
    onInput?.(v);
  }

  function onPointerMove(e: PointerEvent): void {
    if (!dragging || disabled) return;
    const v = valueFromPointer(e.clientX);
    onInput?.(v);
  }

  function onPointerUp(e: PointerEvent): void {
    if (!dragging) return;
    dragging = false;
    try {
      trackEl?.releasePointerCapture(e.pointerId);
    } catch {
      // ignore if already released
    }
    const v = valueFromPointer(e.clientX);
    onChange?.(v);
  }

  function onKeyDown(e: KeyboardEvent): void {
    if (disabled) return;
    const delta = Math.max(step, (max - min) * 0.02);
    if (e.key === 'ArrowRight' || e.key === 'ArrowUp') {
      e.preventDefault();
      const next = Math.min(max, value + delta);
      onInput?.(next);
      onChange?.(next);
    } else if (e.key === 'ArrowLeft' || e.key === 'ArrowDown') {
      e.preventDefault();
      const next = Math.max(min, value - delta);
      onInput?.(next);
      onChange?.(next);
    }
  }
</script>

<div
  bind:this={trackEl}
  class="slider"
  class:disabled
  class:active={dragging || hover}
  role="slider"
  tabindex={disabled ? -1 : 0}
  aria-label={label}
  aria-valuemin={min}
  aria-valuemax={max}
  aria-valuenow={Math.round(value * 100) / 100}
  onpointerdown={onPointerDown}
  onpointermove={onPointerMove}
  onpointerup={onPointerUp}
  onpointerenter={() => (hover = true)}
  onpointerleave={() => (hover = false)}
  onkeydown={onKeyDown}
>
  <div class="rail" style="height: {height}px;">
    <div class="fill" style="width: {pct}%;"></div>
  </div>
  <div class="thumb" style="left: {pct}%;"></div>
</div>

<style>
  .slider {
    position: relative;
    width: 100%;
    height: 18px;
    display: flex;
    align-items: center;
    cursor: pointer;
    touch-action: none;
  }
  .slider.disabled {
    cursor: not-allowed;
    opacity: 0.45;
  }
  .rail {
    position: relative;
    width: 100%;
    background: var(--surface-2);
    border-radius: 999px;
    overflow: hidden;
  }
  .fill {
    height: 100%;
    background: var(--accent);
    border-radius: 999px;
    transition: background 120ms ease;
  }
  .thumb {
    position: absolute;
    top: 50%;
    width: 12px;
    height: 12px;
    border-radius: 999px;
    background: var(--text);
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.45);
    transform: translate(-50%, -50%) scale(0);
    transition: transform 120ms var(--ease);
    pointer-events: none;
  }
  .slider.active .thumb,
  .slider:focus-visible .thumb {
    transform: translate(-50%, -50%) scale(1);
  }
</style>
