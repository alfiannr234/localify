package com.localify.service;

import com.localify.model.Track;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/**
 * Persists user application preferences such as Discord RPC enablement.
 */
public class SettingsService {

    private static final String PREF_DISCORD_RPC = "discord.rpc.enabled";
    private static final String PREF_DISCORD_CLIENT_ID = "discord.rpc.client_id";
    public static final String DEFAULT_CLIENT_ID = "1570070424627191808";

    private static final String PREF_THEME = "app.theme";
    public static final String DEFAULT_THEME = "DARK";

    private static final String PREF_ACCENT = "app.accent";
    public static final String DEFAULT_ACCENT = "ORANGE";

    private static final String PREF_LYRICS_OFFSET = "lyrics.sync.offset";
    public static final double DEFAULT_LYRICS_OFFSET = 0.30; // 300ms lead anticipation to eliminate delay
    private static final String PREF_TRACK_OFFSET_PREFIX = "lyrics.track_offset.";

    private final Path configFile;
    private final Properties properties = new Properties();

    public SettingsService() {
        this(Path.of(System.getProperty("user.home"), ".localify", "config.properties"));
    }

    public SettingsService(Path configFile) {
        this.configFile = configFile;
        load();
    }

    public synchronized void load() {
        if (Files.isRegularFile(configFile)) {
            try (InputStream in = Files.newInputStream(configFile)) {
                properties.load(in);
            } catch (IOException ignored) {
            }
        }
    }

    public synchronized void save() {
        try {
            Files.createDirectories(configFile.getParent());
            try (OutputStream out = Files.newOutputStream(configFile)) {
                properties.store(out, "Localify Settings");
            }
        } catch (IOException ignored) {
        }
    }

    public synchronized boolean isDiscordRpcEnabled() {
        return Boolean.parseBoolean(properties.getProperty(PREF_DISCORD_RPC, "true"));
    }

    public synchronized void setDiscordRpcEnabled(boolean enabled) {
        properties.setProperty(PREF_DISCORD_RPC, String.valueOf(enabled));
        save();
    }

    public synchronized String getDiscordClientId() {
        String val = properties.getProperty(PREF_DISCORD_CLIENT_ID);
        if (val == null || val.isBlank() || val.equals("1338048293888360448") || val.equals("802222525110812725")) {
            return DEFAULT_CLIENT_ID;
        }
        return val.strip();
    }

    public synchronized void setDiscordClientId(String clientId) {
        if (clientId != null && !clientId.isBlank()) {
            properties.setProperty(PREF_DISCORD_CLIENT_ID, clientId.strip());
        } else {
            properties.remove(PREF_DISCORD_CLIENT_ID);
        }
        save();
    }

    public synchronized String getTheme() {
        return properties.getProperty(PREF_THEME, DEFAULT_THEME).toUpperCase();
    }

    public synchronized void setTheme(String theme) {
        if (theme != null && !theme.isBlank()) {
            properties.setProperty(PREF_THEME, theme.strip().toUpperCase());
        } else {
            properties.setProperty(PREF_THEME, DEFAULT_THEME);
        }
        save();
    }

    public synchronized String getAccent() {
        return properties.getProperty(PREF_ACCENT, DEFAULT_ACCENT).toUpperCase();
    }

    public synchronized void setAccent(String accent) {
        if (accent != null && !accent.isBlank()) {
            properties.setProperty(PREF_ACCENT, accent.strip().toUpperCase());
        } else {
            properties.setProperty(PREF_ACCENT, DEFAULT_ACCENT);
        }
        save();
    }

    public synchronized String getAccentHex() {
        return getAccentHex(getAccent());
    }

    public static String getAccentHex(String accent) {
        if (accent == null) return "#F36B21";
        return switch (accent.toUpperCase()) {
            case "GREEN" -> "#1DB954";
            case "BLUE" -> "#0078D4";
            case "PURPLE" -> "#8B5CF6";
            case "RED" -> "#E91E63";
            case "AMBER" -> "#E5A00D";
            default -> "#F36B21";
        };
    }

    public synchronized double getLyricsOffset() {
        String val = properties.getProperty(PREF_LYRICS_OFFSET);
        if (val == null || val.isBlank()) {
            return DEFAULT_LYRICS_OFFSET;
        }
        try {
            return Double.parseDouble(val.strip());
        } catch (NumberFormatException e) {
            return DEFAULT_LYRICS_OFFSET;
        }
    }

    public synchronized void setLyricsOffset(double offsetSeconds) {
        properties.setProperty(PREF_LYRICS_OFFSET, String.format(java.util.Locale.US, "%.2f", offsetSeconds));
        save();
    }

    private String getTrackKey(Track track) {
        if (track == null || track.getPath() == null) return "";
        return track.getPath().toAbsolutePath().normalize().toString().replace('\\', '/').toLowerCase(Locale.ROOT);
    }

    private String getTrackFileKey(Track track) {
        if (track == null || track.getFileName() == null) return "";
        return track.getFileName().toLowerCase(Locale.ROOT);
    }

    public synchronized boolean hasTrackLyricsOffset(Track track) {
        if (track == null) return false;
        String pKey = PREF_TRACK_OFFSET_PREFIX + "path." + getTrackKey(track);
        String fKey = PREF_TRACK_OFFSET_PREFIX + "file." + getTrackFileKey(track);
        return properties.containsKey(pKey) || properties.containsKey(fKey);
    }

    public synchronized double getEffectiveLyricsOffset(Track track) {
        if (track == null) {
            return getLyricsOffset();
        }
        String pKey = PREF_TRACK_OFFSET_PREFIX + "path." + getTrackKey(track);
        String val = properties.getProperty(pKey);
        if (val == null || val.isBlank()) {
            String fKey = PREF_TRACK_OFFSET_PREFIX + "file." + getTrackFileKey(track);
            val = properties.getProperty(fKey);
        }
        if (val != null && !val.isBlank()) {
            try {
                return Double.parseDouble(val.strip());
            } catch (NumberFormatException ignored) {}
        }
        return getLyricsOffset();
    }

    public synchronized void setTrackLyricsOffset(Track track, double offsetSeconds) {
        if (track == null) return;
        double clamped = Math.max(-5.0, Math.min(5.0, Math.round(offsetSeconds * 100.0) / 100.0));
        String formatted = String.format(java.util.Locale.US, "%.2f", clamped);
        String pKey = PREF_TRACK_OFFSET_PREFIX + "path." + getTrackKey(track);
        String fKey = PREF_TRACK_OFFSET_PREFIX + "file." + getTrackFileKey(track);
        properties.setProperty(pKey, formatted);
        properties.setProperty(fKey, formatted);
        save();
    }

    public synchronized void removeTrackLyricsOffset(Track track) {
        if (track == null) return;
        String pKey = PREF_TRACK_OFFSET_PREFIX + "path." + getTrackKey(track);
        String fKey = PREF_TRACK_OFFSET_PREFIX + "file." + getTrackFileKey(track);
        properties.remove(pKey);
        properties.remove(fKey);
        save();
    }
}
