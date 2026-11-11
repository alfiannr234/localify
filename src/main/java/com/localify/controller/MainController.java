package com.localify.controller;

import com.localify.model.LyricLine;
import com.localify.model.Playlist;
import com.localify.model.SongListItem;
import com.localify.model.Track;
import com.localify.service.AudioService;
import com.localify.service.DiscordRpcService;
import com.localify.service.MusicScannerService;
import com.localify.service.PlaylistService;
import com.localify.service.SettingsService;
import com.localify.service.ThemeManager;
import com.localify.service.UpdateService;
import java.io.ByteArrayInputStream;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.animation.TranslateTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Bounds;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.Text;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.Window;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import javafx.scene.control.TextInputDialog;
import javafx.scene.control.OverrunStyle;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Controller for the full desktop player window.
 * Features a modern grouped card list layout with single-click playback,
 * Shuffle & Play, multi-criteria sorting (A-Z, Artist, Album, Year, Date Added),
 * genre filtering, synchronized lyrics, and Discord Rich Presence settings.
 */
public class MainController implements ContextMenuBuilder.TrackActionHandler {

    public enum SortMode {
        A_Z("A - Z"),
        ARTIST("Artist"),
        ALBUM("Album"),
        RELEASE_YEAR("Release year"),
        DATE_ADDED("Date added");

        private final String label;

        SortMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private enum FilterKind {
        NONE, ARTIST, ALBUM, PLAYLIST
    }

    @FXML private BorderPane rootPane;
    @FXML private HBox playbackErrorBanner;
    @FXML private Label playbackErrorLabel;
    @FXML private Button dismissErrorButton;
    @FXML private HBox statusToastBar;
    @FXML private Label statusToastLabel;

    // Sidebar
    @FXML private Label folderPathLabel;
    @FXML private Button allSongsNavButton;
    @FXML private Button playlistsNavButton;
    @FXML private Label allSongsCountLabel;
    @FXML private Button chooseFolderButton;
    @FXML private Button rescanButton;
    @FXML private HBox activeFilterBox;
    @FXML private Label activeFilterLabel;
    @FXML private Button clearFilterButton;
    @FXML private Button newPlaylistButton;
    @FXML private ListView<Playlist> playlistListView;
    @FXML private Label playlistEmptyLabel;
    @FXML private Button settingsButton;

    // Center header & modern toolbar (Images 2, 3 & 4)
    @FXML private StackPane windowRootStack;
    @FXML private Button playlistBackButton;
    @FXML private Label viewTitleLabel;
    @FXML private HBox libraryTabsBox;
    @FXML private Button tabSongsButton;
    @FXML private Button tabAlbumsButton;
    @FXML private Button tabArtistsButton;
    @FXML private Button addFolderHeaderButton;
    @FXML private TextField searchField;
    @FXML private Button clearSearchButton;
    @FXML private HBox defaultToolbar;
    @FXML private Button shufflePlayButton;
    @FXML private MenuButton sortMenuButton;
    @FXML private MenuButton genreMenuButton;

    // Playlists & Your Library Grid View (Matching Gambar 2 - MoLow style)
    @FXML private VBox playlistsViewPane;
    @FXML private Button addPlaylistHeaderButton;
    @FXML private HBox libraryFilterPillsBox;
    @FXML private Button pillAllButton;
    @FXML private Button pillPlaylistsButton;
    @FXML private Button pillAlbumsButton;
    @FXML private Button pillArtistsButton;
    @FXML private MenuButton libraryGridSortButton;
    @FXML private VBox playlistsEmptyPromptBox;
    @FXML private ScrollPane libraryGridScrollPane;
    @FXML private FlowPane libraryGridFlowPane;

    public enum LibraryGridTab {
        ALL, PLAYLISTS, ALBUMS, ARTISTS
    }
    private LibraryGridTab currentGridTab = LibraryGridTab.ALL;
    private String currentGridSort = "RECENT";

    // Multi-selection action bar (Image 4)
    @FXML private HBox selectionToolbar;
    @FXML private CheckBox selectAllCheckBox;
    @FXML private Label selectionCountLabel;
    @FXML private Button clearSelectionButton;
    @FXML private Button selectionPlayButton;
    @FXML private Button selectionPlayNextButton;
    @FXML private MenuButton selectionAddToButton;

    // Multi-state center content
    @FXML private StackPane contentStack;
    @FXML private ListView<SongListItem> songListView;
    @FXML private StackPane sectionJumpOverlay;
    @FXML private GridPane sectionJumpGrid;

    private static final String[][] ALPHABET_GRID = {
            {"&", "#", "A", "B", "C", "D", "E", "F"},
            {"G", "H", "I", "J", "K", "L", "M", "N"},
            {"O", "P", "Q", "R", "S", "T", "U", "V"},
            {"W", "X", "Y", "Z", "..."}
    };

    @FXML private VBox loadingStateBox;
    @FXML private ProgressBar scanProgressBar;
    @FXML private Label loadingMessageLabel;

    @FXML private VBox emptyStateBox;
    @FXML private Label emptyTitleLabel;
    @FXML private Label emptyDetailLabel;
    @FXML private Button emptyPrimaryActionButton;
    @FXML private Button emptySecondaryActionButton;

    @FXML private VBox errorStateBox;
    @FXML private Label errorTitleLabel;
    @FXML private Label errorDetailLabel;
    @FXML private Button errorRetryButton;
    @FXML private Button errorChooseFolderButton;

    // In-Page Settings View (Image 2)
    @FXML private VBox libraryViewPane;
    @FXML private ScrollPane settingsViewPane;
    @FXML private Button settingsBackButton;
    @FXML private Label settingsMusicFolderLabel;
    @FXML private MenuButton settingsThemeMenu;
    @FXML private MenuButton settingsAccentMenu;
    @FXML private Label settingsDiscordStatusLabel;
    @FXML private CheckBox settingsDiscordCheckBox;
    @FXML private TextField settingsDiscordClientIdField;

    // Right panel: Lyrics & Playing Next
    @FXML private VBox rightDrawer;
    @FXML private ToggleButton lyricsTabButton;
    @FXML private ToggleButton queueTabButton;
    @FXML private Button reloadLyricsButton;
    @FXML private StackPane rightContentStack;
    @FXML private ScrollPane lyricsScrollPane;
    @FXML private VBox lyricsContainer;
    @FXML private VBox lyricsEmptyBox;
    @FXML private Label lyricsEmptyLabel;
    @FXML private VBox queuePane;
    @FXML private Label queueNowPlayingLabel;
    @FXML private ListView<Track> upcomingListView;
    @FXML private VBox queueEmptyBox;
    @FXML private Button clearQueueButton;

    // Bottom Now Playing bar (Image 5: Click to expand to Image 4, 3-dots removed!)
    @FXML private HBox nowPlayingCardBox;
    @FXML private StackPane coverArtFrame;
    @FXML private ImageView nowPlayingCover;
    @FXML private FontIcon coverPlaceholderIcon;
    @FXML private Label nowPlayingTitle;
    @FXML private Button nowPlayingArtistButton;
    @FXML private ToggleButton shuffleButton;
    @FXML private Button prevButton;
    @FXML private Button playPauseButton;
    @FXML private FontIcon playPauseIcon;
    @FXML private Button nextButton;
    @FXML private Button repeatButton;
    @FXML private FontIcon repeatIcon;
    @FXML private Label elapsedTimeLabel;
    @FXML private Slider seekSlider;
    @FXML private Label totalTimeLabel;
    @FXML private ToggleButton toggleRightDrawerButton;
    @FXML private Button muteButton;
    @FXML private FontIcon volumeIcon;
    @FXML private Slider volumeSlider;
    @FXML private Button miniPlayerButton;
    @FXML private Button morePlaybackOptionsButton;

    // Full-Window Now Playing Expanded Overlay (Image 4: Windows 11 Media Player Style)
    @FXML private VBox nowPlayingExpandedOverlay;
    @FXML private Button expandedBackButton;
    @FXML private StackPane expandedCenterStack;
    @FXML private VBox expandedCoverContainer;
    @FXML private StackPane expandedCoverFrame;
    @FXML private ImageView expandedCoverImage;
    @FXML private FontIcon expandedPlaceholderIcon;
    @FXML private StackPane expandedCoverOverlayHint;
    @FXML private VBox expandedLyricsBox;
    @FXML private Button expandedChangeLrcButton;
    @FXML private Button expandedSwitchToCoverButton;
    @FXML private ScrollPane expandedLyricsScrollPane;
    @FXML private VBox expandedLyricsContainer;
    @FXML private VBox expandedLyricsEmptyBox;
    @FXML private Label expandedLyricsEmptyLabel;
    @FXML private Label expandedElapsedLabel;
    @FXML private Slider expandedSeekSlider;
    @FXML private Label expandedTotalLabel;
    @FXML private Label expandedTitleLabel;
    @FXML private Label expandedArtistLabel;
    @FXML private ToggleButton expandedShuffleButton;
    @FXML private Button expandedPrevButton;
    @FXML private Button expandedRewindButton;
    @FXML private Button expandedPlayPauseButton;
    @FXML private FontIcon expandedPlayPauseIcon;
    @FXML private Button expandedForwardButton;
    @FXML private Button expandedNextButton;
    @FXML private Button expandedRepeatButton;
    @FXML private FontIcon expandedRepeatIcon;
    @FXML private Button expandedLyricsButton;
    @FXML private FontIcon expandedLyricsButtonIcon;
    @FXML private Button expandedMuteButton;
    @FXML private FontIcon expandedVolumeIcon;
    @FXML private Slider expandedVolumeSlider;
    @FXML private Button expandedMiniPlayerButton;
    @FXML private Button expandedFullscreenButton;
    @FXML private FontIcon expandedFullscreenIcon;
    @FXML private Button expandedMoreOptionsButton;

    @FXML private Label settingsLyricsOffsetLabel;

    private boolean expandedLyricsMode = false;
    private Timeline expandedScrollTimeline;
    private Rectangle expandedCoverClip;

    private final ObservableList<Track> masterLibrary = FXCollections.observableArrayList();
    private final Map<Path, Track> tracksByPath = new HashMap<>();
    private final ObservableList<Track> activeBaseTracks = FXCollections.observableArrayList();
    private final List<Track> displayedTracks = new ArrayList<>();

    private final ExecutorService thumbnailExecutor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "localify-thumb-loader");
        t.setDaemon(true);
        return t;
    });
    private final Map<Path, Image> thumbnailCache = new ConcurrentHashMap<>();

    private AudioService audioService;
    private MusicScannerService scannerService;
    private PlaylistService playlistService;
    private ContextMenuBuilder contextMenuBuilder;
    private SettingsService settingsService;
    private DiscordRpcService discordRpcService;
    private Runnable onOpenMiniPlayer;

    private Path activeMusicDirectory;
    private Task<List<Track>> runningScanTask;
    private boolean scanning;
    private String scanErrorText;

    private FilterKind filterKind = FilterKind.NONE;
    private String filterValue = "";
    private Playlist selectedPlaylist;

    private SortMode currentSortMode = SortMode.A_Z;
    private String currentGenreFilter = "All genres";
    private final Set<Track> selectedTracks = new LinkedHashSet<>();

    private boolean scrubbingSeek;
    private boolean expandedScrubbingSeek;
    private final PauseTransition toastHideTimer = new PauseTransition(Duration.seconds(3.5));
    private ContextMenu activeTrackOptionsMenu;

    public void initServices(
            AudioService audioService,
            MusicScannerService scannerService,
            PlaylistService playlistService,
            ContextMenuBuilder contextMenuBuilder,
            SettingsService settingsService,
            DiscordRpcService discordRpcService,
            Runnable onOpenMiniPlayer) {
        this.audioService = audioService;
        this.scannerService = scannerService;
        this.playlistService = playlistService;
        this.contextMenuBuilder = contextMenuBuilder;
        this.settingsService = settingsService;
        this.discordRpcService = discordRpcService;
        this.onOpenMiniPlayer = onOpenMiniPlayer;
        if (this.audioService != null && this.settingsService != null) {
            this.audioService.setSettingsService(settingsService);
        }
        this.activeMusicDirectory = scannerService.getDefaultMusicDirectory();

        setupSongListView();
        setupSidebar();
        setupSearchAndFilters();
        setupSelectionAddToMenu();
        updateSelectionToolbar();
        setupRightPanel();
        setupTransportBar();
        setupExpandedOverlay();
        setupBannersAndShortcuts();
        setupSectionJumpOverlay();
        setupSettingsView();

        scanMusicDirectory(activeMusicDirectory);
    }

    @FXML
    public void initialize() {
        if (sectionJumpOverlay != null) {
            setupSectionJumpOverlay();
        }
    }

    /** Overload for backwards compatibility */
    public void initServices(
            AudioService audioService,
            MusicScannerService scannerService,
            PlaylistService playlistService,
            ContextMenuBuilder contextMenuBuilder,
            Runnable onOpenMiniPlayer) {
        initServices(
                audioService,
                scannerService,
                playlistService,
                contextMenuBuilder,
                new SettingsService(),
                new DiscordRpcService(new SettingsService()),
                onOpenMiniPlayer);
    }

    private void setupSongListView() {
        songListView.setCellFactory(lv -> new ListCell<>() {
            private final Label sectionHeader = new Label();
            private final GridPane card = new GridPane();
            private final Label titleLabel = new Label();
            private final StackPane titleClipPane = new StackPane();
            private final Rectangle titleClip = new Rectangle();
            private final FontIcon activeIcon = new FontIcon("mdi2v-volume-high");

            private final StackPane coverThumbFrame = new StackPane();
            private final ImageView coverThumbView = new ImageView();
            private final Rectangle coverThumbClip = new Rectangle(36, 36);
            private final FontIcon coverThumbIcon = new FontIcon("mdi2m-music-note");

            // Row-level selection checkbox and individual play button (Images 3 & 4)
            private final CheckBox itemCheckBox = new CheckBox();
            private final Button itemPlayButton = new Button();
            private final HBox actionBox = new HBox(6, itemCheckBox, itemPlayButton);

            private final HBox titleBox = new HBox(8, actionBox, coverThumbFrame, activeIcon, titleClipPane);
            private final Label artistLabel = new Label();
            private final Label albumLabel = new Label();
            private final Label genreLabel = new Label();
            private final Label durationLabel = new Label();

            private TranslateTransition marqueeAnimation;

            {
                sectionHeader.getStyleClass().add("song-section-header");
                sectionHeader.setMinWidth(0);
                sectionHeader.setMaxWidth(Double.MAX_VALUE);
                sectionHeader.setCursor(Cursor.HAND);
                sectionHeader.setTooltip(new Tooltip("Jump to section (&, #, A-Z)"));
                sectionHeader.setOnMouseClicked(event -> {
                    if (event.getButton() == MouseButton.PRIMARY) {
                        showSectionJumpOverlay();
                        event.consume();
                    }
                });

                this.setOnMouseClicked(event -> {
                    if (getItem() instanceof SongListItem.SectionHeader && event.getButton() == MouseButton.PRIMARY) {
                        showSectionJumpOverlay();
                        event.consume();
                    }
                });

                card.getStyleClass().add("song-card-row");
                card.setAlignment(Pos.CENTER_LEFT);
                card.setHgap(10);
                card.setMinWidth(0);

                // Strictly bind width to ListView width to completely eliminate horizontal scrolling
                card.prefWidthProperty().bind(songListView.widthProperty().subtract(26));
                card.maxWidthProperty().bind(songListView.widthProperty().subtract(26));

                ColumnConstraints colTitle = new ColumnConstraints();
                colTitle.setPercentWidth(34);
                colTitle.setHgrow(Priority.ALWAYS);
                colTitle.setMinWidth(0);

                ColumnConstraints colArtist = new ColumnConstraints();
                colArtist.setPercentWidth(22);
                colArtist.setMinWidth(0);

                ColumnConstraints colAlbum = new ColumnConstraints();
                colAlbum.setPercentWidth(20);
                colAlbum.setMinWidth(0);

                ColumnConstraints colGenre = new ColumnConstraints();
                colGenre.setPercentWidth(14);
                colGenre.setMinWidth(0);

                ColumnConstraints colDuration = new ColumnConstraints();
                colDuration.setPercentWidth(10);
                colDuration.setMinWidth(0);
                colDuration.setHalignment(HPos.RIGHT);

                card.getColumnConstraints().addAll(colTitle, colArtist, colAlbum, colGenre, colDuration);

                // Action box (checkbox + play button)
                actionBox.setAlignment(Pos.CENTER_LEFT);
                actionBox.setMinWidth(56);
                actionBox.setPrefWidth(56);
                actionBox.setMaxWidth(56);
                itemCheckBox.getStyleClass().add("row-checkbox");
                itemCheckBox.setAccessibleText("Select song");

                FontIcon playIcon = new FontIcon("mdi2p-play");
                playIcon.setIconSize(13);
                playIcon.setIconColor(javafx.scene.paint.Color.web("#F36B21"));
                itemPlayButton.setGraphic(playIcon);
                itemPlayButton.getStyleClass().add("row-play-button");
                itemPlayButton.setTooltip(new Tooltip("Play"));
                itemPlayButton.setAccessibleText("Play this song");

                // Cover art thumbnail
                coverThumbFrame.setMinSize(36, 36);
                coverThumbFrame.setPrefSize(36, 36);
                coverThumbFrame.setMaxSize(36, 36);
                coverThumbFrame.getStyleClass().add("row-cover-frame");

                coverThumbView.setFitWidth(36);
                coverThumbView.setFitHeight(36);
                coverThumbClip.setArcWidth(8);
                coverThumbClip.setArcHeight(8);
                coverThumbView.setClip(coverThumbClip);

                coverThumbIcon.setIconSize(16);
                coverThumbIcon.setIconColor(javafx.scene.paint.Color.web("#777777"));
                coverThumbFrame.getChildren().addAll(coverThumbIcon, coverThumbView);

                // Title clip pane for smooth sliding marquee on hover
                titleClip.widthProperty().bind(titleClipPane.widthProperty());
                titleClip.heightProperty().bind(titleClipPane.heightProperty());
                titleClipPane.setClip(titleClip);
                titleClipPane.setAlignment(Pos.CENTER_LEFT);
                titleClipPane.setMinWidth(0);
                titleClipPane.getChildren().add(titleLabel);
                HBox.setHgrow(titleClipPane, Priority.ALWAYS);

                titleBox.setAlignment(Pos.CENTER_LEFT);
                titleBox.setMinWidth(0);

                titleLabel.getStyleClass().add("song-card-title");
                titleLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
                titleLabel.setMinWidth(0);
                activeIcon.getStyleClass().add("playing-row-icon");

                artistLabel.getStyleClass().add("song-card-subtext");
                artistLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
                artistLabel.setMinWidth(0);

                albumLabel.getStyleClass().add("song-card-subtext");
                albumLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
                albumLabel.setMinWidth(0);

                genreLabel.getStyleClass().add("song-card-subtext");
                genreLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
                genreLabel.setMinWidth(0);

                durationLabel.getStyleClass().add("song-card-subtext");
                durationLabel.setMinWidth(0);

                card.add(titleBox, 0, 0);
                card.add(artistLabel, 1, 0);
                card.add(albumLabel, 2, 0);
                card.add(genreLabel, 3, 0);
                card.add(durationLabel, 4, 0);

                // Hover marquee animation & action box reveal
                card.setOnMouseEntered(event -> {
                    actionBox.setOpacity(1.0);
                    startTitleMarqueeIfOverflows();
                });
                card.setOnMouseExited(event -> {
                    if (selectedTracks.isEmpty() && getItem() instanceof SongListItem.SongEntry entry && !selectedTracks.contains(entry.track())) {
                        actionBox.setOpacity(0.0);
                    }
                    stopTitleMarquee();
                });

                itemPlayButton.setOnAction(event -> {
                    if (getItem() instanceof SongListItem.SongEntry entry) {
                        playTrackInCurrentView(entry.track());
                        event.consume();
                    }
                });

                itemCheckBox.setOnAction(event -> {
                    if (getItem() instanceof SongListItem.SongEntry entry) {
                        Track t = entry.track();
                        if (itemCheckBox.isSelected()) {
                            selectedTracks.add(t);
                        } else {
                            selectedTracks.remove(t);
                        }
                        updateSelectionToolbar();
                        songListView.refresh();
                        event.consume();
                    }
                });

                // Row click interaction:
                // Double-click plays track
                // Single-click when in selection mode toggles selection
                // Right-click opens context menu
                card.setOnMouseClicked(event -> {
                    SongListItem item = getItem();
                    if (item instanceof SongListItem.SongEntry entry && !isEmpty()) {
                        Track t = entry.track();
                        if (event.getButton() == MouseButton.PRIMARY) {
                            if (event.getClickCount() == 2) {
                                playTrackInCurrentView(t);
                            } else if (event.getClickCount() == 1) {
                                if (!selectedTracks.isEmpty()) {
                                    if (selectedTracks.contains(t)) {
                                        selectedTracks.remove(t);
                                    } else {
                                        selectedTracks.add(t);
                                    }
                                    updateSelectionToolbar();
                                    songListView.refresh();
                                }
                            }
                        } else if (event.getButton() == MouseButton.SECONDARY) {
                            songListView.getSelectionModel().select(item);
                            ContextMenu menu = contextMenuBuilder.buildTrackMenu(
                                    t, getWindow(), currentRemoveLabel(), MainController.this);
                            menu.show(this, event.getScreenX(), event.getScreenY());
                        }
                    }
                });
            }

            private void startTitleMarqueeIfOverflows() {
                stopTitleMarquee();
                if (getItem() instanceof SongListItem.SongEntry entry) {
                    String title = entry.track().getTitle();
                    if (title == null || title.isBlank()) {
                        return;
                    }
                    double visibleWidth = titleClipPane.getWidth();
                    if (visibleWidth <= 15) {
                        return;
                    }

                    Font font = titleLabel.getFont();
                    if (font == null || font.getSize() < 12.0) {
                        font = Font.font("Segoe UI", javafx.scene.text.FontWeight.SEMI_BOLD, 13.5);
                    }
                    Text helper = new Text(title);
                    helper.setFont(font);
                    double textWidth = helper.getLayoutBounds().getWidth();

                    double overflow = textWidth - visibleWidth;
                    if (overflow > 6) {
                        // Expand layout bounds so the label draws all text characters without truncation
                        titleLabel.setMinWidth(textWidth + 24);
                        titleLabel.setPrefWidth(textWidth + 24);
                        titleLabel.setMaxWidth(Double.MAX_VALUE);
                        titleLabel.setTextOverrun(javafx.scene.control.OverrunStyle.CLIP);

                        // Slow, comfortable reading speed: ~22 pixels per second
                        double speedPxPerSec = 22.0;
                        double durationSec = Math.max(3.0, overflow / speedPxPerSec);

                        marqueeAnimation = new TranslateTransition(Duration.seconds(durationSec), titleLabel);
                        marqueeAnimation.setFromX(0);
                        marqueeAnimation.setToX(-overflow - 12);
                        marqueeAnimation.setInterpolator(Interpolator.LINEAR);
                        marqueeAnimation.setAutoReverse(true);
                        marqueeAnimation.setCycleCount(Animation.INDEFINITE);
                        marqueeAnimation.setDelay(Duration.millis(700)); // 700ms pause to read the start comfortably
                        marqueeAnimation.play();
                    }
                }
            }

            private void stopTitleMarquee() {
                if (marqueeAnimation != null) {
                    marqueeAnimation.stop();
                    marqueeAnimation = null;
                }
                titleLabel.setTranslateX(0);
                titleLabel.setMinWidth(0);
                titleLabel.setPrefWidth(Region.USE_COMPUTED_SIZE);
                titleLabel.setMaxWidth(Double.MAX_VALUE);
                titleLabel.setTextOverrun(javafx.scene.control.OverrunStyle.ELLIPSIS);
            }

            @Override
            protected void updateItem(SongListItem item, boolean empty) {
                stopTitleMarquee();
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    setDisable(false);
                    return;
                }

                if (item instanceof SongListItem.SectionHeader header) {
                    sectionHeader.setText(header.title());
                    setGraphic(sectionHeader);
                    setText(null);
                    setDisable(false);
                    setCursor(Cursor.HAND);
                } else if (item instanceof SongListItem.SongEntry entry) {
                    setDisable(false);
                    setCursor(Cursor.DEFAULT);
                    Track track = entry.track();

                    titleLabel.setText(track.getTitle());
                    artistLabel.setText(track.getDisplayArtist());
                    albumLabel.setText(track.getDisplayAlbum());
                    genreLabel.setText(track.getDisplayGenre());
                    durationLabel.setText(track.getDuration() > 0 ? Track.formatTime(track.getDuration()) : "00:00");

                    Image thumb = track.getThumbnail();
                    if (thumb == null) {
                        thumb = thumbnailCache.get(track.getPath());
                        if (thumb != null) {
                            track.setThumbnail(thumb);
                        }
                    }

                    if (thumb != null) {
                        coverThumbView.setImage(thumb);
                        coverThumbView.setVisible(true);
                        coverThumbIcon.setVisible(false);
                    } else {
                        coverThumbView.setVisible(false);
                        coverThumbIcon.setVisible(true);

                        Path trackPath = track.getPath();
                        thumbnailExecutor.submit(() -> {
                            byte[] art = scannerService.readArtwork(trackPath);
                            if (art != null && art.length > 0) {
                                try {
                                    Image img = new Image(new ByteArrayInputStream(art), 72, 72, true, true);
                                    track.setThumbnail(img);
                                    thumbnailCache.put(trackPath, img);
                                    Platform.runLater(() -> {
                                        if (!isEmpty() && getItem() instanceof SongListItem.SongEntry currentEntry
                                                && currentEntry.track().getPath().equals(trackPath)) {
                                            coverThumbView.setImage(img);
                                            coverThumbView.setVisible(true);
                                            coverThumbIcon.setVisible(false);
                                        }
                                    });
                                } catch (Exception ignored) {
                                }
                            }
                        });
                    }

                    boolean isSelected = selectedTracks.contains(track);
                    itemCheckBox.setSelected(isSelected);

                    // Action box visibility: if multi-select active or track selected, keep visible; otherwise visible on hover
                    if (!selectedTracks.isEmpty() || isSelected) {
                        actionBox.setOpacity(1.0);
                    } else {
                        actionBox.setOpacity(0.0);
                    }

                    boolean isActive = track.equals(audioService.getCurrentTrack());
                    boolean isPlaying = isActive && audioService.playingProperty().get();

                    card.getStyleClass().removeAll("song-card-tinted", "song-card-active", "song-card-selected");
                    titleLabel.getStyleClass().remove("song-card-title-active");

                    if (isActive) {
                        card.getStyleClass().add("song-card-active");
                        titleLabel.getStyleClass().add("song-card-title-active");
                        activeIcon.setVisible(true);
                        activeIcon.setManaged(true);
                        activeIcon.setIconLiteral(isPlaying ? "mdi2v-volume-high" : "mdi2p-pause");
                    } else if (isSelected) {
                        card.getStyleClass().add("song-card-selected");
                        activeIcon.setVisible(false);
                        activeIcon.setManaged(false);
                    } else {
                        if (entry.isCardBackground()) {
                            card.getStyleClass().add("song-card-tinted");
                        }
                        activeIcon.setVisible(false);
                        activeIcon.setManaged(false);
                    }

                    setGraphic(card);
                    setText(null);
                }
            }
        });

        songListView.setOnKeyPressed(event -> {
            SongListItem selected = songListView.getSelectionModel().getSelectedItem();
            if (selected instanceof SongListItem.SongEntry entry) {
                if (event.getCode() == KeyCode.ENTER) {
                    playTrackInCurrentView(entry.track());
                    event.consume();
                } else if (event.getCode() == KeyCode.CONTEXT_MENU
                        || (event.getCode() == KeyCode.F10 && event.isShiftDown())) {
                    Bounds bounds = songListView.localToScreen(songListView.getBoundsInLocal());
                    double x = bounds != null ? bounds.getMinX() + 140 : 200;
                    double y = bounds != null ? bounds.getMinY() + 140 : 200;
                    ContextMenu menu = contextMenuBuilder.buildTrackMenu(
                            entry.track(), getWindow(), currentRemoveLabel(), this);
                    menu.show(songListView, x, y);
                    event.consume();
                } else if (event.getCode() == KeyCode.DELETE) {
                    onRemove(entry.track());
                    event.consume();
                }
            }
        });

        audioService.currentTrackProperty().addListener((obs, o, n) -> songListView.refresh());
        audioService.playingProperty().addListener((obs, o, n) -> songListView.refresh());
    }

    private void setupSidebar() {
        playlistListView.setItems(playlistService.getPlaylists());
        playlistListView.setCellFactory(lv -> new ListCell<>() {
            private final FontIcon icon = new FontIcon("mdi2p-playlist-music");
            private final Label nameLabel = new Label();
            private final Label countLabel = new Label();
            private final Region spacer = new Region();
            private final HBox row = new HBox(8, icon, nameLabel, spacer, countLabel);

            {
                icon.getStyleClass().add("sidebar-item-icon");
                nameLabel.getStyleClass().add("playlist-cell-name");
                countLabel.getStyleClass().add("count-pill");
                HBox.setHgrow(spacer, Priority.ALWAYS);
                row.setAlignment(Pos.CENTER_LEFT);
            }

            @Override
            protected void updateItem(Playlist playlist, boolean empty) {
                super.updateItem(playlist, empty);
                if (empty || playlist == null) {
                    setText(null);
                    setGraphic(null);
                    setContextMenu(null);
                    return;
                }
                nameLabel.setText(playlist.getName());
                countLabel.setText(String.valueOf(playlist.getTracks().size()));
                setGraphic(row);

                ContextMenu plMenu = new ContextMenu();
                MenuItem playPl = new MenuItem("Play playlist");
                playPl.setOnAction(e -> {
                    if (!playlist.getTracks().isEmpty()) {
                        showPlaylist(playlist);
                        audioService.playFromContext(playlist.getTracks(), playlist.getTracks().get(0));
                    }
                });
                MenuItem deletePl = new MenuItem("Delete playlist");
                deletePl.setOnAction(e -> deletePlaylist(playlist));
                plMenu.getItems().addAll(playPl, deletePl);
                setContextMenu(plMenu);
            }
        });

        playlistListView.getSelectionModel().selectedItemProperty().addListener((obs, oldPl, newPl) -> {
            if (newPl != null) {
                showPlaylist(newPl);
            }
        });

        Runnable updatePlaylistEmpty = () -> {
            boolean empty = playlistService.getPlaylists().isEmpty();
            if (playlistEmptyLabel != null) {
                playlistEmptyLabel.setVisible(empty);
                playlistEmptyLabel.setManaged(empty);
            }
        };
        playlistService.getPlaylists().addListener((ListChangeListener<Playlist>) c -> {
            updatePlaylistEmpty.run();
            renderLibraryGrid();
        });
        updatePlaylistEmpty.run();

        masterLibrary.addListener((ListChangeListener<Track>) c -> {
            if (allSongsCountLabel != null) {
                allSongsCountLabel.setText(String.valueOf(masterLibrary.size()));
            }
            renderLibraryGrid();
        });

        if (libraryGridSortButton != null) {
            libraryGridSortButton.getItems().clear();
            MenuItem recentItem = new MenuItem("Recently added");
            recentItem.setOnAction(e -> {
                currentGridSort = "RECENT";
                libraryGridSortButton.setText("Recently added");
                renderLibraryGrid();
            });
            MenuItem azItem = new MenuItem("A - Z");
            azItem.setOnAction(e -> {
                currentGridSort = "ALPHA";
                libraryGridSortButton.setText("A - Z");
                renderLibraryGrid();
            });
            MenuItem countItem = new MenuItem("Most songs");
            countItem.setOnAction(e -> {
                currentGridSort = "COUNT";
                libraryGridSortButton.setText("Most songs");
                renderLibraryGrid();
            });
            libraryGridSortButton.getItems().addAll(recentItem, azItem, countItem);
            libraryGridSortButton.setText("Recently added");
        }
    }

    private void setupSearchAndFilters() {
        searchField.textProperty().addListener((obs, oldQuery, newQuery) -> {
            boolean hasQuery = newQuery != null && !newQuery.isBlank();
            clearSearchButton.setVisible(hasQuery);
            clearSearchButton.setManaged(hasQuery);
            rebuildSongList();
        });
        clearSearchButton.setVisible(false);
        clearSearchButton.setManaged(false);

        // Setup Sort Menu items (matching Image 3)
        sortMenuButton.getItems().clear();
        for (SortMode mode : SortMode.values()) {
            MenuItem item = new MenuItem(mode.getLabel());
            item.setOnAction(e -> setSortMode(mode));
            sortMenuButton.getItems().add(item);
        }
        sortMenuButton.setText(currentSortMode.getLabel());
        updateNavTabsHighlight();

        // Setup Genre Menu initial items
        refreshGenreMenuOptions();
    }

    public void setSortMode(SortMode mode) {
        this.currentSortMode = mode;
        if (sortMenuButton != null) {
            sortMenuButton.setText(mode.getLabel());
        }
        updateNavTabsHighlight();
        rebuildSongList();
    }

    private void updateNavTabsHighlight() {
        if (tabSongsButton == null || tabAlbumsButton == null || tabArtistsButton == null) {
            return;
        }
        tabSongsButton.getStyleClass().remove("nav-tab-active");
        tabAlbumsButton.getStyleClass().remove("nav-tab-active");
        tabArtistsButton.getStyleClass().remove("nav-tab-active");

        if (currentSortMode == SortMode.ALBUM) {
            tabAlbumsButton.getStyleClass().add("nav-tab-active");
        } else if (currentSortMode == SortMode.ARTIST) {
            tabArtistsButton.getStyleClass().add("nav-tab-active");
        } else {
            tabSongsButton.getStyleClass().add("nav-tab-active");
        }
    }

    @FXML
    private void handleTabSongs() {
        if (filterKind != FilterKind.NONE) {
            showAllSongs();
        }
        setSortMode(SortMode.A_Z);
    }

    @FXML
    private void handleTabAlbums() {
        if (filterKind != FilterKind.NONE) {
            showAllSongs();
        }
        setSortMode(SortMode.ALBUM);
    }

    @FXML
    private void handleTabArtists() {
        if (filterKind != FilterKind.NONE) {
            showAllSongs();
        }
        setSortMode(SortMode.ARTIST);
    }

    private void refreshGenreMenuOptions() {
        Set<String> genres = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        boolean hasUnknown = false;
        for (Track t : masterLibrary) {
            if (t.hasGenre()) {
                genres.add(t.getGenre());
            } else {
                hasUnknown = true;
            }
        }

        genreMenuButton.getItems().clear();
        MenuItem allItem = new MenuItem("All genres");
        allItem.setOnAction(e -> {
            currentGenreFilter = "All genres";
            genreMenuButton.setText("All genres");
            rebuildSongList();
        });
        genreMenuButton.getItems().add(allItem);

        for (String g : genres) {
            MenuItem gItem = new MenuItem(g);
            gItem.setOnAction(e -> {
                currentGenreFilter = g;
                genreMenuButton.setText(g);
                rebuildSongList();
            });
            genreMenuButton.getItems().add(gItem);
        }

        if (hasUnknown) {
            MenuItem unkItem = new MenuItem("Unknown genre");
            unkItem.setOnAction(e -> {
                currentGenreFilter = "Unknown genre";
                genreMenuButton.setText("Unknown genre");
                rebuildSongList();
            });
            genreMenuButton.getItems().add(unkItem);
        }
        genreMenuButton.setText(currentGenreFilter);
    }

    private void rebuildSongList() {
        List<Track> base = new ArrayList<>(activeBaseTracks);

        // Filter by Genre
        if (!"All genres".equalsIgnoreCase(currentGenreFilter)) {
            base = base.stream()
                    .filter(t -> t.getDisplayGenre().equalsIgnoreCase(currentGenreFilter))
                    .toList();
        }

        // Filter by Search Query
        String query = searchField.getText() == null ? "" : searchField.getText().strip().toLowerCase(Locale.ROOT);
        if (!query.isEmpty()) {
            base = base.stream()
                    .filter(t -> t.getTitle().toLowerCase(Locale.ROOT).contains(query)
                            || t.getDisplayArtist().toLowerCase(Locale.ROOT).contains(query)
                            || t.getDisplayAlbum().toLowerCase(Locale.ROOT).contains(query)
                            || t.getDisplayGenre().toLowerCase(Locale.ROOT).contains(query))
                    .toList();
        }

        // Sort base tracks
        Comparator<Track> comp = switch (currentSortMode) {
            case A_Z -> Comparator.comparing(Track::getTitle, String.CASE_INSENSITIVE_ORDER);
            case ARTIST -> Comparator.comparing(Track::getDisplayArtist, (a, b) -> {
                boolean aUnk = "Unknown artist".equalsIgnoreCase(a);
                boolean bUnk = "Unknown artist".equalsIgnoreCase(b);
                if (aUnk != bUnk) return aUnk ? 1 : -1;
                return a.compareToIgnoreCase(b);
            });
            case ALBUM -> Comparator.comparing(Track::getDisplayAlbum, (a, b) -> {
                boolean aUnk = "Unknown album".equalsIgnoreCase(a);
                boolean bUnk = "Unknown album".equalsIgnoreCase(b);
                if (aUnk != bUnk) return aUnk ? 1 : -1;
                return a.compareToIgnoreCase(b);
            });
            case RELEASE_YEAR -> (t1, t2) -> {
                boolean y1Known = t1.hasYear();
                boolean y2Known = t2.hasYear();
                if (y1Known != y2Known) return y1Known ? -1 : 1;
                return t2.getYear().compareToIgnoreCase(t1.getYear());
            };
            case DATE_ADDED -> Comparator.comparingLong(Track::getDateAdded).reversed();
        };

        List<Track> sorted = new ArrayList<>(base);
        sorted.sort(comp);
        displayedTracks.clear();
        displayedTracks.addAll(sorted);

        // Group into sections with headers ('&', '#', 'A'...)
        Map<String, List<Track>> groups = new LinkedHashMap<>();
        for (Track t : sorted) {
            String key = determineGroupKey(t, currentSortMode);
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(t);
        }

        List<SongListItem> items = new ArrayList<>();
        int cardIndex = 0;
        for (Map.Entry<String, List<Track>> entry : groups.entrySet()) {
            items.add(new SongListItem.SectionHeader(entry.getKey()));
            for (Track t : entry.getValue()) {
                items.add(new SongListItem.SongEntry(t, cardIndex % 2 == 0));
                cardIndex++;
            }
        }

        songListView.getItems().setAll(items);
        updateSelectionToolbar();
        refreshViewState();
    }

    private void setupSectionJumpOverlay() {
        sectionJumpOverlay.setOnMouseClicked(event -> {
            // Dismiss overlay if user clicks on the backdrop outside the letter buttons
            if (event.getTarget() == sectionJumpOverlay || event.getTarget() == sectionJumpGrid) {
                hideSectionJumpOverlay();
                event.consume();
            }
        });

        sectionJumpOverlay.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                hideSectionJumpOverlay();
                event.consume();
            } else {
                String text = event.getText();
                if (text != null && !text.isEmpty()) {
                    char ch = text.charAt(0);
                    if (Character.isLetter(ch)) {
                        String upper = String.valueOf(Character.toUpperCase(ch));
                        jumpToSection(upper);
                        hideSectionJumpOverlay();
                        event.consume();
                    } else if (Character.isDigit(ch)) {
                        jumpToSection("#");
                        hideSectionJumpOverlay();
                        event.consume();
                    } else if (ch == '&') {
                        jumpToSection("&");
                        hideSectionJumpOverlay();
                        event.consume();
                    }
                }
            }
        });
    }

    public void showSectionJumpOverlay() {
        if (displayedTracks.isEmpty()) {
            return;
        }

        Set<String> existingHeaders = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (SongListItem item : songListView.getItems()) {
            if (item instanceof SongListItem.SectionHeader h) {
                existingHeaders.add(h.title().trim());
            }
        }

        sectionJumpGrid.getChildren().clear();

        if (currentSortMode == SortMode.RELEASE_YEAR || currentSortMode == SortMode.DATE_ADDED) {
            int col = 0;
            int row = 0;
            int maxCols = 4;
            for (String title : existingHeaders) {
                Button chip = new Button(title);
                chip.getStyleClass().add("section-jump-chip");
                chip.setOnAction(e -> {
                    hideSectionJumpOverlay();
                    jumpToSection(title);
                    e.consume();
                });
                sectionJumpGrid.add(chip, col, row);
                col++;
                if (col >= maxCols) {
                    col = 0;
                    row++;
                }
            }
            Button closeBtn = new Button("...");
            closeBtn.getStyleClass().add("section-jump-chip");
            closeBtn.setOnAction(e -> {
                hideSectionJumpOverlay();
                e.consume();
            });
            sectionJumpGrid.add(closeBtn, col, row);
        } else {
            // 8-column alphabet layout matching Image 1 (media_1794196368577_10db7f83.png)
            for (int r = 0; r < ALPHABET_GRID.length; r++) {
                String[] rowItems = ALPHABET_GRID[r];
                for (int c = 0; c < rowItems.length; c++) {
                    String token = rowItems[c];
                    Button btn = new Button(token);
                    btn.getStyleClass().add("section-jump-btn");

                    if ("...".equals(token)) {
                        btn.setOnAction(e -> {
                            hideSectionJumpOverlay();
                            e.consume();
                        });
                    } else if (existingHeaders.contains(token)) {
                        btn.setOnAction(e -> {
                            hideSectionJumpOverlay();
                            jumpToSection(token);
                            e.consume();
                        });
                    } else {
                        btn.getStyleClass().add("section-jump-btn-disabled");
                        btn.setDisable(true);
                    }
                    sectionJumpGrid.add(btn, c, r);
                }
            }
        }

        setPaneVisible(sectionJumpOverlay, true);
        sectionJumpOverlay.requestFocus();
    }

    public void hideSectionJumpOverlay() {
        setPaneVisible(sectionJumpOverlay, false);
    }

    public void jumpToSection(String targetTitle) {
        if (targetTitle == null || targetTitle.isBlank()) {
            return;
        }
        List<SongListItem> items = songListView.getItems();
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i) instanceof SongListItem.SectionHeader h
                    && h.title().equalsIgnoreCase(targetTitle.trim())) {
                final int targetIndex = i;
                songListView.scrollTo(targetIndex);
                Platform.runLater(() -> songListView.scrollTo(targetIndex));
                break;
            }
        }
    }

    private static String determineGroupKey(Track track, SortMode mode) {
        return switch (mode) {
            case A_Z -> {
                String title = track.getTitle().strip();
                if (title.isEmpty()) yield "#";
                char first = title.charAt(0);
                if (Character.isLetter(first)) {
                    yield String.valueOf(Character.toUpperCase(first));
                } else if (Character.isDigit(first)) {
                    yield "#";
                } else {
                    yield "&";
                }
            }
            case ARTIST -> {
                String artist = track.getDisplayArtist().strip();
                if ("Unknown artist".equalsIgnoreCase(artist) || artist.isEmpty()) yield "#";
                char first = artist.charAt(0);
                if (Character.isLetter(first)) yield String.valueOf(Character.toUpperCase(first));
                yield "#";
            }
            case ALBUM -> {
                String album = track.getDisplayAlbum().strip();
                if ("Unknown album".equalsIgnoreCase(album) || album.isEmpty()) yield "#";
                char first = album.charAt(0);
                if (Character.isLetter(first)) yield String.valueOf(Character.toUpperCase(first));
                yield "#";
            }
            case RELEASE_YEAR -> track.hasYear() ? track.getYear() : "Unknown year";
            case DATE_ADDED -> {
                long added = track.getDateAdded();
                if (added <= 0) yield "Older";
                LocalDate date = Instant.ofEpochMilli(added).atZone(ZoneId.systemDefault()).toLocalDate();
                LocalDate now = LocalDate.now();
                long days = ChronoUnit.DAYS.between(date, now);
                if (days <= 0) yield "Today";
                if (days <= 7) yield "This week";
                if (days <= 30) yield "This month";
                if (date.getYear() == now.getYear()) yield "Earlier this year";
                yield String.valueOf(date.getYear());
            }
        };
    }

    private void setupRightPanel() {
        lyricsTabButton.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (!isSelected && !queueTabButton.isSelected()) {
                lyricsTabButton.setSelected(true);
                return;
            }
            if (isSelected) {
                queueTabButton.setSelected(false);
                showRightSubView(true);
            }
        });

        queueTabButton.selectedProperty().addListener((obs, wasSelected, isSelected) -> {
            if (!isSelected && !lyricsTabButton.isSelected()) {
                queueTabButton.setSelected(true);
                return;
            }
            if (isSelected) {
                lyricsTabButton.setSelected(false);
                showRightSubView(false);
            }
        });

        toggleRightDrawerButton.setSelected(false);
        rightDrawer.setVisible(false);
        rightDrawer.setManaged(false);
        toggleRightDrawerButton.selectedProperty().addListener((obs, oldV, visible) -> {
            rightDrawer.setVisible(visible);
            rightDrawer.setManaged(visible);
            if (visible) {
                if (!toggleRightDrawerButton.getStyleClass().contains("control-toggle-active")) {
                    toggleRightDrawerButton.getStyleClass().add("control-toggle-active");
                }
            } else {
                toggleRightDrawerButton.getStyleClass().remove("control-toggle-active");
            }
        });

        lyricsEmptyLabel.textProperty().bind(audioService.lyricStatusMessageProperty());
        if (expandedLyricsEmptyLabel != null) {
            expandedLyricsEmptyLabel.textProperty().bind(audioService.lyricStatusMessageProperty());
        }
        audioService.getCurrentLyrics().addListener((ListChangeListener<LyricLine>) c -> {
            rebuildLyricsView();
            rebuildExpandedLyricsView();
        });
        audioService.lyricStatusMessageProperty().addListener((obs, o, msg) -> {
            updateLyricsEmptyVisibility();
            updateExpandedLyricsEmptyVisibility();
        });
        audioService.activeLyricIndexProperty().addListener((obs, oldIdx, newIdx) -> {
            highlightAndScrollLyric(newIdx.intValue());
            highlightAndScrollExpandedLyric(newIdx.intValue());
        });

        upcomingListView.setItems(audioService.getUpcomingTracks());
        upcomingListView.setCellFactory(lv -> new ListCell<>() {
            private final Label titleLbl = new Label();
            private final Label artistLbl = new Label();
            private final VBox textCol = new VBox(2, titleLbl, artistLbl);
            private final Button removeBtn = new Button();
            private final HBox box = new HBox(8, textCol, removeBtn);

            {
                titleLbl.getStyleClass().add("queue-item-title");
                artistLbl.getStyleClass().add("queue-item-artist");
                textCol.setMinWidth(0);
                HBox.setHgrow(textCol, Priority.ALWAYS);

                FontIcon closeIcon = new FontIcon("mdi2c-close");
                closeIcon.getStyleClass().add("small-icon");
                removeBtn.setGraphic(closeIcon);
                removeBtn.getStyleClass().add("icon-button-subtle");
                removeBtn.setAccessibleText("Remove from queue");
                removeBtn.setTooltip(new Tooltip("Remove from queue"));
                removeBtn.setOnAction(e -> {
                    int idx = getIndex();
                    if (idx >= 0) {
                        audioService.removeUpcomingAt(idx);
                    }
                });

                box.setAlignment(Pos.CENTER_LEFT);
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
                    setGraphic(box);
                }
            }
        });

        upcomingListView.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                int idx = upcomingListView.getSelectionModel().getSelectedIndex();
                if (idx >= 0) {
                    audioService.playUpcomingAt(idx);
                }
            }
        });

        Runnable refreshQueueSummary = () -> {
            Track active = audioService.getCurrentTrack();
            queueNowPlayingLabel.setText(active != null
                    ? active.getTitle() + " - " + active.getDisplayArtist()
                    : "Nothing playing");
            boolean emptyUpcoming = audioService.getUpcomingTracks().isEmpty();
            queueEmptyBox.setVisible(emptyUpcoming);
            queueEmptyBox.setManaged(emptyUpcoming);
            upcomingListView.setVisible(!emptyUpcoming);
            upcomingListView.setManaged(!emptyUpcoming);
            clearQueueButton.setDisable(emptyUpcoming);
        };
        audioService.currentTrackProperty().addListener((obs, o, n) -> refreshQueueSummary.run());
        audioService.getUpcomingTracks().addListener((ListChangeListener<Track>) c -> refreshQueueSummary.run());
        refreshQueueSummary.run();

        showRightSubView(true);
        rebuildLyricsView();
        updateLyricsOffsetDisplay();
    }

    private void setupTransportBar() {
        Rectangle clip = new Rectangle(56, 56);
        clip.setArcWidth(10);
        clip.setArcHeight(10);
        nowPlayingCover.setClip(clip);

        audioService.currentArtworkProperty().addListener((obs, oldImg, newImg) -> {
            boolean hasArt = newImg != null;
            nowPlayingCover.setImage(newImg);
            nowPlayingCover.setVisible(hasArt);
            coverPlaceholderIcon.setVisible(!hasArt);

            if (expandedCoverImage != null) {
                expandedCoverImage.setImage(newImg);
                expandedCoverImage.setVisible(hasArt);
            }
            if (expandedPlaceholderIcon != null) {
                expandedPlaceholderIcon.setVisible(!hasArt);
            }
        });
        nowPlayingCover.setVisible(false);

        audioService.currentTrackProperty().addListener((obs, oldTrack, track) -> {
            if (track == null) {
                nowPlayingTitle.textProperty().unbind();
                nowPlayingArtistButton.textProperty().unbind();
                nowPlayingTitle.setText("No track selected");
                nowPlayingArtistButton.setText("Click any song to start listening");
                nowPlayingArtistButton.setDisable(true);
                seekSlider.setDisable(true);
                seekSlider.setValue(0);
                elapsedTimeLabel.setText("00:00");
                totalTimeLabel.setText("00:00");

                if (expandedTitleLabel != null) {
                    expandedTitleLabel.setText("No track playing");
                    expandedArtistLabel.setText("Unknown artist");
                    expandedSeekSlider.setDisable(true);
                    expandedSeekSlider.setValue(0);
                    expandedElapsedLabel.setText("0:00:00");
                    expandedTotalLabel.setText("0:00:00");
                }
            } else {
                nowPlayingTitle.textProperty().bind(track.titleProperty());
                nowPlayingArtistButton.textProperty().bind(Bindings.createStringBinding(
                        track::getDisplayArtist, track.artistProperty()));
                nowPlayingArtistButton.setDisable(!track.hasArtist());
                seekSlider.setDisable(false);

                if (expandedTitleLabel != null) {
                    expandedTitleLabel.setText(track.getTitle());
                    expandedArtistLabel.setText(track.getDisplayArtist());
                    expandedSeekSlider.setDisable(false);
                }
            }
        });
        nowPlayingArtistButton.setDisable(true);
        seekSlider.setDisable(true);

        audioService.playingProperty().addListener((obs, wasPlaying, isPlaying) -> {
            playPauseIcon.setIconLiteral(isPlaying ? "mdi2p-pause" : "mdi2p-play");
            playPauseButton.setAccessibleText(isPlaying ? "Pause" : "Play");
            playPauseButton.setTooltip(new Tooltip(isPlaying ? "Pause (Space)" : "Play (Space)"));

            if (expandedPlayPauseIcon != null) {
                expandedPlayPauseIcon.setIconLiteral(isPlaying ? "mdi2p-pause" : "mdi2p-play");
            }
            if (expandedPlayPauseButton != null) {
                expandedPlayPauseButton.setAccessibleText(isPlaying ? "Pause" : "Play");
                expandedPlayPauseButton.setTooltip(new Tooltip(isPlaying ? "Pause (Space)" : "Play (Space)"));
            }
        });

        shuffleButton.selectedProperty().addListener((obs, oldV, selected) -> {
            if (selected != audioService.shuffleProperty().get()) {
                audioService.toggleShuffle();
            }
            updateToggleStyle(shuffleButton, selected);
        });
        audioService.shuffleProperty().addListener((obs, oldV, selected) -> {
            shuffleButton.setSelected(selected);
            updateToggleStyle(shuffleButton, selected);
            if (expandedShuffleButton != null) {
                expandedShuffleButton.setSelected(selected);
                updateToggleStyle(expandedShuffleButton, selected);
            }
        });

        audioService.repeatModeProperty().addListener((obs, oldMode, mode) -> {
            updateRepeatVisual(mode);
            updateExpandedRepeatVisual(mode);
        });
        updateRepeatVisual(audioService.repeatModeProperty().get());
        updateExpandedRepeatVisual(audioService.repeatModeProperty().get());

        // Seek bar
        audioService.totalDurationSecondsProperty().addListener((obs, oldMax, newMax) -> {
            double total = Math.max(0, newMax.doubleValue());
            seekSlider.setMax(total > 0 ? total : 1);
            totalTimeLabel.setText(Track.formatTime(total));

            if (expandedSeekSlider != null) {
                expandedSeekSlider.setMax(total > 0 ? total : 1);
            }
            if (expandedTotalLabel != null) {
                expandedTotalLabel.setText(formatHoursTime(total));
            }
        });

        audioService.currentTimeSecondsProperty().addListener((obs, oldTime, newTime) -> {
            double secs = newTime.doubleValue();
            if (!scrubbingSeek) {
                seekSlider.setValue(secs);
                elapsedTimeLabel.setText(Track.formatTime(secs));
            }
            if (!expandedScrubbingSeek && expandedSeekSlider != null) {
                expandedSeekSlider.setValue(secs);
                expandedElapsedLabel.setText(formatHoursTime(secs));
            }
        });

        seekSlider.setOnMousePressed(e -> scrubbingSeek = true);
        seekSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (scrubbingSeek || seekSlider.isValueChanging()) {
                elapsedTimeLabel.setText(Track.formatTime(newVal.doubleValue()));
            }
        });
        seekSlider.valueChangingProperty().addListener((obs, wasChanging, changing) -> {
            if (changing) {
                scrubbingSeek = true;
            } else if (scrubbingSeek) {
                scrubbingSeek = false;
                audioService.seek(seekSlider.getValue());
            }
        });
        seekSlider.setOnMouseReleased(e -> {
            if (scrubbingSeek) {
                scrubbingSeek = false;
                audioService.seek(seekSlider.getValue());
            }
        });

        // Volume
        volumeSlider.setMin(0);
        volumeSlider.setMax(1);
        volumeSlider.setValue(audioService.volumeProperty().get());
        volumeSlider.valueProperty().bindBidirectional(audioService.volumeProperty());

        Runnable syncVolumeIcon = () -> {
            boolean isMuted = audioService.mutedProperty().get() || audioService.volumeProperty().get() == 0;
            double v = audioService.volumeProperty().get();
            String iconLit;
            String text;
            if (isMuted) {
                iconLit = "mdi2v-volume-off";
                text = "Unmute";
            } else if (v < 0.45) {
                iconLit = "mdi2v-volume-medium";
                text = "Mute";
            } else {
                iconLit = "mdi2v-volume-high";
                text = "Mute";
            }
            volumeIcon.setIconLiteral(iconLit);
            muteButton.setAccessibleText(text);
            if (expandedVolumeIcon != null) {
                expandedVolumeIcon.setIconLiteral(iconLit);
            }
            if (expandedMuteButton != null) {
                expandedMuteButton.setAccessibleText(text);
            }
        };
        audioService.volumeProperty().addListener((obs, o, n) -> syncVolumeIcon.run());
        audioService.mutedProperty().addListener((obs, o, n) -> syncVolumeIcon.run());
        syncVolumeIcon.run();
    }

    private void setupExpandedOverlay() {
        if (expandedCoverImage != null) {
            expandedCoverClip = new Rectangle(500, 500);
            expandedCoverClip.setArcWidth(24);
            expandedCoverClip.setArcHeight(24);
            expandedCoverImage.setClip(expandedCoverClip);
        }

        if (expandedCenterStack != null) {
            expandedCenterStack.heightProperty().addListener((obs, oldH, newH) -> updateCoverSize());
            expandedCenterStack.widthProperty().addListener((obs, oldW, newW) -> updateCoverSize());
        }

        if (expandedCoverFrame != null) {
            expandedCoverFrame.setCursor(Cursor.DEFAULT);
            expandedCoverFrame.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY) {
                    handleExpandedToggleLyrics();
                }
            });
        }

        if (expandedSeekSlider != null) {
            expandedSeekSlider.setOnMousePressed(e -> expandedScrubbingSeek = true);
            expandedSeekSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                if (expandedScrubbingSeek || expandedSeekSlider.isValueChanging()) {
                    expandedElapsedLabel.setText(formatHoursTime(newVal.doubleValue()));
                }
            });
            expandedSeekSlider.valueChangingProperty().addListener((obs, wasChanging, changing) -> {
                if (changing) {
                    expandedScrubbingSeek = true;
                } else if (expandedScrubbingSeek) {
                    expandedScrubbingSeek = false;
                    audioService.seek(expandedSeekSlider.getValue());
                }
            });
            expandedSeekSlider.setOnMouseReleased(e -> {
                if (expandedScrubbingSeek) {
                    expandedScrubbingSeek = false;
                    audioService.seek(expandedSeekSlider.getValue());
                }
            });
        }

        if (expandedShuffleButton != null) {
            expandedShuffleButton.selectedProperty().addListener((obs, oldV, selected) -> {
                if (selected != audioService.shuffleProperty().get()) {
                    audioService.toggleShuffle();
                }
                updateToggleStyle(expandedShuffleButton, selected);
            });
        }

        if (expandedVolumeSlider != null) {
            expandedVolumeSlider.setMin(0);
            expandedVolumeSlider.setMax(1);
            expandedVolumeSlider.setValue(audioService.volumeProperty().get());
            expandedVolumeSlider.valueProperty().bindBidirectional(audioService.volumeProperty());
        }
    }

    private void updateCoverSize() {
        if (expandedCenterStack == null || expandedCoverFrame == null || expandedCoverImage == null) return;
        double h = expandedCenterStack.getHeight();
        double w = expandedCenterStack.getWidth();
        if (h <= 0 || w <= 0) return;

        // Cover occupies available vertical space like Gambar 3
        double size = Math.min(h - 32.0, w - 48.0);
        size = Math.max(220.0, Math.min(850.0, size));

        expandedCoverFrame.setPrefSize(size, size);
        expandedCoverFrame.setMaxSize(size, size);
        expandedCoverFrame.setMinSize(size, size);

        expandedCoverImage.setFitWidth(size);
        expandedCoverImage.setFitHeight(size);

        if (expandedCoverClip != null) {
            expandedCoverClip.setWidth(size);
            expandedCoverClip.setHeight(size);
            double arc = Math.max(20.0, Math.min(40.0, size * 0.055));
            expandedCoverClip.setArcWidth(arc);
            expandedCoverClip.setArcHeight(arc);
        }

        if (expandedPlaceholderIcon != null) {
            expandedPlaceholderIcon.setIconSize((int) (size * 0.35));
        }
    }

    private void setupBannersAndShortcuts() {
        audioService.errorMessageProperty().addListener((obs, oldErr, err) -> {
            boolean show = err != null && !err.isBlank();
            playbackErrorLabel.setText(show ? err : "");
            playbackErrorBanner.setVisible(show);
            playbackErrorBanner.setManaged(show);
        });
        playbackErrorBanner.setVisible(false);
        playbackErrorBanner.setManaged(false);

        statusToastBar.setVisible(false);
        statusToastBar.setManaged(false);
        toastHideTimer.setOnFinished(e -> {
            statusToastBar.setVisible(false);
            statusToastBar.setManaged(false);
        });

        Node eventTarget = windowRootStack != null ? windowRootStack : rootPane;
        eventTarget.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            Node focused = rootPane.getScene() != null ? rootPane.getScene().getFocusOwner() : null;
            boolean typingInText = focused instanceof javafx.scene.control.TextInputControl;
            if (sectionJumpOverlay != null && sectionJumpOverlay.isVisible()) {
                if (event.getCode() == KeyCode.ESCAPE) {
                    hideSectionJumpOverlay();
                    event.consume();
                    return;
                }
            }
            if (nowPlayingExpandedOverlay != null && nowPlayingExpandedOverlay.isVisible()) {
                if (event.getCode() == KeyCode.ESCAPE) {
                    handleCloseNowPlayingExpanded();
                    event.consume();
                    return;
                } else if (!typingInText && event.getCode() == KeyCode.L) {
                    handleExpandedToggleLyrics();
                    event.consume();
                    return;
                }
            }
            if (event.isControlDown() && event.getCode() == KeyCode.F) {
                searchField.requestFocus();
                searchField.selectAll();
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.M) {
                handleOpenMiniPlayer();
                event.consume();
            } else if (event.isControlDown() && event.isShiftDown() && event.getCode() == KeyCode.E) {
                contextMenuBuilder.showEqualizerDialog(getWindow(), audioService);
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.K) {
                contextMenuBuilder.showCastToDeviceDialog(getWindow(), audioService);
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.I) {
                Track active = audioService.getCurrentTrack();
                if (active != null) {
                    contextMenuBuilder.showPropertiesDialog(active, getWindow());
                } else {
                    showStatusToast("No song is currently playing.");
                }
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.LEFT) {
                audioService.seekBy(-10);
                showStatusToast("Skipped back 10 seconds.");
                event.consume();
            } else if (event.isControlDown() && event.getCode() == KeyCode.RIGHT) {
                audioService.seekBy(30);
                showStatusToast("Skipped forward 30 seconds.");
                event.consume();
            } else if (!typingInText && event.getCode() == KeyCode.SPACE) {
                audioService.togglePlayPause();
                event.consume();
            }
        });
    }

    public void scanMusicDirectory(Path directory) {
        if (runningScanTask != null && runningScanTask.isRunning()) {
            runningScanTask.cancel();
        }
        this.activeMusicDirectory = directory;
        this.scanning = true;
        this.scanErrorText = null;
        if (folderPathLabel != null) {
            folderPathLabel.setText(directory.toString());
            folderPathLabel.setTooltip(new Tooltip(directory.toString()));
        }
        if (settingsMusicFolderLabel != null) {
            settingsMusicFolderLabel.setText(directory.toString());
        }

        Task<List<Track>> task = scannerService.createScanTask(directory);
        this.runningScanTask = task;

        scanProgressBar.progressProperty().unbind();
        scanProgressBar.progressProperty().bind(task.progressProperty());
        loadingMessageLabel.textProperty().unbind();
        loadingMessageLabel.textProperty().bind(task.messageProperty());

        refreshViewState();

        task.setOnSucceeded(e -> {
            scanning = false;
            scanErrorText = null;
            List<Track> discovered = task.getValue();
            masterLibrary.setAll(discovered);
            tracksByPath.clear();
            for (Track t : discovered) {
                tracksByPath.put(t.getPath().toAbsolutePath().normalize(), t);
            }
            loadPlaylistsAfterScan();
            refreshGenreMenuOptions();
            showAllSongs();
        });

        task.setOnFailed(e -> {
            scanning = false;
            Throwable ex = task.getException();
            scanErrorText = MusicScannerService.describe(ex != null ? ex : new RuntimeException("Unknown scan failure"));
            masterLibrary.clear();
            activeBaseTracks.clear();
            displayedTracks.clear();
            songListView.getItems().clear();
            refreshViewState();
        });

        Thread worker = new Thread(task, "localify-music-scanner");
        worker.setDaemon(true);
        worker.start();
    }

    private void loadPlaylistsAfterScan() {
        try {
            playlistService.load(path -> {
                Track cached = tracksByPath.get(path.toAbsolutePath().normalize());
                return cached != null ? cached : scannerService.readTrack(path);
            });
        } catch (IOException ex) {
            showStatusToast("Could not read saved playlists: " + ex.getMessage());
        }
    }

    @FXML
    public void handleAllSongs() {
        if (playlistListView != null) {
            playlistListView.getSelectionModel().clearSelection();
        }
        showAllSongs();
    }

    @FXML
    public void handleOpenPlaylistsView() {
        if (settingsViewPane != null) {
            settingsViewPane.setVisible(false);
            settingsViewPane.setManaged(false);
        }
        if (libraryViewPane != null) {
            libraryViewPane.setVisible(false);
            libraryViewPane.setManaged(false);
        }
        if (playlistsViewPane != null) {
            playlistsViewPane.setVisible(true);
            playlistsViewPane.setManaged(true);
        }
        if (playlistsNavButton != null && !playlistsNavButton.getStyleClass().contains("nav-button-active")) {
            playlistsNavButton.getStyleClass().add("nav-button-active");
        }
        if (allSongsNavButton != null) {
            allSongsNavButton.getStyleClass().remove("nav-button-active");
        }
        if (settingsButton != null) {
            settingsButton.getStyleClass().remove("sidebar-item-active");
        }
        renderLibraryGrid();
    }

    public void showAllSongs() {
        handleCloseSettings();
        if (playlistsViewPane != null) {
            playlistsViewPane.setVisible(false);
            playlistsViewPane.setManaged(false);
        }
        if (libraryViewPane != null) {
            libraryViewPane.setVisible(true);
            libraryViewPane.setManaged(true);
        }
        if (playlistBackButton != null) {
            playlistBackButton.setVisible(false);
            playlistBackButton.setManaged(false);
        }
        filterKind = FilterKind.NONE;
        filterValue = "";
        selectedPlaylist = null;
        activeBaseTracks.setAll(masterLibrary);
        updateNavigationHighlight();
        rebuildSongList();
    }

    public void showArtistFilter(String artist) {
        handleCloseSettings();
        if (artist == null || artist.isBlank()) {
            return;
        }
        if (playlistsViewPane != null) {
            playlistsViewPane.setVisible(false);
            playlistsViewPane.setManaged(false);
        }
        if (libraryViewPane != null) {
            libraryViewPane.setVisible(true);
            libraryViewPane.setManaged(true);
        }
        if (playlistBackButton != null) {
            playlistBackButton.setVisible(true);
            playlistBackButton.setManaged(true);
        }
        if (playlistListView != null) {
            playlistListView.getSelectionModel().clearSelection();
        }
        filterKind = FilterKind.ARTIST;
        filterValue = artist;
        selectedPlaylist = null;
        activeBaseTracks.setAll(masterLibrary.stream()
                .filter(t -> t.getArtist().equalsIgnoreCase(artist))
                .toList());
        updateNavigationHighlight();
        rebuildSongList();
    }

    public void showAlbumFilter(String album) {
        handleCloseSettings();
        if (album == null || album.isBlank()) {
            return;
        }
        if (playlistsViewPane != null) {
            playlistsViewPane.setVisible(false);
            playlistsViewPane.setManaged(false);
        }
        if (libraryViewPane != null) {
            libraryViewPane.setVisible(true);
            libraryViewPane.setManaged(true);
        }
        if (playlistBackButton != null) {
            playlistBackButton.setVisible(true);
            playlistBackButton.setManaged(true);
        }
        if (playlistListView != null) {
            playlistListView.getSelectionModel().clearSelection();
        }
        filterKind = FilterKind.ALBUM;
        filterValue = album;
        selectedPlaylist = null;
        activeBaseTracks.setAll(masterLibrary.stream()
                .filter(t -> t.getAlbum().equalsIgnoreCase(album))
                .toList());
        updateNavigationHighlight();
        rebuildSongList();
    }

    private void showPlaylist(Playlist playlist) {
        handleCloseSettings();
        if (playlistsViewPane != null) {
            playlistsViewPane.setVisible(false);
            playlistsViewPane.setManaged(false);
        }
        if (libraryViewPane != null) {
            libraryViewPane.setVisible(true);
            libraryViewPane.setManaged(true);
        }
        if (playlistBackButton != null) {
            playlistBackButton.setVisible(true);
            playlistBackButton.setManaged(true);
        }
        filterKind = FilterKind.PLAYLIST;
        filterValue = playlist.getName();
        selectedPlaylist = playlist;
        activeBaseTracks.setAll(playlist.getTracks());
        updateNavigationHighlight();
        rebuildSongList();
    }

    @FXML
    public void handlePillAll() {
        currentGridTab = LibraryGridTab.ALL;
        updatePillStyles();
        renderLibraryGrid();
    }

    @FXML
    public void handlePillPlaylists() {
        currentGridTab = LibraryGridTab.PLAYLISTS;
        updatePillStyles();
        renderLibraryGrid();
    }

    @FXML
    public void handlePillAlbums() {
        currentGridTab = LibraryGridTab.ALBUMS;
        updatePillStyles();
        renderLibraryGrid();
    }

    @FXML
    public void handlePillArtists() {
        currentGridTab = LibraryGridTab.ARTISTS;
        updatePillStyles();
        renderLibraryGrid();
    }

    private void updatePillStyles() {
        if (pillAllButton != null) {
            pillAllButton.getStyleClass().remove("pill-button-active");
            if (currentGridTab == LibraryGridTab.ALL) pillAllButton.getStyleClass().add("pill-button-active");
        }
        if (pillPlaylistsButton != null) {
            pillPlaylistsButton.getStyleClass().remove("pill-button-active");
            if (currentGridTab == LibraryGridTab.PLAYLISTS) pillPlaylistsButton.getStyleClass().add("pill-button-active");
        }
        if (pillAlbumsButton != null) {
            pillAlbumsButton.getStyleClass().remove("pill-button-active");
            if (currentGridTab == LibraryGridTab.ALBUMS) pillAlbumsButton.getStyleClass().add("pill-button-active");
        }
        if (pillArtistsButton != null) {
            pillArtistsButton.getStyleClass().remove("pill-button-active");
            if (currentGridTab == LibraryGridTab.ARTISTS) pillArtistsButton.getStyleClass().add("pill-button-active");
        }
    }

    public void renderLibraryGrid() {
        if (libraryGridFlowPane == null) return;
        libraryGridFlowPane.getChildren().clear();

        boolean showPlaylists = (currentGridTab == LibraryGridTab.ALL || currentGridTab == LibraryGridTab.PLAYLISTS);
        boolean showAlbums = (currentGridTab == LibraryGridTab.ALL || currentGridTab == LibraryGridTab.ALBUMS);
        boolean showArtists = (currentGridTab == LibraryGridTab.ARTISTS);

        List<Playlist> plList = new ArrayList<>(playlistService.getPlaylists());
        if ("ALPHA".equals(currentGridSort)) {
            plList.sort(Comparator.comparing(p -> p.getName().toLowerCase(Locale.ROOT)));
        } else if ("COUNT".equals(currentGridSort)) {
            plList.sort(Comparator.comparingInt((Playlist p) -> p.getTracks().size()).reversed());
        }

        if (showPlaylists) {
            for (Playlist pl : plList) {
                libraryGridFlowPane.getChildren().add(createPlaylistCard(pl));
            }
        }

        if (showAlbums) {
            Map<String, List<Track>> byAlbum = new LinkedHashMap<>();
            for (Track t : masterLibrary) {
                String alb = (t.getAlbum() == null || t.getAlbum().isBlank()) ? "Unknown album" : t.getAlbum();
                byAlbum.computeIfAbsent(alb, k -> new ArrayList<>()).add(t);
            }
            List<Map.Entry<String, List<Track>>> albumEntries = new ArrayList<>(byAlbum.entrySet());
            if ("ALPHA".equals(currentGridSort)) {
                albumEntries.sort(Comparator.comparing(e -> e.getKey().toLowerCase(Locale.ROOT)));
            } else if ("COUNT".equals(currentGridSort)) {
                albumEntries.sort(Comparator.comparingInt((Map.Entry<String, List<Track>> e) -> e.getValue().size()).reversed());
            }
            for (Map.Entry<String, List<Track>> entry : albumEntries) {
                libraryGridFlowPane.getChildren().add(createAlbumCard(entry.getKey(), entry.getValue()));
            }
        }

        if (showArtists) {
            Map<String, List<Track>> byArtist = new LinkedHashMap<>();
            for (Track t : masterLibrary) {
                String art = (t.getArtist() == null || t.getArtist().isBlank()) ? "Unknown artist" : t.getArtist();
                byArtist.computeIfAbsent(art, k -> new ArrayList<>()).add(t);
            }
            List<Map.Entry<String, List<Track>>> artistEntries = new ArrayList<>(byArtist.entrySet());
            if ("ALPHA".equals(currentGridSort)) {
                artistEntries.sort(Comparator.comparing(e -> e.getKey().toLowerCase(Locale.ROOT)));
            } else if ("COUNT".equals(currentGridSort)) {
                artistEntries.sort(Comparator.comparingInt((Map.Entry<String, List<Track>> e) -> e.getValue().size()).reversed());
            }
            for (Map.Entry<String, List<Track>> entry : artistEntries) {
                libraryGridFlowPane.getChildren().add(createArtistCard(entry.getKey(), entry.getValue()));
            }
        }

        boolean isEmpty = libraryGridFlowPane.getChildren().isEmpty();
        if (playlistsEmptyPromptBox != null) {
            playlistsEmptyPromptBox.setVisible(isEmpty);
            playlistsEmptyPromptBox.setManaged(isEmpty);
        }
    }

    private Node createPlaylistCard(Playlist playlist) {
        VBox card = new VBox(8);
        card.getStyleClass().add("grid-media-card");
        card.setPrefWidth(176);
        card.setMaxWidth(176);
        card.setCursor(Cursor.HAND);

        StackPane coverFrame = new StackPane();
        coverFrame.getStyleClass().add("grid-card-cover-frame");
        coverFrame.setMinSize(160, 160);
        coverFrame.setPrefSize(160, 160);
        coverFrame.setMaxSize(160, 160);

        FontIcon placeholderIcon = new FontIcon("mdi2p-playlist-music");
        placeholderIcon.setIconSize(48);
        placeholderIcon.setIconColor(Color.web("#888888"));

        ImageView coverView = new ImageView();
        coverView.setFitWidth(160);
        coverView.setFitHeight(160);
        coverView.setPreserveRatio(false);
        Rectangle clip = new Rectangle(160, 160);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        coverView.setClip(clip);

        loadPlaylistArtwork(playlist, coverView, placeholderIcon);

        StackPane hoverOverlay = new StackPane();
        hoverOverlay.getStyleClass().add("grid-card-hover-overlay");
        hoverOverlay.setVisible(false);

        Button playBtn = new Button();
        playBtn.getStyleClass().add("grid-card-play-btn");
        FontIcon playIcon = new FontIcon("mdi2p-play");
        playIcon.setIconSize(20);
        playIcon.setIconColor(Color.web("#0A0A0A"));
        playBtn.setGraphic(playIcon);
        StackPane.setAlignment(playBtn, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(playBtn, new Insets(0, 10, 10, 0));
        playBtn.setOnAction(e -> {
            e.consume();
            if (!playlist.getTracks().isEmpty()) {
                showPlaylist(playlist);
                audioService.playFromContext(playlist.getTracks(), playlist.getTracks().get(0));
            } else {
                showStatusToast("Playlist \"" + playlist.getName() + "\" is empty.");
            }
        });

        Button editCoverBtn = new Button();
        editCoverBtn.getStyleClass().add("grid-card-edit-btn");
        FontIcon camIcon = new FontIcon("mdi2c-camera-outline");
        camIcon.setIconSize(16);
        camIcon.setIconColor(Color.web("#F5F5F5"));
        editCoverBtn.setGraphic(camIcon);
        editCoverBtn.setTooltip(new Tooltip("Change cover image"));
        StackPane.setAlignment(editCoverBtn, Pos.TOP_RIGHT);
        StackPane.setMargin(editCoverBtn, new Insets(8, 8, 0, 0));
        editCoverBtn.setOnAction(e -> {
            e.consume();
            handleChangePlaylistCover(playlist);
        });

        hoverOverlay.getChildren().addAll(playBtn, editCoverBtn);
        coverFrame.getChildren().addAll(placeholderIcon, coverView, hoverOverlay);

        card.setOnMouseEntered(e -> hoverOverlay.setVisible(true));
        card.setOnMouseExited(e -> hoverOverlay.setVisible(false));

        Label titleLabel = new Label(playlist.getName());
        titleLabel.getStyleClass().add("grid-card-title");
        titleLabel.setMaxWidth(160);
        titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        int count = playlist.getTracks().size();
        String sub = count == 1 ? "Playlist · 1 song" : "Playlist · " + count + " songs";
        Label subLabel = new Label(sub);
        subLabel.getStyleClass().add("grid-card-subtitle");
        subLabel.setMaxWidth(160);
        subLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        VBox textInfo = new VBox(2, titleLabel, subLabel);
        card.getChildren().addAll(coverFrame, textInfo);

        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                showPlaylist(playlist);
            }
        });

        ContextMenu menu = new ContextMenu();
        MenuItem playItem = new MenuItem("Play");
        playItem.setOnAction(e -> {
            if (!playlist.getTracks().isEmpty()) {
                showPlaylist(playlist);
                audioService.playFromContext(playlist.getTracks(), playlist.getTracks().get(0));
            }
        });

        MenuItem changeCoverItem = new MenuItem("Change cover image...");
        changeCoverItem.setOnAction(e -> handleChangePlaylistCover(playlist));

        MenuItem resetCoverItem = new MenuItem("Reset cover image");
        resetCoverItem.setDisable(playlist.getCustomCoverPath() == null);
        resetCoverItem.setOnAction(e -> {
            try {
                playlistService.setCustomCover(playlist, null);
                renderLibraryGrid();
                showStatusToast("Reset cover for playlist \"" + playlist.getName() + "\".");
            } catch (IOException ex) {
                ContextMenuBuilder.showErrorAlert(getWindow(), "Could not reset cover", ex.getMessage());
            }
        });

        MenuItem renameItem = new MenuItem("Rename playlist...");
        renameItem.setOnAction(e -> handleRenamePlaylist(playlist));

        MenuItem deleteItem = new MenuItem("Delete playlist");
        deleteItem.setOnAction(e -> deletePlaylist(playlist));

        menu.getItems().addAll(playItem, new SeparatorMenuItem(), changeCoverItem, resetCoverItem, renameItem, new SeparatorMenuItem(), deleteItem);
        card.setOnContextMenuRequested(e -> menu.show(card, e.getScreenX(), e.getScreenY()));

        return card;
    }

    private void loadPlaylistArtwork(Playlist playlist, ImageView coverView, FontIcon placeholderIcon) {
        if (playlist.getCustomCoverPath() != null && Files.isRegularFile(playlist.getCustomCoverPath())) {
            try {
                Image img = new Image(playlist.getCustomCoverPath().toUri().toString(), 320, 320, true, true);
                coverView.setImage(img);
                coverView.setVisible(true);
                placeholderIcon.setVisible(false);
                return;
            } catch (Exception ignored) {}
        }

        for (Track t : playlist.getTracks()) {
            if (t.getThumbnail() != null) {
                coverView.setImage(t.getThumbnail());
                coverView.setVisible(true);
                placeholderIcon.setVisible(false);
                return;
            }
            byte[] art = scannerService.readArtwork(t.getPath());
            if (art != null && art.length > 0) {
                try {
                    Image img = new Image(new ByteArrayInputStream(art), 320, 320, true, true);
                    t.setThumbnail(img);
                    coverView.setImage(img);
                    coverView.setVisible(true);
                    placeholderIcon.setVisible(false);
                    return;
                } catch (Exception ignored) {}
            }
        }

        coverView.setVisible(false);
        placeholderIcon.setVisible(true);
    }

    public void handleChangePlaylistCover(Playlist playlist) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Playlist Cover Image - " + playlist.getName());
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Image Files (*.png, *.jpg, *.jpeg, *.webp, *.gif, *.bmp)",
                        "*.png", "*.jpg", "*.jpeg", "*.webp", "*.gif", "*.bmp", "*.PNG", "*.JPG", "*.JPEG", "*.WEBP")
        );
        File file = chooser.showOpenDialog(getWindow());
        if (file != null) {
            try {
                playlistService.setCustomCover(playlist, file.toPath());
                renderLibraryGrid();
                showStatusToast("Updated cover for playlist \"" + playlist.getName() + "\".");
            } catch (IOException ex) {
                ContextMenuBuilder.showErrorAlert(getWindow(), "Could not set playlist cover", ex.getMessage());
            }
        }
    }

    private void handleRenamePlaylist(Playlist playlist) {
        TextInputDialog dialog = new TextInputDialog(playlist.getName());
        dialog.setTitle("Rename Playlist");
        dialog.setHeaderText("Enter new name for playlist \"" + playlist.getName() + "\":");
        dialog.setContentText("Playlist name:");
        dialog.initOwner(getWindow());
        dialog.showAndWait().ifPresent(newName -> {
            String validation = playlistService.validateName(newName);
            if (validation != null && !newName.equalsIgnoreCase(playlist.getName())) {
                ContextMenuBuilder.showErrorAlert(getWindow(), "Invalid playlist name", validation);
                return;
            }
            try {
                playlistService.rename(playlist, newName);
                renderLibraryGrid();
                if (filterKind == FilterKind.PLAYLIST && Objects.equals(selectedPlaylist, playlist)) {
                    viewTitleLabel.setText(playlist.getName());
                }
                showStatusToast("Renamed playlist to \"" + playlist.getName() + "\".");
            } catch (IOException ex) {
                ContextMenuBuilder.showErrorAlert(getWindow(), "Could not rename playlist", ex.getMessage());
            }
        });
    }

    private Node createAlbumCard(String albumName, List<Track> tracks) {
        VBox card = new VBox(8);
        card.getStyleClass().add("grid-media-card");
        card.setPrefWidth(176);
        card.setMaxWidth(176);
        card.setCursor(Cursor.HAND);

        StackPane coverFrame = new StackPane();
        coverFrame.getStyleClass().add("grid-card-cover-frame");
        coverFrame.setMinSize(160, 160);
        coverFrame.setPrefSize(160, 160);
        coverFrame.setMaxSize(160, 160);

        FontIcon placeholderIcon = new FontIcon("mdi2a-album");
        placeholderIcon.setIconSize(48);
        placeholderIcon.setIconColor(Color.web("#888888"));

        ImageView coverView = new ImageView();
        coverView.setFitWidth(160);
        coverView.setFitHeight(160);
        coverView.setPreserveRatio(false);
        Rectangle clip = new Rectangle(160, 160);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        coverView.setClip(clip);

        for (Track t : tracks) {
            if (t.getThumbnail() != null) {
                coverView.setImage(t.getThumbnail());
                coverView.setVisible(true);
                placeholderIcon.setVisible(false);
                break;
            }
            byte[] art = scannerService.readArtwork(t.getPath());
            if (art != null && art.length > 0) {
                try {
                    Image img = new Image(new ByteArrayInputStream(art), 320, 320, true, true);
                    t.setThumbnail(img);
                    coverView.setImage(img);
                    coverView.setVisible(true);
                    placeholderIcon.setVisible(false);
                    break;
                } catch (Exception ignored) {}
            }
        }

        StackPane hoverOverlay = new StackPane();
        hoverOverlay.getStyleClass().add("grid-card-hover-overlay");
        hoverOverlay.setVisible(false);

        Button playBtn = new Button();
        playBtn.getStyleClass().add("grid-card-play-btn");
        FontIcon playIcon = new FontIcon("mdi2p-play");
        playIcon.setIconSize(20);
        playIcon.setIconColor(Color.web("#0A0A0A"));
        playBtn.setGraphic(playIcon);
        StackPane.setAlignment(playBtn, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(playBtn, new Insets(0, 10, 10, 0));
        playBtn.setOnAction(e -> {
            e.consume();
            if (!tracks.isEmpty()) {
                showAlbumFilter(albumName);
                audioService.playFromContext(tracks, tracks.get(0));
            }
        });

        hoverOverlay.getChildren().add(playBtn);
        coverFrame.getChildren().addAll(placeholderIcon, coverView, hoverOverlay);

        card.setOnMouseEntered(e -> hoverOverlay.setVisible(true));
        card.setOnMouseExited(e -> hoverOverlay.setVisible(false));

        Label titleLabel = new Label(albumName);
        titleLabel.getStyleClass().add("grid-card-title");
        titleLabel.setMaxWidth(160);
        titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        String artist = tracks.isEmpty() ? "" : tracks.get(0).getDisplayArtist();
        int count = tracks.size();
        String sub = "Album · " + count + (count == 1 ? " song" : " songs");
        Label subLabel = new Label(sub);
        subLabel.getStyleClass().add("grid-card-subtitle");
        subLabel.setMaxWidth(160);
        subLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        VBox textInfo = new VBox(2, titleLabel, subLabel);
        card.getChildren().addAll(coverFrame, textInfo);

        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                showAlbumFilter(albumName);
            }
        });

        return card;
    }

    private Node createArtistCard(String artistName, List<Track> tracks) {
        VBox card = new VBox(8);
        card.getStyleClass().add("grid-media-card");
        card.setPrefWidth(176);
        card.setMaxWidth(176);
        card.setCursor(Cursor.HAND);

        StackPane coverFrame = new StackPane();
        coverFrame.getStyleClass().add("grid-card-cover-frame");
        coverFrame.setMinSize(160, 160);
        coverFrame.setPrefSize(160, 160);
        coverFrame.setMaxSize(160, 160);

        FontIcon placeholderIcon = new FontIcon("mdi2a-account-music");
        placeholderIcon.setIconSize(48);
        placeholderIcon.setIconColor(Color.web("#888888"));

        ImageView coverView = new ImageView();
        coverView.setFitWidth(160);
        coverView.setFitHeight(160);
        coverView.setPreserveRatio(false);
        Rectangle clip = new Rectangle(160, 160);
        clip.setArcWidth(160);
        clip.setArcHeight(160);
        coverView.setClip(clip);

        for (Track t : tracks) {
            if (t.getThumbnail() != null) {
                coverView.setImage(t.getThumbnail());
                coverView.setVisible(true);
                placeholderIcon.setVisible(false);
                break;
            }
            byte[] art = scannerService.readArtwork(t.getPath());
            if (art != null && art.length > 0) {
                try {
                    Image img = new Image(new ByteArrayInputStream(art), 320, 320, true, true);
                    t.setThumbnail(img);
                    coverView.setImage(img);
                    coverView.setVisible(true);
                    placeholderIcon.setVisible(false);
                    break;
                } catch (Exception ignored) {}
            }
        }

        StackPane hoverOverlay = new StackPane();
        hoverOverlay.getStyleClass().add("grid-card-hover-overlay");
        hoverOverlay.setVisible(false);

        Button playBtn = new Button();
        playBtn.getStyleClass().add("grid-card-play-btn");
        FontIcon playIcon = new FontIcon("mdi2p-play");
        playIcon.setIconSize(20);
        playIcon.setIconColor(Color.web("#0A0A0A"));
        playBtn.setGraphic(playIcon);
        StackPane.setAlignment(playBtn, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(playBtn, new Insets(0, 10, 10, 0));
        playBtn.setOnAction(e -> {
            e.consume();
            if (!tracks.isEmpty()) {
                showArtistFilter(artistName);
                audioService.playFromContext(tracks, tracks.get(0));
            }
        });

        hoverOverlay.getChildren().add(playBtn);
        coverFrame.getChildren().addAll(placeholderIcon, coverView, hoverOverlay);

        card.setOnMouseEntered(e -> hoverOverlay.setVisible(true));
        card.setOnMouseExited(e -> hoverOverlay.setVisible(false));

        Label titleLabel = new Label(artistName);
        titleLabel.getStyleClass().add("grid-card-title");
        titleLabel.setMaxWidth(160);
        titleLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        int count = tracks.size();
        String sub = "Artist · " + count + (count == 1 ? " song" : " songs");
        Label subLabel = new Label(sub);
        subLabel.getStyleClass().add("grid-card-subtitle");
        subLabel.setMaxWidth(160);
        subLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        VBox textInfo = new VBox(2, titleLabel, subLabel);
        card.getChildren().addAll(coverFrame, textInfo);

        card.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                showArtistFilter(artistName);
            }
        });

        return card;
    }

    @FXML
    private void handleClearFilter() {
        handleAllSongs();
    }

    @FXML
    private void handleClearSearch() {
        searchField.clear();
        searchField.requestFocus();
    }

    @FXML
    private void handleChooseFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Music Folder");
        File current = activeMusicDirectory != null ? activeMusicDirectory.toFile() : null;
        if (current != null && current.isDirectory()) {
            chooser.setInitialDirectory(current);
        }
        File chosen = chooser.showDialog(getWindow());
        if (chosen != null) {
            scanMusicDirectory(chosen.toPath());
        }
    }

    @FXML
    private void handleRescan() {
        scanMusicDirectory(activeMusicDirectory);
    }

    @FXML
    private void handleNewPlaylist() {
        contextMenuBuilder.showCreatePlaylistDialog(getWindow(), created -> {
            playlistListView.getSelectionModel().select(created);
            showStatusToast("Created playlist \"" + created.getName() + "\".");
        });
    }

    @FXML
    private void handleShuffleAndPlay() {
        if (!displayedTracks.isEmpty()) {
            List<Track> shuffled = new ArrayList<>(displayedTracks);
            Collections.shuffle(shuffled);
            audioService.playFromContext(shuffled, shuffled.get(0));
            showStatusToast("Shuffled & playing " + shuffled.size() + " songs.");
        }
    }

    @FXML
    private void handleSelectAllToggle() {
        boolean select = selectAllCheckBox.isSelected();
        if (select) {
            selectedTracks.addAll(displayedTracks);
        } else {
            selectedTracks.clear();
        }
        updateSelectionToolbar();
        songListView.refresh();
    }

    @FXML
    private void handleClearSelection() {
        selectedTracks.clear();
        updateSelectionToolbar();
        songListView.refresh();
    }

    @FXML
    private void handleSelectionPlay() {
        if (selectedTracks.isEmpty()) return;
        List<Track> toPlay = new ArrayList<>(selectedTracks);
        Track first = toPlay.get(0);
        audioService.playFromContext(toPlay, first);
        showStatusToast("Playing " + toPlay.size() + " selected songs.");
        handleClearSelection();
    }

    @FXML
    private void handleSelectionPlayNext() {
        if (selectedTracks.isEmpty()) return;
        List<Track> toQueue = new ArrayList<>(selectedTracks);
        for (int i = toQueue.size() - 1; i >= 0; i--) {
            audioService.playNext(toQueue.get(i));
        }
        showStatusToast("Queued " + toQueue.size() + " songs to Play Next.");
        handleClearSelection();
    }

    private void setupSelectionAddToMenu() {
        if (selectionAddToButton == null) return;
        selectionAddToButton.setOnShowing(event -> {
            selectionAddToButton.getItems().clear();
            if (selectedTracks.isEmpty()) return;

            MenuItem newPlItem = new MenuItem("New playlist...");
            FontIcon plusIcon = new FontIcon("mdi2p-plus");
            plusIcon.setIconSize(14);
            plusIcon.setIconColor(javafx.scene.paint.Color.web("#F5F5F5"));
            newPlItem.setGraphic(plusIcon);
            newPlItem.setOnAction(e -> {
                List<Track> tracksToAdd = new ArrayList<>(selectedTracks);
                contextMenuBuilder.showCreatePlaylistDialog(getWindow(), created -> {
                    try {
                        playlistService.add(created, tracksToAdd);
                        showStatusToast("Created \"" + created.getName() + "\" with " + tracksToAdd.size() + " songs.");
                    } catch (IOException ex) {
                        ContextMenuBuilder.showErrorAlert(getWindow(), "Could not add to playlist", ex.getMessage());
                    }
                });
                handleClearSelection();
            });
            selectionAddToButton.getItems().add(newPlItem);

            List<Playlist> playlists = playlistService.getPlaylists();
            if (!playlists.isEmpty()) {
                selectionAddToButton.getItems().add(new SeparatorMenuItem());
                for (Playlist pl : playlists) {
                    MenuItem plItem = new MenuItem(pl.getName());
                    FontIcon plIcon = new FontIcon("mdi2p-playlist-music");
                    plIcon.setIconSize(14);
                    plIcon.setIconColor(javafx.scene.paint.Color.web("#B3B3B3"));
                    plItem.setGraphic(plIcon);
                    plItem.setOnAction(e -> {
                        List<Track> tracksToAdd = new ArrayList<>(selectedTracks);
                        try {
                            playlistService.add(pl, tracksToAdd);
                            showStatusToast("Added " + tracksToAdd.size() + " songs to \"" + pl.getName() + "\".");
                        } catch (IOException ex) {
                            ContextMenuBuilder.showErrorAlert(getWindow(), "Could not add to playlist", ex.getMessage());
                        }
                        handleClearSelection();
                    });
                    selectionAddToButton.getItems().add(plItem);
                }
            }
        });
    }

    private void updateSelectionToolbar() {
        int count = selectedTracks.size();
        boolean hasSelection = count > 0;
        if (selectionToolbar != null) {
            selectionToolbar.setVisible(hasSelection);
            selectionToolbar.setManaged(hasSelection);
        }
        if (defaultToolbar != null) {
            defaultToolbar.setVisible(!hasSelection);
            defaultToolbar.setManaged(!hasSelection);
        }
        if (hasSelection && selectionCountLabel != null) {
            selectionCountLabel.setText(count + " song" + (count > 1 ? "s" : "") + " selected");
            boolean allSelected = !displayedTracks.isEmpty() && selectedTracks.containsAll(displayedTracks);
            if (selectAllCheckBox != null) {
                selectAllCheckBox.setSelected(allSelected);
            }
        }
    }

    @FXML
    public void handleOpenSettings() {
        if (settingsViewPane != null) {
            settingsViewPane.setVisible(true);
            settingsViewPane.setManaged(true);
        }
        if (libraryViewPane != null) {
            libraryViewPane.setVisible(false);
            libraryViewPane.setManaged(false);
        }
        if (playlistsViewPane != null) {
            playlistsViewPane.setVisible(false);
            playlistsViewPane.setManaged(false);
        }
        if (settingsButton != null && !settingsButton.getStyleClass().contains("sidebar-item-active")) {
            settingsButton.getStyleClass().add("sidebar-item-active");
        }
        if (settingsMusicFolderLabel != null) {
            settingsMusicFolderLabel.setText(activeMusicDirectory != null ? activeMusicDirectory.toString() : "");
        }
    }

    @FXML
    public void handleCloseSettings() {
        if (settingsViewPane != null) {
            settingsViewPane.setVisible(false);
            settingsViewPane.setManaged(false);
        }
        if (playlistsViewPane != null && playlistsNavButton != null && playlistsNavButton.getStyleClass().contains("nav-button-active")) {
            playlistsViewPane.setVisible(true);
            playlistsViewPane.setManaged(true);
            if (libraryViewPane != null) {
                libraryViewPane.setVisible(false);
                libraryViewPane.setManaged(false);
            }
        } else {
            if (libraryViewPane != null) {
                libraryViewPane.setVisible(true);
                libraryViewPane.setManaged(true);
            }
        }
        if (settingsButton != null) {
            settingsButton.getStyleClass().remove("sidebar-item-active");
        }
    }

    @FXML
    private void handleRescanLibrary() {
        if (activeMusicDirectory != null) {
            scanMusicDirectory(activeMusicDirectory);
            showStatusToast("Rescanning library: " + activeMusicDirectory.getFileName());
        }
    }

    @FXML
    private void handleResetDiscordClientId() {
        if (settingsService != null) {
            settingsService.setDiscordClientId(SettingsService.DEFAULT_CLIENT_ID);
            if (settingsDiscordClientIdField != null) {
                settingsDiscordClientIdField.setText(SettingsService.DEFAULT_CLIENT_ID);
            }
            showStatusToast("Reset Discord Client ID to default");
        }
    }

    @FXML
    private void handleApplyAppUpdate() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Pilih File Update Localify (.jar atau .exe)");
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("File Update (*.jar, *.exe)", "*.jar", "*.exe"),
                new FileChooser.ExtensionFilter("JAR Package (*.jar)", "*.jar"),
                new FileChooser.ExtensionFilter("Installer EXE (*.exe)", "*.exe")
        );

        Window window = getWindow();
        File selected = chooser.showOpenDialog(window);
        if (selected == null) return;

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Konfirmasi Update");
        confirm.setHeaderText("Perbarui Localify");
        confirm.setContentText("Aplikasi akan ditutup sejenak dan diperbarui dengan file:\n" +
                selected.getName() + "\n\nSemua data lagu, playlist, dan pengaturan Anda tetap aman 100%.\nLanjutkan proses update?");
        if (window != null) confirm.initOwner(window);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            try {
                UpdateService.applyUpdate(selected.toPath());
                showStatusToast("Memulai update... Localify akan restart otomatis.");
                Platform.runLater(() -> {
                    shutdown();
                    Platform.exit();
                    System.exit(0);
                });
            } catch (Exception ex) {
                Alert errorAlert = new Alert(Alert.AlertType.ERROR);
                errorAlert.setTitle("Gagal Update");
                errorAlert.setHeaderText("Tidak dapat menerapkan update");
                errorAlert.setContentText(ex.getMessage());
                if (window != null) errorAlert.initOwner(window);
                errorAlert.showAndWait();
            }
        }
    }

    public void setupSettingsView() {
        if (settingsViewPane == null) return;

        if (settingsMusicFolderLabel != null) {
            settingsMusicFolderLabel.setText(activeMusicDirectory != null ? activeMusicDirectory.toString() : "");
        }

        if (settingsThemeMenu != null) {
            settingsThemeMenu.getItems().clear();
            List<String> themeLabels = List.of("Dark (Default)", "Light", "Nord Dark", "Dracula");
            for (String label : themeLabels) {
                String code = switch (label) {
                    case "Light" -> "LIGHT";
                    case "Nord Dark" -> "NORD";
                    case "Dracula" -> "DRACULA";
                    default -> "DARK";
                };
                MenuItem item = new MenuItem(label);
                item.setOnAction(e -> {
                    settingsThemeMenu.setText(label);
                    if (settingsService != null) {
                        settingsService.setTheme(code);
                        ThemeManager.apply(code, settingsService.getAccent());
                    }
                    showStatusToast("Theme set to " + label);
                });
                settingsThemeMenu.getItems().add(item);
            }
            String current = settingsService != null ? settingsService.getTheme() : "DARK";
            String display = switch (current) {
                case "LIGHT" -> "Light";
                case "NORD" -> "Nord Dark";
                case "DRACULA" -> "Dracula";
                default -> "Dark (Default)";
            };
            settingsThemeMenu.setText(display);
        }

        if (settingsAccentMenu != null) {
            settingsAccentMenu.getItems().clear();
            Map<String, String> accents = new LinkedHashMap<>();
            accents.put("ORANGE", "Zest (Orange)");
            accents.put("GREEN", "Spotify Green");
            accents.put("BLUE", "Electric Blue");
            accents.put("PURPLE", "Purple Velvet");
            accents.put("RED", "Crimson Red");
            accents.put("AMBER", "Amber Gold");

            for (Map.Entry<String, String> entry : accents.entrySet()) {
                String code = entry.getKey();
                String label = entry.getValue();
                MenuItem item = new MenuItem(label);
                item.setOnAction(e -> {
                    settingsAccentMenu.setText(label);
                    if (settingsService != null) {
                        settingsService.setAccent(code);
                        ThemeManager.apply(settingsService.getTheme(), code);
                    }
                    showStatusToast("Accent color set to " + label);
                });
                settingsAccentMenu.getItems().add(item);
            }
            String current = settingsService != null ? settingsService.getAccent() : "ORANGE";
            settingsAccentMenu.setText(accents.getOrDefault(current, "Zest (Orange)"));
        }

        if (settingsDiscordCheckBox != null && discordRpcService != null) {
            settingsDiscordCheckBox.setSelected(discordRpcService.isEnabled());
            settingsDiscordCheckBox.selectedProperty().addListener((obs, oldV, isSelected) -> {
                discordRpcService.setEnabled(isSelected);
            });
            discordRpcService.enabledProperty().addListener((obs, oldV, isSelected) -> {
                settingsDiscordCheckBox.setSelected(isSelected);
            });
        }

        if (settingsDiscordStatusLabel != null && discordRpcService != null) {
            settingsDiscordStatusLabel.textProperty().bind(
                    Bindings.concat("Status: ", discordRpcService.connectionStatusProperty()));
        }

        if (settingsDiscordClientIdField != null && settingsService != null) {
            settingsDiscordClientIdField.setText(settingsService.getDiscordClientId());
            settingsDiscordClientIdField.textProperty().addListener((obs, oldV, newId) -> {
                if (newId != null && !newId.isBlank()) {
                    settingsService.setDiscordClientId(newId);
                    if (discordRpcService != null) {
                        discordRpcService.reconnect();
                    }
                }
            });
        }
        updateLyricsOffsetDisplay();
    }

    public void shutdown() {
        thumbnailExecutor.shutdownNow();
    }

    @FXML
    private void handleReloadLyrics() {
        audioService.reloadLyrics();
        showStatusToast("Reloaded .lrc lyrics check.");
    }

    @FXML
    private void handleClearQueue() {
        audioService.clearUpcoming();
        showStatusToast("Cleared upcoming queue.");
    }

    @FXML
    private void handleDismissPlaybackError() {
        audioService.clearError();
    }

    @FXML
    private void handleNowPlayingArtistClick() {
        Track active = audioService.getCurrentTrack();
        if (active != null && active.hasArtist()) {
            showArtistFilter(active.getArtist());
        }
    }

    @FXML
    private void handleNowPlayingCardClick() {
        openNowPlayingExpanded();
    }

    public void openNowPlayingExpanded() {
        if (nowPlayingExpandedOverlay != null) {
            nowPlayingExpandedOverlay.setVisible(true);
            nowPlayingExpandedOverlay.setManaged(true);
            syncExpandedView();
        }
    }

    @FXML
    private void handleCloseNowPlayingExpanded() {
        if (nowPlayingExpandedOverlay != null) {
            nowPlayingExpandedOverlay.setVisible(false);
            nowPlayingExpandedOverlay.setManaged(false);
        }
    }

    @FXML
    private void handleExpandedRewind10() {
        audioService.seekBy(-10.0);
    }

    @FXML
    private void handleExpandedForward10() {
        audioService.seekBy(10.0);
    }

    @FXML
    private void handleExpandedToggleLyrics() {
        setExpandedLyricsMode(!expandedLyricsMode);
    }

    @FXML
    private void handleSwitchToCover() {
        setExpandedLyricsMode(false);
    }

    public void setExpandedLyricsMode(boolean lyricsActive) {
        this.expandedLyricsMode = lyricsActive;
        if (expandedCoverContainer != null) {
            expandedCoverContainer.setVisible(!lyricsActive);
            expandedCoverContainer.setManaged(!lyricsActive);
        }
        if (expandedLyricsBox != null) {
            expandedLyricsBox.setVisible(lyricsActive);
            expandedLyricsBox.setManaged(lyricsActive);
        }
        if (expandedLyricsButton != null) {
            if (lyricsActive) {
                if (!expandedLyricsButton.getStyleClass().contains("expanded-lyrics-button-active")) {
                    expandedLyricsButton.getStyleClass().add("expanded-lyrics-button-active");
                }
            } else {
                expandedLyricsButton.getStyleClass().remove("expanded-lyrics-button-active");
            }
        }
        if (lyricsActive) {
            rebuildExpandedLyricsView();
            highlightAndScrollExpandedLyric(audioService.activeLyricIndexProperty().get());
        }
    }

    @FXML
    private void handleChangeLyricsFile() {
        Track current = audioService.getCurrentTrack();
        if (current == null) {
            showStatusToast("Pilih lagu yang sedang diputar terlebih dahulu.");
            return;
        }
        promptChangeLyricsFile(current);
    }

    public void promptChangeLyricsFile(Track track) {
        if (track == null) return;
        javafx.stage.FileChooser chooser = new javafx.stage.FileChooser();
        chooser.setTitle("Pilih File Lirik (.lrc) untuk: " + track.getTitle());
        chooser.getExtensionFilters().addAll(
                new javafx.stage.FileChooser.ExtensionFilter("File Lirik LRC (*.lrc, *.txt)", "*.lrc", "*.txt"),
                new javafx.stage.FileChooser.ExtensionFilter("Semua File (*.*)", "*.*")
        );
        Path parent = track.getPath().getParent();
        if (parent != null && java.nio.file.Files.isDirectory(parent)) {
            chooser.setInitialDirectory(parent.toFile());
        }
        File selected = chooser.showOpenDialog(getWindow());
        if (selected != null && selected.isFile()) {
            try {
                audioService.applyCustomLrc(track, selected.toPath());
                rebuildLyricsView();
                rebuildExpandedLyricsView();
                showStatusToast("File lirik berhasil diganti: " + selected.getName());
            } catch (Exception ex) {
                showStatusToast("Gagal menerapkan file lirik: " + ex.getMessage());
            }
        }
    }

    @FXML
    private void handleNudgeLyricsOffsetMinusLarge() {
        adjustLyricsOffset(-0.5);
    }

    @FXML
    private void handleNudgeLyricsOffsetMinus() {
        adjustLyricsOffset(-0.2);
    }

    @FXML
    private void handleNudgeLyricsOffsetPlus() {
        adjustLyricsOffset(+0.2);
    }

    @FXML
    private void handleNudgeLyricsOffsetPlusLarge() {
        adjustLyricsOffset(+0.5);
    }

    @FXML
    private void handleResetLyricsOffset() {
        if (audioService != null) {
            audioService.setLyricsOffsetSeconds(0.0);
            updateLyricsOffsetDisplay();
            showStatusToast("Offset lirik direset ke 0.0s (Normal)");
        }
    }

    private void adjustLyricsOffset(double delta) {
        if (audioService != null) {
            double current = audioService.getLyricsOffsetSeconds();
            double updated = Math.round((current + delta) * 100.0) / 100.0;
            audioService.setLyricsOffsetSeconds(updated);
            updateLyricsOffsetDisplay();
            String hint = updated > 0 ? " (lebih cepat)" : (updated < 0 ? " (lebih lambat)" : "");
            showStatusToast("Offset lirik: " + String.format(java.util.Locale.US, "%+.2fs%s", updated, hint));
        }
    }

    private void updateLyricsOffsetDisplay() {
        if (audioService == null) return;
        double offset = audioService.getLyricsOffsetSeconds();
        String formatted = String.format(java.util.Locale.US, "%+.2fs", offset);
        if (settingsLyricsOffsetLabel != null) {
            settingsLyricsOffsetLabel.setText(formatted);
        }
    }

    private void rebuildExpandedLyricsView() {
        if (expandedLyricsContainer == null) {
            return;
        }
        expandedLyricsContainer.getChildren().clear();
        List<LyricLine> lines = audioService.getCurrentLyrics();
        boolean hasLines = lines != null && !lines.isEmpty();

        updateExpandedLyricsEmptyVisibility();

        if (!hasLines) {
            return;
        }

        for (LyricLine line : lines) {
            String text = line.text();
            Button lineBtn = new Button(text.isBlank() ? "• • •" : text);
            lineBtn.getStyleClass().add("expanded-lyric-button");
            lineBtn.setWrapText(true);
            lineBtn.setMaxWidth(780);
            double seekTarget = line.timeSeconds();
            lineBtn.setOnAction(e -> audioService.seek(seekTarget));
            expandedLyricsContainer.getChildren().add(lineBtn);
        }

        highlightAndScrollExpandedLyric(audioService.activeLyricIndexProperty().get());
    }

    private void updateExpandedLyricsEmptyVisibility() {
        if (expandedLyricsEmptyBox == null || expandedLyricsScrollPane == null) {
            return;
        }
        boolean hasLines = !audioService.getCurrentLyrics().isEmpty();
        expandedLyricsEmptyBox.setVisible(!hasLines);
        expandedLyricsEmptyBox.setManaged(!hasLines);
        expandedLyricsScrollPane.setVisible(hasLines);
        expandedLyricsScrollPane.setManaged(hasLines);
    }

    private void highlightAndScrollExpandedLyric(int activeIndex) {
        if (expandedLyricsContainer == null || expandedLyricsScrollPane == null) {
            return;
        }
        ObservableList<Node> nodes = expandedLyricsContainer.getChildren();
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            n.getStyleClass().removeAll("expanded-lyric-active", "expanded-lyric-past");
            if (i == activeIndex) {
                n.getStyleClass().add("expanded-lyric-active");
            } else if (i < activeIndex) {
                n.getStyleClass().add("expanded-lyric-past");
            }
        }

        if (activeIndex >= 0 && activeIndex < nodes.size()) {
            Platform.runLater(() -> {
                expandedLyricsContainer.applyCss();
                expandedLyricsContainer.layout();
                Node activeNode = nodes.get(activeIndex);
                double contentHeight = expandedLyricsContainer.getBoundsInLocal().getHeight();
                double viewportHeight = expandedLyricsScrollPane.getViewportBounds().getHeight();
                if (contentHeight <= viewportHeight || viewportHeight <= 0) {
                    return;
                }
                Bounds nb = activeNode.getBoundsInParent();
                double nodeCenterY = nb.getMinY() + nb.getHeight() / 2.0;
                double targetVvalue = (nodeCenterY - viewportHeight / 2.0) / (contentHeight - viewportHeight);
                targetVvalue = Math.max(0.0, Math.min(1.0, targetVvalue));

                if (expandedScrollTimeline != null) {
                    expandedScrollTimeline.stop();
                }
                expandedScrollTimeline = new Timeline(
                        new KeyFrame(Duration.millis(200),
                                new KeyValue(expandedLyricsScrollPane.vvalueProperty(), targetVvalue, Interpolator.EASE_OUT))
                );
                expandedScrollTimeline.play();
            });
        }
    }

    @FXML
    private void handleToggleFullscreen() {
        Stage stage = getStage();
        if (stage != null) {
            boolean nextState = !stage.isFullScreen();
            stage.setFullScreen(nextState);
            updateFullscreenVisual(nextState);
        }
    }

    private void updateFullscreenVisual(boolean fullScreen) {
        if (expandedFullscreenIcon != null) {
            expandedFullscreenIcon.setIconLiteral(fullScreen ? "mdi2f-fullscreen-exit" : "mdi2f-fullscreen");
        }
        if (expandedFullscreenButton != null) {
            expandedFullscreenButton.setAccessibleText(fullScreen ? "Exit fullscreen" : "Fullscreen");
        }
    }

    private void syncExpandedView() {
        Track current = audioService.getCurrentTrack();
        if (current == null) {
            if (expandedTitleLabel != null) {
                expandedTitleLabel.setText("No track playing");
                expandedArtistLabel.setText("Unknown artist");
                expandedSeekSlider.setDisable(true);
                expandedSeekSlider.setValue(0);
                expandedElapsedLabel.setText("0:00:00");
                expandedTotalLabel.setText("0:00:00");
            }
            if (expandedCoverImage != null) {
                expandedCoverImage.setImage(null);
                expandedCoverImage.setVisible(false);
            }
            if (expandedPlaceholderIcon != null) {
                expandedPlaceholderIcon.setVisible(true);
            }
        } else {
            if (expandedTitleLabel != null) {
                expandedTitleLabel.setText(current.getTitle());
                expandedArtistLabel.setText(current.getDisplayArtist());
                expandedSeekSlider.setDisable(false);
            }
            Image artwork = audioService.currentArtworkProperty().get();
            boolean hasArt = artwork != null;
            if (expandedCoverImage != null) {
                expandedCoverImage.setImage(artwork);
                expandedCoverImage.setVisible(hasArt);
            }
            if (expandedPlaceholderIcon != null) {
                expandedPlaceholderIcon.setVisible(!hasArt);
            }

            double total = Math.max(0, audioService.totalDurationSecondsProperty().get());
            if (expandedSeekSlider != null) {
                expandedSeekSlider.setMax(total > 0 ? total : 1);
            }
            if (expandedTotalLabel != null) {
                expandedTotalLabel.setText(formatHoursTime(total));
            }

            double currentSec = audioService.currentTimeSecondsProperty().get();
            if (!expandedScrubbingSeek && expandedSeekSlider != null) {
                expandedSeekSlider.setValue(currentSec);
                expandedElapsedLabel.setText(formatHoursTime(currentSec));
            }
        }

        boolean isPlaying = audioService.playingProperty().get();
        if (expandedPlayPauseIcon != null) {
            expandedPlayPauseIcon.setIconLiteral(isPlaying ? "mdi2p-pause" : "mdi2p-play");
        }
        if (expandedPlayPauseButton != null) {
            expandedPlayPauseButton.setAccessibleText(isPlaying ? "Pause" : "Play");
        }

        boolean isShuffle = audioService.shuffleProperty().get();
        if (expandedShuffleButton != null) {
            expandedShuffleButton.setSelected(isShuffle);
            updateToggleStyle(expandedShuffleButton, isShuffle);
        }

        updateExpandedRepeatVisual(audioService.repeatModeProperty().get());

        Stage stage = getStage();
        if (stage != null) {
            updateFullscreenVisual(stage.isFullScreen());
        }

        updateCoverSize();

        if (expandedLyricsMode) {
            rebuildExpandedLyricsView();
            highlightAndScrollExpandedLyric(audioService.activeLyricIndexProperty().get());
        }
    }

    @FXML
    private void handlePlayPause() {
        if (audioService.getCurrentTrack() == null && !displayedTracks.isEmpty()) {
            playTrackInCurrentView(displayedTracks.get(0));
        } else {
            audioService.togglePlayPause();
        }
    }

    @FXML
    private void handlePrevious() {
        audioService.previous();
    }

    @FXML
    private void handleNext() {
        audioService.next();
    }

    @FXML
    private void handleRepeatCycle() {
        audioService.cycleRepeatMode();
    }

    @FXML
    private void handleMuteToggle() {
        audioService.toggleMute();
    }

    @FXML
    private void handleOpenMiniPlayer() {
        if (onOpenMiniPlayer != null) {
            onOpenMiniPlayer.run();
        }
    }

    @FXML
    private void handleMorePlaybackOptions() {
        if (morePlaybackOptionsButton != null) {
            ContextMenu menu = contextMenuBuilder.buildPlaybackOptionsMenu(getWindow(), audioService, this::showStatusToast);
            menu.show(morePlaybackOptionsButton, Side.TOP, 0, -6);
        }
    }

    @FXML
    private void handleExpandedMoreOptions() {
        if (expandedMoreOptionsButton != null) {
            ContextMenu menu = contextMenuBuilder.buildPlaybackOptionsMenu(getWindow(), audioService, this::showStatusToast);
            menu.show(expandedMoreOptionsButton, Side.TOP, 0, -6);
        }
    }

    // TrackActionHandler implementation
    @Override
    public void onPlay(Track track) {
        playTrackInCurrentView(track);
    }

    @Override
    public void onPlayNext(Track track) {
        audioService.playNext(track);
        showStatusToast("Playing next: \"" + track.getTitle() + "\"");
    }

    @Override
    public void onAddToQueue(Track track) {
        audioService.addToQueue(track);
        showStatusToast("Added \"" + track.getTitle() + "\" to queue.");
    }

    @Override
    public void onAddToPlaylist(Playlist playlist, Track track) {
        try {
            playlistService.add(playlist, List.of(track));
            playlistListView.refresh();
            if (selectedPlaylist != null && selectedPlaylist.equals(playlist)) {
                activeBaseTracks.setAll(playlist.getTracks());
                rebuildSongList();
            }
            showStatusToast("Added \"" + track.getTitle() + "\" to \"" + playlist.getName() + "\".");
        } catch (IOException ex) {
            ContextMenuBuilder.showErrorAlert(getWindow(), "Could not save playlist", ex.getMessage());
        }
    }

    @Override
    public void onRemove(Track track) {
        if (track == null) {
            return;
        }
        if (filterKind == FilterKind.PLAYLIST && selectedPlaylist != null) {
            try {
                playlistService.remove(selectedPlaylist, track);
                activeBaseTracks.setAll(selectedPlaylist.getTracks());
                playlistListView.refresh();
                rebuildSongList();
                showStatusToast("Removed \"" + track.getTitle() + "\" from playlist \"" + selectedPlaylist.getName() + "\".");
            } catch (IOException ex) {
                ContextMenuBuilder.showErrorAlert(getWindow(), "Could not update playlist", ex.getMessage());
            }
        } else {
            masterLibrary.remove(track);
            activeBaseTracks.remove(track);
            audioService.removeFromQueue(track);
            rebuildSongList();
            showStatusToast("Removed \"" + track.getTitle() + "\" from current session (file kept on disk).");
        }
    }

    @Override
    public void onShowAlbum(String album) {
        showAlbumFilter(album);
    }

    @Override
    public void onShowArtist(String artist) {
        showArtistFilter(artist);
    }

    @Override
    public void onMetadataUpdated(Track track) {
        rebuildSongList();
        upcomingListView.refresh();
        audioService.refreshArtworkFor(track);
        refreshViewState();
    }

    @Override
    public void onChangeLyrics(Track track) {
        promptChangeLyricsFile(track);
    }

    @Override
    public void onStatusMessage(String message) {
        showStatusToast(message);
    }

    public void playTrackInCurrentView(Track track) {
        List<Track> context = displayedTracks.isEmpty()
                ? List.of(track)
                : new ArrayList<>(displayedTracks);
        audioService.playFromContext(context, track);
    }

    private void deletePlaylist(Playlist playlist) {
        try {
            boolean wasViewing = Objects.equals(selectedPlaylist, playlist);
            playlistService.delete(playlist);
            renderLibraryGrid();
            if (wasViewing) {
                handleOpenPlaylistsView();
            }
            showStatusToast("Deleted playlist \"" + playlist.getName() + "\".");
        } catch (IOException ex) {
            ContextMenuBuilder.showErrorAlert(getWindow(), "Could not delete playlist", ex.getMessage());
        }
    }

    private void updateNavigationHighlight() {
        boolean inPlaylistsView = playlistsViewPane != null && playlistsViewPane.isVisible();
        boolean isAll = filterKind == FilterKind.NONE && !inPlaylistsView;
        if (allSongsNavButton != null) {
            if (isAll) {
                if (!allSongsNavButton.getStyleClass().contains("nav-button-active")) {
                    allSongsNavButton.getStyleClass().add("nav-button-active");
                }
            } else {
                allSongsNavButton.getStyleClass().remove("nav-button-active");
            }
        }

        if (playlistsNavButton != null) {
            boolean isPlaylistsActive = inPlaylistsView || filterKind == FilterKind.PLAYLIST;
            if (isPlaylistsActive) {
                if (!playlistsNavButton.getStyleClass().contains("nav-button-active")) {
                    playlistsNavButton.getStyleClass().add("nav-button-active");
                }
            } else {
                playlistsNavButton.getStyleClass().remove("nav-button-active");
            }
        }

        boolean showChip = filterKind == FilterKind.ARTIST || filterKind == FilterKind.ALBUM;
        if (activeFilterBox != null) {
            activeFilterBox.setVisible(showChip);
            activeFilterBox.setManaged(showChip);
        }
        if (showChip && activeFilterLabel != null) {
            String prefix = filterKind == FilterKind.ARTIST ? "Artist: " : "Album: ";
            activeFilterLabel.setText(prefix + filterValue);
        }
    }

    private void refreshViewState() {
        String heading = switch (filterKind) {
            case NONE -> "Music";
            case ARTIST -> filterValue;
            case ALBUM -> filterValue;
            case PLAYLIST -> selectedPlaylist != null ? selectedPlaylist.getName() : "Playlist";
        };
        viewTitleLabel.setText(heading);

        boolean isMainLibrary = filterKind == FilterKind.NONE;
        if (libraryTabsBox != null) {
            libraryTabsBox.setVisible(isMainLibrary);
            libraryTabsBox.setManaged(isMainLibrary);
        }
        if (playlistBackButton != null) {
            boolean showBack = filterKind != FilterKind.NONE;
            playlistBackButton.setVisible(showBack);
            playlistBackButton.setManaged(showBack);
        }

        boolean showLoading = scanning;
        boolean showError = !scanning && scanErrorText != null;
        boolean showEmpty = !scanning && scanErrorText == null && displayedTracks.isEmpty();
        boolean showList = !scanning && scanErrorText == null && !displayedTracks.isEmpty();

        setPaneVisible(loadingStateBox, showLoading);
        setPaneVisible(errorStateBox, showError);
        setPaneVisible(emptyStateBox, showEmpty);
        setPaneVisible(songListView, showList);

        if (showLoading || showError || showEmpty) {
            hideSectionJumpOverlay();
        }

        if (showError) {
            errorTitleLabel.setText("Could not read music folder");
            errorDetailLabel.setText(scanErrorText);
        } else if (showEmpty) {
            boolean hasQuery = searchField.getText() != null && !searchField.getText().isBlank();
            if (hasQuery || filterKind == FilterKind.ARTIST || filterKind == FilterKind.ALBUM
                    || !"All genres".equalsIgnoreCase(currentGenreFilter)) {
                emptyTitleLabel.setText("No matching songs");
                emptyDetailLabel.setText("No tracks match your current filter or genre. Clear search or reset genre to view all songs.");
                emptyPrimaryActionButton.setText("Clear filter & genre");
                emptyPrimaryActionButton.setOnAction(e -> {
                    searchField.clear();
                    currentGenreFilter = "All genres";
                    genreMenuButton.setText("All genres");
                    showAllSongs();
                });
                emptySecondaryActionButton.setVisible(false);
                emptySecondaryActionButton.setManaged(false);
            } else if (filterKind == FilterKind.PLAYLIST) {
                emptyTitleLabel.setText("This playlist is empty");
                emptyDetailLabel.setText("Open All songs, right-click any track, and choose Add to -> \"" + heading + "\".");
                emptyPrimaryActionButton.setText("Browse all songs");
                emptyPrimaryActionButton.setOnAction(e -> handleAllSongs());
                emptySecondaryActionButton.setVisible(false);
                emptySecondaryActionButton.setManaged(false);
            } else {
                emptyTitleLabel.setText("No audio files in this folder");
                emptyDetailLabel.setText("Place .mp3, .wav, .flac, or .m4a files inside \""
                        + activeMusicDirectory + "\", or choose another folder.");
                emptyPrimaryActionButton.setText("Choose folder");
                emptyPrimaryActionButton.setOnAction(e -> handleChooseFolder());
                emptySecondaryActionButton.setText("Rescan folder");
                emptySecondaryActionButton.setOnAction(e -> handleRescan());
                emptySecondaryActionButton.setVisible(true);
                emptySecondaryActionButton.setManaged(true);
            }
        }
    }

    private void showRightSubView(boolean showLyrics) {
        boolean hasLyrics = !audioService.getCurrentLyrics().isEmpty();
        setPaneVisible(lyricsScrollPane, showLyrics && hasLyrics);
        setPaneVisible(lyricsEmptyBox, showLyrics && !hasLyrics);
        setPaneVisible(reloadLyricsButton, showLyrics);
        setPaneVisible(queuePane, !showLyrics);
    }

    private void rebuildLyricsView() {
        lyricsContainer.getChildren().clear();
        List<LyricLine> lines = audioService.getCurrentLyrics();
        for (LyricLine line : lines) {
            Button lineBtn = new Button(line.text().isBlank() ? "· · ·" : line.text());
            lineBtn.setWrapText(true);
            lineBtn.setMaxWidth(Double.MAX_VALUE);
            lineBtn.setAlignment(Pos.CENTER_LEFT);
            lineBtn.getStyleClass().add("lyric-line-button");
            lineBtn.setAccessibleText("Jump to lyric at " + Track.formatTime(line.timeSeconds()) + ": " + line.text());
            double seekTarget = line.timeSeconds();
            lineBtn.setOnAction(e -> audioService.seek(seekTarget));
            lyricsContainer.getChildren().add(lineBtn);
        }
        lyricsContainer.setPadding(new Insets(90, 12, 110, 12));
        updateLyricsEmptyVisibility();
        highlightAndScrollLyric(audioService.activeLyricIndexProperty().get());
    }

    private void updateLyricsEmptyVisibility() {
        if (!lyricsTabButton.isSelected()) {
            return;
        }
        boolean hasLyrics = !audioService.getCurrentLyrics().isEmpty();
        setPaneVisible(lyricsScrollPane, hasLyrics);
        setPaneVisible(lyricsEmptyBox, !hasLyrics);
    }

    private void highlightAndScrollLyric(int activeIndex) {
        ObservableList<Node> nodes = lyricsContainer.getChildren();
        for (int i = 0; i < nodes.size(); i++) {
            Node n = nodes.get(i);
            n.getStyleClass().removeAll("lyric-line-active", "lyric-line-past");
            if (i == activeIndex) {
                n.getStyleClass().add("lyric-line-active");
            } else if (i < activeIndex) {
                n.getStyleClass().add("lyric-line-past");
            }
        }
        if (activeIndex < 0 || activeIndex >= nodes.size() || !lyricsScrollPane.isVisible()) {
            return;
        }
        Platform.runLater(() -> {
            if (activeIndex < 0 || activeIndex >= nodes.size()) {
                return;
            }
            lyricsContainer.applyCss();
            lyricsContainer.layout();
            Node activeNode = nodes.get(activeIndex);
            double contentHeight = lyricsContainer.getBoundsInLocal().getHeight();
            double viewportHeight = lyricsScrollPane.getViewportBounds().getHeight();
            if (contentHeight <= viewportHeight || viewportHeight <= 0) {
                return;
            }
            Bounds nb = activeNode.getBoundsInParent();
            double rowCenter = nb.getMinY() + nb.getHeight() / 2.0;
            double targetV = (rowCenter - viewportHeight / 2.0) / (contentHeight - viewportHeight);
            double clampedV = Math.max(0.0, Math.min(1.0, targetV));
            Timeline scrollAnim = new Timeline(new KeyFrame(
                    Duration.millis(160),
                    new KeyValue(lyricsScrollPane.vvalueProperty(), clampedV, Interpolator.EASE_OUT)));
            scrollAnim.play();
        });
    }

    private void updateRepeatVisual(AudioService.RepeatMode mode) {
        switch (mode) {
            case OFF -> {
                repeatIcon.setIconLiteral("mdi2r-repeat-off");
                repeatButton.getStyleClass().remove("control-toggle-active");
                repeatButton.setAccessibleText("Repeat off");
                repeatButton.setTooltip(new Tooltip("Repeat: Off"));
            }
            case ALL -> {
                repeatIcon.setIconLiteral("mdi2r-repeat");
                if (!repeatButton.getStyleClass().contains("control-toggle-active")) {
                    repeatButton.getStyleClass().add("control-toggle-active");
                }
                repeatButton.setAccessibleText("Repeat all");
                repeatButton.setTooltip(new Tooltip("Repeat: All"));
            }
            case ONE -> {
                repeatIcon.setIconLiteral("mdi2r-repeat-once");
                if (!repeatButton.getStyleClass().contains("control-toggle-active")) {
                    repeatButton.getStyleClass().add("control-toggle-active");
                }
                repeatButton.setAccessibleText("Repeat current track");
                repeatButton.setTooltip(new Tooltip("Repeat: One"));
            }
        }
    }

    private static void updateToggleStyle(ToggleButton button, boolean active) {
        if (active) {
            if (!button.getStyleClass().contains("control-toggle-active")) {
                button.getStyleClass().add("control-toggle-active");
            }
        } else {
            button.getStyleClass().remove("control-toggle-active");
        }
    }

    private String currentRemoveLabel() {
        return filterKind == FilterKind.PLAYLIST ? "Remove from playlist" : "Remove";
    }

    private void showStatusToast(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
        statusToastLabel.setText(message);
        statusToastBar.setVisible(true);
        statusToastBar.setManaged(true);
        toastHideTimer.playFromStart();
    }

    private static void setPaneVisible(Node node, boolean visible) {
        node.setVisible(visible);
        node.setManaged(visible);
    }

    private void updateExpandedRepeatVisual(AudioService.RepeatMode mode) {
        if (expandedRepeatIcon == null || expandedRepeatButton == null) {
            return;
        }
        switch (mode) {
            case OFF -> {
                expandedRepeatIcon.setIconLiteral("mdi2r-repeat-off");
                expandedRepeatButton.getStyleClass().remove("control-toggle-active");
                expandedRepeatButton.setAccessibleText("Repeat off");
                expandedRepeatButton.setTooltip(new Tooltip("Repeat: Off"));
            }
            case ALL -> {
                expandedRepeatIcon.setIconLiteral("mdi2r-repeat");
                if (!expandedRepeatButton.getStyleClass().contains("control-toggle-active")) {
                    expandedRepeatButton.getStyleClass().add("control-toggle-active");
                }
                expandedRepeatButton.setAccessibleText("Repeat all");
                expandedRepeatButton.setTooltip(new Tooltip("Repeat: All"));
            }
            case ONE -> {
                expandedRepeatIcon.setIconLiteral("mdi2r-repeat-once");
                if (!expandedRepeatButton.getStyleClass().contains("control-toggle-active")) {
                    expandedRepeatButton.getStyleClass().add("control-toggle-active");
                }
                expandedRepeatButton.setAccessibleText("Repeat current track");
                expandedRepeatButton.setTooltip(new Tooltip("Repeat: One"));
            }
        }
    }

    private static String formatHoursTime(double seconds) {
        if (Double.isNaN(seconds) || seconds < 0) {
            return "0:00:00";
        }
        long totalSecs = Math.round(seconds);
        long hours = totalSecs / 3600;
        long minutes = (totalSecs % 3600) / 60;
        long secs = totalSecs % 60;
        return String.format("%d:%02d:%02d", hours, minutes, secs);
    }

    private Stage getStage() {
        Window window = getWindow();
        return window instanceof Stage stage ? stage : null;
    }

    private Window getWindow() {
        return rootPane != null && rootPane.getScene() != null ? rootPane.getScene().getWindow() : null;
    }
}
