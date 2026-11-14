package com.localify.service;

import com.localify.model.LyricLine;
import com.localify.model.Track;
import javafx.animation.AnimationTimer;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.IntegerProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyIntegerProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleIntegerProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.image.Image;
import javafx.scene.media.AudioEqualizer;
import javafx.scene.media.EqualizerBand;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Wraps JavaFX {@link MediaPlayer} together with playback queue, shuffle/repeat state,
 * embedded cover art, and synchronized {@code .lrc} lyric tracking.
 */
public class AudioService {

    public enum RepeatMode {
        OFF, ALL, ONE
    }

    @FunctionalInterface
    public interface ThrowingAction {
        void run() throws Exception;
    }

    private final MusicScannerService scannerService;
    private final LrcParserService lrcParserService;
    private DiscordRpcService discordRpcService;

    private final ObservableList<Track> queue = FXCollections.observableArrayList();
    private final ObservableList<Track> upcomingTracks = FXCollections.observableArrayList();
    private final List<Track> unshuffledOrder = new ArrayList<>();

    private final ObjectProperty<Track> currentTrack = new SimpleObjectProperty<>(this, "currentTrack", null);
    private final ObjectProperty<Image> currentArtwork = new SimpleObjectProperty<>(this, "currentArtwork", null);
    private final IntegerProperty currentIndex = new SimpleIntegerProperty(this, "currentIndex", -1);
    private final BooleanProperty playing = new SimpleBooleanProperty(this, "playing", false);
    private final DoubleProperty currentTimeSeconds = new SimpleDoubleProperty(this, "currentTimeSeconds", 0);
    private final DoubleProperty totalDurationSeconds = new SimpleDoubleProperty(this, "totalDurationSeconds", 0);
    private final DoubleProperty volume = new SimpleDoubleProperty(this, "volume", 0.8);
    private final BooleanProperty muted = new SimpleBooleanProperty(this, "muted", false);
    private final BooleanProperty shuffle = new SimpleBooleanProperty(this, "shuffle", false);
    private final ObjectProperty<RepeatMode> repeatMode = new SimpleObjectProperty<>(this, "repeatMode", RepeatMode.OFF);
    private final StringProperty errorMessage = new SimpleStringProperty(this, "errorMessage", "");

    private final ObservableList<LyricLine> currentLyrics = FXCollections.observableArrayList();
    private final IntegerProperty activeLyricIndex = new SimpleIntegerProperty(this, "activeLyricIndex", -1);
    private final StringProperty lyricStatusMessage = new SimpleStringProperty(
            this, "lyricStatusMessage", "Select a track to load synchronized lyrics.");

    private SettingsService settingsService;
    private final DoubleProperty lyricsOffsetSeconds = new SimpleDoubleProperty(
            this, "lyricsOffsetSeconds", SettingsService.DEFAULT_LYRICS_OFFSET);
    private AnimationTimer lyricSyncTimer;
    private long lastAnchorNano = 0;
    private double lastAnchorSecs = 0;

    private final DoubleProperty playbackRate = new SimpleDoubleProperty(this, "playbackRate", 1.0);
    private final BooleanProperty equalizerEnabled = new SimpleBooleanProperty(this, "equalizerEnabled", true);
    private final double[] equalizerGains = new double[10];
    private String equalizerPreset = "Flat";

    private MediaPlayer mediaPlayer;
    private double volumeBeforeMute = 0.8;

    public AudioService(MusicScannerService scannerService, LrcParserService lrcParserService) {
        this.scannerService = scannerService;
        this.lrcParserService = lrcParserService;

        volume.addListener((obs, oldVal, newVal) -> {
            double clamped = clamp(newVal.doubleValue(), 0.0, 1.0);
            if (clamped > 0 && muted.get()) {
                muted.set(false);
            }
            if (clamped > 0) {
                volumeBeforeMute = clamped;
            }
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(clamped);
            }
        });

        muted.addListener((obs, oldVal, isMuted) -> {
            if (mediaPlayer != null) {
                mediaPlayer.setMute(isMuted);
            }
        });

        playing.addListener((obs, oldV, isPlaying) -> {
            if (isPlaying) {
                startSyncTimer();
            } else {
                stopSyncTimer();
            }
            notifyDiscord();
        });
        currentTrack.addListener((obs, oldT, newT) -> notifyDiscord());
    }

    public void setDiscordRpcService(DiscordRpcService discordRpcService) {
        this.discordRpcService = discordRpcService;
    }

    private void notifyDiscord() {
        if (discordRpcService != null) {
            Track t = currentTrack.get();
            if (t != null) {
                discordRpcService.updatePresence(t, playing.get(), currentTimeSeconds.get(), totalDurationSeconds.get());
            } else {
                discordRpcService.clearPresence();
            }
        }
    }

    /** Sets the playback queue from a library/playlist view and starts playing {@code target}. */
    public void playFromContext(List<Track> contextTracks, Track target) {
        if (target == null) {
            return;
        }
        List<Track> source = (contextTracks == null || contextTracks.isEmpty())
                ? List.of(target)
                : new ArrayList<>(contextTracks);
        if (!source.contains(target)) {
            source = new ArrayList<>(source);
            source.add(0, target);
        }

        unshuffledOrder.clear();
        unshuffledOrder.addAll(source);

        if (shuffle.get()) {
            List<Track> rest = new ArrayList<>(source);
            rest.remove(target);
            Collections.shuffle(rest);
            List<Track> ordered = new ArrayList<>(source.size());
            ordered.add(target);
            ordered.addAll(rest);
            queue.setAll(ordered);
            playAtIndex(0, true);
        } else {
            queue.setAll(source);
            int idx = queue.indexOf(target);
            playAtIndex(Math.max(0, idx), true);
        }
    }

    public void playTrack(Track track) {
        if (track == null) {
            return;
        }
        int existing = queue.indexOf(track);
        if (existing >= 0) {
            playAtIndex(existing, true);
        } else {
            playFromContext(List.of(track), track);
        }
    }

    /** Inserts {@code track} immediately after the active song, or plays it now if nothing is loaded. */
    public void playNext(Track track) {
        if (track == null) {
            return;
        }
        if (currentTrack.get() == null || currentIndex.get() < 0) {
            playFromContext(List.of(track), track);
            return;
        }
        int insertAt = Math.min(currentIndex.get() + 1, queue.size());
        // Avoid duplicate right after current when the track exists further down the upcoming list.
        for (int i = queue.size() - 1; i > currentIndex.get(); i--) {
            if (queue.get(i).equals(track)) {
                queue.remove(i);
            }
        }
        insertAt = Math.min(currentIndex.get() + 1, queue.size());
        queue.add(insertAt, track);
        if (!unshuffledOrder.contains(track)) {
            unshuffledOrder.add(track);
        }
        refreshUpcoming();
    }

    /** Appends {@code track} to the end of the active queue, or loads and plays it if idle. */
    public void addToQueue(Track track) {
        if (track == null) {
            return;
        }
        if (currentTrack.get() == null || currentIndex.get() < 0) {
            playFromContext(List.of(track), track);
            return;
        }
        queue.add(track);
        if (!unshuffledOrder.contains(track)) {
            unshuffledOrder.add(track);
        }
        refreshUpcoming();
    }

    /** Jumps to a specific entry in {@link #getUpcomingTracks()}. */
    public void playUpcomingAt(int upcomingOffset) {
        int targetIndex = currentIndex.get() + 1 + upcomingOffset;
        if (targetIndex >= 0 && targetIndex < queue.size()) {
            playAtIndex(targetIndex, true);
        }
    }

    public void removeUpcomingAt(int upcomingOffset) {
        int targetIndex = currentIndex.get() + 1 + upcomingOffset;
        if (targetIndex >= 0 && targetIndex < queue.size()) {
            Track removed = queue.remove(targetIndex);
            unshuffledOrder.remove(removed);
            refreshUpcoming();
        }
    }

    public void clearUpcoming() {
        int cur = currentIndex.get();
        if (cur >= 0 && cur + 1 < queue.size()) {
            List<Track> removed = new ArrayList<>(queue.subList(cur + 1, queue.size()));
            queue.remove(cur + 1, queue.size());
            unshuffledOrder.removeAll(removed);
            refreshUpcoming();
        }
    }

    /** Removes a track from the playback queue; advances cleanly if it was playing. */
    public void removeFromQueue(Track track) {
        if (track == null) {
            return;
        }
        unshuffledOrder.remove(track);
        int idx = queue.indexOf(track);
        if (idx < 0) {
            return;
        }
        int cur = currentIndex.get();
        queue.remove(idx);
        if (queue.isEmpty()) {
            stopAndClear();
            return;
        }
        if (idx < cur) {
            currentIndex.set(cur - 1);
            refreshUpcoming();
        } else if (idx == cur) {
            boolean wasPlaying = playing.get();
            int nextIdx = Math.min(idx, queue.size() - 1);
            playAtIndex(nextIdx, wasPlaying);
        } else {
            refreshUpcoming();
        }
    }

    public void togglePlayPause() {
        if (mediaPlayer == null) {
            if (!queue.isEmpty()) {
                playAtIndex(Math.max(0, currentIndex.get()), true);
            }
            return;
        }
        if (playing.get()) {
            pause();
        } else {
            play();
        }
    }

    public void play() {
        if (mediaPlayer != null) {
            mediaPlayer.play();
        } else if (!queue.isEmpty()) {
            playAtIndex(Math.max(0, currentIndex.get()), true);
        }
    }

    public void pause() {
        if (mediaPlayer != null) {
            mediaPlayer.pause();
        }
    }

    public void next() {
        if (queue.isEmpty()) {
            return;
        }
        int nextIndex = currentIndex.get() + 1;
        if (nextIndex < queue.size()) {
            playAtIndex(nextIndex, true);
        } else if (repeatMode.get() == RepeatMode.ALL) {
            playAtIndex(0, true);
        } else {
            // Reached end of queue with repeat off: cue first track paused.
            playAtIndex(0, false);
        }
    }

    public void previous() {
        if (queue.isEmpty()) {
            return;
        }
        if (mediaPlayer != null && currentTimeSeconds.get() > 3.0) {
            seek(0);
            return;
        }
        int prevIndex = currentIndex.get() - 1;
        if (prevIndex >= 0) {
            playAtIndex(prevIndex, true);
        } else if (repeatMode.get() == RepeatMode.ALL) {
            playAtIndex(queue.size() - 1, true);
        } else {
            seek(0);
        }
    }

    public void seek(double seconds) {
        if (mediaPlayer == null) {
            return;
        }
        double total = totalDurationSeconds.get();
        double clamped = total > 0 ? clamp(seconds, 0.0, total) : Math.max(0.0, seconds);
        mediaPlayer.seek(Duration.seconds(clamped));
        currentTimeSeconds.set(clamped);
        lastAnchorSecs = clamped;
        lastAnchorNano = System.nanoTime();
        updateActiveLyric(clamped);
        notifyDiscord();
    }

    public void seekBy(double deltaSeconds) {
        seek(currentTimeSeconds.get() + deltaSeconds);
    }

    public void toggleShuffle() {
        boolean enable = !shuffle.get();
        shuffle.set(enable);
        Track active = currentTrack.get();
        if (queue.size() <= 1 || active == null) {
            return;
        }
        if (enable) {
            if (unshuffledOrder.isEmpty()) {
                unshuffledOrder.addAll(queue);
            }
            List<Track> rest = new ArrayList<>(queue);
            rest.remove(active);
            Collections.shuffle(rest);
            List<Track> reordered = new ArrayList<>(queue.size());
            reordered.add(active);
            reordered.addAll(rest);
            queue.setAll(reordered);
            currentIndex.set(0);
        } else if (!unshuffledOrder.isEmpty()) {
            queue.setAll(unshuffledOrder);
            int restored = queue.indexOf(active);
            currentIndex.set(Math.max(0, restored));
        }
        refreshUpcoming();
    }

    public RepeatMode cycleRepeatMode() {
        RepeatMode nextMode = switch (repeatMode.get()) {
            case OFF -> RepeatMode.ALL;
            case ALL -> RepeatMode.ONE;
            case ONE -> RepeatMode.OFF;
        };
        repeatMode.set(nextMode);
        return nextMode;
    }

    public void toggleMute() {
        if (muted.get()) {
            muted.set(false);
            if (volume.get() == 0) {
                volume.set(volumeBeforeMute > 0 ? volumeBeforeMute : 0.8);
            }
        } else {
            if (volume.get() > 0) {
                volumeBeforeMute = volume.get();
            }
            muted.set(true);
        }
    }

    /**
     * Temporarily releases the Windows file handle when editing tags on the active track,
     * then reloads the track at the exact playback timestamp.
     */
    public void withReleasedFile(Track track, ThrowingAction action) throws Exception {
        boolean isActive = track != null && track.equals(currentTrack.get()) && mediaPlayer != null;
        boolean wasPlaying = isActive && playing.get();
        double resumeAt = isActive ? currentTimeSeconds.get() : 0;
        if (isActive) {
            disposePlayer();
        }
        try {
            action.run();
        } finally {
            if (isActive) {
                loadTrackMedia(track, wasPlaying, resumeAt);
            }
        }
    }

    public void refreshArtworkFor(Track track) {
        if (track != null && track.equals(currentTrack.get())) {
            loadArtwork(track);
        }
    }

    public void reloadLyrics() {
        Track active = currentTrack.get();
        if (active != null) {
            loadLyrics(active);
            updateActiveLyric(currentTimeSeconds.get());
        }
    }

    public void applyCustomLrc(Track track, Path customLrcFile) throws IOException {
        if (track == null || customLrcFile == null || !java.nio.file.Files.isRegularFile(customLrcFile)) {
            return;
        }
        Path destination = track.getPath().resolveSibling(track.getBaseName() + ".lrc");
        java.nio.file.Files.copy(customLrcFile, destination, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        if (track.equals(currentTrack.get())) {
            reloadLyrics();
        }
    }

    public void stopAndClear() {
        disposePlayer();
        queue.clear();
        upcomingTracks.clear();
        unshuffledOrder.clear();
        currentIndex.set(-1);
        currentTrack.set(null);
        currentArtwork.set(null);
        currentTimeSeconds.set(0);
        totalDurationSeconds.set(0);
        currentLyrics.clear();
        activeLyricIndex.set(-1);
        lyricStatusMessage.set("Select a track to load synchronized lyrics.");
        if (discordRpcService != null) {
            discordRpcService.clearPresence();
        }
    }

    public void shutdown() {
        disposePlayer();
        if (discordRpcService != null) {
            discordRpcService.shutdown();
        }
    }

    private void playAtIndex(int index, boolean autoPlay) {
        if (index < 0 || index >= queue.size()) {
            return;
        }
        Track track = queue.get(index);
        currentIndex.set(index);
        currentTrack.set(track);
        errorMessage.set("");
        refreshUpcoming();
        loadArtwork(track);
        loadLyrics(track);
        loadTrackMedia(track, autoPlay, 0);
    }

    private void loadTrackMedia(Track track, boolean autoPlay, double startSeconds) {
        disposePlayer();
        Path path = track.getPath();
        if (!Files.isRegularFile(path)) {
            playing.set(false);
            errorMessage.set("File not found on disk: " + path.getFileName());
            return;
        }

        currentTimeSeconds.set(startSeconds);
        totalDurationSeconds.set(track.getDuration());

        try {
            Media media = new Media(path.toUri().toString());
            media.setOnError(() -> handleMediaFailure(track, media.getError()));

            MediaPlayer player = new MediaPlayer(media);
            this.mediaPlayer = player;
            player.setVolume(clamp(volume.get(), 0.0, 1.0));
            player.setMute(muted.get());
            player.setRate(playbackRate.get());
            applyEqualizerSettings(player);

            player.statusProperty().addListener((obs, oldStatus, status) ->
                    playing.set(status == MediaPlayer.Status.PLAYING));

            player.setOnReady(() -> {
                Duration mediaDuration = media.getDuration();
                if (mediaDuration != null && !mediaDuration.isUnknown() && !mediaDuration.isIndefinite()) {
                    double secs = mediaDuration.toSeconds();
                    if (secs > 0) {
                        totalDurationSeconds.set(secs);
                        if (track.getDuration() <= 0) {
                            track.setDuration(secs);
                        }
                    }
                }
                if (startSeconds > 0) {
                    player.seek(Duration.seconds(startSeconds));
                }
                if (autoPlay) {
                    player.play();
                }
            });

            player.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (newTime != null && !newTime.isUnknown()) {
                    double secs = Math.max(0.0, newTime.toSeconds());
                    currentTimeSeconds.set(secs);
                    lastAnchorSecs = secs;
                    lastAnchorNano = System.nanoTime();
                    updateActiveLyric(secs);
                }
            });

            player.setOnEndOfMedia(this::handleEndOfMedia);
            player.setOnError(() -> handleMediaFailure(track, player.getError()));

            if (autoPlay) {
                player.play();
            }
        } catch (RuntimeException ex) {
            handleMediaFailure(track, ex);
        }
    }

    private void handleEndOfMedia() {
        if (repeatMode.get() == RepeatMode.ONE) {
            seek(0);
            play();
            return;
        }
        next();
    }

    private void handleMediaFailure(Track track, Throwable error) {
        Platform.runLater(() -> {
            playing.set(false);
            String ext = track.getExtension().toUpperCase();
            if (error instanceof MediaException me
                    && me.getType() == MediaException.Type.MEDIA_UNSUPPORTED) {
                errorMessage.set("JavaFX Media on this system cannot decode " + ext
                        + " (" + track.getFileName() + "). Convert to MP3, WAV, or AAC/M4A to play.");
            } else {
                String detail = error != null && error.getMessage() != null
                        ? error.getMessage()
                        : "Unsupported or corrupted audio stream.";
                errorMessage.set("Could not play \"" + track.getTitle() + "\": " + detail);
            }
        });
    }

    private void loadArtwork(Track track) {
        byte[] bytes = scannerService.readArtwork(track.getPath());
        if (bytes == null || bytes.length == 0) {
            currentArtwork.set(null);
            return;
        }
        Image img = new Image(new ByteArrayInputStream(bytes), 256, 256, true, true);
        currentArtwork.set(img.isError() ? null : img);
    }

    private void loadLyrics(Track track) {
        currentLyrics.clear();
        activeLyricIndex.set(-1);
        if (settingsService != null && track != null) {
            this.lyricsOffsetSeconds.set(settingsService.getEffectiveLyricsOffset(track));
        }
        Optional<Path> lrc = lrcParserService.findLrcFor(track.getPath());
        if (lrc.isEmpty()) {
            lyricStatusMessage.set("Searching online synced lyrics...");
            lrcParserService.autoFetchAndSaveLrc(track).thenAccept(optPath -> {
                javafx.application.Platform.runLater(() -> {
                    if (track.equals(currentTrack.get())) {
                        if (optPath.isPresent()) {
                            try {
                                List<LyricLine> parsed = lrcParserService.parse(optPath.get());
                                currentLyrics.setAll(parsed);
                                lyricStatusMessage.set("");
                            } catch (Exception e) {
                                lyricStatusMessage.set("Could not read lyrics: " + e.getMessage());
                            }
                        } else {
                            lyricStatusMessage.set("No .lrc file found. Place \"" + track.getBaseName()
                                    + ".lrc\" next to the audio file for synced lyrics.");
                        }
                    }
                });
            });
            return;
        }
        try {
            List<LyricLine> parsed = lrcParserService.parse(lrc.get());
            if (parsed.isEmpty()) {
                lyricStatusMessage.set("Searching online synced lyrics...");
                lrcParserService.autoFetchAndSaveLrc(track).thenAccept(optPath -> {
                    javafx.application.Platform.runLater(() -> {
                        if (track.equals(currentTrack.get()) && optPath.isPresent()) {
                            try {
                                List<LyricLine> reParsed = lrcParserService.parse(optPath.get());
                                if (!reParsed.isEmpty()) {
                                    currentLyrics.setAll(reParsed);
                                    lyricStatusMessage.set("");
                                }
                            } catch (Exception ignored) {}
                        }
                    });
                });
            } else {
                currentLyrics.setAll(parsed);
                lyricStatusMessage.set("");
            }
        } catch (Exception e) {
            lyricStatusMessage.set("Could not read \"" + lrc.get().getFileName() + "\": " + e.getMessage());
        }
    }

    private void updateActiveLyric(double seconds) {
        if (currentLyrics.isEmpty()) {
            if (activeLyricIndex.get() != -1) {
                activeLyricIndex.set(-1);
            }
            return;
        }
        int low = 0;
        int high = currentLyrics.size() - 1;
        int found = -1;
        double targetSeconds = seconds + lyricsOffsetSeconds.get();
        while (low <= high) {
            int mid = (low + high) >>> 1;
            if (currentLyrics.get(mid).timeSeconds() <= targetSeconds) {
                found = mid;
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        if (found != activeLyricIndex.get()) {
            activeLyricIndex.set(found);
        }
    }

    private void startSyncTimer() {
        lastAnchorNano = System.nanoTime();
        lastAnchorSecs = currentTimeSeconds.get();
        if (lyricSyncTimer == null) {
            try {
                lyricSyncTimer = new AnimationTimer() {
                    @Override
                    public void handle(long now) {
                        if (mediaPlayer == null || !playing.get()) {
                            return;
                        }
                        if (lastAnchorNano > 0) {
                            double elapsed = (now - lastAnchorNano) / 1_000_000_000.0 * playbackRate.get();
                            double preciseSecs = Math.max(0.0, lastAnchorSecs + elapsed);
                            updateActiveLyric(preciseSecs);
                        }
                    }
                };
            } catch (Exception ignored) {
            }
        }
        if (lyricSyncTimer != null) {
            lyricSyncTimer.start();
        }
    }

    private void stopSyncTimer() {
        if (lyricSyncTimer != null) {
            lyricSyncTimer.stop();
        }
        lastAnchorNano = 0;
    }

    private void refreshUpcoming() {
        int cur = currentIndex.get();
        if (cur >= 0 && cur + 1 < queue.size()) {
            upcomingTracks.setAll(queue.subList(cur + 1, queue.size()));
        } else {
            upcomingTracks.clear();
        }
    }

    private void disposePlayer() {
        stopSyncTimer();
        if (mediaPlayer != null) {
            try {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            } catch (RuntimeException ignored) {
                // MediaPlayer may already be in DISPOSED/HALTED state.
            }
            mediaPlayer = null;
        }
        playing.set(false);
    }

    private static double clamp(double val, double min, double max) {
        return Math.max(min, Math.min(max, val));
    }

    public ObservableList<Track> getQueue() {
        return queue;
    }

    public ObservableList<Track> getUpcomingTracks() {
        return upcomingTracks;
    }

    public ObservableList<LyricLine> getCurrentLyrics() {
        return currentLyrics;
    }

    public Track getCurrentTrack() {
        return currentTrack.get();
    }

    public ReadOnlyObjectProperty<Track> currentTrackProperty() {
        return currentTrack;
    }

    public ReadOnlyObjectProperty<Image> currentArtworkProperty() {
        return currentArtwork;
    }

    public ReadOnlyIntegerProperty currentIndexProperty() {
        return currentIndex;
    }

    public ReadOnlyBooleanProperty playingProperty() {
        return playing;
    }

    public ReadOnlyDoubleProperty currentTimeSecondsProperty() {
        return currentTimeSeconds;
    }

    public ReadOnlyDoubleProperty totalDurationSecondsProperty() {
        return totalDurationSeconds;
    }

    public DoubleProperty volumeProperty() {
        return volume;
    }

    public BooleanProperty mutedProperty() {
        return muted;
    }

    public BooleanProperty shuffleProperty() {
        return shuffle;
    }

    public ObjectProperty<RepeatMode> repeatModeProperty() {
        return repeatMode;
    }

    public ReadOnlyStringProperty errorMessageProperty() {
        return errorMessage;
    }

    public void clearError() {
        errorMessage.set("");
    }

    public ReadOnlyIntegerProperty activeLyricIndexProperty() {
        return activeLyricIndex;
    }

    public ReadOnlyStringProperty lyricStatusMessageProperty() {
        return lyricStatusMessage;
    }

    public void setSettingsService(SettingsService settingsService) {
        this.settingsService = settingsService;
        if (settingsService != null) {
            Track active = currentTrack.get();
            if (active != null) {
                this.lyricsOffsetSeconds.set(settingsService.getEffectiveLyricsOffset(active));
            } else {
                this.lyricsOffsetSeconds.set(settingsService.getLyricsOffset());
            }
        }
    }

    public SettingsService getSettingsService() {
        return settingsService;
    }

    public DoubleProperty lyricsOffsetSecondsProperty() {
        return lyricsOffsetSeconds;
    }

    public double getLyricsOffsetSeconds() {
        return lyricsOffsetSeconds.get();
    }

    public void setLyricsOffsetSeconds(double offset) {
        double clamped = Math.max(-5.0, Math.min(5.0, Math.round(offset * 100.0) / 100.0));
        this.lyricsOffsetSeconds.set(clamped);
        Track active = currentTrack.get();
        if (settingsService != null) {
            if (active != null) {
                settingsService.setTrackLyricsOffset(active, clamped);
            } else {
                settingsService.setLyricsOffset(clamped);
            }
        }
        double current = currentTimeSeconds.get();
        if (lastAnchorNano > 0) {
            double elapsed = (System.nanoTime() - lastAnchorNano) / 1_000_000_000.0 * playbackRate.get();
            current = Math.max(0.0, lastAnchorSecs + elapsed);
        }
        activeLyricIndex.set(-1);
        updateActiveLyric(current);
    }

    public void setTrackLyricsOffsetSeconds(Track track, double offset) {
        double clamped = Math.max(-5.0, Math.min(5.0, Math.round(offset * 100.0) / 100.0));
        if (settingsService != null && track != null) {
            settingsService.setTrackLyricsOffset(track, clamped);
        }
        if (track != null && track.equals(currentTrack.get())) {
            this.lyricsOffsetSeconds.set(clamped);
            activeLyricIndex.set(-1);
            updateActiveLyric(currentTimeSeconds.get());
        }
    }

    public void resetTrackLyricsOffset(Track track) {
        if (settingsService != null && track != null) {
            settingsService.removeTrackLyricsOffset(track);
        }
        if (track != null && track.equals(currentTrack.get())) {
            double fallback = settingsService != null ? settingsService.getLyricsOffset() : SettingsService.DEFAULT_LYRICS_OFFSET;
            this.lyricsOffsetSeconds.set(fallback);
            activeLyricIndex.set(-1);
            updateActiveLyric(currentTimeSeconds.get());
        }
    }

    public void setPlaybackRate(double rate) {
        double clamped = clamp(rate, 0.25, 4.0);
        playbackRate.set(clamped);
        if (mediaPlayer != null) {
            mediaPlayer.setRate(clamped);
        }
    }

    public double getPlaybackRate() {
        return playbackRate.get();
    }

    public DoubleProperty playbackRateProperty() {
        return playbackRate;
    }

    private void applyEqualizerSettings(MediaPlayer player) {
        if (player == null) return;
        try {
            AudioEqualizer eq = player.getAudioEqualizer();
            if (eq != null) {
                eq.setEnabled(equalizerEnabled.get());
                var bands = eq.getBands();
                for (int i = 0; i < Math.min(bands.size(), equalizerGains.length); i++) {
                    bands.get(i).setGain(equalizerGains[i]);
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void setEqualizerBandGain(int bandIndex, double gainDb) {
        if (bandIndex >= 0 && bandIndex < equalizerGains.length) {
            equalizerGains[bandIndex] = clamp(gainDb, -24.0, 12.0);
            if (mediaPlayer != null) {
                applyEqualizerSettings(mediaPlayer);
            }
        }
    }

    public double getEqualizerBandGain(int bandIndex) {
        if (bandIndex >= 0 && bandIndex < equalizerGains.length) {
            return equalizerGains[bandIndex];
        }
        return 0.0;
    }

    public double[] getEqualizerGains() {
        return equalizerGains.clone();
    }

    public void setEqualizerPreset(String presetName) {
        this.equalizerPreset = presetName != null ? presetName : "Flat";
        double[] gains = switch (equalizerPreset.toUpperCase()) {
            case "BASS BOOST" -> new double[]{6.0, 5.5, 4.0, 2.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0};
            case "TREBLE BOOST" -> new double[]{0.0, 0.0, 0.0, 0.0, 0.0, 1.5, 3.0, 5.0, 6.0, 6.0};
            case "ROCK" -> new double[]{4.5, 3.0, 1.5, -0.5, -1.0, 1.0, 2.5, 3.5, 4.5, 4.5};
            case "POP" -> new double[]{-1.5, 1.5, 3.5, 4.0, 3.0, 1.5, 0.0, 1.5, 2.5, 3.0};
            case "VOCAL" -> new double[]{-2.0, -1.0, 1.0, 3.0, 4.5, 4.0, 2.5, 1.0, 0.0, -1.0};
            case "ELECTRONIC" -> new double[]{5.0, 4.5, 2.0, 0.0, -1.5, 1.0, 2.0, 3.5, 4.5, 5.0};
            default -> new double[10]; // Flat
        };
        System.arraycopy(gains, 0, equalizerGains, 0, Math.min(gains.length, equalizerGains.length));
        if (mediaPlayer != null) {
            applyEqualizerSettings(mediaPlayer);
        }
    }

    public String getEqualizerPreset() {
        return equalizerPreset;
    }

    public void setEqualizerEnabled(boolean enabled) {
        equalizerEnabled.set(enabled);
        if (mediaPlayer != null) {
            applyEqualizerSettings(mediaPlayer);
        }
    }

    public boolean isEqualizerEnabled() {
        return equalizerEnabled.get();
    }

    public BooleanProperty equalizerEnabledProperty() {
        return equalizerEnabled;
    }
}
