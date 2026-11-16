<script lang="ts">
  import { open } from '@tauri-apps/plugin-dialog';
  import Icon from '../components/Icon.svelte';
  import LyricsSources from '../components/LyricsSources.svelte';
  import Toggle from '../components/Toggle.svelte';
  import { api } from '../lib/api';
  import { applyAppearance } from '../lib/appearance.svelte';
  import { library } from '../lib/library.svelte';
  import { formatOffsetSeconds, parseOffsetInput } from '../lib/lyrics';
  import { lyricsStore } from '../lib/lyrics.svelte';
  import { player } from '../lib/player.svelte';
  import { DEFAULT_DISCORD_CLIENT_ID, settings } from '../lib/settings.svelte';
  import { ACCENTS, THEMES } from '../lib/themes';
  import { TRANSLATE_LANGUAGES } from '../lib/translate';
  import { triggerTranslateSync } from '../lib/translate.svelte';
  import type { AudioDeviceInfo } from '../lib/types';

  let devices = $state<AudioDeviceInfo[]>([]);
  let filterText = $state('');
  let offsetDraft = $state('0.00');
  let offsetFocused = $state(false);
  let searchEl: HTMLInputElement | undefined = $state();

  let subAccount = $state<{ server: string; user: string } | null>(null);
  let subServer = $state('');
  let subUser = $state('');
  let subPass = $state('');
  let subBusy = $state(false);
  let subErr = $state('');

  $effect(() => {
    void api.audioDevices().then((d) => (devices = d));
    void api.subsonicStatus().then((s) => (subAccount = s));
  });

  async function connectSubsonic(e: Event): Promise<void> {
    e.preventDefault();
    subBusy = true;
    subErr = '';
    try {
      subAccount = await api.subsonicConnect(subServer, subUser, subPass);
      subPass = '';
    } catch (err) {
      subErr = err instanceof Error ? err.message : String(err);
    } finally {
      subBusy = false;
    }
  }

  async function logoutSubsonic(): Promise<void> {
    await api.subsonicLogout();
    subAccount = null;
  }

  $effect(() => {
    if (!offsetFocused) {
      const current = lyricsStore.trackPath ? lyricsStore.trackOffset : settings.defaultOffset;
      offsetDraft = formatOffsetSeconds(current);
    }
  });

  function matches(keywords: string): boolean {
    const q = filterText.trim().toLowerCase();
    if (!q) return true;
    return keywords.toLowerCase().includes(q);
  }

  async function addFolder(): Promise<void> {
    const picked = await open({ directory: true, multiple: false });
    if (picked && !Array.isArray(picked)) {
      await library.addFolder(picked);
    }
  }

  function commitOffset(): void {
    offsetFocused = false;
    const parsed = parseOffsetInput(offsetDraft);
    if (parsed !== null) {
      void lyricsStore.setOffset(parsed);
    } else {
      const cur = lyricsStore.trackPath ? lyricsStore.trackOffset : settings.defaultOffset;
      offsetDraft = formatOffsetSeconds(cur);
    }
  }

  function refreshTheme(): void {
    applyAppearance(player.currentTrack?.artColor ?? null);
  }

  function onKeyDown(e: KeyboardEvent): void {
    if ((e.ctrlKey || e.metaKey) && e.key.toLowerCase() === 'f') {
      e.preventDefault();
      searchEl?.focus();
    }
  }
</script>

<svelte:window onkeydown={onKeyDown} />

<div class="settings-page scroll-thin">
  <div class="s-head">
    <h1>Settings</h1>
    <input
      bind:this={searchEl}
      class="input filter-box"
      placeholder="Filter settings (Ctrl+F)"
      bind:value={filterText}
    />
  </div>

  {#if matches('libraries folder scan music')}
    <section class="card-sec">
      <h2>Libraries</h2>
      <div class="row">
        <div class="row-info">
          <div class="row-title">Music library locations</div>
          <div class="row-desc">
            {library.folders.length === 0 ? 'No folders added' : `${library.folders.length} watched folders`}
          </div>
        </div>
        <div class="row-actions">
          <button type="button" class="btn" disabled={library.scanning} onclick={() => library.rescan()}>
            <Icon name="refresh" size={15} class={library.scanning ? 'spin' : ''} />
            <span>Refresh</span>
          </button>
          <button type="button" class="btn btn-primary" onclick={addFolder}>
            <Icon name="folder-plus" size={15} />
            <span>Add Folder</span>
          </button>
        </div>
      </div>

      {#if library.folders.length > 0}
        <div class="folder-list">
          {#each library.folders as folder (folder)}
            <div class="folder-item">
              <Icon name="folder" size={16} />
              <span class="truncate">{folder}</span>
              <button
                type="button"
                class="btn-icon"
                aria-label="Remove folder"
                onclick={() => library.removeFolder(folder)}
              >
                <Icon name="trash" size={15} />
              </button>
            </div>
          {/each}
        </div>
      {/if}
    </section>
  {/if}

  {#if matches('appearance theme accent color artwork')}
    <section class="card-sec">
      <h2>Personalization</h2>
      <div class="row">
        <div class="row-info">
          <div class="row-title">App theme</div>
          <div class="row-desc">Select application color scheme</div>
        </div>
        <select
          class="input"
          aria-label="App theme"
          value={settings.theme}
          onchange={(e) => {
            settings.theme = (e.currentTarget as HTMLSelectElement).value;
            settings.save('theme', settings.theme);
            refreshTheme();
          }}
        >
          {#each THEMES as th (th.id)}
            <option value={th.id}>{th.label}</option>
          {/each}
        </select>
      </div>

      <div class="row">
        <div class="row-info">
          <div class="row-title">Accent color</div>
          <div class="row-desc">Highlight color for playback controls and active items</div>
        </div>
        <div class="accent-pills">
          {#each ACCENTS as ac (ac.id)}
            <button
              type="button"
              class="swatch"
              class:active={settings.accent === ac.id && !settings.matchArtwork}
              style="background: {ac.dark};"
              aria-label={ac.label}
              title={ac.label}
              onclick={() => {
                settings.accent = ac.id;
                settings.matchArtwork = false;
                settings.save('accent', ac.id);
                settings.save('match_artwork', 'false');
                refreshTheme();
              }}
            ></button>
          {/each}
        </div>
      </div>

      <div class="row">
        <div class="row-info">
          <div class="row-title">Adaptive cover accent</div>
          <div class="row-desc">Tint accent color to match the playing album artwork</div>
        </div>
        <Toggle
          checked={settings.matchArtwork}
          label="Adaptive cover accent"
          onChange={(v) => {
            settings.matchArtwork = v;
            settings.save('match_artwork', String(v));
            refreshTheme();
          }}
        />
      </div>
    </section>
  {/if}

  {#if matches('sinkronisasi lirik timing offset font size romaji translate language word timing lrclib netease kugou subsonic')}
    <section class="card-sec">
      <h2>Lyrics</h2>

      <!-- Strict user request: NO badge pill next to heading, editable input + ONLY -0.1s and +0.1s buttons, ZERO toast popup -->
      <div class="row">
        <div class="row-info">
          <div class="row-title">Sinkronisasi lirik</div>
          <div class="row-desc">
            Atur ketepatan waktu lirik (- = lebih lambat, + = lebih cepat)
            {#if player.currentTrack}
              <br />
              <span class="active-track-note">Lagu aktif: "{player.currentTrack.title}"</span>
            {/if}
          </div>
        </div>
        <div class="offset-ctrl">
          <button type="button" class="btn tabular" onclick={() => lyricsStore.adjustOffset(-0.1)}>
            -0.1s
          </button>
          <input
            type="text"
            class="input offset-edit tabular"
            aria-label="Sinkronisasi lirik dalam detik"
            bind:value={offsetDraft}
            onfocus={() => (offsetFocused = true)}
            onblur={commitOffset}
            onkeydown={(e) => {
              if (e.key === 'Enter') (e.currentTarget as HTMLInputElement).blur();
            }}
          />
          <button type="button" class="btn tabular" onclick={() => lyricsStore.adjustOffset(0.1)}>
            +0.1s
          </button>
        </div>
      </div>

      <div class="row">
        <div class="row-info">
          <div class="row-title">Prefer word-timed lyrics</div>
          <div class="row-desc">Keep line-synced hits as fallback while checking remaining sources for word timing</div>
        </div>
        <Toggle
          checked={settings.preferWordTiming}
          label="Prefer word-timed lyrics"
          onChange={(v) => {
            settings.preferWordTiming = v;
            settings.save('prefer_word_timing', String(v));
          }}
        />
      </div>

      <div class="sources-block">
        <div class="row-title">Lyrics provider priority</div>
        <div class="row-desc">Drag rows or use arrow keys to reorder how lyrics are fetched</div>
        <LyricsSources />
      </div>

      <div class="row">
        <div class="row-info">
          <div class="row-title">Translate lyrics</div>
          <div class="row-desc">Display translated lines underneath original lyrics</div>
        </div>
        <Toggle
          checked={settings.translateLyrics}
          label="Translate lyrics"
          onChange={(v) => {
            settings.translateLyrics = v;
            settings.save('translate_lyrics', String(v));
            triggerTranslateSync();
          }}
        />
      </div>

      <div class="row">
        <div class="row-info">
          <div class="row-title">Translation language</div>
          <div class="row-desc">Target language for lyrics translation and word lookups</div>
        </div>
        <select
          class="input"
          aria-label="Translation language"
          value={settings.lyricsLanguage}
          onchange={(e) => {
            settings.lyricsLanguage = (e.currentTarget as HTMLSelectElement).value;
            settings.save('lyrics_language', settings.lyricsLanguage);
            triggerTranslateSync();
          }}
        >
          <option value="">System Language</option>
          {#each TRANSLATE_LANGUAGES as [code, label] (code)}
            <option value={code}>{label}</option>
          {/each}
        </select>
      </div>

      <div class="row">
        <div class="row-info">
          <div class="row-title">Show Romaji / Romanization</div>
          <div class="row-desc">Display offline Romanized reading beneath Japanese and Korean lyrics</div>
        </div>
        <Toggle
          checked={settings.romanizeLyrics}
          label="Show romanization"
          onChange={(v) => {
            settings.romanizeLyrics = v;
            settings.save('romanize_lyrics', String(v));
          }}
        />
      </div>

      <div class="row">
        <div class="row-info">
          <div class="row-title">Lyrics font size</div>
          <div class="row-desc tabular">{settings.lyricsFontSize}px</div>
        </div>
        <input
          type="range"
          min="16"
          max="32"
          step="1"
          aria-label="Lyrics font size"
          value={settings.lyricsFontSize}
          oninput={(e) => {
            settings.lyricsFontSize = Number((e.currentTarget as HTMLInputElement).value);
            settings.save('lyrics_font_size', String(settings.lyricsFontSize));
          }}
        />
      </div>
    </section>
  {/if}

  {#if matches('navidrome subsonic server credentials')}
    <section class="card-sec">
      <h2>Navidrome / Subsonic Server</h2>
      {#if subAccount}
        <div class="row">
          <div class="row-info">
            <div class="row-title">{subAccount.server}</div>
            <div class="row-desc">Signed in as {subAccount.user} (lyrics lookups enabled)</div>
          </div>
          <button type="button" class="btn" onclick={logoutSubsonic}>Sign out</button>
        </div>
      {:else}
        <form class="sub-form" onsubmit={connectSubsonic}>
          <div class="sub-grid">
            <input class="input" placeholder="Server URL (e.g. http://192.168.1.5:4533)" bind:value={subServer} />
            <input class="input" placeholder="Username" bind:value={subUser} />
            <input class="input" type="password" placeholder="Password" bind:value={subPass} />
            <button type="submit" class="btn btn-primary" disabled={subBusy}>
              {subBusy ? 'Connecting...' : 'Connect'}
            </button>
          </div>
          {#if subErr}
            <div class="sub-err" role="alert">{subErr}</div>
          {/if}
        </form>
      {/if}
    </section>
  {/if}

  {#if matches('audio output device discord rich presence rpc client id')}
    <section class="card-sec">
      <h2>Audio & Integrations</h2>

      <div class="row">
        <div class="row-info">
          <div class="row-title">Audio output device</div>
          <div class="row-desc">Select playback destination device</div>
        </div>
        <select
          class="input"
          aria-label="Audio output device"
          value={settings.outputDevice}
          onchange={async (e) => {
            const dev = (e.currentTarget as HTMLSelectElement).value;
            settings.outputDevice = dev;
            settings.save('output_device', dev);
            await api.audioSetDevice(dev || null);
          }}
        >
          <option value="">System Default</option>
          {#each devices as dev (dev.name)}
            <option value={dev.name}>{dev.name}{dev.isDefault ? ' (Default)' : ''}</option>
          {/each}
        </select>
      </div>

      <div class="row">
        <div class="row-info">
          <div class="row-title">Discord Rich Presence</div>
          <div class="row-desc">Share currently playing track on your Discord profile</div>
        </div>
        <Toggle
          checked={settings.discordEnabled}
          label="Enable Discord Rich Presence"
          onChange={(v) => {
            settings.discordEnabled = v;
            settings.save('discord_enabled', String(v));
            player.syncIntegrations();
          }}
        />
      </div>

      <div class="row">
        <div class="row-info">
          <div class="row-title">Discord Application Client ID</div>
          <div class="row-desc">Hidden by default; reveal only if you want a custom Discord app ID</div>
        </div>
        <div class="row-actions">
          {#if settings.discordClientIdRevealed}
            <input
              class="input cid-input tabular"
              aria-label="Discord Application Client ID"
              value={settings.discordClientId}
              oninput={(e) => {
                settings.discordClientId = (e.currentTarget as HTMLInputElement).value;
                settings.save('discord_client_id', settings.discordClientId);
              }}
            />
            <button
              type="button"
              class="btn"
              onclick={() => {
                settings.discordClientId = DEFAULT_DISCORD_CLIENT_ID;
                settings.save('discord_client_id', DEFAULT_DISCORD_CLIENT_ID);
                player.syncIntegrations();
              }}
            >
              Reset
            </button>
          {/if}
          <button
            type="button"
            class="btn"
            onclick={() => (settings.discordClientIdRevealed = !settings.discordClientIdRevealed)}
          >
            <Icon name={settings.discordClientIdRevealed ? 'eye-off' : 'eye'} size={15} />
            <span>{settings.discordClientIdRevealed ? 'Hide' : 'Show'}</span>
          </button>
        </div>
      </div>
    </section>
  {/if}
</div>

<style>
  .settings-page {
    height: 100%;
    overflow-y: auto;
    padding: 24px 28px 48px;
    display: flex;
    flex-direction: column;
    gap: 20px;
    max-width: 880px;
  }
  .s-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
  }
  .s-head h1 {
    margin: 0;
    font-size: 26px;
    font-weight: 700;
  }
  .filter-box {
    width: 240px;
  }
  .card-sec {
    background: var(--surface);
    border: 1px solid var(--border);
    border-radius: var(--r-lg);
    padding: 16px 20px;
    display: flex;
    flex-direction: column;
    gap: 14px;
  }
  .card-sec h2 {
    margin: 0;
    font-size: 14px;
    font-weight: 700;
    color: var(--accent);
    letter-spacing: 0.02em;
  }
  .row {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 18px;
    padding-top: 12px;
    border-top: 1px solid var(--border);
  }
  .card-sec > .row:first-of-type {
    padding-top: 4px;
    border-top: none;
  }
  .row-info {
    display: flex;
    flex-direction: column;
    gap: 2px;
  }
  .row-title {
    font-size: 13.5px;
    font-weight: 600;
    color: var(--text);
  }
  .row-desc {
    font-size: 12px;
    color: var(--text-2);
  }
  .active-track-note {
    color: var(--accent);
    font-weight: 500;
  }
  .row-actions {
    display: flex;
    align-items: center;
    gap: 8px;
    flex-shrink: 0;
  }
  .folder-list {
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .folder-item {
    display: flex;
    align-items: center;
    gap: 10px;
    padding: 7px 10px;
    border-radius: var(--r-sm);
    background: var(--surface-2);
    font-size: 12.5px;
  }
  .folder-item span {
    flex: 1;
    min-width: 0;
  }
  .accent-pills {
    display: flex;
    gap: 8px;
  }
  .swatch {
    width: 22px;
    height: 22px;
    border-radius: 999px;
    border: 2px solid transparent;
    padding: 0;
  }
  .swatch.active {
    border-color: var(--text);
    transform: scale(1.12);
  }
  .offset-ctrl {
    display: flex;
    align-items: center;
    gap: 6px;
  }
  .offset-edit {
    width: 72px;
    text-align: center;
    font-weight: 600;
  }
  .cid-input {
    width: 185px;
    font-family: var(--font-mono);
    font-size: 12px;
  }
  .sources-block {
    padding-top: 12px;
    border-top: 1px solid var(--border);
    display: flex;
    flex-direction: column;
    gap: 6px;
  }
  .sub-form {
    display: flex;
    flex-direction: column;
    gap: 10px;
  }
  .sub-grid {
    display: grid;
    grid-template-columns: 1.6fr 1fr 1fr auto;
    gap: 8px;
  }
  .sub-err {
    font-size: 12.5px;
    color: var(--danger);
  }
</style>
