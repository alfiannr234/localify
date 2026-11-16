# Localify Design System Specification

> Dial: ENERGY 2 / RHYTHM 2 / MOTION 2
> Reading this as: lightweight desktop music & synced-lyrics player for personal audio libraries, in a Localify Obsidian + Warm Zest style inspired by Qistara's translucent speed and clarity.

---

## 1. Visual Identity & Core Palette

Every text pairing below has been measured against WCAG AA (`>= 4.5:1` for normal text, `>= 3.0:1` for large text and non-text UI boundaries).

### Default Theme: Localify Obsidian (Dark)

| Token | Value | Role & Contrast |
| --- | --- | --- |
| `--bg` | `#111318` | Primary workspace canvas (cool obsidian, prevents OLED/IPS halation) |
| `--bg-2` | `#181B22` | Secondary cards, dropdown panels, sidebar surface |
| `--fill` | `rgba(255, 255, 255, 0.055)` | Subtle zebra rows and hover wash |
| `--fill-2` | `rgba(255, 255, 255, 0.095)` | Active nav pills and secondary controls |
| `--text` | `#F1F3F9` | Primary headings & track titles (`16.75:1` on `--bg`, PASS) |
| `--text-2` | `#A0A7B8` | Artist names, durations, secondary copy (`7.71:1` on `--bg`, `7.15:1` on `--bg-2`, PASS) |
| `--text-3` | `#747C90` | Muted section captions & keyboard shortcut badges |
| `--sep` | `rgba(170, 180, 210, 0.12)` | Hairline borders separating structural regions |
| `--accent` | `#F5823E` | Localify Warm Zest (`7.21:1` on `#111318`, PASS) |
| `--on-accent` | `#111318` | Text/icons inside accent buttons (`7.21:1` on `#F5823E`, PASS) |

### Light Theme: Localify Daylight

| Token | Value | Role & Contrast |
| --- | --- | --- |
| `--bg` | `#FFFFFF` | Primary light canvas |
| `--bg-2` | `#F5F5F7` | Sidebar and surface containers |
| `--text` | `#1D1D1F` | Primary ink (`16.83:1` on `#FFFFFF`, PASS) |
| `--text-2` | `#5E5E64` | Secondary metadata (`6.44:1` on `#FFFFFF`, PASS) |
| `--accent` | `#C0480C` | Deepened Zest Ember (`5.03:1` on `#FFFFFF`, PASS) |
| `--on-accent` | `#FFFFFF` | Ink on accent fills (`5.03:1` on `#C0480C`, PASS) |

### Additional Built-in Themes & Accents

- **Themes**: Automatic (OS Light/Dark), Localify Obsidian, Daylight, Nord Dark (`#2E3440`), Dracula (`#282A36`), Catppuccin Frappe (`#303446`), Rose Pine (`#191724`), Gruvbox Dark (`#282828`).
- **Accents**: Zest (`#F5823E`), Emerald (`#1DB954`), Ocean (`#2563EB`), Brass (`#E5A93C`), Violet (`#8B5CF6`), Rose (`#E11D48`), plus **Adaptive Cover Accent** (extracts dominant vivid hue from active cover art and clamps lightness to guarantee `>= 4.5:1` contrast via `readableAccent`).

---

## 2. Typography & Scale

- **Primary Font**: `'Inter Variable', 'Segoe UI Variable Text', 'Segoe UI', system-ui, sans-serif` (bundled locally via `@fontsource-variable/inter` for zero remote font dependency and crisp Latin/Indonesian legibility).
- **Numerals**: `font-variant-numeric: tabular-nums` enforced on all song durations, timestamps, bitrate/sample-rate tables, and lyrics timing offsets so scrolling/updating numbers never jitter horizontally.
- **Scale**:
  - Display Title (`.t-title`): `28px`, weight `700`, tracking `-0.022em`
  - Section Heading (`.t-h2`): `19px`, weight `650`, tracking `-0.015em`
  - Body / Table Row: `13px`, weight `400/550`, line-height `1.4`
  - Lyrics Line: `clamp(22px, 2.2vw, 38px)`, weight `750`, tracking `-0.02em`, line-height `1.22`

---

## 3. Window Surfaces, Hierarchy & Motion Reasons (R-01 to R-31)

- **Chrome & Mica (R-10)**: Windows 11 Mica material (`windowEffects: ["mica"]`) with translucent frosted blur strictly limited to at most 2 structural overlays (`TopBar` and `PlayerBar`); all cards, lists, and settings rows remain solid/matte so foreground hierarchy never flattens. Solid-chrome and reduced-transparency fallbacks are built into `:root.solid` and `@media (prefers-reduced-transparency: reduce)`.
- **Radii (R-11)**: Tokenized `--rk` corner scale (`--r-xs: 4px`, `--r-sm: 6px`, `--r: 8px`, `--r-lg: 12px`, `--r-xl: 16px`). Buttons use `--r` (`8px`), pills/tabs use capsule radius only for distinct tab switches or play FAB, cards use `--r-lg` (`12px`).
- **Motion (R-19, MOTION 2)**:
  - Tactile pointer-down press (`scale(0.96)`, `120ms`) gives immediate desktop responsiveness.
  - Spring-damped lyrics scrolling (`stiffness: 0.08, damping: 0.86`) glides smoothly to keep the active vocal line centered at 32% viewport height, pausing automatically for 3s on manual mouse wheel scroll.
  - Strictly respects `@media (prefers-reduced-motion: reduce)` by switching all springs and transitions to instant jumps (`1ms`).
- **Elevation (R-12)**: Shadows reserved exclusively for floating ContextMenus (`--shadow-pop`), Now Playing artwork (`--shadow-art`), and modal sheets.
- **Accessibility & Input Affordances (R-32, R-39)**:
  - Enabled native `<button>` and interactive controls show `cursor: pointer`; disabled controls show `cursor: not-allowed`.
  - `:focus-visible` renders a `2px solid var(--accent)` ring with `2px` offset across all themes.
  - Every data view provides explicit Loading, Empty, and Error states (`R-27`).
