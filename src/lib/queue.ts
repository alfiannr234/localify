import type { RepeatMode, Track } from './types';

/**
 * Builds the playback queue from a full context list (`contextTracks`) while ensuring
 * clicking one track inside search or a filtered list plays from the intended context cleanly.
 */
export function buildQueueOrder(
  contextTracks: Track[],
  startPath: string,
  shuffle: boolean
): string[] {
  const uniquePaths: string[] = [];
  const seen = new Set<string>();
  for (const t of contextTracks) {
    if (!seen.has(t.path)) {
      seen.add(t.path);
      uniquePaths.push(t.path);
    }
  }
  if (!seen.has(startPath)) {
    uniquePaths.unshift(startPath);
  }

  if (!shuffle) {
    return uniquePaths;
  }

  const rest = uniquePaths.filter((p) => p !== startPath);
  for (let i = rest.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [rest[i], rest[j]] = [rest[j], rest[i]];
  }
  return [startPath, ...rest];
}

export function resolveNextIndex(
  currentIndex: number,
  queueLength: number,
  repeat: RepeatMode,
  manualTrigger: boolean
): number | null {
  if (queueLength <= 0) return null;
  if (repeat === 'one' && !manualTrigger) return currentIndex;
  if (currentIndex + 1 < queueLength) return currentIndex + 1;
  if (repeat === 'all') return 0;
  return null;
}

export function resolvePrevIndex(
  currentIndex: number,
  queueLength: number,
  repeat: RepeatMode
): number | null {
  if (queueLength <= 0) return null;
  if (currentIndex - 1 >= 0) return currentIndex - 1;
  if (repeat === 'all') return queueLength - 1;
  return 0;
}
