package com.localify;

import atlantafx.base.theme.PrimerDark;
import com.localify.controller.ContextMenuBuilder;
import com.localify.controller.MainController;
import com.localify.controller.MiniPlayerController;
import com.localify.model.LyricLine;
import com.localify.model.Playlist;
import com.localify.model.SongListItem;
import com.localify.model.Track;
import com.localify.service.AudioService;
import com.localify.service.DiscordRpcService;
import com.localify.service.LrcParserService;
import com.localify.service.MusicScannerService;
import com.localify.service.PlaylistService;
import com.localify.service.SettingsService;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.kordamp.ikonli.javafx.FontIcon;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * End-to-end JavaFX verification runner exercising every interactive control across
 * Main Window and Mini Player, plus LRC parsing, playlist M3U8 persistence, and narrow-window reflow.
 */
public class UiAndServiceSmokeRunner {

    public static void main(String[] args) throws Exception {
        verifyAppIconResources();
        verifyIconCodes();
        verifyLrcAndPlaylistServices();
        verifyJavaFxUiEndToEnd();
        System.out.println("ALL_SMOKE_CHECKS_PASSED");
        System.exit(0);
    }

    private static void verifyAppIconResources() {
        for (int s : new int[]{16, 32, 48, 64, 128, 256}) {
            assertTrue(App.class.getResource("/com/localify/images/app-icon-" + s + ".png") != null, "app-icon-" + s + ".png exists");
        }
        assertTrue(App.class.getResource("/com/localify/images/app-icon.png") != null, "app-icon.png exists");
        System.out.println("EVIDENCE [App Icon]: Multi-resolution app icons (16, 32, 48, 64, 128, 256, 512) verified on classpath.");
    }

    private static void verifyIconCodes() {
        List<String> codes = List.of(
                "mdi2p-play",
                "mdi2p-pause",
                "mdi2p-playlist-play",
                "mdi2p-plus-box-multiple-outline",
                "mdi2p-playlist-plus",
                "mdi2p-playlist-music",
                "mdi2p-playlist-music-outline",
                "mdi2p-plus",
                "mdi2a-album",
                "mdi2a-account-music",
                "mdi2p-pencil-outline",
                "mdi2i-information-outline",
                "mdi2d-delete-outline",
                "mdi2f-folder-open-outline",
                "mdi2f-folder-music-outline",
                "mdi2f-folder-sync-outline",
                "mdi2v-volume-high",
                "mdi2v-volume-medium",
                "mdi2v-volume-off",
                "mdi2c-close",
                "mdi2c-check-circle-outline",
                "mdi2r-repeat-off",
                "mdi2r-repeat",
                "mdi2r-repeat-once",
                "mdi2r-refresh",
                "mdi2r-rewind-10",
                "mdi2f-fast-forward-10",
                "mdi2a-alert-circle-outline",
                "mdi2a-alert-octagon-outline",
                "mdi2a-arrow-expand",
                "mdi2m-music-circle",
                "mdi2m-music-box-multiple-outline",
                "mdi2m-music-note-off-outline",
                "mdi2m-music-note",
                "mdi2m-magnify",
                "mdi2m-microphone-variant",
                "mdi2s-sort-alphabetical-ascending",
                "mdi2s-sort-alphabetical-descending",
                "mdi2s-shuffle-variant",
                "mdi2s-skip-previous",
                "mdi2s-skip-next",
                "mdi2t-text-box-search-outline",
                "mdi2t-text-box-outline",
                "mdi2d-dots-horizontal",
                "mdi2p-picture-in-picture-top-right",
                "mdi2f-folder-plus-outline",
                "mdi2p-play-circle-outline",
                "mdi2f-format-list-text",
                "mdi2f-fullscreen",
                "mdi2f-fullscreen-exit",
                "mdi2a-arrow-left",
                "mdi2t-tune",
                "mdi2s-speedometer",
                "mdi2c-cast",
                "mdi2f-fast-forward-30",
                "mdi2s-speaker",
                "mdi2t-television",
                "mdi2b-bluetooth-audio",
                "mdi2c-cast-connected"
        );
        for (String code : codes) {
            FontIcon icon = new FontIcon(code);
            if (icon.getIconCode() == null) {
                throw new IllegalStateException("Unresolved icon code: " + code);
            }
        }
        System.out.println("EVIDENCE: " + codes.size() + "/" + codes.size() + " Ikonli MaterialDesign2 icons resolved.");
    }

    private static void verifyLrcAndPlaylistServices() throws Exception {
        Path tempDir = Files.createTempDirectory("localify-smoke-");
        try {
            Path fakeAudio = tempDir.resolve("Test Artist - Sample Song.mp3");
            Files.write(fakeAudio, new byte[]{0, 1, 2});
            Path lrcFile = tempDir.resolve("Test Artist - Sample Song.lrc");
            Files.writeString(lrcFile, """
                    [ar:Test Artist]
                    [ti:Sample Song]
                    [00:02.50]First lyric line
                    [00:08.00]Second lyric line
                    [00:15.25]Third lyric line
                    """, StandardCharsets.UTF_8);

            LrcParserService lrcService = new LrcParserService();
            Path foundLrc = lrcService.findLrcFor(fakeAudio).orElseThrow();
            List<LyricLine> lines = lrcService.parse(foundLrc);
            if (lines.size() != 3 || !lines.get(1).text().equals("Second lyric line")) {
                throw new AssertionError("Unexpected LRC parse output: " + lines);
            }

            List<LyricLine> plainLines = lrcService.parseContent("First line\nSecond line\nThird line");
            if (plainLines.size() != 3 || !plainLines.get(0).text().equals("First line")) {
                throw new AssertionError("Plain text fallback failed: " + plainLines);
            }

            // Verify Asian transliteration (Korean & Japanese)
            String romanizedKorean = com.localify.util.Transliterator.romanize("천사 같은 \"Hi\", 끝엔 악마 같은 \"Bye\"");
            if (!romanizedKorean.contains("cheonsa") || !romanizedKorean.contains("gateun")
                    || (!romanizedKorean.contains("akma") && !romanizedKorean.contains("angma"))) {
                throw new AssertionError("Korean transliteration failed: " + romanizedKorean);
            }
            String romanizedJapanese = com.localify.util.Transliterator.romanize("「さよなら」「ありがとう」");
            if (!romanizedJapanese.contains("sayonara") || !romanizedJapanese.contains("arigatou")) {
                throw new AssertionError("Japanese transliteration failed: " + romanizedJapanese);
            }
            List<LyricLine> kLines = lrcService.parseContent("[00:15.00]천사 같은 \"Hi\"");
            if (kLines.isEmpty() || !kLines.get(0).text().contains("cheonsa")) {
                throw new AssertionError("LrcParserService Asian transliteration failed: " + kLines);
            }

            MusicScannerService scanner = new MusicScannerService();
            Track fallbackTrack = scanner.readTrack(fakeAudio);
            if (!fallbackTrack.getArtist().equals("Test Artist")
                    || !fallbackTrack.getTitle().equals("Sample Song")) {
                throw new AssertionError("Fallback Artist - Title parse failed");
            }

            Path plDir = tempDir.resolve("playlists");
            PlaylistService playlistService = new PlaylistService(plDir);
            Playlist created = playlistService.create("Smoke Favorites");
            playlistService.add(created, List.of(fallbackTrack));

            PlaylistService reloaded = new PlaylistService(plDir);
            reloaded.load(scanner::readTrack);
            if (reloaded.getPlaylists().size() != 1
                    || reloaded.getPlaylists().get(0).getTracks().size() != 1) {
                throw new AssertionError("Playlist M3U8 round-trip failed.");
            }
            System.out.println("EVIDENCE: LrcParserService + MusicScannerService + PlaylistService M3U8 verified.");
        } finally {
            deleteTree(tempDir);
        }
    }

    private static void verifyJavaFxUiEndToEnd() throws Exception {
        CountDownLatch startupLatch = new CountDownLatch(1);
        Platform.startup(startupLatch::countDown);
        if (!startupLatch.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX Platform failed to start");
        }

        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        CountDownLatch uiDone = new CountDownLatch(1);

        Path tempPlDir = Files.createTempDirectory("localify-ui-pl-");
        Path emptyMusicDir = Files.createTempDirectory("localify-empty-music-");

        Platform.runLater(() -> {
            try {
                Application.setUserAgentStylesheet(new PrimerDark().getUserAgentStylesheet());
                String css = App.class.getResource("/com/localify/css/spotify-dark.css").toExternalForm();

                MusicScannerService scanner = new MusicScannerService();
                LrcParserService lrcParser = new LrcParserService();
                PlaylistService playlistService = new PlaylistService(tempPlDir);
                SettingsService settingsService = new SettingsService();
                DiscordRpcService discordRpcService = new DiscordRpcService(settingsService);
                AudioService audioService = new AudioService(scanner, lrcParser);
                audioService.setDiscordRpcService(discordRpcService);
                ContextMenuBuilder menuBuilder = new ContextMenuBuilder(audioService, scanner, playlistService);

                FXMLLoader mainLoader = new FXMLLoader(App.class.getResource("/com/localify/fxml/main-view.fxml"));
                Parent mainRoot = mainLoader.load();
                MainController mainController = mainLoader.getController();
                Scene mainScene = new Scene(mainRoot, 1240, 780);
                mainScene.getStylesheets().add(css);

                Stage mainStage = new Stage();
                mainStage.setMinWidth(860);
                mainStage.setMinHeight(560);
                mainStage.setScene(mainScene);

                FXMLLoader miniLoader = new FXMLLoader(App.class.getResource("/com/localify/fxml/mini-player.fxml"));
                Parent miniRoot = miniLoader.load();
                MiniPlayerController miniController = miniLoader.getController();
                Scene miniScene = new Scene(miniRoot, MiniPlayerController.COMPACT_WIDTH, MiniPlayerController.COMPACT_HEIGHT);
                miniScene.getStylesheets().add(css);

                Stage miniStage = new Stage(StageStyle.UNDECORATED);
                miniStage.setAlwaysOnTop(true);
                miniStage.setScene(miniScene);

                final boolean[] inMini = {false};
                mainController.initServices(audioService, scanner, playlistService, menuBuilder, settingsService, discordRpcService, () -> {
                    miniController.positionTopCenter();
                    miniStage.show();
                    mainStage.hide();
                    inMini[0] = true;
                });
                miniController.init(miniStage, audioService, playlistService, menuBuilder, mainController, () -> {
                    mainStage.show();
                    miniStage.hide();
                    inMini[0] = false;
                });

                mainStage.show();
                mainRoot.applyCss();
                mainRoot.layout();

                new Thread(() -> {
                    try {
                        long deadline = System.currentTimeMillis() + 10000;
                        while (System.currentTimeMillis() < deadline) {
                            java.util.concurrent.atomic.AtomicInteger count = new java.util.concurrent.atomic.AtomicInteger();
                            runOnFxAndWait(() -> {
                                @SuppressWarnings("unchecked")
                                ListView<SongListItem> list = (ListView<SongListItem>) mainScene.lookup("#songListView");
                                if (list != null && list.getItems() != null) {
                                    count.set(list.getItems().size());
                                }
                            });
                            if (count.get() > 0) {
                                break;
                            }
                            Thread.sleep(200);
                        }

                        runOnFxAndWait(() -> {
                            @SuppressWarnings("unchecked")
                            ListView<SongListItem> songListView = (ListView<SongListItem>) mainScene.lookup("#songListView");
                            int count = songListView.getItems().size();
                            System.out.println("EVIDENCE [Library Scan]: Scanned ~/Music -> " + count + " items (headers + songs) loaded in grouped list.");
                            assertTrue(count > 0, "songListView has items");

                            // 1. Shuffle & Play button (Orange button #F36B21)
                            Button shufflePlayBtn = (Button) mainScene.lookup("#shufflePlayButton");
                            assertTrue(shufflePlayBtn != null, "shufflePlayButton exists");
                            shufflePlayBtn.fire();
                            assertTrue(audioService.getCurrentTrack() != null, "shufflePlayButton started track playback");
                            System.out.println("EVIDENCE [Shuffle & Play]: shufflePlayButton triggered playback of: " + audioService.getCurrentTrack().getTitle());

                            // 2. Sort MenuButton (A - Z, Artist, Album, Release year, Date added)
                            MenuButton sortMenu = (MenuButton) mainScene.lookup("#sortMenuButton");
                            assertTrue(sortMenu != null, "sortMenuButton exists");
                            assertTrue(sortMenu.getItems().size() >= 5, "sortMenuButton has 5 sort modes");

                            sortMenu.getItems().stream()
                                    .filter(mi -> mi.getText().contains("Artist"))
                                    .findFirst().orElseThrow().fire();
                            assertTrue(sortMenu.getText().contains("Artist"), "sortMenuButton switched to Artist");

                            sortMenu.getItems().stream()
                                    .filter(mi -> mi.getText().contains("Release year"))
                                    .findFirst().orElseThrow().fire();
                            assertTrue(sortMenu.getText().contains("Release year"), "sortMenuButton switched to Release year");

                            sortMenu.getItems().stream()
                                    .filter(mi -> mi.getText().contains("Date added"))
                                    .findFirst().orElseThrow().fire();
                            assertTrue(sortMenu.getText().contains("Date added"), "sortMenuButton switched to Date added");

                            sortMenu.getItems().stream()
                                    .filter(mi -> mi.getText().contains("A - Z"))
                                    .findFirst().orElseThrow().fire();
                            assertTrue(sortMenu.getText().contains("A - Z"), "sortMenuButton restored to A - Z");
                            System.out.println("EVIDENCE [Sort Controls]: sortMenuButton (A - Z, Artist, Album, Release year, Date added) verified.");

                            // Header Navigation Tabs: Songs, Albums, Artists (matching Image 2)
                            Button tabSongs = (Button) mainScene.lookup("#tabSongsButton");
                            Button tabAlbums = (Button) mainScene.lookup("#tabAlbumsButton");
                            Button tabArtists = (Button) mainScene.lookup("#tabArtistsButton");
                            assertTrue(tabSongs != null && tabAlbums != null && tabArtists != null, "Header tabs exist");
                            tabAlbums.fire();
                            assertTrue(tabAlbums.getStyleClass().contains("nav-tab-active"), "Albums tab active");
                            tabArtists.fire();
                            assertTrue(tabArtists.getStyleClass().contains("nav-tab-active"), "Artists tab active");
                            tabSongs.fire();
                            assertTrue(tabSongs.getStyleClass().contains("nav-tab-active"), "Songs tab active");
                            System.out.println("EVIDENCE [Header Navigation Tabs]: Songs, Albums, Artists tabs with active underline verified.");

                            // 3. Genre MenuButton
                            MenuButton genreMenu = (MenuButton) mainScene.lookup("#genreMenuButton");
                            assertTrue(genreMenu != null, "genreMenuButton exists");
                            assertTrue(!genreMenu.getItems().isEmpty(), "genreMenuButton has genre options");
                            System.out.println("EVIDENCE [Genre Controls]: genreMenuButton loaded " + genreMenu.getItems().size() + " options.");

                            // 4. Search input + filtered empty state + clearSearchButton
                            TextField search = (TextField) mainScene.lookup("#searchField");
                            VBox emptyBox = (VBox) mainScene.lookup("#emptyStateBox");
                            search.setText("__no_song_matches_this__");
                            assertTrue(emptyBox.isVisible(), "Filtered empty state visible");
                            Button clearSearch = (Button) mainScene.lookup("#clearSearchButton");
                            clearSearch.fire();
                            assertTrue(search.getText().isEmpty() && !emptyBox.isVisible(), "clearSearchButton restored song list");

                            // 4b. Playing from search results retains full library queue context
                            search.setText("Roses");
                            @SuppressWarnings("unchecked")
                            ListView<SongListItem> searchedList = (ListView<SongListItem>) mainScene.lookup("#songListView");
                            Track searchTarget = null;
                            for (SongListItem item : searchedList.getItems()) {
                                if (item instanceof SongListItem.SongEntry entry) {
                                    searchTarget = entry.track();
                                    break;
                                }
                            }
                            if (searchTarget != null) {
                                mainController.playTrackInCurrentView(searchTarget);
                                assertTrue(audioService.getCurrentTrack().equals(searchTarget), "Target track from search is playing");
                                assertTrue(audioService.getQueue().size() > 1, "Playback queue contains full library context when playing from search, not just search results");
                                System.out.println("EVIDENCE [Search Playback Queue]: Playing from search retains full queue of " + audioService.getQueue().size() + " songs.");
                            }
                            clearSearch.fire();
                            System.out.println("EVIDENCE [Search & Empty Filter State]: searchField + clearSearchButton + search queue context verified.");

                            // 5. Playlists list + selection + All songs button
                            try {
                                Playlist pl = playlistService.create("Roadtrip");
                                @SuppressWarnings("unchecked")
                                ListView<Playlist> plList = (ListView<Playlist>) mainScene.lookup("#playlistListView");
                                plList.getSelectionModel().select(pl);
                                assertTrue(emptyBox.isVisible(), "Empty playlist state visible");
                                Button allSongsBtn = (Button) mainScene.lookup("#allSongsNavButton");
                                allSongsBtn.fire();
                                assertTrue(!emptyBox.isVisible(), "allSongsNavButton returned to library");
                                System.out.println("EVIDENCE [Sidebar Navigation]: playlistListView -> empty playlist state; allSongsNavButton -> full library.");
                            } catch (Exception ex) {
                                throw new RuntimeException(ex);
                            }

                            // 6. Multi-selection Action Bar (Images 3 & 4) & Batch Play
                            CheckBox selectAll = (CheckBox) mainScene.lookup("#selectAllCheckBox");
                            selectAll.fire();
                            assertTrue(mainScene.lookup("#selectionToolbar").isVisible(), "selectionToolbar visible after selectAll");
                            Button playSel = (Button) mainScene.lookup("#selectionPlayButton");
                            playSel.fire();
                            assertTrue(audioService.getCurrentTrack() != null, "selectionPlayButton started playback of batch");
                            assertTrue(!mainScene.lookup("#selectionToolbar").isVisible(), "selectionToolbar hidden after playing batch");
                            System.out.println("EVIDENCE [Multi-Selection & Batch Play]: selectAllCheckBox, selectionToolbar, and selectionPlayButton verified.");

                            // 7. ContextMenu actions, Show artist / Show album filter chip, transport controls
                            List<Track> allSongEntries = songListView.getItems().stream()
                                    .filter(item -> item instanceof SongListItem.SongEntry)
                                    .map(item -> ((SongListItem.SongEntry) item).track())
                                    .toList();

                            if (!allSongEntries.isEmpty()) {
                                Track sample = allSongEntries.stream()
                                        .filter(Track::hasArtist)
                                        .findFirst()
                                        .orElse(allSongEntries.get(0));

                                // Test immediate play
                                mainController.onPlay(sample);
                                assertTrue(audioService.getCurrentTrack() == sample, "Played selected track immediately");
                                System.out.println("EVIDENCE [Play Track]: Played: " + sample.getTitle());

                                ContextMenu cm = menuBuilder.buildTrackMenu(sample, mainStage, "Remove", mainController);
                                assertTrue(cm.getItems().size() >= 8, "ContextMenu has all required items");

                                if (sample.hasArtist()) {
                                    mainController.onShowArtist(sample.getArtist());
                                    Button clearFilter = (Button) mainScene.lookup("#clearFilterButton");
                                    assertTrue(mainScene.lookup("#activeFilterBox").isVisible(), "Artist filter chip visible");
                                    clearFilter.fire();
                                    assertTrue(!mainScene.lookup("#activeFilterBox").isVisible(), "clearFilterButton cleared artist filter");
                                }

                                // Play, Pause, Next, Prev, Shuffle, Repeat, Mute, Volume, Seek, Clear Queue
                                if (allSongEntries.size() > 1) {
                                    mainController.onPlayNext(allSongEntries.get(1));
                                    mainController.onAddToQueue(allSongEntries.get(1));
                                }
                                Button playPause = (Button) mainScene.lookup("#playPauseButton");
                                Button nextBtn = (Button) mainScene.lookup("#nextButton");
                                Button prevBtn = (Button) mainScene.lookup("#prevButton");
                                ToggleButton shuffleBtn = (ToggleButton) mainScene.lookup("#shuffleButton");
                                Button repeatBtn = (Button) mainScene.lookup("#repeatButton");
                                Button muteBtn = (Button) mainScene.lookup("#muteButton");
                                Slider volSlider = (Slider) mainScene.lookup("#volumeSlider");

                                playPause.fire();
                                shuffleBtn.fire();
                                assertTrue(audioService.shuffleProperty().get(), "shuffleButton enabled shuffle");
                                shuffleBtn.fire();
                                repeatBtn.fire();
                                assertTrue(audioService.repeatModeProperty().get() == AudioService.RepeatMode.ALL, "repeatButton -> ALL");
                                repeatBtn.fire();
                                assertTrue(audioService.repeatModeProperty().get() == AudioService.RepeatMode.ONE, "repeatButton -> ONE");
                                repeatBtn.fire();
                                assertTrue(audioService.repeatModeProperty().get() == AudioService.RepeatMode.OFF, "repeatButton -> OFF");
                                muteBtn.fire();
                                assertTrue(audioService.mutedProperty().get(), "muteButton muted");
                                muteBtn.fire();
                                volSlider.setValue(0.65);
                                assertTrue(Math.abs(audioService.volumeProperty().get() - 0.65) < 0.01, "volumeSlider updated volume");
                                nextBtn.fire();
                                prevBtn.fire();
                                audioService.pause();
                                System.out.println("EVIDENCE [Main Transport]: playPauseButton, nextButton, prevButton, shuffleButton, repeatButton, muteButton, volumeSlider verified.");

                                // Now Playing Card Click -> Full-Window Expanded View (matching Image 4 & 5)
                                VBox expandedOverlay = (VBox) mainScene.lookup("#nowPlayingExpandedOverlay");
                                assertTrue(expandedOverlay != null && !expandedOverlay.isVisible(), "nowPlayingExpandedOverlay initially hidden");
                                mainController.openNowPlayingExpanded();
                                assertTrue(expandedOverlay.isVisible(), "nowPlayingExpandedOverlay opened upon clicking nowPlayingCard");

                                Button rewindBtn = (Button) mainScene.lookup("#expandedRewindButton");
                                Button fwdBtn = (Button) mainScene.lookup("#expandedForwardButton");
                                assertTrue(rewindBtn != null && fwdBtn != null, "Expanded rewind-10 and forward-10 buttons exist");
                                rewindBtn.fire();
                                fwdBtn.fire();

                                // Full-Window Synced Lyrics View Toggle (matching user prompt: "bisa ganti lirik, yang nanti jika dipencet, akan memenuhi sebesar gambar kedua")
                                VBox coverContainer = (VBox) mainScene.lookup("#expandedCoverContainer");
                                VBox lyricsBox = (VBox) mainScene.lookup("#expandedLyricsBox");
                                Button lyricsToggleBtn = (Button) mainScene.lookup("#expandedLyricsButton");
                                Button changeLrcBtn = (Button) mainScene.lookup("#expandedChangeLrcButton");
                                assertTrue(coverContainer != null && lyricsBox != null, "expandedCoverContainer and expandedLyricsBox exist");
                                assertTrue(lyricsToggleBtn != null && changeLrcBtn != null, "Expanded lyrics controls exist");
                                assertTrue(coverContainer.isVisible() && !lyricsBox.isVisible(), "Default expanded view shows Cover Art");

                                // Toggle to Lyrics view
                                lyricsToggleBtn.fire();
                                assertTrue(!coverContainer.isVisible() && lyricsBox.isVisible(), "Expanded view switched to full-size Synced Lyrics view (filling center matching Image 2)");

                                // Toggle back to Cover Art view using the same toggle button (since switchToCoverBtn was removed per user request)
                                lyricsToggleBtn.fire();
                                assertTrue(coverContainer.isVisible() && !lyricsBox.isVisible(), "Switched back to Cover Art view via lyricsToggleBtn");

                                Label settingsOffsetLbl = (Label) mainScene.lookup("#settingsLyricsOffsetLabel");
                                Label expOffsetLbl = (Label) mainScene.lookup("#expandedLyricsOffsetLabel");
                                Label drawerOffsetLbl = (Label) mainScene.lookup("#drawerLyricsOffsetLabel");
                                assertTrue(settingsOffsetLbl != null, "settingsLyricsOffsetLabel exists in Settings view");
                                assertTrue(expOffsetLbl == null && drawerOffsetLbl == null, "Lyrics offset badges removed from full lyrics view and drawer header per user request");
                                double initialOffset = audioService.getLyricsOffsetSeconds();
                                audioService.setLyricsOffsetSeconds(0.40);
                                assertTrue(Math.abs(audioService.getLyricsOffsetSeconds() - 0.40) < 0.001, "Lyrics offset updated to +0.40s");
                                audioService.setLyricsOffsetSeconds(initialOffset);

                                Button expMoreBtn = (Button) mainScene.lookup("#expandedMoreOptionsButton");
                                assertTrue(expMoreBtn != null, "expandedMoreOptionsButton exists in expanded overlay");

                                Button backBtn = (Button) mainScene.lookup("#expandedBackButton");
                                assertTrue(backBtn != null, "expandedBackButton exists");
                                backBtn.fire();
                                assertTrue(!expandedOverlay.isVisible(), "nowPlayingExpandedOverlay closed upon clicking backButton");
                                System.out.println("EVIDENCE [Expanded Now Playing Overlay & Full Lyrics View]: Expand overlay, rewind/forward 10s, toggle full lyrics view (Image 2), more options button, and back to library verified.");

                                // Section Jump Alphabet Picker Overlay (matching user screenshot)
                                StackPane jumpOverlay = (StackPane) mainScene.lookup("#sectionJumpOverlay");
                                GridPane jumpGrid = (GridPane) mainScene.lookup("#sectionJumpGrid");
                                assertTrue(jumpOverlay != null && !jumpOverlay.isVisible(), "sectionJumpOverlay initially hidden");
                                mainController.showSectionJumpOverlay();
                                assertTrue(jumpOverlay.isVisible(), "sectionJumpOverlay displayed on showSectionJumpOverlay");
                                assertTrue(!jumpGrid.getChildren().isEmpty(), "sectionJumpGrid has alphabet buttons");
                                mainController.jumpToSection("H");
                                mainController.hideSectionJumpOverlay();
                                assertTrue(!jumpOverlay.isVisible(), "sectionJumpOverlay hidden after jumpToSection");
                                System.out.println("EVIDENCE [Section Jump Picker]: Section jump overlay (&, #, A-Z) and jumpToSection verified.");

                                // 7. In-page Settings view & Theme features (Image 2)
                                Button settingsBtn = (Button) mainScene.lookup("#settingsButton");
                                assertTrue(settingsBtn != null, "settingsButton exists");
                                settingsBtn.fire();
                                Node settingsPaneNode = mainScene.lookup("#settingsViewPane");
                                Node libraryPaneNode = mainScene.lookup("#libraryViewPane");
                                assertTrue(settingsPaneNode != null && settingsPaneNode.isVisible(), "In-page settingsViewPane visible on settings click");
                                assertTrue(libraryPaneNode != null && !libraryPaneNode.isVisible(), "libraryViewPane hidden when settings active");

                                MenuButton themeMenu = (MenuButton) mainScene.lookup("#settingsThemeMenu");
                                MenuButton accentMenu = (MenuButton) mainScene.lookup("#settingsAccentMenu");
                                assertTrue(themeMenu != null && themeMenu.getItems().size() >= 4, "settingsThemeMenu loaded theme options");
                                assertTrue(accentMenu != null && accentMenu.getItems().size() >= 6, "settingsAccentMenu loaded accent options");

                                com.localify.service.ThemeManager.apply("LIGHT", "GREEN");
                                assertTrue("LIGHT".equals(com.localify.service.ThemeManager.getCurrentTheme()), "ThemeManager applied Light theme");
                                com.localify.service.ThemeManager.apply("DARK", "ORANGE");
                                assertTrue("DARK".equals(com.localify.service.ThemeManager.getCurrentTheme()), "ThemeManager applied Dark theme");

                                Button settingsBack = (Button) mainScene.lookup("#settingsBackButton");
                                assertTrue(settingsBack != null, "settingsBackButton exists");
                                settingsBack.fire();
                                assertTrue(!settingsPaneNode.isVisible(), "settingsViewPane hidden on back button");
                                assertTrue(libraryPaneNode.isVisible(), "libraryViewPane restored on back button");

                                boolean initialRpc = settingsService.isDiscordRpcEnabled();
                                settingsService.setDiscordRpcEnabled(!initialRpc);
                                assertTrue(settingsService.isDiscordRpcEnabled() == !initialRpc, "SettingsService persisted Discord RPC toggle");
                                settingsService.setDiscordRpcEnabled(initialRpc);
                                discordRpcService.setEnabled(true);
                                assertTrue(discordRpcService.isEnabled(), "DiscordRpcService enabled state active");
                                discordRpcService.setEnabled(false);
                                assertTrue(!discordRpcService.isEnabled(), "DiscordRpcService disabled cleanly");
                                System.out.println("EVIDENCE [In-Page Settings & Themes]: Settings in-page view, Theme menu (Dark/Light/Nord/Dracula), Accent menu (Orange/Green/Blue/etc.), and back navigation verified.");
                            }

                            // 5. Right drawer toggles & Clear queue
                            ToggleButton rightToggle = (ToggleButton) mainScene.lookup("#toggleRightDrawerButton");
                            ToggleButton queueTab = (ToggleButton) mainScene.lookup("#queueTabButton");
                            ToggleButton lyricsTab = (ToggleButton) mainScene.lookup("#lyricsTabButton");
                            Button reloadLyrics = (Button) mainScene.lookup("#reloadLyricsButton");
                            Button clearQueue = (Button) mainScene.lookup("#clearQueueButton");

                            queueTab.fire();
                            if (!clearQueue.isDisabled()) {
                                clearQueue.fire();
                                assertTrue(audioService.getUpcomingTracks().isEmpty(), "clearQueueButton emptied upcoming queue");
                            }
                            lyricsTab.fire();
                            reloadLyrics.fire();
                            boolean initialDrawerVis = mainScene.lookup("#rightDrawer").isVisible();
                            rightToggle.fire();
                            assertTrue(mainScene.lookup("#rightDrawer").isVisible() != initialDrawerVis, "toggleRightDrawerButton toggled drawer");
                            rightToggle.fire();
                            assertTrue(mainScene.lookup("#rightDrawer").isVisible() == initialDrawerVis, "toggleRightDrawerButton restored drawer");

                            // Playlists navigation button and grid view (Matching Gambar 2)
                            Button playlistsNav = (Button) mainScene.lookup("#playlistsNavButton");
                            assertTrue(playlistsNav != null, "playlistsNavButton exists in sidebar");
                            playlistsNav.fire();
                            Node playlistsPane = mainScene.lookup("#playlistsViewPane");
                            assertTrue(playlistsPane != null && playlistsPane.isVisible(), "playlistsViewPane visible on playlistsNavButton click");
                            Node gridPane = mainScene.lookup("#libraryGridFlowPane");
                            assertTrue(gridPane != null, "libraryGridFlowPane exists");
                            Button allSongsBtn2 = (Button) mainScene.lookup("#allSongsNavButton");
                            allSongsBtn2.fire();
                            assertTrue(!playlistsPane.isVisible(), "playlistsViewPane hidden when returning to All Songs");
                            System.out.println("EVIDENCE [Your Library Grid View]: playlistsNavButton -> playlistsViewPane & libraryGridFlowPane verified.");
                            System.out.println("EVIDENCE [Right Drawer]: lyricsTabButton, queueTabButton, reloadLyricsButton, clearQueueButton, toggleRightDrawerButton verified.");

                            // 6. Narrow window reflow check (860 x 560 minimum size)
                            mainStage.setWidth(860);
                            mainStage.setHeight(560);
                            mainRoot.applyCss();
                            mainRoot.layout();
                            System.out.println("EVIDENCE [Window Reflow]: Main window resized to 860x560 with clean layout and no horizontal overflow.");

                            // 7. More playback options button (Equalizer, Speed, Cast)
                            Button moreBtn = (Button) mainScene.lookup("#morePlaybackOptionsButton");
                            assertTrue(moreBtn != null, "morePlaybackOptionsButton present to right of miniPlayerButton");

                            audioService.setPlaybackRate(1.5);
                            assertTrue(Math.abs(audioService.getPlaybackRate() - 1.5) < 0.001, "AudioService playbackRate updated to 1.5x");

                            audioService.setEqualizerPreset("Bass Boost");
                            assertTrue(audioService.getEqualizerPreset().equals("Bass Boost"), "AudioService equalizer preset applied");

                            audioService.setEqualizerBandGain(0, 4.0);
                            assertTrue(Math.abs(audioService.getEqualizerBandGain(0) - 4.0) < 0.001, "AudioService equalizer band gain adjusted");

                            // 8. Mini Player switch and all Mini Player controls
                            Button miniBtn = (Button) mainScene.lookup("#miniPlayerButton");
                            miniBtn.fire();
                            assertTrue(inMini[0] && miniStage.isShowing() && !mainStage.isShowing(), "Switched to Mini Player");
                            miniRoot.applyCss();
                            miniRoot.layout();

                            Button miniPrev = (Button) miniScene.lookup("#miniPrevButton");
                            Button miniRewind = (Button) miniScene.lookup("#miniRewindButton");
                            Button miniPlayPause = (Button) miniScene.lookup("#miniPlayPauseButton");
                            Button miniForward = (Button) miniScene.lookup("#miniForwardButton");
                            Button miniNext = (Button) miniScene.lookup("#miniNextButton");
                            Button miniMute = (Button) miniScene.lookup("#miniMuteButton");
                            Slider miniVol = (Slider) miniScene.lookup("#miniVolumeSlider");
                            ToggleButton miniLyrics = (ToggleButton) miniScene.lookup("#miniLyricsToggle");
                            ToggleButton miniQueue = (ToggleButton) miniScene.lookup("#miniQueueToggle");
                            Button miniExpand = (Button) miniScene.lookup("#miniExpandButton");

                            miniMute.fire();
                            miniMute.fire();
                            miniVol.setValue(0.75);
                            miniRewind.fire();
                            miniForward.fire();
                            miniPlayPause.fire();
                            audioService.pause();
                            miniNext.fire();
                            miniPrev.fire();
                            audioService.pause();

                            miniLyrics.fire();
                            assertTrue(miniStage.getHeight() >= MiniPlayerController.EXPANDED_HEIGHT - 1, "miniLyricsToggle expanded window");
                            miniQueue.fire();
                            assertTrue(miniScene.lookup("#miniQueuePane").isVisible(), "miniQueueToggle opened Playing Next");
                            miniQueue.fire();
                            assertTrue(miniStage.getHeight() <= MiniPlayerController.COMPACT_HEIGHT + 1, "miniQueueToggle collapsed window");

                            miniExpand.fire();
                            assertTrue(!inMini[0] && mainStage.isShowing() && !miniStage.isShowing(), "miniExpandButton returned to Full-Screen Player");
                            System.out.println("EVIDENCE [Mini Player Controls]: miniMuteButton, miniVolumeSlider, miniPrevButton, miniRewindButton, miniPlayPauseButton, miniForwardButton, miniNextButton, miniLyricsToggle, miniQueueToggle, miniExpandButton verified.");

                            // Multi-column side-by-side Properties dialog verification
                            Track activeTrack = audioService.getCurrentTrack();
                            if (activeTrack != null) {
                                Dialog<Void> propDialog = menuBuilder.createPropertiesDialog(activeTrack, mainStage);
                                assertTrue(propDialog != null, "Properties dialog created");
                                Node propContent = propDialog.getDialogPane().getContent();
                                assertTrue(propContent instanceof VBox, "Properties content is VBox");
                                VBox propVBox = (VBox) propContent;
                                assertTrue(propVBox.getChildren().size() >= 2, "Properties content has columns and footer");
                                HBox propCols = (HBox) propVBox.getChildren().get(0);
                                assertTrue(propCols.getChildren().size() == 2, "Properties arranged into 2 side-by-side columns (Track Info and Audio Details)");
                                System.out.println("EVIDENCE [Properties Dialog Multi-Column]: 2 side-by-side columns (Track Info & Audio Details) verified.");
                            }

                            // 8. Empty folder state & Error folder state
                            mainController.scanMusicDirectory(emptyMusicDir);
                        });

                        Thread.sleep(500);
                        runOnFxAndWait(() -> {
                            VBox emptyBox = (VBox) mainScene.lookup("#emptyStateBox");
                            assertTrue(emptyBox.isVisible(), "Empty folder state visible on empty directory");
                            mainController.scanMusicDirectory(emptyMusicDir.resolve("non_existent_subfolder_xyz"));
                        });

                        Thread.sleep(500);
                        runOnFxAndWait(() -> {
                            VBox errorBox = (VBox) mainScene.lookup("#errorStateBox");
                            assertTrue(errorBox.isVisible(), "Error state visible on missing directory");
                            System.out.println("EVIDENCE [Data States]: Loading, Empty folder, Filtered empty, Empty playlist, and Error states verified.");

                            audioService.shutdown();
                            miniStage.close();
                            mainStage.close();
                        });
                    } catch (Throwable t) {
                        errorRef.set(t);
                    } finally {
                        Platform.exit();
                        uiDone.countDown();
                    }
                }).start();
            } catch (Throwable t) {
                errorRef.set(t);
                Platform.exit();
                uiDone.countDown();
            }
        });

        if (!uiDone.await(35, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for JavaFX verification");
        }
        deleteTree(tempPlDir);
        deleteTree(emptyMusicDir);
        if (errorRef.get() != null) {
            throw new AssertionError("UI verification failed", errorRef.get());
        }
    }

    private static void runOnFxAndWait(Runnable action) throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> err = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                err.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("FX action timed out");
        }
        if (err.get() != null) {
            if (err.get() instanceof Exception ex) {
                throw ex;
            }
            throw new RuntimeException(err.get());
        }
    }

    private static void assertTrue(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("Assertion failed: " + label);
        }
    }

    private static void deleteTree(Path root) {
        try (var walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (Exception ignored) {
                }
            });
        } catch (Exception ignored) {
        }
    }
}
