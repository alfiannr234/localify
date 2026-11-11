package com.localify.service;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.jaudiotagger.tag.images.StandardArtwork;
import org.jaudiotagger.tag.reference.PictureTypes;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.localify.model.Track;

/**
 * Service that ensures every song has album cover art.
 * Fetches official 600x600 artwork from iTunes and Deezer APIs,
 * or generates distinctive modern album art when not found,
 * and embeds the artwork directly into the audio file tags.
 */
public class AutoCoverArtService {

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final Pattern CLEAN_REGEX = Pattern.compile(
            "(?i)\\[[^\\]]*(lyrics?|official|video|audio|mv|remaster|4k|hd|ver|edit|explicit)[^\\]]*\\]|" +
            "\\([^\\)]*(lyrics?|official|video|audio|mv|remaster|4k|hd|ver|edit|explicit)[^\\)]*\\)|" +
            "\\b(lyrics?|official video|music video|lyric video|official audio|visualizer|remastered)\\b|" +
            "[\\[\\]\\(\\)\\「\\」\\【\\】\\!\\?]",
            Pattern.CASE_INSENSITIVE
    );

    private static final Color[][] GRADIENT_PALETTES = {
            {new Color(0x2E, 0x08, 0x54), new Color(0xF3, 0x6B, 0x21)}, // Deep violet to Localify orange
            {new Color(0x0F, 0x20, 0x27), new Color(0x2C, 0x53, 0x64)}, // Dark ocean cyan
            {new Color(0x3A, 0x1C, 0x71), new Color(0xD7, 0x6D, 0x77)}, // Purple rose
            {new Color(0x1A, 0x2A, 0x6C), new Color(0xFD, 0x1D, 0x1D)}, // Deep blue to fire red
            {new Color(0x11, 0x11, 0x11), new Color(0x43, 0x43, 0x43)}, // Sleek carbon dark
            {new Color(0x23, 0x07, 0x4D), new Color(0xCC, 0x53, 0x33)}, // Night ember
            {new Color(0x13, 0x4E, 0x5E), new Color(0x71, 0xB2, 0x80)}, // Emerald forest
            {new Color(0x4A, 0x00, 0xE0), new Color(0x8E, 0x2D, 0xE2)}, // Royal violet
            {new Color(0x20, 0x01, 0x22), new Color(0x6F, 0x00, 0x00)}, // Dark ruby
            {new Color(0x14, 0x1E, 0x30), new Color(0x24, 0x3B, 0x55)}  // Midnight navy
    };

    private static final Map<String, String> ONLINE_ARTWORK_URLS = new ConcurrentHashMap<>();

    public static String getArtworkUrl(Track track) {
        if (track == null) return null;
        String key = cacheKeyFor(track);
        return ONLINE_ARTWORK_URLS.get(key);
    }

    public static String findArtworkUrlOnline(Track track) {
        if (track == null) return null;
        String key = cacheKeyFor(track);
        String cached = ONLINE_ARTWORK_URLS.get(key);
        if (cached != null) return cached;

        String title = track.getTitle();
        String artist = track.getArtist();
        if ((title == null || title.isBlank()) && track.getPath() != null) {
            String base = track.getPath().getFileName().toString().replaceFirst("\\.[^.]+$", "");
            if (base.contains(" - ")) {
                String[] parts = base.split(" - ", 2);
                artist = parts[0];
                title = parts[1];
            } else {
                title = base;
            }
        }

        String cleanTitle = cleanTerm(title);
        String cleanArtist = cleanTerm(artist);

        String searchQuery = (cleanArtist.isBlank() ? cleanTitle : cleanArtist + " " + cleanTitle).strip();
        String url = queryItunesUrl(searchQuery);
        if (url == null && !cleanTitle.isBlank()) {
            url = queryItunesUrl(cleanTitle);
        }
        if (url == null) {
            url = queryDeezerUrl(searchQuery);
        }
        if (url != null) {
            ONLINE_ARTWORK_URLS.put(key, url);
            return url;
        }
        return null;
    }

    public static String queryItunesUrl(String query) {
        if (query == null || query.isBlank()) return null;
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://itunes.apple.com/search?term=" + encoded + "&entity=song&limit=1";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Localify/1.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                Pattern p = Pattern.compile("\"artworkUrl100\"\\s*:\\s*\"([^\"]+)\"");
                Matcher m = p.matcher(resp.body());
                if (m.find()) {
                    String artUrl = m.group(1).replace("\\/", "/");
                    return artUrl.replace("100x100bb.jpg", "600x600bb.jpg");
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public static String queryDeezerUrl(String query) {
        if (query == null || query.isBlank()) return null;
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://api.deezer.com/search?q=" + encoded + "&limit=1";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Localify/1.0")
                    .timeout(Duration.ofSeconds(4))
                    .GET()
                    .build();
            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                Pattern p = Pattern.compile("\"(cover_big|cover_xl|cover_medium)\"\\s*:\\s*\"([^\"]+)\"");
                Matcher m = p.matcher(resp.body());
                if (m.find()) {
                    return m.group(2).replace("\\/", "/");
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static String cacheKeyFor(Track track) {
        if (track == null) return "";
        if (track.getPath() != null) {
            return track.getPath().toString();
        }
        return (track.getArtist() == null ? "" : track.getArtist()) + "::" + (track.getTitle() == null ? "" : track.getTitle());
    }

    /** Checks if a file currently has embedded cover art. */
    public boolean hasCoverArt(Path path) {
        try {
            AudioFile af = AudioFileIO.read(path.toFile());
            Tag tag = af.getTag();
            if (tag == null) return false;
            Artwork artwork = tag.getFirstArtwork();
            return artwork != null && artwork.getBinaryData() != null && artwork.getBinaryData().length > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** Embeds artwork bytes into an audio file's tags. */
    public boolean embedArtwork(Path path, byte[] imageBytes) {
        if (imageBytes == null || imageBytes.length == 0) {
            return false;
        }
        try {
            File f = path.toFile();
            if (!f.canWrite()) {
                f.setWritable(true);
            }
            AudioFile af = AudioFileIO.read(f);
            Tag tag = af.getTagOrCreateAndSetDefault();

            Artwork artwork = new StandardArtwork();
            artwork.setBinaryData(imageBytes);
            artwork.setMimeType("image/jpeg");
            artwork.setDescription("Cover Art");
            artwork.setPictureType(PictureTypes.DEFAULT_ID);

            tag.deleteArtworkField();
            tag.setField(artwork);
            af.commit();
            return true;
        } catch (Exception e) {
            System.err.println("Could not embed artwork in " + path.getFileName() + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Resolves artwork for a track by querying iTunes, then Deezer, or generating
     * a clean procedural album cover as a foolproof fallback.
     */
    public byte[] resolveArtwork(Path path) {
        String fileName = path.getFileName().toString();
        String baseName = fileName.contains(".") ? fileName.substring(0, fileName.lastIndexOf('.')) : fileName;

        // Try extracting artist and title
        String artist = "";
        String title = baseName;

        try {
            AudioFile af = AudioFileIO.read(path.toFile());
            Tag tag = af.getTag();
            if (tag != null) {
                artist = tag.getFirst(org.jaudiotagger.tag.FieldKey.ARTIST);
                title = tag.getFirst(org.jaudiotagger.tag.FieldKey.TITLE);
            }
        } catch (Exception ignored) {
        }

        if (artist == null || artist.isBlank() || "Unknown artist".equalsIgnoreCase(artist)) {
            if (baseName.contains(" - ")) {
                String[] parts = baseName.split(" - ", 2);
                artist = parts[0].strip();
                title = parts[1].strip();
            } else {
                artist = "";
                title = baseName;
            }
        }

        String cleanTitle = cleanTerm(title);
        String cleanArtist = cleanTerm(artist);

        // 1. Try iTunes with Artist + Title
        String searchQuery = (cleanArtist.isBlank() ? cleanTitle : cleanArtist + " " + cleanTitle).strip();
        String itunesUrl = queryItunesUrl(searchQuery);
        if (itunesUrl == null && !cleanTitle.isBlank()) {
            itunesUrl = queryItunesUrl(cleanTitle);
        }
        byte[] art = null;
        if (itunesUrl != null) {
            ONLINE_ARTWORK_URLS.put(path.toString(), itunesUrl);
            art = downloadBytes(itunesUrl);
        }

        // 2. Fallback to Deezer
        if (art == null) {
            String deezerUrl = queryDeezerUrl(searchQuery);
            if (deezerUrl == null && !cleanTitle.isBlank()) {
                deezerUrl = queryDeezerUrl(cleanTitle);
            }
            if (deezerUrl != null) {
                ONLINE_ARTWORK_URLS.put(path.toString(), deezerUrl);
                art = downloadBytes(deezerUrl);
            }
        }

        // 4. Foolproof Procedural Album Art Generator
        if (art == null) {
            art = generateProceduralCover(
                    cleanTitle.isBlank() ? baseName : cleanTitle,
                    cleanArtist.isBlank() ? "Localify Music" : cleanArtist);
        }

        return art;
    }

    private byte[] fetchFromItunes(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://itunes.apple.com/search?term=" + encoded + "&entity=song&limit=1";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Localify/1.0 (Windows NT 10.0; Win64; x64)")
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();

            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String body = resp.body();
                Pattern p = Pattern.compile("\"artworkUrl100\"\\s*:\\s*\"([^\"]+)\"");
                Matcher m = p.matcher(body);
                if (m.find()) {
                    String artUrl = m.group(1).replace("\\/", "/");
                    // Upgrade to 600x600 high quality
                    artUrl = artUrl.replace("100x100bb.jpg", "600x600bb.jpg");
                    return downloadBytes(artUrl);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private byte[] fetchFromDeezer(String query) {
        if (query == null || query.isBlank()) {
            return null;
        }
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://api.deezer.com/search?q=" + encoded + "&limit=1";
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Localify/1.0")
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();

            HttpResponse<String> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                String body = resp.body();
                Pattern p = Pattern.compile("\"(cover_big|cover_xl|cover_medium)\"\\s*:\\s*\"([^\"]+)\"");
                Matcher m = p.matcher(body);
                if (m.find()) {
                    String artUrl = m.group(2).replace("\\/", "/");
                    return downloadBytes(artUrl);
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private byte[] downloadBytes(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .header("User-Agent", "Localify/1.0")
                    .timeout(Duration.ofSeconds(8))
                    .GET()
                    .build();
            HttpResponse<byte[]> resp = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200) {
                byte[] bytes = resp.body();
                if (bytes.length > 512) {
                    return bytes;
                }
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    /**
     * Generates a modern 600x600 album artwork with a smooth gradient,
     * vinyl texture circles, and clean typography.
     */
    public byte[] generateProceduralCover(String title, String artist) {
        try {
            int width = 600;
            int height = 600;
            BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = img.createGraphics();

            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

            int paletteIdx = Math.abs((title + artist).hashCode()) % GRADIENT_PALETTES.length;
            Color[] colors = GRADIENT_PALETTES[paletteIdx];
            GradientPaint gp = new GradientPaint(0, 0, colors[0], width, height, colors[1]);
            g2.setPaint(gp);
            g2.fillRect(0, 0, width, height);

            // Subtle vinyl groove concentric circles
            g2.setColor(new Color(255, 255, 255, 12));
            for (int r = 100; r < 580; r += 28) {
                g2.drawOval((width - r) / 2, (height - r) / 2, r, r);
            }

            // Central frosted badge
            int badgeSize = 260;
            int bx = (width - badgeSize) / 2;
            int by = (height - badgeSize) / 2 - 20;
            g2.setColor(new Color(0, 0, 0, 80));
            g2.fillOval(bx, by, badgeSize, badgeSize);
            g2.setColor(new Color(243, 107, 33, 180)); // Orange accent ring
            g2.drawOval(bx, by, badgeSize, badgeSize);

            // Vinyl center hole
            g2.setColor(new Color(20, 20, 20));
            g2.fillOval((width - 44) / 2, (height - 44) / 2 - 20, 44, 44);
            g2.setColor(new Color(243, 107, 33));
            g2.fillOval((width - 16) / 2, (height - 16) / 2 - 20, 16, 16);

            // Title Typography
            g2.setFont(new Font("Segoe UI", Font.BOLD, 28));
            FontMetrics fm = g2.getFontMetrics();
            String displayTitle = truncateString(title, 24);
            int tw = fm.stringWidth(displayTitle);
            g2.setColor(Color.WHITE);
            g2.drawString(displayTitle, (width - tw) / 2, 470);

            // Artist Typography
            g2.setFont(new Font("Segoe UI", Font.PLAIN, 18));
            FontMetrics fmA = g2.getFontMetrics();
            String displayArtist = truncateString(artist, 30);
            int aw = fmA.stringWidth(displayArtist);
            g2.setColor(new Color(220, 220, 220, 200));
            g2.drawString(displayArtist, (width - aw) / 2, 510);

            g2.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(img, "jpg", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            return new byte[0];
        }
    }

    private static String cleanTerm(String raw) {
        if (raw == null) return "";
        String s = CLEAN_REGEX.matcher(raw).replaceAll(" ");
        return s.replaceAll("\\s+", " ").strip();
    }

    private static String truncateString(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
    }

    /** Processes all audio files in the folder and embeds artwork where missing. */
    public int processDirectory(Path musicDir, Consumer<String> callback) {
        if (!Files.isDirectory(musicDir)) {
            return 0;
        }

        List<Path> files;
        try (var s = Files.list(musicDir)) {
            files = s.filter(p -> {
                String n = p.toString().toLowerCase(Locale.ROOT);
                return n.endsWith(".mp3") || n.endsWith(".m4a") || n.endsWith(".flac") || n.endsWith(".wav");
            }).toList();
        } catch (Exception e) {
            return 0;
        }

        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger updated = new AtomicInteger(0);
        int total = files.size();

        // Use thread pool for fast parallel network queries
        ForkJoinPool customPool = new ForkJoinPool(8);
        try {
            customPool.submit(() -> files.parallelStream().forEach(path -> {
                int current = processed.incrementAndGet();
                if (hasCoverArt(path)) {
                    if (callback != null) {
                        callback.accept("[" + current + "/" + total + "] Already has artwork: " + path.getFileName());
                    }
                    return;
                }

                if (callback != null) {
                    callback.accept("[" + current + "/" + total + "] Fetching cover for: " + path.getFileName());
                }

                byte[] art = resolveArtwork(path);
                if (art != null && art.length > 0) {
                    boolean ok = embedArtwork(path, art);
                    if (ok) {
                        updated.incrementAndGet();
                        if (callback != null) {
                            callback.accept("[" + current + "/" + total + "] Embedded artwork for: " + path.getFileName());
                        }
                    }
                }
            })).get();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            customPool.shutdown();
        }

        return updated.get();
    }
}
