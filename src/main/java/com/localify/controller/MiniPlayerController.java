package com.localify.controller;

import com.localify.model.LyricLine;
import com.localify.model.Playlist;
import com.localify.model.Track;
import com.localify.service.AudioService;
import com.localify.service.PlaylistService;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.io.IOException;
import java.util.List;

/**
 * Controller for the frameless, always-on-top Mini Player window docked at the top-center of the screen.
 */
public class MiniPlayerController implements ContextMenuBuilder.TrackActionHandler {

    public static final double COMPACT_WIDTH = 340.0;
    public static final double COMPACT_HEIGHT = 118.0;
    public static final double EXPANDED_HEIGHT = 330.0;

    @FXML private VBox miniRoot;
    @FXML private StackPane miniCoverFrame;
    @FXML private ImageView miniCoverImage;
    @FXML private FontIcon miniCoverPlaceholder;
    @FXML private Label miniTitleLabel;
    @FXML private Label miniArtistLabel;
    @FXML private Button miniOptionsButton;
    @FXML private Button miniExpandButton;

    @FXML private Slider miniSeekSlider;
    @FXML private Label miniElapsedLabel;
    @FXML private Label miniRemainingLabel;

    @FXML private Button miniMuteButton;
    @FXML private FontIcon miniVolumeIcon;
    @FXML private Slider miniVolumeSlider;

    @FXML private Button miniPrevButton;
    @FXML private Button miniRewindButton;
    @FXML private Button miniPlayPauseButton;
    @FXML private FontIcon miniPlayPauseIcon;
    @FXML private Button miniForwardButton;
    @FXML private Button miniNextButton;

    @FXML private ToggleButton miniLyricsToggle;
    @FXML private ToggleButton miniQueueToggle;

    @FXML private StackPane miniDrawerContainer;
    @FXML private ScrollPane miniLyricsScrollPane;
    @FXML private VBox miniLyricsBox;
    @FXML private Label miniLyricsEmptyLabel;

    @FXML private VBox miniQueuePane;
    @FXML private ListView<Track> miniUpcomingList;
    @FXML private Label miniQueueEmptyLabel;
    @FXML private Button miniClearQueueButton;

    private AudioService audioService;
    private PlaylistService playlistService;
    private ContextMenuBuilder contextMenuBuilder;
    private MainController mainController;
    private Stage miniStage;
    private Runnable onReturnFullScreen;

    private double dragOffsetX;
    private double dragOffsetY;
    private boolean draggingWindow;
    private boolean scrubbingSeek;
    private ContextMenu optionsMenu;

    public void init(
            Stage miniStage,
            AudioService audioService,
            PlaylistService playlistService,
            ContextMenuBuilder contextMenuBuilder,
            MainController mainController,
            Runnable onReturnFullScreen) {
        this.miniStage = miniStage;
        this.audioService = audioService;
        this.playlistService = playlistService;
        this.contextMenuBuilder = contextMenuBuilder;
        this.mainController = mainController;
        this.onReturnFullScreen = onReturnFullScreen;

        setupCustomWindowDrag();
        setupTrackAndSeekBindings();
        setupVolumeAndTransport();
        setupExpandableDrawer();
        setupKeyboardShortcuts();
    }

    public void positionTopCenter() {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        double width = COMPACT_WIDTH;
        double x = bounds.getMinX() + (bounds.getWidth() - width) / 2.0;
        double y = bounds.getMinY() + 16.0;
        miniStage.setX(Math.round(x));
        miniStage.setY(Math.round(y));
        applyWindowSize();
    }

    private void setupCustomWindowDrag() {
        miniRoot.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (isInteractiveTarget(event.getTarget())) {
                draggingWindow = false;
                return;
            }
            draggingWindow = true;
            dragOffsetX = event.getScreenX() - miniStage.getX();
            dragOffsetY = event.getScreenY() - miniStage.getY();
        });

        miniRoot.addEventFilter(MouseEvent.MOUSE_DRAGGED, event -> {
            if (draggingWindow) {
                miniStage.setX(event.getScreenX() - dragOffsetX);
                miniStage.setY(event.getScreenY() - dragOffsetY);
            }
        });

        miniRoot.addEventFilter(MouseEvent.MOUSE_RELEASED, event -> draggingWindow = false);
    }

    private static boolean isInteractiveTarget(Object target) {
        if (!(target instanceof Node node)) {
            return false;
        }
        Node cur = node;
        while (cur != null) {
            if (cur instanceof ButtonBase || cur instanceof Slider || cur instanceof ListCell<?>
                    || cur instanceof ScrollPane) {
                return true;
            }
            cur = cur.getParent();
        }
        return false;
    }

    private void setupTrackAndSeekBindings() {
        Rectangle clip = new Rectangle(38, 38);
        clip.setArcWidth(6);
        clip.setArcHeight(6);
        miniCoverImage.setClip(clip);

        audioService.currentArtworkProperty().addListener((obs, oldImg, img) -> {
            boolean hasImg = img != null;
            miniCoverImage.setImage(img);
            miniCoverImage.setVisible(hasImg);
            miniCoverPlaceholder.setVisible(!hasImg);
        });
        miniCoverImage.setVisible(false);

        audioService.currentTrackProperty().addListener((obs, oldTrack, track) -> {
            if (track == null) {
                miniTitleLabel.textProperty().unbind();
                miniArtistLabel.textProperty().unbind();
                miniTitleLabel.setText("No track playing");
                miniArtistLabel.setText("Open Full-Screen Player to pick a song");
                miniOptionsButton.setDisable(true);
                miniSeekSlider.setDisable(true);
                miniSeekSlider.setValue(0);
                miniElapsedLabel.setText("0:00");
                miniRemainingLabel.setText("-0:00");
            } else {
                miniTitleLabel.textProperty().bind(track.titleProperty());
                miniArtistLabel.textProperty().bind(Bindings.createStringBinding(
                        track::getDisplayArtist, track.artistProperty()));
                miniOptionsButton.setDisable(false);
                miniSeekSlider.setDisable(false);
            }
        });
        miniOptionsButton.setDisable(audioService.getCurrentTrack() == null);
        miniSeekSlider.setDisable(audioService.getCurrentTrack() == null);

        audioService.totalDurationSecondsProperty().addListener((obs, oldD, newD) -> {
            double total = Math.max(0, newD.doubleValue());
            miniSeekSlider.setMax(total > 0 ? total : 1);
            updateTimeLabels(audioService.currentTimeSecondsProperty().get(), total);
        });

        audioService.currentTimeSecondsProperty().addListener((obs, oldT, newT) -> {
            if (!scrubbingSeek) {
                double cur = newT.doubleValue();
                miniSeekSlider.setValue(cur);
                updateTimeLabels(cur, audioService.totalDurationSecondsProperty().get());
            }
        });

        miniSeekSlider.setOnMousePressed(e -> scrubbingSeek = true);
        miniSeekSlider.valueProperty().addListener((obs, oldV, newV) -> {
            if (scrubbingSeek || miniSeekSlider.isValueChanging()) {
                updateTimeLabels(newV.doubleValue(), audioService.totalDurationSecondsProperty().get());
            }
        });
        miniSeekSlider.valueChangingProperty().addListener((obs, wasChanging, changing) -> {
            if (changing) {
                scrubbingSeek = true;
            } else if (scrubbingSeek) {
                scrubbingSeek = false;
                audioService.seek(miniSeekSlider.getValue());
            }
        });
        miniSeekSlider.setOnMouseReleased(e -> {
            if (scrubbingSeek) {
                scrubbingSeek = false;
                audioService.seek(miniSeekSlider.getValue());
            }
        });
        miniSeekSlider.setOnKeyReleased(e -> {
            if (e.getCode() == KeyCode.LEFT || e.getCode() == KeyCode.RIGHT
                    || e.getCode() == KeyCode.HOME || e.getCode() == KeyCode.END) {
                audioService.seek(miniSeekSlider.getValue());
            }
        });
    }

    private void updateTimeLabels(double current, double total) {
        miniElapsedLabel.setText(Track.formatTime(current));
        double remaining = Math.max(0.0, total - current);
        miniRemainingLabel.setText("-" + Track.formatTime(remaining));
    }

    private void setupVolumeAndTransport() {
        audioService.playingProperty().addListener((obs, wasPlaying, isPlaying) -> {
            miniPlayPauseIcon.setIconLiteral(isPlaying ? "mdi2p-pause" : "mdi2p-play");
            miniPlayPauseButton.setAccessibleText(isPlaying ? "Pause" : "Play");
            miniPlayPauseButton.setTooltip(new Tooltip(isPlaying ? "Pause (Space)" : "Play (Space)"));
        });

        miniVolumeSlider.setMin(0);
        miniVolumeSlider.setMax(1);
        miniVolumeSlider.setValue(audioService.volumeProperty().get());
        miniVolumeSlider.valueProperty().bindBidirectional(audioService.volumeProperty());

        Runnable syncVolume = () -> {
            boolean isMuted = audioService.mutedProperty().get() || audioService.volumeProperty().get() == 0;
            double v = audioService.volumeProperty().get();
            if (isMuted) {
                miniVolumeIcon.setIconLiteral("mdi2v-volume-off");
                miniMuteButton.setAccessibleText("Unmute");
            } else if (v < 0.45) {
                miniVolumeIcon.setIconLiteral("mdi2v-volume-medium");
                miniMuteButton.setAccessibleText("Mute");
            } else {
                miniVolumeIcon.setIconLiteral("mdi2v-volume-high");
                miniMuteButton.setAccessibleText("Mute");
            }
        };
        audioService.volumeProperty().addListener((obs, o, n) -> syncVolume.run());
        audioService.mutedProperty().addListener((obs, o, n) -> syncVolume.run());
        syncVolume.run();
    }

    private void setupExpandableDrawer() {
        miniLyricsToggle.selectedProperty().addListener((obs, oldV, selected) -> {
            if (selected) {
                miniQueueToggle.setSelected(false);
            }
            refreshDrawerState();
        });

        miniQueueToggle.selectedProperty().addListener((obs, oldV, selected) -> {
            if (selected) {
                miniLyricsToggle.setSelected(false);
            }
            refreshDrawerState();
        });

        miniLyricsEmptyLabel.textProperty().bind(audioService.lyricStatusMessageProperty());
        audioService.getCurrentLyrics().addListener((ListChangeListener<LyricLine>) c -> rebuildMiniLyrics());
        audioService.lyricStatusMessageProperty().addListener((obs, o, n) -> refreshDrawerState());
        audioService.activeLyricIndexProperty().addListener((obs, oldIdx, newIdx) ->
                highlightMiniLyric(newIdx.intValue()));

        miniUpcomingList.setItems(audioService.getUpcomingTracks());
        miniUpcomingList.setCellFactory(lv -> new ListCell<>() {
            private final Label titleLbl = new Label();
            private final Label artistLbl = new Label();
            private final VBox textCol = new VBox(1, titleLbl, artistLbl);
            private final Button removeBtn = new Button();
            private final HBox row = new HBox(8, textCol, removeBtn);

            {
                titleLbl.getStyleClass().add("queue-item-title");
                artistLbl.getStyleClass().add("queue-item-artist");
                textCol.setMinWidth(0);
                HBox.setHgrow(textCol, Priority.ALWAYS);

                FontIcon icon = new FontIcon("mdi2c-close");
                icon.getStyleClass().add("small-icon");
                removeBtn.setGraphic(icon);
                removeBtn.getStyleClass().add("icon-button-subtle");
                removeBtn.setAccessibleText("Remove from queue");
                removeBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0) {
                        audioService.removeUpcomingAt(idx);
                    }
                });
                row.setAlignment(Pos.CENTER_LEFT);
                setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
            }

            @Override
            protected void updateItem(Track item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                } else {
                    titleLbl.setText(item.getTitle());
                    artistLbl.setText(item.getDisplayArtist());
                    setGraphic(row);
                }
            }
        });

        miniUpcomingList.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                int idx = miniUpcomingList.getSelectionModel().getSelectedIndex();
                if (idx >= 0) {
                    audioService.playUpcomingAt(idx);
                }
            }
        });
        miniUpcomingList.setOnKeyPressed(e -> {
            int idx = miniUpcomingList.getSelectionModel().getSelectedIndex();
            if (idx < 0) {
                return;
            }
            if (e.getCode() == KeyCode.ENTER) {
                audioService.playUpcomingAt(idx);
                e.consume();
            } else if (e.getCode() == KeyCode.DELETE) {
                audioService.removeUpcomingAt(idx);
                e.consume();
            }
        });

        audioService.getUpcomingTracks().addListener((ListChangeListener<Track>) c -> refreshQueueVisibility());
        refreshQueueVisibility();
        rebuildMiniLyrics();
        refreshDrawerState();
    }

    private void setupKeyboardShortcuts() {
        miniRoot.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE
                    || (event.isControlDown() && event.getCode() == KeyCode.M)) {
                handleReturnFullScreen();
                event.consume();
            } else if (event.isAltDown()) {
                // Keyboard fallback for repositioning the frameless window.
                switch (event.getCode()) {
                    case LEFT -> { miniStage.setX(miniStage.getX() - 16); event.consume(); }
                    case RIGHT -> { miniStage.setX(miniStage.getX() + 16); event.consume(); }
                    case UP -> { miniStage.setY(miniStage.getY() - 16); event.consume(); }
                    case DOWN -> { miniStage.setY(miniStage.getY() + 16); event.consume(); }
                    default -> { }
                }
            } else if (event.getCode() == KeyCode.SPACE) {
                audioService.togglePlayPause();
                event.consume();
            }
        });
    }

    private void refreshDrawerState() {
        boolean showLyrics = miniLyricsToggle.isSelected();
        boolean showQueue = miniQueueToggle.isSelected();
        boolean expanded = showLyrics || showQueue;

        setVisibleManaged(miniDrawerContainer, expanded);
        boolean hasLyrics = !audioService.getCurrentLyrics().isEmpty();
        setVisibleManaged(miniLyricsScrollPane, showLyrics && hasLyrics);
        setVisibleManaged(miniLyricsEmptyLabel, showLyrics && !hasLyrics);
        setVisibleManaged(miniQueuePane, showQueue);

        setToggleActiveClass(miniLyricsToggle, showLyrics);
        setToggleActiveClass(miniQueueToggle, showQueue);

        applyWindowSize();
        if (showLyrics) {
            highlightMiniLyric(audioService.activeLyricIndexProperty().get());
        }
    }

    private void applyWindowSize() {
        boolean expanded = miniLyricsToggle.isSelected() || miniQueueToggle.isSelected();
        double h = expanded ? EXPANDED_HEIGHT : COMPACT_HEIGHT;
        miniStage.setWidth(COMPACT_WIDTH);
        miniStage.setHeight(h);
    }

    private void refreshQueueVisibility() {
        boolean empty = audioService.getUpcomingTracks().isEmpty();
        setVisibleManaged(miniQueueEmptyLabel, empty);
        setVisibleManaged(miniUpcomingList, !empty);
        miniClearQueueButton.setDisable(empty);
    }

    private void rebuildMiniLyrics() {
        miniLyricsBox.getChildren().clear();
        List<LyricLine> lines = audioService.getCurrentLyrics();
        for (LyricLine line : lines) {
            Button btn = new Button(line.text().isBlank() ? "· · ·" : line.text());
            btn.setWrapText(true);
            btn.setMaxWidth(Double.MAX_VALUE);
            btn.setAlignment(Pos.CENTER_LEFT);
            btn.getStyleClass().add("lyric-line-button");
            double target = line.timeSeconds();
            btn.setOnAction(e -> audioService.seek(target));
            miniLyricsBox.getChildren().add(btn);
        }
        miniLyricsBox.setPadding(new Insets(56, 8, 64, 8));
        highlightMiniLyric(audioService.activeLyricIndexProperty().get());
    }

    private void highlightMiniLyric(int activeIndex) {
        ObservableList<Node> children = miniLyricsBox.getChildren();
        for (int i = 0; i < children.size(); i++) {
            Node n = children.get(i);
            n.getStyleClass().removeAll("lyric-line-active", "lyric-line-past");
            if (i == activeIndex) {
                n.getStyleClass().add("lyric-line-active");
            } else if (i < activeIndex) {
                n.getStyleClass().add("lyric-line-past");
            }
        }
        if (activeIndex < 0 || activeIndex >= children.size() || !miniLyricsScrollPane.isVisible()) {
            return;
        }
        Platform.runLater(() -> {
            if (activeIndex < 0 || activeIndex >= children.size()) {
                return;
            }
            miniLyricsBox.applyCss();
            miniLyricsBox.layout();
            Node activeNode = children.get(activeIndex);
            double contentHeight = miniLyricsBox.getBoundsInLocal().getHeight();
            double viewportHeight = miniLyricsScrollPane.getViewportBounds().getHeight();
            if (contentHeight <= viewportHeight || viewportHeight <= 0) {
                return;
            }
            Bounds nb = activeNode.getBoundsInParent();
            double center = nb.getMinY() + nb.getHeight() / 2.0;
            double targetV = (center - viewportHeight / 2.0) / (contentHeight - viewportHeight);
            double clampedV = Math.max(0.0, Math.min(1.0, targetV));
            Timeline anim = new Timeline(new KeyFrame(
                    Duration.millis(160),
                    new KeyValue(miniLyricsScrollPane.vvalueProperty(), clampedV, Interpolator.EASE_OUT)));
            anim.play();
        });
    }

    @FXML
    private void handleMiniPlayPause() {
        audioService.togglePlayPause();
    }

    @FXML
    private void handleMiniPrevious() {
        audioService.previous();
    }

    @FXML
    private void handleMiniRewind() {
        audioService.seekBy(-10.0);
    }

    @FXML
    private void handleMiniForward() {
        audioService.seekBy(10.0);
    }

    @FXML
    private void handleMiniNext() {
        audioService.next();
    }

    @FXML
    private void handleMiniMute() {
        audioService.toggleMute();
    }

    @FXML
    private void handleMiniClearQueue() {
        audioService.clearUpcoming();
    }

    @FXML
    private void handleMiniOptions() {
        Track active = audioService.getCurrentTrack();
        if (active == null) {
            return;
        }
        if (optionsMenu != null && optionsMenu.isShowing()) {
            optionsMenu.hide();
            return;
        }
        optionsMenu = contextMenuBuilder.buildTrackMenu(
                active, miniStage, "Remove from queue", this);
        Bounds b = miniOptionsButton.localToScreen(miniOptionsButton.getBoundsInLocal());
        if (b != null) {
            optionsMenu.show(miniOptionsButton, b.getMinX(), b.getMaxY() + 4);
        }
    }

    @FXML
    private void handleReturnFullScreen() {
        if (onReturnFullScreen != null) {
            onReturnFullScreen.run();
        }
    }

    // TrackActionHandler delegation from Mini Player's (...) menu
    @Override
    public void onPlay(Track track) {
        audioService.playTrack(track);
    }

    @Override
    public void onPlayNext(Track track) {
        audioService.playNext(track);
    }

    @Override
    public void onAddToQueue(Track track) {
        audioService.addToQueue(track);
    }

    @Override
    public void onAddToPlaylist(Playlist playlist, Track track) {
        try {
            playlistService.add(playlist, List.of(track));
        } catch (IOException ex) {
            ContextMenuBuilder.showErrorAlert(miniStage, "Could not save playlist", ex.getMessage());
        }
    }

    @Override
    public void onRemove(Track track) {
        audioService.removeFromQueue(track);
    }

    @Override
    public void onShowAlbum(String album) {
        handleReturnFullScreen();
        mainController.showAlbumFilter(album);
    }

    @Override
    public void onShowArtist(String artist) {
        handleReturnFullScreen();
        mainController.showArtistFilter(artist);
    }

    @Override
    public void onMetadataUpdated(Track track) {
        mainController.onMetadataUpdated(track);
    }

    @Override
    public void onStatusMessage(String message) {
        mainController.onStatusMessage(message);
    }

    private static void setToggleActiveClass(ToggleButton button, boolean active) {
        if (active) {
            if (!button.getStyleClass().contains("control-toggle-active")) {
                button.getStyleClass().add("control-toggle-active");
            }
        } else {
            button.getStyleClass().remove("control-toggle-active");
        }
    }

    private static void setVisibleManaged(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }
}
