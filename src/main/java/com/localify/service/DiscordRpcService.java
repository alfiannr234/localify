package com.localify.service;

import com.localify.model.Track;
import javafx.application.Platform;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

import java.io.File;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ByteChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Pure Java Discord Rich Presence (RPC) IPC client.
 * Connects directly to Discord local IPC pipes without native JNI/DLL requirements.
 */
public class DiscordRpcService {

    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int OP_CLOSE = 2;

    private final SettingsService settingsService;
    private final BooleanProperty enabled = new SimpleBooleanProperty(this, "enabled", false);
    private final StringProperty connectionStatus = new SimpleStringProperty(this, "connectionStatus", "Disabled");

    private final BlockingQueue<PresencePayload> payloadQueue = new LinkedBlockingQueue<>();
    private volatile boolean running = true;
    private volatile PresencePayload lastPayload;
    private Thread workerThread;
    private ByteChannel activeChannel;

    private record PresencePayload(Track track, boolean isPlaying, double currentSeconds, double totalSeconds, boolean clear) {}

    public DiscordRpcService(SettingsService settingsService) {
        this.settingsService = settingsService;
        boolean initialEnabled = settingsService.isDiscordRpcEnabled();
        this.enabled.set(initialEnabled);
        this.connectionStatus.set(initialEnabled ? "Connecting to Discord..." : "Disabled");

        this.enabled.addListener((obs, oldVal, newVal) -> {
            settingsService.setDiscordRpcEnabled(newVal);
            if (newVal) {
                setStatus("Connecting to Discord...");
            } else {
                clearPresence();
                setStatus("Disabled");
            }
        });

        startWorker();
    }

    public BooleanProperty enabledProperty() {
        return enabled;
    }

    public boolean isEnabled() {
        return enabled.get();
    }

    public void setEnabled(boolean value) {
        enabled.set(value);
    }

    public StringProperty connectionStatusProperty() {
        return connectionStatus;
    }

    public String getConnectionStatus() {
        return connectionStatus.get();
    }

    public void updatePresence(Track track, boolean isPlaying, double currentSeconds, double totalSeconds) {
        if (!enabled.get()) {
            return;
        }
        PresencePayload payload = new PresencePayload(track, isPlaying, currentSeconds, totalSeconds, false);
        this.lastPayload = payload;
        payloadQueue.offer(payload);
    }

    public void clearPresence() {
        this.lastPayload = null;
        payloadQueue.offer(new PresencePayload(null, false, 0, 0, true));
    }

    public void reconnect() {
        closeChannel();
        if (enabled.get()) {
            setStatus("Connecting to Discord...");
        }
    }

    public void shutdown() {
        running = false;
        if (workerThread != null) {
            workerThread.interrupt();
        }
        closeChannel();
    }

    private void setStatus(String status) {
        Platform.runLater(() -> connectionStatus.set(status));
    }

    private void startWorker() {
        workerThread = new Thread(() -> {
            long lastAttempt = 0;
            while (running) {
                try {
                    if (!enabled.get()) {
                        closeChannel();
                        Thread.sleep(500);
                        continue;
                    }

                    if (activeChannel == null) {
                        long now = System.currentTimeMillis();
                        if (now - lastAttempt > 3500) {
                            lastAttempt = now;
                            connectToDiscord();
                        }
                        if (activeChannel == null) {
                            Thread.sleep(1000);
                            continue;
                        }
                    }

                    PresencePayload payload = payloadQueue.poll(1500, TimeUnit.MILLISECONDS);
                    if (payload != null && activeChannel != null) {
                        sendPresence(payload);
                    }
                } catch (InterruptedException e) {
                    break;
                } catch (Exception ex) {
                    closeChannel();
                    setStatus("Discord disconnected. Reconnecting...");
                }
            }
            closeChannel();
        }, "localify-discord-rpc");
        workerThread.setDaemon(true);
        workerThread.start();
    }

    private void connectToDiscord() {
        String clientId = settingsService.getDiscordClientId();
        ByteChannel channel = null;

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            for (int i = 0; i < 10; i++) {
                String pipePath = "\\\\.\\pipe\\discord-ipc-" + i;
                try {
                    RandomAccessFile raf = new RandomAccessFile(pipePath, "rw");
                    channel = raf.getChannel();
                    break;
                } catch (Exception ignored) {
                }
            }
        } else {
            String runtimeDir = System.getenv("XDG_RUNTIME_DIR");
            String tempDir = System.getenv("TMPDIR");
            String[] baseDirs = { runtimeDir, tempDir, "/tmp" };
            for (String base : baseDirs) {
                if (base != null && !base.isBlank()) {
                    for (int i = 0; i < 10; i++) {
                        Path socketPath = Path.of(base, "discord-ipc-" + i);
                        if (Files.exists(socketPath)) {
                            try {
                                SocketChannel sc = SocketChannel.open(StandardProtocolFamily.UNIX);
                                sc.connect(UnixDomainSocketAddress.of(socketPath));
                                channel = sc;
                                break;
                            } catch (Exception ignored) {
                            }
                        }
                    }
                }
                if (channel != null) break;
            }
        }

        if (channel != null) {
            try {
                String handshakeJson = "{\"v\":1,\"client_id\":\"" + escapeJson(clientId) + "\"}";
                writePacket(channel, OP_HANDSHAKE, handshakeJson);
                String readyResponse = readPacket(channel); // Read DISPATCH / READY response
                this.activeChannel = channel;
                String userLabel = parseUserLabel(readyResponse);
                setStatus(userLabel != null ? "Connected to Discord (" + userLabel + ")" : "Connected to Discord");
                if (lastPayload != null && !lastPayload.clear()) {
                    sendPresence(lastPayload);
                }
            } catch (Exception e) {
                try {
                    channel.close();
                } catch (Exception ignored) {
                }
                String msg = e.getMessage();
                if (msg != null && msg.contains("4000")) {
                    setStatus("Invalid Discord Client ID. Enter valid ID in Settings.");
                } else {
                    setStatus("Discord desktop app not detected");
                }
            }
        } else {
            setStatus("Discord desktop app not detected");
        }
    }

    private void sendPresence(PresencePayload p) {
        this.lastPayload = p;
        if (activeChannel == null) {
            return;
        }
        try {
            long pid = ProcessHandle.current().pid();
            String nonce = UUID.randomUUID().toString();
            String frameJson;

            if (p.clear() || p.track() == null) {
                frameJson = String.format(
                        "{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":%d,\"activity\":null},\"nonce\":\"%s\"}",
                        pid, nonce
                );
            } else {
                Track t = p.track();
                String title = escapeJson(t.getTitle());
                String artist = escapeJson(t.getDisplayArtist());
                long nowMs = System.currentTimeMillis();
                long startMs = Math.max(0, nowMs - (long) (p.currentSeconds() * 1000));

                String activityName = t.hasArtist() ? artist : "Localify";

                StringBuilder act = new StringBuilder();
                act.append("{\"type\":2"); // Type 2 = Listening to
                act.append(",\"name\":\"").append(activityName).append("\""); // Sets status profile & member list to 🎵 <Artist>
                act.append(",\"details\":\"").append(title).append("\"");
                act.append(",\"state\":\"").append(p.isPlaying() ? artist : artist + " (Paused)").append("\"");

                if (p.isPlaying() && p.totalSeconds() > 0) {
                    long endMs = startMs + (long) (p.totalSeconds() * 1000);
                    act.append(",\"timestamps\":{\"start\":").append(startMs).append(",\"end\":").append(endMs).append("}");
                }

                String artUrl = AutoCoverArtService.getArtworkUrl(t);
                if (artUrl == null || artUrl.isBlank()) {
                    artUrl = AutoCoverArtService.findArtworkUrlOnline(t);
                }

                String localifyLogoUrl = "https://raw.githubusercontent.com/Alfiannr234/localify/main/localify.png";
                String displayCover = (artUrl != null && !artUrl.isBlank()) ? artUrl : localifyLogoUrl;

                act.append(",\"assets\":{");
                act.append("\"large_image\":\"").append(escapeJson(displayCover)).append("\"");
                if (!localifyLogoUrl.equals(displayCover)) {
                    act.append(",\"small_image\":\"").append(localifyLogoUrl).append("\"");
                    act.append(",\"small_text\":\"Localify\"");
                }
                act.append("}}");

                frameJson = String.format(
                        "{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":%d,\"activity\":%s},\"nonce\":\"%s\"}",
                        pid, act.toString(), nonce
                );
            }

            writePacket(activeChannel, OP_FRAME, frameJson);
            readPacket(activeChannel);
        } catch (Exception e) {
            closeChannel();
            setStatus("Discord disconnected. Reconnecting...");
        }
    }

    private void writePacket(ByteChannel channel, int op, String json) throws Exception {
        byte[] payloadBytes = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + payloadBytes.length);
        buf.order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(op);
        buf.putInt(payloadBytes.length);
        buf.put(payloadBytes);
        buf.flip();
        while (buf.hasRemaining()) {
            channel.write(buf);
        }
    }

    private String readPacket(ByteChannel channel) throws Exception {
        ByteBuffer header = ByteBuffer.allocate(8);
        header.order(ByteOrder.LITTLE_ENDIAN);
        while (header.hasRemaining()) {
            int read = channel.read(header);
            if (read < 0) {
                throw new IllegalStateException("Pipe closed by Discord");
            }
        }
        header.flip();
        int op = header.getInt();
        int len = header.getInt();
        if (op == OP_CLOSE) {
            String errPayload = "";
            if (len > 0) {
                ByteBuffer payload = ByteBuffer.allocate(len);
                while (payload.hasRemaining()) {
                    channel.read(payload);
                }
                errPayload = new String(payload.array(), StandardCharsets.UTF_8);
            }
            throw new IllegalStateException("Discord closed connection (op=2): " + errPayload);
        }
        if (len > 0) {
            ByteBuffer payload = ByteBuffer.allocate(len);
            while (payload.hasRemaining()) {
                int read = channel.read(payload);
                if (read < 0) {
                    throw new IllegalStateException("Pipe closed by Discord");
                }
            }
            return new String(payload.array(), StandardCharsets.UTF_8);
        }
        return "";
    }

    private static String parseUserLabel(String json) {
        if (json == null) return null;
        try {
            int gIdx = json.indexOf("\"global_name\":");
            if (gIdx >= 0) {
                int start = gIdx + 14;
                while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == ':')) {
                    start++;
                }
                if (start < json.length() && json.charAt(start) == '"') {
                    int end = json.indexOf('"', start + 1);
                    if (end > start) {
                        return json.substring(start + 1, end);
                    }
                }
            }
            int uIdx = json.indexOf("\"username\":");
            if (uIdx >= 0) {
                int start = uIdx + 11;
                while (start < json.length() && (json.charAt(start) == ' ' || json.charAt(start) == ':')) {
                    start++;
                }
                if (start < json.length() && json.charAt(start) == '"') {
                    int end = json.indexOf('"', start + 1);
                    if (end > start) {
                        return json.substring(start + 1, end);
                    }
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private void closeChannel() {
        if (activeChannel != null) {
            try {
                activeChannel.close();
            } catch (Exception ignored) {
            }
            activeChannel = null;
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
