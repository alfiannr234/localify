import type { Track } from './types';

export function formatTime(secs: number): string {
  if (!Number.isFinite(secs) || secs < 0) return '0:00';
  const total = Math.floor(secs);
  const hrs = Math.floor(total / 3600);
  const mins = Math.floor((total % 3600) / 60);
  const rem = total % 60;
  if (hrs > 0) {
    return `${hrs}:${String(mins).padStart(2, '0')}:${String(rem).padStart(2, '0')}`;
  }
  return `${mins}:${String(rem).padStart(2, '0')}`;
}

export function formatTotalDuration(totalSecs: number): string {
  if (!Number.isFinite(totalSecs) || totalSecs <= 0) return '0 min';
  const hrs = Math.floor(totalSecs / 3600);
  const mins = Math.round((totalSecs % 3600) / 60);
  if (hrs > 0) return `${hrs} hr ${mins} min`;
  return `${Math.max(1, mins)} min`;
}

export function formatFileSize(bytes: number): string {
  if (!Number.isFinite(bytes) || bytes <= 0) return '0 B';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(2)} GB`;
}

export function formatAudioSpecs(track: Track | null): string {
  if (!track) return '';
  const parts: string[] = [];
  if (track.codec) parts.push(track.codec);
  if (track.bitDepth) parts.push(`${track.bitDepth}-bit`);
  if (track.sampleRate) {
    const khz = track.sampleRate % 1000 === 0 ? (track.sampleRate / 1000).toFixed(0) : (track.sampleRate / 1000).toFixed(1);
    parts.push(`${khz} kHz`);
  }
  if (track.bitrate && !track.bitDepth) parts.push(`${track.bitrate} kbps`);
  return parts.join(' / ');
}

export function clamp(val: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, val));
}

export function sortLetterKey(text: string): string {
  const c = text.trim().charAt(0).toUpperCase();
  return c >= 'A' && c <= 'Z' ? c : '#';
}
