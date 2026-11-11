package com.localify;

import atlantafx.base.theme.PrimerDark;
import com.localify.controller.ContextMenuBuilder;
import com.localify.controller.MainController;
import com.localify.controller.MiniPlayerController;
import com.localify.service.AudioService;
import com.localify.service.DiscordRpcService;
import com.localify.service.LrcParserService;
import com.localify.service.MusicScannerService;
import com.localify.service.PlaylistService;
import com.localify.service.SettingsService;
import com.localify.service.ThemeManager;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.IOException;
import java.net.URL;

/**
 * Entry point for Localify. Launches {@link FxApplication} without directly subclassing
 * {@link Application} on the outer class so shaded JARs and {@code jpackage --main-class com.localify.App}
 * start cleanly on the classpath as well as via {@code javafx:run}.
 */
public final class App {

    private App() {
    }

    public static void main(String[] args) {
        Application.launch(FxApplication.class, args);
    }

    public static class FxApplication extends Application {

        private AudioService audioService;
        private SettingsService settingsService;
        private PlaylistService playlistService;
        private ContextMenuBuilder contextMenuBuilder;
        private MainController mainController;
        private String stylesheet;
        private Stage mainStage;
        private Stage miniStage;
        private MiniPlayerController miniController;

        @Override
        public void start(Stage primaryStage) throws IOException {
            this.settingsService = new SettingsService();
            ThemeManager.apply(settingsService.getTheme(), settingsService.getAccent());

            MusicScannerService scannerService = new MusicScannerService();
            LrcParserService lrcParserService = new LrcParserService();
            this.playlistService = new PlaylistService();
            DiscordRpcService discordRpcService = new DiscordRpcService(settingsService);

            this.audioService = new AudioService(scannerService, lrcParserService);
            this.audioService.setSettingsService(settingsService);
            this.audioService.setDiscordRpcService(discordRpcService);

            this.contextMenuBuilder = new ContextMenuBuilder(
                    audioService, scannerService, playlistService);

            this.stylesheet = requireResource("/com/localify/css/spotify-dark.css").toExternalForm();

            FXMLLoader mainLoader = new FXMLLoader(requireResource("/com/localify/fxml/main-view.fxml"));
            Parent mainRoot = mainLoader.load();
            this.mainController = mainLoader.getController();

            Scene mainScene = new Scene(mainRoot, 1240, 780);
            mainScene.getStylesheets().add(stylesheet);
            ThemeManager.registerScene(mainScene);

            this.mainStage = primaryStage;
            primaryStage.setTitle("Localify");
            primaryStage.setMinWidth(860);
            primaryStage.setMinHeight(560);
            primaryStage.setScene(mainScene);
            primaryStage.setOnCloseRequest(event -> Platform.exit());
            loadStageIcons(primaryStage);

            mainController.initServices(
                    audioService,
                    scannerService,
                    playlistService,
                    contextMenuBuilder,
                    settingsService,
                    discordRpcService,
                    this::switchToMiniPlayer);

            primaryStage.setMaximized(true);
            primaryStage.show();
            Platform.runLater(() -> primaryStage.setMaximized(true));
        }

        private synchronized void ensureMiniPlayerLoaded() {
            if (miniStage != null) return;
            try {
                FXMLLoader miniLoader = new FXMLLoader(requireResource("/com/localify/fxml/mini-player.fxml"));
                Parent miniRoot = miniLoader.load();
                this.miniController = miniLoader.getController();

                Scene miniScene = new Scene(
                        miniRoot,
                        MiniPlayerController.COMPACT_WIDTH,
                        MiniPlayerController.COMPACT_HEIGHT);
                miniScene.getStylesheets().add(stylesheet);
                ThemeManager.registerScene(miniScene);

                this.miniStage = new Stage(StageStyle.UNDECORATED);
                miniStage.setTitle("Localify Mini Player");
                miniStage.setAlwaysOnTop(true);
                miniStage.setResizable(false);
                miniStage.setScene(miniScene);
                loadStageIcons(miniStage);

                miniController.init(
                        miniStage,
                        audioService,
                        playlistService,
                        contextMenuBuilder,
                        mainController,
                        this::switchToMainWindow);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }

        private void switchToMiniPlayer() {
            ensureMiniPlayerLoaded();
            if (miniController != null && miniStage != null) {
                miniController.positionTopCenter();
                miniStage.show();
                miniStage.toFront();
                miniStage.requestFocus();
                mainStage.hide();
            }
        }

        private void switchToMainWindow() {
            mainStage.show();
            if (mainStage.isIconified()) {
                mainStage.setIconified(false);
            }
            mainStage.toFront();
            mainStage.requestFocus();
            if (miniStage != null) {
                miniStage.hide();
            }
        }

        @Override
        public void stop() {
            if (mainController != null) {
                mainController.shutdown();
            }
            if (audioService != null) {
                audioService.shutdown();
            }
        }

        private static void loadStageIcons(Stage stage) {
            try {
                stage.getIcons().clear();
                int[] sizes = {16, 32, 48, 64, 128, 256};
                for (int s : sizes) {
                    URL iconUrl = App.class.getResource("/com/localify/images/app-icon-" + s + ".png");
                    if (iconUrl != null) {
                        stage.getIcons().add(new Image(iconUrl.toExternalForm()));
                    }
                }
                URL mainIconUrl = App.class.getResource("/com/localify/images/app-icon.png");
                if (mainIconUrl != null) {
                    stage.getIcons().add(new Image(mainIconUrl.toExternalForm()));
                }
            } catch (Exception ignored) {
            }
        }

        private static URL requireResource(String path) {
            URL url = App.class.getResource(path);
            if (url == null) {
                throw new IllegalStateException("Missing classpath resource: " + path);
            }
            return url;
        }
    }
}
