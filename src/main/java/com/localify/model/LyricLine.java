package com.localify.model;

/**
 * One line of a synced .lrc file.
 *
 * @param timeSeconds when the line starts, in seconds from the beginning of the song
 * @param text        the lyric text; empty for instrumental gaps
 */
public record LyricLine(double timeSeconds, String text) {
}
