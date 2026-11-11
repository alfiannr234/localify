package com.localify.service;

import com.localify.model.Track;
import javafx.concurrent.Task;
import javafx.scene.image.Image;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.audio.AudioHeader;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Finds audio files in the Music folder and reads/writes their tags with JAudioTagger. */
public class MusicScannerService {

    public static final Set<String> SUPPORTED_EXTENSIONS = Set.of("mp3", "wav", "flac", "m4a");

    /** "Artist - Title" in a file name, used only when the file has no tags. */
    private static final Pattern ARTIST_TITLE = Pattern.compile("^(.+?)\\s+[-\u2013]\\s+(.+)$");

    // JAudioTagger logs every odd frame it meets at INFO/WARNING; the reference keeps the level from being GC'd.
    private static final Logger JAUDIOTAGGER_LOG = Logger.getLogger("org.jaudiotagger");

    static {
        JAUDIOTAGGER_LOG.setLevel(Level.OFF);
    }

    public Path getDefaultMusicDirectory() {
        return Path.of(System.getProperty("user.home"), "Music");
    }

    public Task<List<Track>> createScanTask(Path directory) {
        return new Task<>() {
            @Override
            protected List<Track> call() throws Exception {
                updateMessage("Looking for audio files in " + directory);
                if (!Files.isDirectory(directory)) {
                    throw new NoSuchFileException(directory.toString());
                }
                List<Path> files = findAudioFiles(directory);
                int total = files.size();
                if (total == 0) {
                    return List.of();
                }
                Track[] trackArray = new Track[total];
                java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger(0);
                int parallelism = Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors()));
                java.util.concurrent.ForkJoinPool pool = new java.util.concurrent.ForkJoinPool(parallelism);
                try {
                    pool.submit(() -> java.util.stream.IntStream.range(0, total).parallel().forEach(i -> {
                        if (isCancelled()) {
                            return;
                        }
                        trackArray[i] = readTrack(files.get(i));
                        int c = count.incrementAndGet();
                        if (c % 15 == 0 || c == total) {
                            updateProgress(c, total);
                            updateMessage("Reading tags: " + c + " of " + total + " files");
                        }
                    })).get();
                } finally {
                    pool.shutdown();
                }
                List<Track> tracks = new ArrayList<>(total);
                for (Track t : trackArray) {
                    if (t != null) {
                        tracks.add(t);
                    }
                }
                return tracks;
            }
        };
    }

    /** Walks the folder tree; unreadable sub-folders are skipped instead of aborting the scan. */
    public List<Path> findAudioFiles(Path directory) throws IOException {
        List<Path> found = new ArrayList<>();
        Files.walkFileTree(directory, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.isRegularFile() && isSupported(file)) {
                            found.add(file.toAbsolutePath().normalize());
                        }
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path file, IOException exc) {
                        return FileVisitResult.CONTINUE;
                    }
                });
        found.sort(Comparator.comparing(p -> p.toString().toLowerCase(Locale.ROOT)));
        return found;
    }

    public static boolean isSupported(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 && SUPPORTED_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /** Never throws: a file with broken tags still becomes a track named after the file. */
    public Track readTrack(Path file) {
        String title = "";
        String artist = "";
        String album = "";
        String genre = "";
        String year = "";
        long dateAdded = System.currentTimeMillis();
        double duration = 0;
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            dateAdded = attrs.creationTime().toMillis();
            if (dateAdded <= 0) {
                dateAdded = attrs.lastModifiedTime().toMillis();
            }
        } catch (Exception ignored) {
        }

        try {
            AudioFile audioFile = AudioFileIO.read(file.toFile());
            Tag tag = audioFile.getTag();
            if (tag != null) {
                title = safeFirst(tag, FieldKey.TITLE);
                artist = safeFirst(tag, FieldKey.ARTIST);
                album = safeFirst(tag, FieldKey.ALBUM);
                genre = safeFirst(tag, FieldKey.GENRE);
                year = safeFirst(tag, FieldKey.YEAR);
            }
            AudioHeader header = audioFile.getAudioHeader();
            if (header != null) {
                duration = header.getTrackLength();
            }
        } catch (Exception | LinkageError ignored) {
            // Unreadable or unknown container: fall back to the file name below.
        }

        if (title.isBlank()) {
            String baseName = baseName(file);
            Matcher m = ARTIST_TITLE.matcher(baseName);
            if (m.matches()) {
                title = m.group(2);
                if (artist.isBlank()) {
                    artist = m.group(1);
                }
            } else {
                title = baseName;
            }
        }
        return new Track(file, title, artist, album, genre, year, dateAdded, duration);
    }

    private final AutoCoverArtService autoCoverArtService = new AutoCoverArtService();

    /** Returns the embedded picture, or companion picture, or generates procedural art. */
    public byte[] readArtwork(Path file) {
        try {
            Tag tag = AudioFileIO.read(file.toFile()).getTag();
            if (tag != null) {
                Artwork artwork = tag.getFirstArtwork();
                if (artwork != null && artwork.getBinaryData() != null && artwork.getBinaryData().length > 0) {
                    return artwork.getBinaryData();
                }
            }
        } catch (Exception | LinkageError ignored) {
        }

        // Check companion artwork files in directory
        try {
            String fileName = file.getFileName().toString();
            String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;
            List<Path> candidates = List.of(
                    file.resolveSibling(baseName + ".cover.jpg"),
                    file.resolveSibling(baseName + ".jpg"),
                    file.resolveSibling(baseName + ".png"),
                    file.resolveSibling("cover.jpg"),
                    file.resolveSibling("folder.jpg")
            );
            for (Path c : candidates) {
                if (Files.isRegularFile(c)) {
                    return Files.readAllBytes(c);
                }
            }
        } catch (Exception ignored) {
        }

        // Generate procedural cover art as foolproof fallback
        String baseName = baseName(file);
        return autoCoverArtService.generateProceduralCover(baseName, "Localify");
    }

    public void writeTags(Path file, String title, String artist, String album, String genre, String year) throws Exception {
        AudioFile audioFile = AudioFileIO.read(file.toFile());
        Tag tag = audioFile.getTagOrCreateAndSetDefault();
        setOrClear(tag, FieldKey.TITLE, title);
        setOrClear(tag, FieldKey.ARTIST, artist);
        setOrClear(tag, FieldKey.ALBUM, album);
        setOrClear(tag, FieldKey.GENRE, genre);
        setOrClear(tag, FieldKey.YEAR, year);
        audioFile.commit();
    }

    public void writeTags(Path file, String title, String artist, String album) throws Exception {
        writeTags(file, title, artist, album, "", "");
    }

    /** File and stream details for the Properties dialog, in display order. */
    public Map<String, String> readProperties(Track track) {
        Map<String, String> props = new LinkedHashMap<>();
        Path file = track.getPath();
        props.put("Artist", track.getDisplayArtist());
        props.put("Album", track.getDisplayAlbum());
        props.put("Genre", track.getDisplayGenre());
        props.put("Year", track.getDisplayYear());
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            props.put("Size", humanSize(attrs.size()));
            props.put("Modified", DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH)
                    .withZone(ZoneId.systemDefault())
                    .format(attrs.lastModifiedTime().toInstant()));
        } catch (IOException e) {
            props.put("Size", "Unavailable (" + describe(e) + ")");
        }
        try {
            AudioFile audioFile = AudioFileIO.read(file.toFile());
            AudioHeader header = audioFile.getAudioHeader();
            props.put("Format", header.getFormat());
            props.put("Encoding", header.getEncodingType());
            props.put("Bitrate", header.getBitRate() + " kbps" + (header.isVariableBitRate() ? " (variable)" : ""));
            props.put("Sample rate", header.getSampleRate() + " Hz");
            props.put("Channels", header.getChannels());
            props.put("Length", Track.formatTime(header.getTrackLength()));
            Tag tag = audioFile.getTag();
            props.put("Cover art", tag != null && tag.getFirstArtwork() != null ? "Embedded" : "None");
        } catch (Exception | LinkageError e) {
            props.put("Audio details", "Couldn't read the tags: " + e.getMessage());
        }
        return props;
    }

    public static String describe(Throwable error) {
        if (error instanceof NoSuchFileException e) {
            return "The folder " + e.getFile() + " doesn't exist.";
        }
        if (error instanceof AccessDeniedException e) {
            return "Localify doesn't have permission to read " + e.getFile() + ".";
        }
        return error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
    }

    private static void setOrClear(Tag tag, FieldKey key, String value) throws Exception {
        if (value == null || value.isBlank()) {
            tag.deleteField(key);
        } else {
            tag.setField(key, value.strip());
        }
    }

    private static String safeFirst(Tag tag, FieldKey key) {
        try {
            String value = tag.getFirst(key);
            return value == null ? "" : value.strip();
        } catch (RuntimeException e) {
            return "";
        }
    }

    private static String baseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(Locale.ENGLISH, "%.1f KB", kb);
        }
        return String.format(Locale.ENGLISH, "%.1f MB", kb / 1024.0);
    }
}
