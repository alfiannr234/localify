<script lang="ts">
  import Toggle from '../components/Toggle.svelte';
  import { EQ_BAND_LABELS, EQ_PRESETS, evalEqResponseDb } from '../lib/eq';
  import { eqStore } from '../lib/eq.svelte';

  const W = 680;
  const H = 170;
  const PAD_X = 24;
  const MIN_F = 20;
  const MAX_F = 20000;

  function xForFreq(freq: number): number {
    const t = (Math.log10(freq) - Math.log10(MIN_F)) / (Math.log10(MAX_F) - Math.log10(MIN_F));
    return PAD_X + t * (W - PAD_X * 2);
  }

  function yForDb(db: number): number {
    const clamped = Math.max(-15, Math.min(15, db));
    return H * 0.5 - (clamped / 15) * (H * 0.42);
  }

  let curvePath = $derived.by(() => {
    const pts: string[] = [];
    const steps = 90;
    for (let i = 0; i <= steps; i++) {
      const t = i / steps;
      const freq = Math.pow(10, Math.log10(MIN_F) + t * (Math.log10(MAX_F) - Math.log10(MIN_F)));
      const db = evalEqResponseDb(freq, eqStore.config);
      pts.push(`${xForFreq(freq).toFixed(1)},${yForDb(db).toFixed(1)}`);
    }
    return `M ${pts.join(' L ')}`;
  });

  let fillPath = $derived(`${curvePath} L ${W - PAD_X},${H} L ${PAD_X},${H} Z`);
</script>

<div class="eq-page scroll-thin">
  <div class="eq-head">
    <div>
      <h1>Equalizer</h1>
      <p class="sub">Real-time 10-band biquad filter curve applied in the audio engine.</p>
    </div>
    <div class="eq-toggle">
      <span>{eqStore.config.enabled ? 'Active' : 'Bypassed'}</span>
      <Toggle
        checked={eqStore.config.enabled}
        label="Enable Equalizer"
        onChange={(v) => eqStore.setEnabled(v)}
      />
    </div>
  </div>

  <div class="presets">
    {#each EQ_PRESETS as p (p.id)}
      <button
        type="button"
        class="preset-chip"
        class:active={eqStore.activePresetId === p.id}
        onclick={() => eqStore.applyPreset(p.id)}
      >
        {p.label}
      </button>
    {/each}
  </div>

  <div class="curve-card">
    <svg viewBox="0 0 {W} {H}" class="curve-svg" aria-label="Equalizer frequency response">
      <line x1={PAD_X} y1={yForDb(0)} x2={W - PAD_X} y2={yForDb(0)} stroke="var(--border-strong)" stroke-dasharray="4 4" />
      <line x1={PAD_X} y1={yForDb(6)} x2={W - PAD_X} y2={yForDb(6)} stroke="var(--border)" />
      <line x1={PAD_X} y1={yForDb(-6)} x2={W - PAD_X} y2={yForDb(-6)} stroke="var(--border)" />
      <path d={fillPath} fill="var(--accent-soft)" />
      <path
        d={curvePath}
        fill="none"
        stroke={eqStore.config.enabled ? 'var(--accent)' : 'var(--text-3)'}
        stroke-width="2.5"
        stroke-linecap="round"
      />
    </svg>
  </div>

  <div class="sliders-panel">
    <div class="band-col preamp">
      <span class="gain-lbl tabular">{eqStore.config.preampDb > 0 ? `+${eqStore.config.preampDb.toFixed(1)}` : eqStore.config.preampDb.toFixed(1)}</span>
      <input
        type="range"
        min="-12"
        max="12"
        step="0.5"
        aria-label="Preamp gain"
        value={eqStore.config.preampDb}
        oninput={(e) => eqStore.setPreamp(Number((e.currentTarget as HTMLInputElement).value))}
      />
      <span class="freq-lbl">PRE</span>
    </div>

    <div class="v-sep"></div>

    {#each EQ_BAND_LABELS as lbl, i (lbl)}
      {@const val = eqStore.config.gainsDb[i]}
      <div class="band-col">
        <span class="gain-lbl tabular">{val > 0 ? `+${val.toFixed(1)}` : val.toFixed(1)}</span>
        <input
          type="range"
          min="-12"
          max="12"
          step="0.5"
          aria-label="{lbl} Hz gain"
          value={val}
          oninput={(e) => eqStore.setBand(i, Number((e.currentTarget as HTMLInputElement).value))}
        />
        <span class="freq-lbl tabular">{lbl}</span>
      </div>
    {/each}
  </div>
</div>

<style>
  .eq-page {
    height: 100%;
    overflow-y: auto;
    padding: 24px 28px 36px;
    display: flex;
    flex-direction: column;
    gap: 20px;
    max-width: 900px;
  }
  .eq-head {
    display: flex;
    justify-content: space-between;
    align-items: center;
    gap: 12px;
  }
  .eq-head h1 {
    margin: 0;
    font-size: 24px;
    font-weight: 700;
  }
  .sub {
    margin: 3px 0 0;
    color: var(--text-2);
    font-size: 13px;
  }
  .eq-toggle {
    display: flex;
    align-items: center;
    gap: 10px;
    font-weight: 600;
  }
  .presets {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .preset-chip {
    height: 30px;
    padding: 0 12px;
    border-radius: var(--r-full);
    border: 1px solid var(--border);
    background: var(--surface);
    color: var(--text-2);
    font-size: 12.5px;
    font-weight: 500;
  }
  .preset-chip:hover {
    background: var(--surface-2);
    color: var(--text);
  }
  .preset-chip.active {
    background: var(--accent);
    color: var(--accent-fg);
    border-color: transparent;
    font-weight: 600;
  }
  .curve-card {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--r-lg);
    padding: 12px;
  }
  .curve-svg {
    width: 100%;
    height: 170px;
    display: block;
  }
  .sliders-panel {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--r-lg);
    padding: 20px 18px;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 8px;
  }
  .band-col {
    display: flex;
    flex-direction: column;
    align-items: center;
    gap: 10px;
    flex: 1;
  }
  .band-col input[type='range'] {
    writing-mode: vertical-lr;
    direction: rtl;
    width: 28px;
    height: 150px;
    accent-color: var(--accent);
    cursor: pointer;
  }
  .gain-lbl {
    font-size: 11px;
    color: var(--text-2);
    min-width: 34px;
    text-align: center;
  }
  .freq-lbl {
    font-size: 11px;
    font-weight: 700;
    color: var(--text-3);
  }
  .v-sep {
    width: 1px;
    height: 180px;
    background: var(--border);
  }
</style>
