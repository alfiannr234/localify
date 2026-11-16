import { api } from './api';
import { resolveAccessibleAccent } from './color';
import { settings } from './settings.svelte';
import { getAccentDef, getThemeDef } from './themes';

export function applyAppearance(trackArtColor: string | null): void {
  const theme = getThemeDef(settings.theme);
  const accentDef = getAccentDef(settings.accent);
  const rawAccent =
    settings.matchArtwork && trackArtColor
      ? trackArtColor
      : theme.isLight
        ? accentDef.light
        : accentDef.dark;

  const resolved = resolveAccessibleAccent(rawAccent, theme.bg, theme.isLight);
  const root = document.documentElement.style;

  root.setProperty('--bg', theme.bg);
  root.setProperty('--bg-elev', theme.bgElev);
  root.setProperty('--surface', theme.surface);
  root.setProperty('--surface-2', theme.surface2);
  root.setProperty('--surface-hover', theme.surfaceHover);
  root.setProperty('--surface-glass', theme.surfaceGlass);
  root.setProperty('--text', theme.text);
  root.setProperty('--text-2', theme.text2);
  root.setProperty('--text-3', theme.text3);
  root.setProperty('--border', theme.border);
  root.setProperty('--border-strong', theme.borderStrong);

  root.setProperty('--accent', resolved.accent);
  root.setProperty('--accent-hover', resolved.accentHover);
  root.setProperty('--accent-fg', resolved.accentFg);
  root.setProperty('--accent-soft', resolved.accentSoft);

  void api.setWindowTheme(theme.isLight ? 'light' : 'dark');
}
