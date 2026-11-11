package com.localify.service;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service to handle seamless in-place updating of Localify.
 * Supports hot-swapping the application JAR in-place without reinstalling,
 * preserving all user libraries, playlists, and settings in %USERPROFILE%\.localify.
 */
public final class UpdateService {
    private static final Logger LOGGER = Logger.getLogger(UpdateService.class.getName());

    private UpdateService() {}

    /**
     * Resolves the current executing JAR file path, or null if running in IDE/development mode.
     */
    public static Path getCurrentJarPath() {
        try {
            var codeSource = UpdateService.class.getProtectionDomain().getCodeSource();
            if (codeSource != null && codeSource.getLocation() != null) {
                URI uri = codeSource.getLocation().toURI();
                Path path = Paths.get(uri).toAbsolutePath();
                if (path.toString().toLowerCase().endsWith(".jar") && Files.exists(path)) {
                    return path;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to resolve current JAR path", e);
        }
        return null;
    }

    /**
     * Finds the launcher Localify.exe if installed via jpackage / Inno Setup.
     * Expected layout: <InstallDir>\app\localify-player-1.0.jar -> <InstallDir>\Localify.exe
     */
    public static Path getLauncherExe(Path jarPath) {
        if (jarPath != null && jarPath.getParent() != null) {
            Path appDir = jarPath.getParent();
            Path installDir = appDir.getParent();
            if (installDir != null) {
                Path exe = installDir.resolve("Localify.exe");
                if (Files.isRegularFile(exe)) {
                    return exe.toAbsolutePath();
                }
            }
        }
        // Fallback to per-user AppData location
        String localAppData = System.getenv("LOCALAPPDATA");
        if (localAppData != null) {
            Path standardExe = Paths.get(localAppData, "Programs", "Localify", "Localify.exe");
            if (Files.isRegularFile(standardExe)) {
                return standardExe.toAbsolutePath();
            }
        }
        return null;
    }

    /**
     * Applies an update file (.jar or .exe).
     */
    public static void applyUpdate(Path updateFile) throws IOException {
        if (updateFile == null || !Files.exists(updateFile)) {
            throw new IOException("File update tidak ditemukan: " + updateFile);
        }

        String fileName = updateFile.getFileName().toString().toLowerCase();

        if (fileName.endsWith(".jar")) {
            applyJarUpdate(updateFile);
        } else if (fileName.endsWith(".exe")) {
            applyExeUpdate(updateFile);
        } else {
            throw new IOException("Tipe file tidak didukung. Harap pilih file .jar atau .exe.");
        }
    }

    private static void applyJarUpdate(Path newJarFile) throws IOException {
        Path currentJar = getCurrentJarPath();
        if (currentJar == null) {
            // Check default installed path as fallback
            String localAppData = System.getenv("LOCALAPPDATA");
            if (localAppData != null) {
                Path standardJar = Paths.get(localAppData, "Programs", "Localify", "app", "localify-player-1.0.jar");
                if (Files.isRegularFile(standardJar)) {
                    currentJar = standardJar;
                }
            }
        }

        if (currentJar == null) {
            throw new IOException("Aplikasi sedang berjalan dalam mode development dan Localify belum diinstall.\n" +
                    "Jalankan installer terlebih dahulu.");
        }

        Path launcherExe = getLauncherExe(currentJar);

        // Write a detached Windows helper batch file to %TEMP%
        Path tempBat = Files.createTempFile("localify-updater-", ".bat");
        StringBuilder script = new StringBuilder();
        script.append("@echo off\r\n");
        script.append("title Localify Auto-Updater\r\n");
        script.append("timeout /t 2 /nobreak >nul\r\n");
        script.append("copy /y \"").append(newJarFile.toAbsolutePath()).append("\" \"")
              .append(currentJar.toAbsolutePath()).append("\" >nul\r\n");
        if (launcherExe != null) {
            script.append("start \"\" \"").append(launcherExe.toAbsolutePath()).append("\"\r\n");
        }
        script.append("del \"%~f0\" >nul 2>&1\r\n");
        script.append("exit\r\n");

        Files.writeString(tempBat, script.toString());

        // Spawn updater process detached
        new ProcessBuilder("cmd.exe", "/c", tempBat.toAbsolutePath().toString()).start();
    }

    private static void applyExeUpdate(Path installerExe) throws IOException {
        new ProcessBuilder("cmd.exe", "/c", "start", "\"\"",
                installerExe.toAbsolutePath().toString(),
                "/VERYSILENT", "/SUPPRESSMSGBOXES", "/FORCECLOSEAPPLICATIONS").start();
    }
}
