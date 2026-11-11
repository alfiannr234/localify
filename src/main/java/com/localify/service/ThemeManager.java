package com.localify.service;

import atlantafx.base.theme.Dracula;
import atlantafx.base.theme.NordDark;
import atlantafx.base.theme.PrimerDark;
import atlantafx.base.theme.PrimerLight;
import javafx.application.Application;
import javafx.scene.Parent;
import javafx.scene.Scene;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages runtime theme switching (AtlantaFX PrimerDark/Light, Nord, Dracula)
 * and dynamic accent color customization for Localify.
 */
public class ThemeManager {

    private static final Set<Scene> REGISTERED_SCENES = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static String currentTheme = "DARK";
    private static String currentAccent = "ORANGE";

    public static void registerScene(Scene scene) {
        if (scene != null) {
            REGISTERED_SCENES.add(scene);
            applyToScene(scene, currentTheme, currentAccent);
        }
    }

    public static void unregisterScene(Scene scene) {
        if (scene != null) {
            REGISTERED_SCENES.remove(scene);
        }
    }

    public static void apply(String theme, String accent) {
        currentTheme = (theme != null && !theme.isBlank()) ? theme.toUpperCase() : "DARK";
        currentAccent = (accent != null && !accent.isBlank()) ? accent.toUpperCase() : "ORANGE";

        try {
            switch (currentTheme) {
                case "LIGHT" -> Application.setUserAgentStylesheet(new PrimerLight().getUserAgentStylesheet());
                case "NORD" -> Application.setUserAgentStylesheet(new NordDark().getUserAgentStylesheet());
                case "DRACULA" -> Application.setUserAgentStylesheet(new Dracula().getUserAgentStylesheet());
                default -> Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
            }
        } catch (Exception ignored) {
        }

        for (Scene s : REGISTERED_SCENES) {
            applyToScene(s, currentTheme, currentAccent);
        }
    }

    private static void applyToScene(Scene scene, String theme, String accent) {
        if (scene == null || scene.getRoot() == null) {
            return;
        }
        Parent root = scene.getRoot();
        root.getStyleClass().removeAll("theme-dark", "theme-light", "theme-nord", "theme-dracula");
        root.getStyleClass().add("theme-" + theme.toLowerCase());

        String hex = SettingsService.getAccentHex(accent);
        root.setStyle(
                "-localify-accent: " + hex + "; " +
                "-fx-accent: " + hex + "; " +
                "-color-accent-emphasis: " + hex + "; " +
                "-color-accent-fg: " + hex + ";"
        );
    }

    public static String getCurrentTheme() {
        return currentTheme;
    }

    public static String getCurrentAccent() {
        return currentAccent;
    }
}
