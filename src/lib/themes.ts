export interface ThemeDef {
  id: string;
  label: string;
  isLight: boolean;
  bg: string;
  bgElev: string;
  surface: string;
  surface2: string;
  surfaceHover: string;
  surfaceGlass: string;
  text: string;
  text2: string;
  text3: string;
  border: string;
  borderStrong: string;
}

export interface AccentDef {
  id: string;
  label: string;
  dark: string;
  light: string;
}

export const THEMES: ThemeDef[] = [
  {
    id: 'obsidian',
    label: 'Obsidian (Dark)',
    isLight: false,
    bg: '#111318',
    bgElev: '#151820',
    surface: '#181B22',
    surface2: '#222631',
    surfaceHover: '#292E3B',
    surfaceGlass: 'rgba(17, 19, 24, 0.80)',
    text: '#F1F3F9',
    text2: '#A0A7B8',
    text3: '#838A9B',
    border: 'rgba(255, 255, 255, 0.08)',
    borderStrong: 'rgba(255, 255, 255, 0.16)',
  },
  {
    id: 'light',
    label: 'Daylight (Light)',
    isLight: true,
    bg: '#F4F5F7',
    bgElev: '#FAFAFB',
    surface: '#FFFFFF',
    surface2: '#E9ECF2',
    surfaceHover: '#DFE3EC',
    surfaceGlass: 'rgba(244, 245, 247, 0.82)',
    text: '#1D1D1F',
    text2: '#555760',
    text3: '#686A74',
    border: 'rgba(0, 0, 0, 0.09)',
    borderStrong: 'rgba(0, 0, 0, 0.18)',
  },
  {
    id: 'nord',
    label: 'Nord',
    isLight: false,
    bg: '#242933',
    bgElev: '#292F3B',
    surface: '#2E3440',
    surface2: '#3B4252',
    surfaceHover: '#434C5E',
    surfaceGlass: 'rgba(36, 41, 51, 0.82)',
    text: '#ECEFF4',
    text2: '#C8D0DF',
    text3: '#9DA7BA',
    border: 'rgba(236, 239, 244, 0.09)',
    borderStrong: 'rgba(236, 239, 244, 0.18)',
  },
  {
    id: 'dracula',
    label: 'Dracula',
    isLight: false,
    bg: '#191A21',
    bgElev: '#21222C',
    surface: '#282A36',
    surface2: '#343746',
    surfaceHover: '#44475A',
    surfaceGlass: 'rgba(25, 26, 33, 0.82)',
    text: '#F8F8F2',
    text2: '#BD93F9',
    text3: '#9CA3C7',
    border: 'rgba(248, 248, 242, 0.08)',
    borderStrong: 'rgba(248, 248, 242, 0.18)',
  },
  {
    id: 'catppuccin',
    label: 'Catppuccin Frappe',
    isLight: false,
    bg: '#232634',
    bgElev: '#292C3C',
    surface: '#303446',
    surface2: '#414559',
    surfaceHover: '#51576D',
    surfaceGlass: 'rgba(35, 38, 52, 0.82)',
    text: '#C6D0F5',
    text2: '#B5BFE2',
    text3: '#949CBB',
    border: 'rgba(198, 208, 245, 0.09)',
    borderStrong: 'rgba(198, 208, 245, 0.18)',
  },
  {
    id: 'rosepine',
    label: 'Rose Pine',
    isLight: false,
    bg: '#191724',
    bgElev: '#1F1D2E',
    surface: '#26233A',
    surface2: '#312E49',
    surfaceHover: '#403D52',
    surfaceGlass: 'rgba(25, 23, 36, 0.82)',
    text: '#E0DEF4',
    text2: '#C4C1DC',
    text3: '#908CAA',
    border: 'rgba(224, 222, 244, 0.09)',
    borderStrong: 'rgba(224, 222, 244, 0.18)',
  },
  {
    id: 'gruvbox',
    label: 'Gruvbox Dark',
    isLight: false,
    bg: '#1D2021',
    bgElev: '#242627',
    surface: '#282828',
    surface2: '#3C3836',
    surfaceHover: '#504945',
    surfaceGlass: 'rgba(29, 32, 33, 0.82)',
    text: '#EBDBB2',
    text2: '#D5C4A1',
    text3: '#A89984',
    border: 'rgba(235, 219, 178, 0.09)',
    borderStrong: 'rgba(235, 219, 178, 0.18)',
  },
];

export const ACCENTS: AccentDef[] = [
  { id: 'zest', label: 'Warm Zest', dark: '#F5823E', light: '#C0480C' },
  { id: 'cobalt', label: 'Electric Blue', dark: '#60A5FA', light: '#1D4ED8' },
  { id: 'emerald', label: 'Emerald', dark: '#34D399', light: '#047857' },
  { id: 'crimson', label: 'Crimson', dark: '#FB7185', light: '#BE123C' },
  { id: 'violet', label: 'Violet', dark: '#A78BFA', light: '#6D28D9' },
  { id: 'gold', label: 'Amber Gold', dark: '#FBBF24', light: '#B45309' },
];

export function getThemeDef(id: string): ThemeDef {
  return THEMES.find((t) => t.id === id) ?? THEMES[0];
}

export function getAccentDef(id: string): AccentDef {
  return ACCENTS.find((a) => a.id === id) ?? ACCENTS[0];
}
