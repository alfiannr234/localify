package com.localify.model;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.nio.file.Path;
import java.util.List;

/** A named, ordered list of tracks stored as an .m3u8 file with optional custom cover artwork. */
public class Playlist {

    private String name;
    private Path file;
    private Path customCoverPath;
    private final ObservableList<Track> tracks;

    public Playlist(String name, Path file, List<Track> tracks) {
        this(name, file, null, tracks);
    }

    public Playlist(String name, Path file, Path customCoverPath, List<Track> tracks) {
        this.name = name;
        this.file = file;
        this.customCoverPath = customCoverPath;
        this.tracks = FXCollections.observableArrayList(tracks);
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Path getFile() {
        return file;
    }

    public void setFile(Path file) {
        this.file = file;
    }

    public Path getCustomCoverPath() {
        return customCoverPath;
    }

    public void setCustomCoverPath(Path customCoverPath) {
        this.customCoverPath = customCoverPath;
    }

    public ObservableList<Track> getTracks() {
        return tracks;
    }

    @Override
    public String toString() {
        return name;
    }
}

