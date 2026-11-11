package com.localify.service;

import com.localify.model.Playlist;
import com.localify.model.Track;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

/** Keeps user playlists as .m3u8 files in ~/.localify/playlists so other players can open them too. */
public class PlaylistService {

    private static final Comparator<Playlist> BY_NAME =
            Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT));
    private static final String ILLEGAL_CHARS = "\\/:*?\"<>|";

    private final Path directory;
    private final ObservableList<Playlist> playlists = FXCollections.observableArrayList();

    public PlaylistService() {
        this(Path.of(System.getProperty("user.home"), ".localify", "playlists"));
    }

    public PlaylistService(Path directory) {
        this.directory = directory;
    }

    public ObservableList<Playlist> getPlaylists() {
        return playlists;
    }

    /**
     * Reads every playlist file. {@code resolver} maps a file path to a Track, so playlist entries
     * share the same objects as the library. Entries whose file no longer exists are dropped.
     */
    public void load(Function<Path, Track> resolver) throws IOException {
        List<Playlist> loaded = new ArrayList<>();
        if (Files.isDirectory(directory)) {
            try (DirectoryStream<Path> files = Files.newDirectoryStream(directory, "*.m3u8")) {
                for (Path file : files) {
                    loaded.add(read(file, resolver));
                }
            }
        }
        loaded.sort(BY_NAME);
        playlists.setAll(loaded);
    }

    /** Returns a reason the name can't be used, or null when it's fine. */
    public String validateName(String rawName) {
        String name = rawName == null ? "" : rawName.strip();
        if (name.isEmpty()) {
            return "Enter a name.";
        }
        for (char c : name.toCharArray()) {
            if (ILLEGAL_CHARS.indexOf(c) >= 0 || c < 32) {
                return "Names can't contain \\ / : * ? \" < > |";
            }
        }
        if (name.endsWith(".")) {
            return "Names can't end with a period.";
        }
        if (name.length() > 100) {
            return "Keep the name under 100 characters.";
        }
        boolean taken = playlists.stream().anyMatch(p -> p.getName().equalsIgnoreCase(name));
        return taken ? "A playlist with this name already exists." : null;
    }

    public Playlist create(String rawName) throws IOException {
        String problem = validateName(rawName);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        String name = rawName.strip();
        Playlist playlist = new Playlist(name, directory.resolve(name + ".m3u8"), List.of());
        save(playlist);
        playlists.add(playlist);
        FXCollections.sort(playlists, BY_NAME);
        return playlist;
    }

    public void delete(Playlist playlist) throws IOException {
        Files.deleteIfExists(playlist.getFile());
        if (playlist.getCustomCoverPath() != null) {
            try {
                Files.deleteIfExists(playlist.getCustomCoverPath());
            } catch (Exception ignored) {}
        }
        playlists.remove(playlist);
    }

    public void setCustomCover(Playlist playlist, Path sourceImage) throws IOException {
        if (sourceImage == null) {
            playlist.setCustomCoverPath(null);
            Path coversDir = directory.resolve("covers");
            if (Files.isDirectory(coversDir)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(coversDir, playlist.getName() + ".*")) {
                    for (Path p : ds) {
                        Files.deleteIfExists(p);
                    }
                } catch (Exception ignored) {}
            }
            save(playlist);
            return;
        }

        Path coversDir = directory.resolve("covers");
        Files.createDirectories(coversDir);
        String ext = "png";
        String fn = sourceImage.getFileName().toString();
        int dot = fn.lastIndexOf('.');
        if (dot > 0 && dot < fn.length() - 1) {
            ext = fn.substring(dot + 1).toLowerCase(Locale.ROOT);
        }
        Path target = coversDir.resolve(playlist.getName() + "." + ext);
        Files.copy(sourceImage, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        playlist.setCustomCoverPath(target);
        save(playlist);
    }

    public void rename(Playlist playlist, String rawNewName) throws IOException {
        String problem = validateName(rawNewName);
        if (problem != null) {
            throw new IllegalArgumentException(problem);
        }
        String newName = rawNewName.strip();
        if (newName.equalsIgnoreCase(playlist.getName())) {
            return;
        }
        Path oldFile = playlist.getFile();
        Path newFile = directory.resolve(newName + ".m3u8");
        Files.deleteIfExists(newFile);
        Files.move(oldFile, newFile);

        if (playlist.getCustomCoverPath() != null && Files.isRegularFile(playlist.getCustomCoverPath())) {
            Path oldCover = playlist.getCustomCoverPath();
            String fn = oldCover.getFileName().toString();
            int dot = fn.lastIndexOf('.');
            String ext = dot > 0 ? fn.substring(dot) : ".png";
            Path newCover = directory.resolve("covers").resolve(newName + ext);
            try {
                Files.move(oldCover, newCover);
                playlist.setCustomCoverPath(newCover);
            } catch (Exception ignored) {}
        }

        playlist.setName(newName);
        playlist.setFile(newFile);
        save(playlist);
        FXCollections.sort(playlists, BY_NAME);
    }

    public void add(Playlist playlist, List<Track> tracks) throws IOException {
        playlist.getTracks().addAll(tracks);
        save(playlist);
    }

    public void remove(Playlist playlist, Track track) throws IOException {
        playlist.getTracks().remove(track);
        save(playlist);
    }

    public void save(Playlist playlist) throws IOException {
        Files.createDirectories(directory);
        StringBuilder out = new StringBuilder("#EXTM3U\n");
        if (playlist.getCustomCoverPath() != null) {
            out.append("#LOCALIFY_COVER:").append(playlist.getCustomCoverPath().toAbsolutePath()).append('\n');
        }
        for (Track t : playlist.getTracks()) {
            out.append("#EXTINF:").append((long) t.getDuration()).append(',')
                    .append(t.getDisplayArtist()).append(" - ").append(t.getTitle()).append('\n')
                    .append(t.getPath().toAbsolutePath()).append('\n');
        }
        Files.writeString(playlist.getFile(), out, StandardCharsets.UTF_8);
    }

    private static Playlist read(Path file, Function<Path, Track> resolver) throws IOException {
        String fileName = file.getFileName().toString();
        String name = fileName.substring(0, fileName.length() - ".m3u8".length());
        List<Track> tracks = new ArrayList<>();
        Path customCover = null;
        for (String raw : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            String line = raw.replace("\uFEFF", "").strip();
            if (line.startsWith("#LOCALIFY_COVER:")) {
                String cp = line.substring("#LOCALIFY_COVER:".length()).strip();
                try {
                    Path p = Path.of(cp);
                    if (Files.isRegularFile(p)) {
                        customCover = p;
                    }
                } catch (Exception ignored) {}
                continue;
            }
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            Path entry;
            try {
                entry = Path.of(line);
            } catch (InvalidPathException e) {
                continue;
            }
            if (!entry.isAbsolute()) {
                entry = file.getParent().resolve(entry);
            }
            entry = entry.toAbsolutePath().normalize();
            if (Files.isRegularFile(entry)) {
                Track track = resolver.apply(entry);
                if (track != null) {
                    tracks.add(track);
                }
            }
        }
        if (customCover == null) {
            Path coversDir = file.getParent().resolve("covers");
            if (Files.isDirectory(coversDir)) {
                try (DirectoryStream<Path> ds = Files.newDirectoryStream(coversDir, name + ".*")) {
                    for (Path cp : ds) {
                        customCover = cp;
                        break;
                    }
                } catch (Exception ignored) {}
            }
        }
        return new Playlist(name, file, customCover, tracks);
    }
}
