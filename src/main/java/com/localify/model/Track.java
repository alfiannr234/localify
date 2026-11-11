package com.localify.model;

import com.localify.util.Transliterator;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.LongProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleLongProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.scene.image.Image;

import java.nio.file.Path;
import java.util.Locale;

/**
 * One local audio file plus the tag fields Localify displays and edits.
 * Two tracks are equal when they point at the same file path.
 */
public class Track {

    private final Path path;
    private final StringProperty title = new SimpleStringProperty(this, "title", "");
    private final StringProperty artist = new SimpleStringProperty(this, "artist", "");
    private final StringProperty album = new SimpleStringProperty(this, "album", "");
    private final StringProperty genre = new SimpleStringProperty(this, "genre", "");
    private final StringProperty year = new SimpleStringProperty(this, "year", "");
    private final LongProperty dateAdded = new SimpleLongProperty(this, "dateAdded", 0);
    private final DoubleProperty duration = new SimpleDoubleProperty(this, "duration", 0);

    public Track(Path path, String title, String artist, String album, double durationSeconds) {
        this(path, title, artist, album, "", "", 0, durationSeconds);
    }

    public Track(Path path, String title, String artist, String album, String genre, String year, long dateAdded, double durationSeconds) {
        this.path = path;
        setTitle(title);
        setArtist(artist);
        setAlbum(album);
        setGenre(genre);
        setYear(year);
        setDateAdded(dateAdded);
        setDuration(durationSeconds);
    }

    public Path getPath() {
        return path;
    }

    public String getFileName() {
        return path.getFileName().toString();
    }

    public String getBaseName() {
        String name = getFileName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public String getExtension() {
        String name = getFileName();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    public String getTitle() {
        return title.get();
    }

    public void setTitle(String value) {
        title.set(Transliterator.romanize(clean(value)));
    }

    public StringProperty titleProperty() {
        return title;
    }

    public String getArtist() {
        return artist.get();
    }

    public void setArtist(String value) {
        artist.set(Transliterator.romanize(clean(value)));
    }

    public StringProperty artistProperty() {
        return artist;
    }

    public String getAlbum() {
        return album.get();
    }

    public void setAlbum(String value) {
        album.set(Transliterator.romanize(clean(value)));
    }

    public StringProperty albumProperty() {
        return album;
    }

    public String getGenre() {
        return genre.get();
    }

    public void setGenre(String value) {
        genre.set(clean(value));
    }

    public StringProperty genreProperty() {
        return genre;
    }

    public String getYear() {
        return year.get();
    }

    public void setYear(String value) {
        year.set(clean(value));
    }

    public StringProperty yearProperty() {
        return year;
    }

    public long getDateAdded() {
        return dateAdded.get();
    }

    public void setDateAdded(long value) {
        dateAdded.set(value);
    }

    public LongProperty dateAddedProperty() {
        return dateAdded;
    }

    public double getDuration() {
        return duration.get();
    }

    public void setDuration(double seconds) {
        duration.set(Double.isFinite(seconds) && seconds > 0 ? seconds : 0);
    }

    public DoubleProperty durationProperty() {
        return duration;
    }

    public boolean hasArtist() {
        return !getArtist().isBlank();
    }

    public boolean hasAlbum() {
        return !getAlbum().isBlank();
    }

    public boolean hasGenre() {
        return !getGenre().isBlank();
    }

    public boolean hasYear() {
        return !getYear().isBlank();
    }

    public String getDisplayArtist() {
        return hasArtist() ? getArtist() : "Unknown artist";
    }

    public String getDisplayAlbum() {
        return hasAlbum() ? getAlbum() : "Unknown album";
    }

    public String getDisplayGenre() {
        return hasGenre() ? getGenre() : "Unknown genre";
    }

    public String getDisplayYear() {
        return hasYear() ? getYear() : "Unknown year";
    }

    private Image thumbnail;

    public Image getThumbnail() {
        return thumbnail;
    }

    public void setThumbnail(Image thumbnail) {
        this.thumbnail = thumbnail;
    }

    /** Formats seconds as m:ss, or h:mm:ss for long files. */
    public static String formatTime(double seconds) {
        if (!(seconds > 0) || Double.isInfinite(seconds)) {
            return "0:00";
        }
        long total = (long) Math.floor(seconds);
        long hours = total / 3600;
        long minutes = (total % 3600) / 60;
        long secs = total % 60;
        return hours > 0
                ? String.format("%d:%02d:%02d", hours, minutes, secs)
                : String.format("%02d:%02d", minutes, secs);
    }

    private static String clean(String value) {
        return value == null ? "" : value.strip();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Track track && path.equals(track.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }

    @Override
    public String toString() {
        return getTitle();
    }
}
