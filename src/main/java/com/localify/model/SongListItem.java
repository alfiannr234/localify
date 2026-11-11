package com.localify.model;

/**
 * Items displayed in the modern grouped song list: either a category section header
 * (e.g. '&', '#', 'A') or an individual track card.
 */
public sealed interface SongListItem permits SongListItem.SectionHeader, SongListItem.SongEntry {

    record SectionHeader(String title) implements SongListItem {
    }

    record SongEntry(Track track, boolean isCardBackground) implements SongListItem {
    }
}
