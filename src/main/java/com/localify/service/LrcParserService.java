package com.localify.service;

import com.localify.model.LyricLine;
import com.localify.model.Track;
import com.localify.util.Transliterator;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryIteratorException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Finds the .lrc file next to a song and turns it into timed lines.
 * Supports automatic on-the-fly online fetching via LRCLIB when no local file exists.
 */
public class LrcParserService {

    /** [mm:ss], [mm:ss.x], [mm:ss.xx] or [mm:ss.xxx]; some tools write ':' instead of '.'. */
    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET = Pattern.compile("^\\[offset:\\s*([+-]?\\d+)\\s*]$", Pattern.CASE_INSENSITIVE);
    /** Word-level timestamps from "enhanced" LRC, e.g. <00:12.34>; not shown. */
    private static final Pattern WORD_TIMESTAMP = Pattern.compile("<\\d{1,3}:\\d{1,2}(?:[.:]\\d{1,3})?>");

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public Optional<Path> findLrcFor(Path audioFile) {
        Path dir = audioFile.getParent();
        if (dir == null) {
            return Optional.empty();
        }
        String wanted = baseName(audioFile) + ".lrc";
        Path direct = dir.resolve(wanted);
        if (Files.isRegularFile(direct)) {
            return Optional.of(direct);
        }
        // Case-sensitive file systems: still accept "Song.LRC" next to "Song.mp3".
        try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
            for (Path candidate : files) {
                if (candidate.getFileName().toString().equalsIgnoreCase(wanted) && Files.isRegularFile(candidate)) {
                    return Optional.of(candidate);
                }
            }
        } catch (IOException | DirectoryIteratorException e) {
            // An unreadable folder means no lyrics, not a failed song.
        }
        return Optional.empty();
    }

    /** Lines sorted by time. Lines without a timestamp (title, artist tags) are skipped. */
    public List<LyricLine> parse(Path lrcFile) throws IOException {
        String content = decode(Files.readAllBytes(lrcFile));
        return parseContent(content);
    }

    /** Parses raw .lrc string into timed lines. */
    public List<LyricLine> parseContent(String content) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        long offsetMs = 0;
        List<LyricLine> lines = new ArrayList<>();

        for (String raw : content.split("\\R")) {
            String line = raw.strip();
            if (line.isEmpty()) {
                continue;
            }
            Matcher offset = OFFSET.matcher(line);
            if (offset.matches()) {
                offsetMs = Long.parseLong(offset.group(1));
                continue;
            }
            // A line may carry several leading timestamps when the same words repeat: [00:12.00][01:30.50]Chorus
            Matcher m = TIMESTAMP.matcher(line);
            List<Double> times = new ArrayList<>();
            int end = 0;
            while (m.find() && m.start() == end) {
                times.add(toSeconds(m));
                end = m.end();
            }
            if (times.isEmpty()) {
                continue;
            }
            String text = WORD_TIMESTAMP.matcher(line.substring(end)).replaceAll("").strip();
            text = Transliterator.romanize(text);
            for (double t : times) {
                lines.add(new LyricLine(t, text));
            }
        }

        // A positive [offset:] means the lyrics should appear earlier.
        double shift = offsetMs / 1000.0;
        List<LyricLine> result = lines.stream()
                .map(l -> new LyricLine(Math.max(0, l.timeSeconds() - shift), l.text()))
                .sorted(Comparator.comparingDouble(LyricLine::timeSeconds))
                .toList();

        if (!result.isEmpty()) {
            return result;
        }

        // Fallback for plain (untimed) lyrics: don't discard text!
        // Read non-blank lines and distribute them so the user can read the lyrics.
        String[] rawLines = content.split("\\R");
        List<String> plain = new ArrayList<>();
        for (String r : rawLines) {
            String trimmed = r.strip();
            if (!trimmed.isEmpty() && !trimmed.startsWith("[")) {
                plain.add(Transliterator.romanize(trimmed));
            }
        }
        if (!plain.isEmpty()) {
            List<LyricLine> plainLines = new ArrayList<>(plain.size());
            double step = 4.0;
            for (int i = 0; i < plain.size(); i++) {
                plainLines.add(new LyricLine(i * step, plain.get(i)));
            }
            return plainLines;
        }

        return List.of();
    }

    /**
     * Automatically queries online synced lyrics from LRCLIB and saves to disk next to the track.
     */
    public CompletableFuture<Optional<Path>> autoFetchAndSaveLrc(Track track) {
        return fetchOnlineLrc(track.getTitle(), track.getArtist()).thenApply(optLyrics -> {
            if (optLyrics.isPresent()) {
                try {
                    Path dir = track.getPath().getParent();
                    if (dir != null && Files.isWritable(dir)) {
                        Path targetLrc = dir.resolve(baseName(track.getPath()) + ".lrc");
                        String romanized = Transliterator.romanize(optLyrics.get());
                        Files.writeString(targetLrc, romanized, StandardCharsets.UTF_8);
                        return Optional.of(targetLrc);
                    }
                } catch (Exception ignored) {
                }
            }
            return Optional.empty();
        });
    }

    /**
     * Queries online synced lyrics from LRCLIB API.
     */
    public CompletableFuture<Optional<String>> fetchOnlineLrc(String trackTitle, String artist) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String clean = cleanQuery(trackTitle);
                // 1. Search with cleaned query
                String searchUrl = "https://lrclib.net/api/search?q=" + URLEncoder.encode(clean, StandardCharsets.UTF_8);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(searchUrl))
                        .header("User-Agent", "Localify/1.0 (https://github.com/localify)")
                        .timeout(Duration.ofSeconds(6))
                        .GET()
                        .build();
                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                if (response.statusCode() == 200) {
                    Optional<String> synced = chooseBestSyncedLyrics(response.body());
                    if (synced.isPresent() && !synced.get().isBlank()) {
                        return synced;
                    }
                }

                // 2. Try get endpoint if artist is known
                if (artist != null && !artist.isBlank() && !"Unknown artist".equalsIgnoreCase(artist)) {
                    String getUrl = "https://lrclib.net/api/get?artist_name="
                            + URLEncoder.encode(artist.trim(), StandardCharsets.UTF_8)
                            + "&track_name=" + URLEncoder.encode(clean, StandardCharsets.UTF_8);
                    HttpRequest getReq = HttpRequest.newBuilder()
                            .uri(URI.create(getUrl))
                            .header("User-Agent", "Localify/1.0 (https://github.com/localify)")
                            .timeout(Duration.ofSeconds(6))
                            .GET()
                            .build();
                    HttpResponse<String> getResp = httpClient.send(getReq, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                    if (getResp.statusCode() == 200) {
                        Optional<String> synced = chooseBestSyncedLyrics(getResp.body());
                        if (synced.isPresent() && !synced.get().isBlank()) {
                            return synced;
                        }
                    }
                }
            } catch (Exception ignored) {
            }
            return Optional.empty();
        });
    }

    public static String cleanQuery(String text) {
        if (text == null) return "";
        String t = text;
        t = t.replaceAll("(?i)\\.(mp3|mp4|wav|m4a|flac)$", "");
        t = t.replaceAll("(?i)\\(lyrics.*?\\)|\\[lyrics.*?\\]|\\blyrics!\\b|\\blyrics\\b|\\(lyric video.*?\\)|\\[lyric video.*?\\]", "");
        t = t.replaceAll("(?i)\\(official.*?\\)|\\[official.*?\\]|\\bofficial video\\b|\\bofficial audio\\b|\\bofficial music video\\b", "");
        t = t.replaceAll("(?i)\\[remastered.*?\\]|\\(audio\\)|\\[ncs release\\]|\\[monstercat release\\]", "");
        t = t.replaceAll("(?i)\\[tiktok song\\]|\\[hd\\]|\\[4k\\]|\\[.*?soundtrack.*?\\]|\\(.*?soundtrack.*?\\)", "");
        t = t.replaceAll("(?i)\\[.*?ending song.*?\\]|\\(.*?ending song.*?\\)|\\[.*?opening.*?\\]|\\(.*?opening.*?\\)", "");
        t = t.replaceAll("(?i)\\[.*?theme.*?\\]|\\(.*?theme.*?\\)|\\[kan.*?eng.*?romaji.*?\\]|\\(kan.*?eng.*?romaji.*?\\)", "");
        t = t.replaceAll("(?i)\\blirik lagu terjemahan\\b|\\blirik & terjemahan\\b|\\blirik terjemahan indonesia\\b|\\bfull version\\b", "");
        t = t.replaceAll("(?i)\\bft\\.?\\s+.*|\\bfeat\\.?\\s+.*", "");
        t = Transliterator.romanize(t);
        t = t.replaceAll("[\"\'\\[\\]\\(\\)<>「」『』【】《》]", " ");
        t = t.replaceAll("[_\\?]", " ");
        t = t.replaceAll("\\s+", " ");
        return t.trim();
    }

    public static List<String> extractAllJsonFields(String json, String key) {
        if (json == null || json.isEmpty()) return List.of();
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher matcher = pattern.matcher(json);
        List<String> results = new ArrayList<>();
        while (matcher.find()) {
            String val = unescapeJson(matcher.group(1));
            if (!val.isBlank()) {
                results.add(val);
            }
        }
        return results;
    }

    public static Optional<String> chooseBestSyncedLyrics(String json) {
        List<String> list = extractAllJsonFields(json, "syncedLyrics");
        if (list.isEmpty()) {
            return Optional.empty();
        }
        for (String s : list) {
            if (!Transliterator.containsAsianCharacters(s)) {
                return Optional.of(s);
            }
        }
        return Optional.of(Transliterator.romanize(list.get(0)));
    }

    public static Optional<String> extractJsonField(String json, String key) {
        if (json == null || json.isEmpty()) return Optional.empty();
        Pattern pattern = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
        Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            return Optional.of(unescapeJson(matcher.group(1)));
        }
        return Optional.empty();
    }

    public static String unescapeJson(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char next = s.charAt(++i);
                switch (next) {
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'u' -> {
                        if (i + 4 < s.length()) {
                            try {
                                int code = Integer.parseInt(s.substring(i + 1, i + 5), 16);
                                sb.append((char) code);
                                i += 4;
                            } catch (NumberFormatException e) {
                                sb.append("\\u");
                            }
                        } else {
                            sb.append("\\u");
                        }
                    }
                    default -> sb.append(next);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static double toSeconds(Matcher m) {
        double seconds = Integer.parseInt(m.group(1)) * 60.0 + Integer.parseInt(m.group(2));
        String fraction = m.group(3);
        if (fraction != null) {
            seconds += Integer.parseInt(fraction) / Math.pow(10, fraction.length());
        }
        return seconds;
    }

    /** UTF-8 (with or without BOM), UTF-16 with BOM, or Windows-1252 for old files. */
    private static String decode(byte[] bytes) {
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFF && (bytes[1] & 0xFF) == 0xFE) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16LE);
        }
        if (bytes.length >= 2 && (bytes[0] & 0xFF) == 0xFE && (bytes[1] & 0xFF) == 0xFF) {
            return new String(bytes, 2, bytes.length - 2, StandardCharsets.UTF_16BE);
        }
        int start = bytes.length >= 3 && (bytes[0] & 0xFF) == 0xEF && (bytes[1] & 0xFF) == 0xBB
                && (bytes[2] & 0xFF) == 0xBF ? 3 : 0;
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes, start, bytes.length - start))
                    .toString();
        } catch (CharacterCodingException e) {
            return new String(bytes, start, bytes.length - start, Charset.forName("windows-1252"));
        }
    }

    private static String baseName(Path file) {
        String name = file.getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
