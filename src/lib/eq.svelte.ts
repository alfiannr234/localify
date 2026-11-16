import { api } from './api';
import { defaultEqConfig, EQ_PRESETS } from './eq';
import type { EqConfig } from './types';

class EqStore {
  config = $state<EqConfig>(defaultEqConfig());
  activePresetId = $state('flat');

  async load(): Promise<void> {
    try {
      const kv = await api.getAllKv();
      if (kv.eq_config) {
        const parsed = JSON.parse(kv.eq_config) as EqConfig;
        if (Array.isArray(parsed.gainsDb) && parsed.gainsDb.length === 10) {
          this.config = parsed;
          void api.audioSetEq(parsed);
        }
      }
      if (kv.eq_preset) this.activePresetId = kv.eq_preset;
    } catch {
      // ignore corrupt eq config
    }
  }

  setEnabled(enabled: boolean): void {
    this.config = { ...this.config, enabled };
    this.persist();
  }

  setPreamp(preampDb: number): void {
    this.config = { ...this.config, preampDb };
    this.activePresetId = 'custom';
    this.persist();
  }

  setBand(index: number, gainDb: number): void {
    const next = [...this.config.gainsDb];
    next[index] = Math.max(-12, Math.min(12, Math.round(gainDb * 10) / 10));
    this.config = { ...this.config, gainsDb: next };
    this.activePresetId = 'custom';
    this.persist();
  }

  applyPreset(id: string): void {
    const p = EQ_PRESETS.find((x) => x.id === id);
    if (!p) return;
    this.activePresetId = id;
    this.config = {
      enabled: id === 'flat' ? this.config.enabled : true,
      preampDb: p.preampDb,
      gainsDb: [...p.gainsDb],
    };
    this.persist();
  }

  private persist(): void {
    void api.audioSetEq(this.config);
    void api.setKv('eq_config', JSON.stringify(this.config));
    void api.setKv('eq_preset', this.activePresetId);
  }
}

export const eqStore = new EqStore();
