package com.localify.controller;

import com.localify.model.Playlist;
import com.localify.model.Track;
import com.localify.service.AudioService;
import com.localify.service.DiscordRpcService;
import com.localify.service.MusicScannerService;
import com.localify.service.PlaylistService;
import com.localify.service.SettingsService;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Dialog;
import javafx.scene.control.DialogPane;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Window;
import org.kordamp.ikonli.javafx.FontIcon;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Builds the track right-click / options menu and hosts the track tag editor,
 * file properties inspector, and playlist creation dialogs.
 */
public class ContextMenuBuilder {

    public interface TrackActionHandler {
        void onPlay(Track track);

        void onPlayNext(Track track);

        void onAddToQueue(Track track);

        void onAddToPlaylist(Playlist playlist, Track track);

        void onRemove(Track track);

        void onShowAlbum(String album);

        void onShowArtist(String artist);

        void onMetadataUpdated(Track track);

        void onStatusMessage(String message);

        default void onChangeLyrics(Track track) {}
    }

    private static final String CSS_RESOURCE = "/com/localify/css/spotify-dark.css";

    private final AudioService audioService;
    private final MusicScannerService scannerService;
    private final PlaylistService playlistService;

    public ContextMenuBuilder(
            AudioService audioService,
            MusicScannerService scannerService,
            PlaylistService playlistService) {
        this.audioService = audioService;
        this.scannerService = scannerService;
        this.playlistService = playlistService;
    }

    public ContextMenu buildTrackMenu(Track track, Window owner, String removeLabel, TrackActionHandler handler) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("track-context-menu");

        MenuItem playItem = item("Play", "mdi2p-play", () -> handler.onPlay(track));
        MenuItem playNextItem = item("Play next", "mdi2p-playlist-play", () -> handler.onPlayNext(track));

        Menu addToMenu = new Menu("Add to");
        addToMenu.setGraphic(icon("mdi2p-plus-box-multiple-outline"));
        MenuItem addQueueItem = item("Play queue", "mdi2p-playlist-plus", () -> handler.onAddToQueue(track));
        addToMenu.getItems().add(addQueueItem);
        addToMenu.getItems().add(new SeparatorMenuItem());

        for (Playlist playlist : playlistService.getPlaylists()) {
            MenuItem plItem = item(playlist.getName(), "mdi2p-playlist-music",
                    () -> handler.onAddToPlaylist(playlist, track));
            addToMenu.getItems().add(plItem);
        }

        MenuItem newPlaylistItem = item("New playlist...", "mdi2p-plus", () ->
                showCreatePlaylistDialog(owner, created -> handler.onAddToPlaylist(created, track)));
        addToMenu.getItems().add(newPlaylistItem);

        MenuItem showAlbumItem = item(
                track.hasAlbum() ? "Show album" : "Show album (untagged)",
                "mdi2a-album",
                () -> handler.onShowAlbum(track.getAlbum()));
        showAlbumItem.setDisable(!track.hasAlbum());

        MenuItem showArtistItem = item(
                track.hasArtist() ? "Show artist" : "Show artist (untagged)",
                "mdi2a-account-music",
                () -> handler.onShowArtist(track.getArtist()));
        showArtistItem.setDisable(!track.hasArtist());

        MenuItem editInfoItem = item("Edit info", "mdi2p-pencil-outline",
                () -> showEditInfoDialog(track, owner, handler));

        MenuItem changeLrcItem = item("Tambah / Ganti lirik (.lrc)...", "mdi2t-text-box-search-outline",
                () -> handler.onChangeLyrics(track));

        MenuItem syncLyricsItem = item("Sinkronisasi lirik (Offset)...", "mdi2s-sync",
                () -> showLyricsOffsetDialog(track, owner));

        MenuItem propertiesItem = item("Properties", "mdi2i-information-outline",
                () -> showPropertiesDialog(track, owner));

        MenuItem removeItem = item(
                removeLabel != null && !removeLabel.isBlank() ? removeLabel : "Remove",
                "mdi2d-delete-outline",
                () -> handler.onRemove(track));
        removeItem.getStyleClass().add("danger-menu-item");

        menu.getItems().addAll(
                playItem,
                playNextItem,
                addToMenu,
                new SeparatorMenuItem(),
                showAlbumItem,
                showArtistItem,
                new SeparatorMenuItem(),
                editInfoItem,
                changeLrcItem,
                syncLyricsItem,
                propertiesItem,
                new SeparatorMenuItem(),
                removeItem
        );
        return menu;
    }

    public void showCreatePlaylistDialog(Window owner, Consumer<Playlist> onCreated) {
        Dialog<Playlist> dialog = new Dialog<>();
        dialog.setTitle("New playlist");
        dialog.setHeaderText("Create a playlist (.m3u8)");
        configureOwnerAndStyle(dialog, owner);

        ButtonType createType = new ButtonType("Create playlist", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(createType, ButtonType.CANCEL);

        TextField nameField = new TextField();
        nameField.setPromptText("e.g. Late Night Acoustic");
        nameField.setAccessibleText("Playlist name");

        Label nameLabel = new Label("Playlist name");
        nameLabel.setLabelFor(nameField);
        nameLabel.getStyleClass().add("field-label");

        Label validationLabel = new Label();
        validationLabel.getStyleClass().add("validation-error-label");
        validationLabel.setWrapText(true);
        validationLabel.setVisible(false);
        validationLabel.setManaged(false);

        VBox content = new VBox(8, nameLabel, nameField, validationLabel);
        content.setPadding(new Insets(8, 4, 4, 4));
        content.setPrefWidth(360);
        dialog.getDialogPane().setContent(content);

        Node createButton = dialog.getDialogPane().lookupButton(createType);
        createButton.getStyleClass().add("accent-button");

        Runnable validate = () -> {
            String problem = playlistService.validateName(nameField.getText());
            createButton.setDisable(problem != null);
            boolean showProblem = !nameField.getText().isBlank() && problem != null;
            validationLabel.setText(problem != null ? problem : "");
            validationLabel.setVisible(showProblem);
            validationLabel.setManaged(showProblem);
        };
        nameField.textProperty().addListener((obs, oldVal, newVal) -> validate.run());
        validate.run();

        Platform.runLater(nameField::requestFocus);

        dialog.setResultConverter(btn -> {
            if (btn == createType) {
                try {
                    return playlistService.create(nameField.getText());
                } catch (IOException | IllegalArgumentException ex) {
                    showErrorAlert(owner, "Could not create playlist", ex.getMessage());
                }
            }
            return null;
        });

        Optional<Playlist> created = dialog.showAndWait();
        created.ifPresent(playlist -> {
            if (onCreated != null) {
                onCreated.accept(playlist);
            }
        });
    }

    public void showEditInfoDialog(Track track, Window owner, TrackActionHandler handler) {
        Dialog<Boolean> dialog = new Dialog<>();
        dialog.setTitle("Edit info");
        dialog.setHeaderText("Edit ID3 metadata for " + track.getFileName());
        configureOwnerAndStyle(dialog, owner);

        ButtonType saveType = new ButtonType("Save tags", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(saveType, ButtonType.CANCEL);

        TextField titleField = new TextField(track.getTitle());
        titleField.setPromptText("Track title");
        titleField.setAccessibleText("Track title");

        TextField artistField = new TextField(track.getArtist());
        artistField.setPromptText("Artist name");
        artistField.setAccessibleText("Artist name");

        TextField albumField = new TextField(track.getAlbum());
        albumField.setPromptText("Album title");
        albumField.setAccessibleText("Album title");

        TextField genreField = new TextField(track.getGenre());
        genreField.setPromptText("Genre");
        genreField.setAccessibleText("Genre");

        TextField yearField = new TextField(track.getYear());
        yearField.setPromptText("Release year");
        yearField.setAccessibleText("Release year");

        GridPane grid = formGrid();
        addFormRow(grid, 0, "Title", titleField);
        addFormRow(grid, 1, "Artist", artistField);
        addFormRow(grid, 2, "Album", albumField);
        addFormRow(grid, 3, "Genre", genreField);
        addFormRow(grid, 4, "Year", yearField);

        Label errorLabel = new Label();
        errorLabel.getStyleClass().add("validation-error-label");
        errorLabel.setWrapText(true);
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        VBox wrapper = new VBox(10, grid, errorLabel);
        wrapper.setPadding(new Insets(8, 4, 4, 4));
        wrapper.setPrefWidth(420);
        dialog.getDialogPane().setContent(wrapper);

        Node saveBtn = dialog.getDialogPane().lookupButton(saveType);
        saveBtn.getStyleClass().add("accent-button");
        saveBtn.setDisable(titleField.getText().isBlank());
        titleField.textProperty().addListener((obs, oldV, newV) -> saveBtn.setDisable(newV == null || newV.isBlank()));

        Platform.runLater(titleField::requestFocus);

        dialog.setResultConverter(button -> {
            if (button != saveType) {
                return Boolean.FALSE;
            }
            String newTitle = titleField.getText().strip();
            String newArtist = artistField.getText().strip();
            String newAlbum = albumField.getText().strip();
            String newGenre = genreField.getText().strip();
            String newYear = yearField.getText().strip();
            try {
                audioService.withReleasedFile(track, () ->
                        scannerService.writeTags(track.getPath(), newTitle, newArtist, newAlbum, newGenre, newYear));
                track.setTitle(newTitle);
                track.setArtist(newArtist);
                track.setAlbum(newAlbum);
                track.setGenre(newGenre);
                track.setYear(newYear);
                return Boolean.TRUE;
            } catch (Exception ex) {
                showErrorAlert(owner, "Could not write audio tags",
                        "Localify could not update \"" + track.getFileName() + "\": " + ex.getMessage());
                return Boolean.FALSE;
            }
        });

        Optional<Boolean> saved = dialog.showAndWait();
        if (saved.orElse(false)) {
            for (Playlist pl : playlistService.getPlaylists()) {
                if (pl.getTracks().contains(track)) {
                    try {
                        playlistService.save(pl);
                    } catch (IOException ignored) {
                        // Playlist display still reflects in-memory Track properties.
                    }
                }
            }
            handler.onMetadataUpdated(track);
            handler.onStatusMessage("Saved metadata for \"" + track.getTitle() + "\".");
        }
    }

    public void showLyricsOffsetDialog(Track track, Window owner) {
        if (track == null) return;
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Sinkronisasi Lirik");
        dialog.setHeaderText("Timing Lirik: " + track.getTitle());
        configureOwnerAndStyle(dialog, owner);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox box = new VBox(14);
        box.setPadding(new Insets(16));
        box.setPrefWidth(440);

        Label infoLabel = new Label("Atur offset waktu kemunculan lirik untuk lagu ini jika vokal mendahului atau terlambat dari lirik.\nNilai (-) membuat lirik muncul lebih lambat/delay, nilai (+) membuat lirik lebih cepat.");
        infoLabel.setWrapText(true);
        infoLabel.setStyle("-fx-text-fill: #B3B3B3; -fx-font-size: 12px;");

        SettingsService settings = audioService != null ? audioService.getSettingsService() : null;

        Label offsetLabel = new Label();
        offsetLabel.getStyleClass().add("count-pill");
        offsetLabel.setStyle("-fx-background-color: #1A1A1A; -fx-border-color: -localify-accent; -fx-border-width: 1px; -fx-border-radius: 6px; -fx-background-radius: 6px; -fx-text-fill: -localify-accent; -fx-font-weight: bold; -fx-font-size: 13px; -fx-padding: 4 12 4 12; -fx-min-width: 90px; -fx-alignment: center;");

        Label statusHint = new Label();
        statusHint.setStyle("-fx-text-fill: #9E9E9E; -fx-font-size: 11.5px;");

        Runnable updateUI = () -> {
            double off = settings != null ? settings.getEffectiveLyricsOffset(track) : 0.0;
            boolean custom = settings != null && settings.hasTrackLyricsOffset(track);
            String sign = off > 0 ? "+" : "";
            offsetLabel.setText(String.format(java.util.Locale.US, "%s%.2fs", sign, off));
            if (custom) {
                statusHint.setText("Tersimpan khusus untuk lagu ini");
            } else {
                statusHint.setText("Mengikuti default global player");
            }
        };
        updateUI.run();

        HBox btnRow = new HBox(6);
        btnRow.setAlignment(Pos.CENTER);

        Button minus02 = new Button("-0.2s");
        minus02.getStyleClass().add("secondary-button");
        minus02.setMinWidth(46);

        Button minus01 = new Button("-0.1s");
        minus01.getStyleClass().add("secondary-button");
        minus01.setMinWidth(46);

        Button plus01 = new Button("+0.1s");
        plus01.getStyleClass().add("secondary-button");
        plus01.setMinWidth(46);

        Button plus02 = new Button("+0.2s");
        plus02.getStyleClass().add("secondary-button");
        plus02.setMinWidth(46);

        minus02.setOnAction(e -> {
            double off = settings != null ? settings.getEffectiveLyricsOffset(track) : 0.0;
            double next = Math.round((off - 0.20) * 100.0) / 100.0;
            if (audioService != null) audioService.setTrackLyricsOffsetSeconds(track, next);
            updateUI.run();
        });

        minus01.setOnAction(e -> {
            double off = settings != null ? settings.getEffectiveLyricsOffset(track) : 0.0;
            double next = Math.round((off - 0.10) * 100.0) / 100.0;
            if (audioService != null) audioService.setTrackLyricsOffsetSeconds(track, next);
            updateUI.run();
        });

        plus01.setOnAction(e -> {
            double off = settings != null ? settings.getEffectiveLyricsOffset(track) : 0.0;
            double next = Math.round((off + 0.10) * 100.0) / 100.0;
            if (audioService != null) audioService.setTrackLyricsOffsetSeconds(track, next);
            updateUI.run();
        });

        plus02.setOnAction(e -> {
            double off = settings != null ? settings.getEffectiveLyricsOffset(track) : 0.0;
            double next = Math.round((off + 0.20) * 100.0) / 100.0;
            if (audioService != null) audioService.setTrackLyricsOffsetSeconds(track, next);
            updateUI.run();
        });

        btnRow.getChildren().addAll(minus02, minus01, offsetLabel, plus01, plus02);

        HBox actionRow = new HBox(10);
        actionRow.setAlignment(Pos.CENTER);

        Button resetBtn = new Button("Gunakan Default Global");
        resetBtn.getStyleClass().add("secondary-button");
        resetBtn.setOnAction(e -> {
            if (audioService != null) audioService.resetTrackLyricsOffset(track);
            updateUI.run();
        });

        actionRow.getChildren().addAll(resetBtn);

        VBox centerBox = new VBox(8, btnRow, statusHint, actionRow);
        centerBox.setAlignment(Pos.CENTER);

        box.getChildren().addAll(infoLabel, centerBox);
        dialog.getDialogPane().setContent(box);
        dialog.showAndWait();
    }

    public void showPropertiesDialog(Track track, Window owner) {
        Dialog<Void> dialog = createPropertiesDialog(track, owner);
        dialog.showAndWait();
    }

    public Dialog<Void> createPropertiesDialog(Track track, Window owner) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Properties");
        dialog.setHeaderText(track.getTitle() + " (" + track.getDisplayArtist() + ")");
        configureOwnerAndStyle(dialog, owner);

        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Map<String, String> props = scannerService.readProperties(track);

        List<String> leftOrder = List.of("Artist", "Album", "Genre", "Year", "Length", "Cover art");
        List<String> rightOrder = List.of("Format", "Bitrate", "Sample rate", "Channels", "Encoding", "Size", "Modified");

        Map<String, String> leftProps = new LinkedHashMap<>();
        Map<String, String> rightProps = new LinkedHashMap<>();

        for (String k : leftOrder) {
            if (props.containsKey(k)) {
                leftProps.put(k, props.get(k));
            }
        }

        for (String k : rightOrder) {
            if (props.containsKey(k)) {
                rightProps.put(k, props.get(k));
            }
        }

        for (Map.Entry<String, String> entry : props.entrySet()) {
            if (!leftProps.containsKey(entry.getKey()) && !rightProps.containsKey(entry.getKey())) {
                if (leftProps.size() <= rightProps.size()) {
                    leftProps.put(entry.getKey(), entry.getValue());
                } else {
                    rightProps.put(entry.getKey(), entry.getValue());
                }
            }
        }

        GridPane leftGrid = createPropertyGrid(leftProps, 75);
        GridPane rightGrid = createPropertyGrid(rightProps, 88);

        VBox leftCol = new VBox(6);
        Label leftTitle = new Label("Track Information");
        leftTitle.getStyleClass().add("prop-section-header");
        leftCol.getChildren().addAll(leftTitle, leftGrid);
        HBox.setHgrow(leftCol, Priority.ALWAYS);

        VBox rightCol = new VBox(6);
        Label rightTitle = new Label("Audio & File Details");
        rightTitle.getStyleClass().add("prop-section-header");
        rightCol.getChildren().addAll(rightTitle, rightGrid);
        HBox.setHgrow(rightCol, Priority.ALWAYS);

        HBox columns = new HBox(24, leftCol, rightCol);
        columns.setAlignment(Pos.TOP_LEFT);

        Button openFolderBtn = new Button("Open file location");
        openFolderBtn.setGraphic(icon("mdi2f-folder-open-outline"));
        openFolderBtn.getStyleClass().add("secondary-button");
        openFolderBtn.setOnAction(e -> openInSystemExplorer(track.getPath(), owner));

        HBox footer = new HBox(openFolderBtn);
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.setPadding(new Insets(10, 0, 0, 0));

        VBox content = new VBox(12, columns, footer);
        content.setPadding(new Insets(8, 6, 6, 6));
        content.setPrefWidth(680);
        dialog.getDialogPane().setContent(content);

        return dialog;
    }

    private static GridPane createPropertyGrid(Map<String, String> properties, double labelWidth) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(8);

        ColumnConstraints col0 = new ColumnConstraints();
        col0.setMinWidth(labelWidth);
        col0.setPrefWidth(labelWidth);

        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);

        grid.getColumnConstraints().addAll(col0, col1);

        int row = 0;
        for (Map.Entry<String, String> entry : properties.entrySet()) {
            Label keyLabel = new Label(entry.getKey());
            keyLabel.getStyleClass().add("prop-key-label");

            TextField valueView = new TextField(entry.getValue());
            valueView.setEditable(false);
            valueView.getStyleClass().add("prop-value-field");
            valueView.setAccessibleText(entry.getKey() + ": " + entry.getValue());
            GridPane.setHgrow(valueView, Priority.ALWAYS);

            grid.add(keyLabel, 0, row);
            grid.add(valueView, 1, row);
            row++;
        }
        return grid;
    }

    public void showSettingsDialog(Window owner, SettingsService settingsService, DiscordRpcService discordService) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Settings");
        dialog.setHeaderText("Localify Preferences");
        configureOwnerAndStyle(dialog, owner);

        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CLOSE);

        Label rpcTitle = new Label("Discord Rich Presence (RPC)");
        rpcTitle.getStyleClass().add("section-caption");

        javafx.scene.control.CheckBox rpcCheck = new javafx.scene.control.CheckBox("Enable Discord Rich Presence");
        rpcCheck.setSelected(discordService.isEnabled());
        rpcCheck.getStyleClass().add("settings-checkbox");

        Label statusLabel = new Label();
        statusLabel.textProperty().bind(javafx.beans.binding.Bindings.concat("Status: ", discordService.connectionStatusProperty()));
        statusLabel.getStyleClass().add("muted-hint-label");

        Label descLabel = new Label("Displays the active song title, artist, album, and live playback time on your Discord profile.");
        descLabel.setWrapText(true);
        descLabel.getStyleClass().add("state-detail");

        rpcCheck.selectedProperty().addListener((obs, oldV, isSelected) -> {
            discordService.setEnabled(isSelected);
        });

        VBox content = new VBox(10, rpcTitle, rpcCheck, statusLabel, descLabel);
        content.setPadding(new Insets(10, 8, 10, 8));
        content.setPrefWidth(460);
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait();
    }

    public void showEqualizerDialog(Window owner, AudioService audioService) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Equalizer");
        dialog.setHeaderText("Equalizer (10 Bands)");
        configureOwnerAndStyle(dialog, owner);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Label statusTitle = new Label("Audio Equalizer");
        statusTitle.getStyleClass().add("settings-card-title");
        Label statusDesc = new Label("Fine-tune frequency curve in real time (-12 dB to +12 dB)");
        statusDesc.getStyleClass().add("settings-card-subtitle");

        CheckBox enableCheck = new CheckBox("Enable Equalizer");
        enableCheck.getStyleClass().add("settings-checkbox");
        enableCheck.setSelected(audioService.isEqualizerEnabled());

        Label presetLabel = new Label("Preset:");
        presetLabel.getStyleClass().add("filter-menu-label");

        ComboBox<String> presetCombo = new ComboBox<>(FXCollections.observableArrayList(
                "Flat", "Bass Boost", "Treble Boost", "Rock", "Pop", "Vocal", "Electronic", "Custom"
        ));
        presetCombo.getStyleClass().add("filter-menu-button");
        presetCombo.setValue(audioService.getEqualizerPreset());

        Button resetBtn = new Button("Reset");
        resetBtn.getStyleClass().add("secondary-button");
        resetBtn.setGraphic(icon("mdi2r-refresh"));

        HBox topControls = new HBox(12, enableCheck, new Region(), presetLabel, presetCombo, resetBtn);
        topControls.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(topControls.getChildren().get(1), Priority.ALWAYS);

        String[] freqLabels = {"32Hz", "64Hz", "125Hz", "250Hz", "500Hz", "1kHz", "2kHz", "4kHz", "8kHz", "16kHz"};
        HBox slidersBox = new HBox(14);
        slidersBox.setAlignment(Pos.CENTER);
        slidersBox.setPadding(new Insets(16, 8, 12, 8));
        slidersBox.getStyleClass().add("equalizer-bands-box");

        Slider[] bandSliders = new Slider[10];
        Label[] gainLabels = new Label[10];
        boolean[] updatingFromPreset = new boolean[]{false};

        for (int i = 0; i < 10; i++) {
            final int bandIndex = i;
            double currentGain = audioService.getEqualizerBandGain(bandIndex);

            Label gainValLabel = new Label(formatGain(currentGain));
            gainValLabel.getStyleClass().add("equalizer-gain-label");
            gainLabels[i] = gainValLabel;

            Slider slider = new Slider(-12.0, 12.0, currentGain);
            slider.setOrientation(Orientation.VERTICAL);
            slider.setPrefHeight(160);
            slider.getStyleClass().add("equalizer-slider");
            bandSliders[i] = slider;

            slider.valueProperty().addListener((obs, oldVal, newVal) -> {
                double val = newVal.doubleValue();
                gainValLabel.setText(formatGain(val));
                audioService.setEqualizerBandGain(bandIndex, val);
                if (!updatingFromPreset[0]) {
                    presetCombo.setValue("Custom");
                }
            });

            Label freqLabel = new Label(freqLabels[i]);
            freqLabel.getStyleClass().add("equalizer-freq-label");

            VBox bandColumn = new VBox(6, gainValLabel, slider, freqLabel);
            bandColumn.setAlignment(Pos.CENTER);
            slidersBox.getChildren().add(bandColumn);
        }

        enableCheck.selectedProperty().addListener((obs, oldVal, isSelected) -> {
            audioService.setEqualizerEnabled(isSelected);
            slidersBox.setDisable(!isSelected);
        });
        slidersBox.setDisable(!enableCheck.isSelected());

        presetCombo.valueProperty().addListener((obs, oldV, newPreset) -> {
            if (newPreset != null && !newPreset.equals("Custom")) {
                updatingFromPreset[0] = true;
                audioService.setEqualizerPreset(newPreset);
                for (int i = 0; i < 10; i++) {
                    double g = audioService.getEqualizerBandGain(i);
                    bandSliders[i].setValue(g);
                    gainLabels[i].setText(formatGain(g));
                }
                updatingFromPreset[0] = false;
            }
        });

        resetBtn.setOnAction(e -> presetCombo.setValue("Flat"));

        VBox content = new VBox(14, new VBox(2, statusTitle, statusDesc), topControls, slidersBox);
        content.setPadding(new Insets(12, 16, 12, 16));
        content.setPrefWidth(540);
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait();
    }

    private static String formatGain(double gain) {
        if (Math.abs(gain) < 0.05) {
            return "0 dB";
        }
        return (gain > 0 ? "+" : "") + String.format(Locale.ROOT, "%.1f dB", gain);
    }

    public void showCastToDeviceDialog(Window owner, AudioService audioService) {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("Cast to a device");
        dialog.setHeaderText("Cast to a device");
        configureOwnerAndStyle(dialog, owner);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        Label title = new Label("Cast playback to external speaker or display");
        title.getStyleClass().add("settings-card-title");
        Label subtitle = new Label("Select an available playback endpoint on your local network or Bluetooth");
        subtitle.getStyleClass().add("settings-card-subtitle");

        HBox currentBox = new HBox(12);
        currentBox.setAlignment(Pos.CENTER_LEFT);
        currentBox.getStyleClass().addAll("settings-card", "active-device-card");
        currentBox.setPadding(new Insets(12, 14, 12, 14));

        FontIcon pcIcon = icon("mdi2s-speaker");
        pcIcon.setIconSize(24);
        VBox pcInfo = new VBox(2);
        Label pcTitle = new Label("This PC (Default Output)");
        pcTitle.getStyleClass().add("settings-card-title");
        Label pcSub = new Label("Local System Speakers / Headphone Jack");
        pcSub.getStyleClass().add("settings-card-subtitle");
        pcInfo.getChildren().addAll(pcTitle, pcSub);
        HBox.setHgrow(pcInfo, Priority.ALWAYS);

        Label activeBadge = new Label("Connected");
        activeBadge.getStyleClass().add("count-pill");

        currentBox.getChildren().addAll(pcIcon, pcInfo, activeBadge);

        Label availableHeader = new Label("Available devices");
        availableHeader.getStyleClass().add("settings-section-title");

        VBox devicesList = new VBox(8);

        record DiscoveredDevice(String name, String type, String iconCode) {}
        List<DiscoveredDevice> samples = List.of(
                new DiscoveredDevice("Living Room TV", "DLNA / UPnP Media Renderer", "mdi2t-television"),
                new DiscoveredDevice("Bluetooth Audio Receiver", "Bluetooth A2DP Sink", "mdi2b-bluetooth-audio"),
                new DiscoveredDevice("Bedroom Smart Display", "Google Cast / Network Endpoint", "mdi2c-cast-connected")
        );

        Label statusBanner = new Label("");
        statusBanner.getStyleClass().add("settings-card-subtitle");
        statusBanner.setVisible(false);
        statusBanner.setManaged(false);

        for (DiscoveredDevice d : samples) {
            HBox devRow = new HBox(12);
            devRow.setAlignment(Pos.CENTER_LEFT);
            devRow.getStyleClass().add("settings-card");
            devRow.setPadding(new Insets(10, 14, 10, 14));

            FontIcon devIcon = icon(d.iconCode());
            devIcon.setIconSize(22);
            VBox devInfo = new VBox(2);
            Label devName = new Label(d.name());
            devName.getStyleClass().add("settings-card-title");
            Label devType = new Label(d.type());
            devType.getStyleClass().add("settings-card-subtitle");
            devInfo.getChildren().addAll(devName, devType);
            HBox.setHgrow(devInfo, Priority.ALWAYS);

            Button connectBtn = new Button("Connect");
            connectBtn.getStyleClass().add("secondary-button");
            connectBtn.setOnAction(e -> {
                if (connectBtn.getText().equals("Connect")) {
                    connectBtn.setText("Disconnect");
                    connectBtn.getStyleClass().remove("secondary-button");
                    connectBtn.getStyleClass().add("accent-button");
                    statusBanner.setText("Casting audio stream to " + d.name() + " (" + d.type() + ")");
                    statusBanner.setVisible(true);
                    statusBanner.setManaged(true);
                    activeBadge.setText("Standby");
                } else {
                    connectBtn.setText("Connect");
                    connectBtn.getStyleClass().remove("accent-button");
                    connectBtn.getStyleClass().add("secondary-button");
                    statusBanner.setText("Reverted audio output to This PC.");
                    activeBadge.setText("Connected");
                }
            });

            devRow.getChildren().addAll(devIcon, devInfo, connectBtn);
            devicesList.getChildren().add(devRow);
        }

        Label hintLabel = new Label("Tip: Press Win+Ctrl+V to switch Windows primary audio output device anytime.");
        hintLabel.getStyleClass().add("section-caption");

        VBox content = new VBox(12,
                new VBox(2, title, subtitle),
                currentBox,
                availableHeader,
                devicesList,
                statusBanner,
                hintLabel
        );
        content.setPadding(new Insets(12, 16, 12, 16));
        content.setPrefWidth(480);
        dialog.getDialogPane().setContent(content);

        dialog.showAndWait();
    }

    public ContextMenu buildPlaybackOptionsMenu(
            Window owner,
            AudioService audioService,
            Consumer<String> toastSender) {
        ContextMenu menu = new ContextMenu();
        menu.getStyleClass().add("track-context-menu");

        // 1. Properties (Ctrl+I)
        MenuItem propItem = item("Properties", "mdi2i-information-outline", () -> {
            Track active = audioService.getCurrentTrack();
            if (active != null) {
                showPropertiesDialog(active, owner);
            } else if (toastSender != null) {
                toastSender.accept("No song is currently playing.");
            }
        });
        propItem.setAccelerator(new KeyCodeCombination(KeyCode.I, KeyCombination.CONTROL_DOWN));

        // 2. Equalizer (Ctrl+Shift+E)
        MenuItem eqItem = item("Equalizer", "mdi2t-tune", () -> {
            showEqualizerDialog(owner, audioService);
        });
        eqItem.setAccelerator(new KeyCodeCombination(KeyCode.E, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));

        // 3. Speed submenu (>)
        Menu speedSubMenu = new Menu("Speed");
        speedSubMenu.setGraphic(icon("mdi2s-speedometer"));
        ToggleGroup speedGroup = new ToggleGroup();

        double currentRate = audioService.getPlaybackRate();
        double[] speeds = {0.25, 0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0};
        String[] speedLabels = {"0.25x", "0.5x", "0.75x", "1.0x (Normal)", "1.25x", "1.5x", "1.75x", "2.0x"};

        for (int i = 0; i < speeds.length; i++) {
            double rate = speeds[i];
            String label = speedLabels[i];
            RadioMenuItem rateItem = new RadioMenuItem(label);
            rateItem.setToggleGroup(speedGroup);
            if (Math.abs(currentRate - rate) < 0.05) {
                rateItem.setSelected(true);
            }
            rateItem.setOnAction(e -> {
                audioService.setPlaybackRate(rate);
                if (toastSender != null) {
                    toastSender.accept("Playback speed: " + label);
                }
            });
            speedSubMenu.getItems().add(rateItem);
        }

        // 4. Cast to device (Ctrl+K)
        MenuItem castItem = item("Cast to device", "mdi2c-cast", () -> {
            showCastToDeviceDialog(owner, audioService);
        });
        castItem.setAccelerator(new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN));

        // Separator
        SeparatorMenuItem sep = new SeparatorMenuItem();

        // 5. Skip back 10 seconds (Ctrl+Left)
        MenuItem back10Item = item("Skip back 10 seconds", "mdi2r-rewind-10", () -> {
            audioService.seekBy(-10);
            if (toastSender != null) {
                toastSender.accept("Skipped back 10 seconds.");
            }
        });
        back10Item.setAccelerator(new KeyCodeCombination(KeyCode.LEFT, KeyCombination.CONTROL_DOWN));

        // 6. Skip forward 30 seconds (Ctrl+Right)
        MenuItem fwd30Item = item("Skip forward 30 seconds", "mdi2f-fast-forward-30", () -> {
            audioService.seekBy(30);
            if (toastSender != null) {
                toastSender.accept("Skipped forward 30 seconds.");
            }
        });
        fwd30Item.setAccelerator(new KeyCodeCombination(KeyCode.RIGHT, KeyCombination.CONTROL_DOWN));

        menu.getItems().addAll(propItem, eqItem, speedSubMenu, castItem, sep, back10Item, fwd30Item);
        return menu;
    }

    public static void showErrorAlert(Window owner, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Localify");
        alert.setHeaderText(header);
        alert.setContentText(content);
        configureOwnerAndStyle(alert, owner);
        alert.showAndWait();
    }

    private static void openInSystemExplorer(Path file, Window owner) {
        Path folder = file.getParent();
        if (folder == null || !Desktop.isDesktopSupported()) {
            return;
        }
        new Thread(() -> {
            try {
                Desktop.getDesktop().open(folder.toFile());
            } catch (IOException ex) {
                Platform.runLater(() -> showErrorAlert(owner, "Could not open folder", ex.getMessage()));
            }
        }, "localify-open-folder").start();
    }

    private static void configureOwnerAndStyle(Dialog<?> dialog, Window owner) {
        if (owner != null) {
            dialog.initOwner(owner);
        }
        DialogPane pane = dialog.getDialogPane();
        pane.getStyleClass().add("localify-dialog");
        URL cssUrl = ContextMenuBuilder.class.getResource(CSS_RESOURCE);
        if (cssUrl != null) {
            String external = cssUrl.toExternalForm();
            if (!pane.getStylesheets().contains(external)) {
                pane.getStylesheets().add(external);
            }
        }
    }

    private static GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(10);
        ColumnConstraints col0 = new ColumnConstraints();
        col0.setMinWidth(96);
        ColumnConstraints col1 = new ColumnConstraints();
        col1.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(col0, col1);
        return grid;
    }

    private static void addFormRow(GridPane grid, int row, String labelText, TextField field) {
        Label label = new Label(labelText);
        label.setLabelFor(field);
        label.getStyleClass().add("field-label");
        GridPane.setHgrow(field, Priority.ALWAYS);
        grid.add(label, 0, row);
        grid.add(field, 1, row);
    }

    private static MenuItem item(String text, String iconCode, Runnable action) {
        MenuItem mi = new MenuItem(text);
        mi.setGraphic(icon(iconCode));
        mi.setOnAction(e -> action.run());
        return mi;
    }

    private static FontIcon icon(String code) {
        FontIcon fi = new FontIcon(code);
        fi.getStyleClass().add("menu-font-icon");
        return fi;
    }
}
