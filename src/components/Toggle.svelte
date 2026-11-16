<script lang="ts">
  interface Props {
    checked: boolean;
    label: string;
    onChange: (next: boolean) => void;
    disabled?: boolean;
  }

  let { checked, label, onChange, disabled = false }: Props = $props();
</script>

<button
  type="button"
  class="switch"
  class:on={checked}
  role="switch"
  aria-checked={checked}
  aria-label={label}
  {disabled}
  onclick={() => onChange(!checked)}
>
  <span class="knob"></span>
</button>

<style>
  .switch {
    position: relative;
    width: 40px;
    height: 22px;
    border-radius: 999px;
    border: 1px solid var(--border-strong);
    background: var(--surface-2);
    padding: 0;
    transition: background 160ms var(--ease), border-color 160ms var(--ease);
    flex-shrink: 0;
  }
  .switch.on {
    background: var(--accent);
    border-color: transparent;
  }
  .knob {
    position: absolute;
    top: 2px;
    left: 2px;
    width: 16px;
    height: 16px;
    border-radius: 999px;
    background: #ffffff;
    box-shadow: 0 1px 3px rgba(0, 0, 0, 0.35);
    transition: transform 160ms var(--ease);
  }
  .switch.on .knob {
    transform: translateX(18px);
    background: var(--accent-fg);
  }
</style>
