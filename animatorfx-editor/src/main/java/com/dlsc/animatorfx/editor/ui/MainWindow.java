package com.dlsc.animatorfx.editor.ui;

import com.dlsc.animatorfx.editor.canvas.CanvasView;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.inspector.sections.MotionPathSection;
import com.dlsc.animatorfx.editor.session.RecoveryStore;
import com.dlsc.animatorfx.editor.session.SessionState.Layout;
import com.dlsc.animatorfx.editor.session.SessionStore;
import com.dlsc.animatorfx.editor.timeline.TimelineView;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Orientation;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TextInputControl;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;

import java.io.File;
import java.util.List;

/**
 * The main window in the motion-tool layout: toolbar with the document tabs at the top, tool rail
 * at the left, canvas in the centre, tabbed side panel at the right, timeline at the bottom and
 * the status bar below it.
 *
 * <p>Canvas, side panel and timeline each sit in their own slot. Their current content
 * ({@link CanvasView}, {@link SidePanel}, {@link TimelineView}) follows the active document
 * through a {@code documentProperty()}; {@link #setCanvasContent}, {@link #setSidePanelContent}
 * and {@link #setTimelineContent} swap in another node, which then follows
 * {@code getWorkspace().activeDocumentProperty()} itself.
 */
public final class MainWindow {

    private static final String APP_NAME = "AnimatorFX";

    private final Stage stage;
    private final Theme theme = new Theme();
    private final Workspace workspace = new Workspace();
    private final NoticeBar noticeBar = new NoticeBar();
    private final Dialogs dialogs;
    private final FileActions fileActions;
    private final ToolBarView toolBar;
    private final ToolRail toolRail = new ToolRail();
    private final StatusBar statusBar = new StatusBar();
    private final CanvasView canvasView = new CanvasView();
    private final SidePanel sidePanel = new SidePanel();
    private final TimelineView timelineView = new TimelineView();
    private final StackPane canvasSlot = new StackPane(canvasView);
    private final StackPane sideSlot = new StackPane(sidePanel);
    private final StackPane timelineSlot = new StackPane(timelineView);
    private final SplitPane upperSplit = new SplitPane(canvasSlot, sideSlot);
    private final SplitPane mainSplit = new SplitPane();
    private final BooleanProperty sidePanelVisible = new SimpleBooleanProperty(this, "sidePanelVisible", true);
    private final BooleanProperty timelineVisible = new SimpleBooleanProperty(this, "timelineVisible", true);
    private final MenuBar menuBar;
    private final Scene scene;
    private final SessionController session;

    private double sideDivider = Layout.DEFAULT.sideDivider();
    private double timelineDivider = Layout.DEFAULT.timelineDivider();
    /** Until the window is up, the split panes do not hold the divider positions to keep. */
    private boolean dividersPlaced;
    private Runnable onLayoutChanged = () -> {
    };
    private final ChangeListener<Object> layoutChanged = (obs, oldValue, newValue) -> onLayoutChanged.run();

    /**
     * Builds the window with the dialogs of the platform and restores the session from the stores.
     */
    public MainWindow(Stage stage, SessionStore sessionStore, RecoveryStore recoveryStore) {
        this(stage, sessionStore, recoveryStore, null);
    }

    /**
     * @param dialogs the dialogs of the file actions; {@code null} for the dialogs of the platform
     */
    public MainWindow(Stage stage, SessionStore sessionStore, RecoveryStore recoveryStore, Dialogs dialogs) {
        this.stage = stage;
        this.dialogs = dialogs != null ? dialogs : new SystemDialogs(theme);
        fileActions = new FileActions(workspace, this.dialogs, noticeBar, () -> stage);
        toolBar = new ToolBarView(new DocumentTabs(workspace, fileActions::close, fileActions::newDocument), theme);

        canvasSlot.setMinSize(0, 0);
        sideSlot.setMinWidth(220);
        timelineSlot.setMinHeight(80);
        upperSplit.getStyleClass().add("upper-split");
        BorderPane upper = new BorderPane(upperSplit);
        upper.setLeft(toolRail);
        upper.setMinHeight(120);
        mainSplit.setOrientation(Orientation.VERTICAL);
        mainSplit.getStyleClass().add("main-split");
        mainSplit.getItems().addAll(upper, timelineSlot);
        upperSplit.setDividerPositions(sideDivider);
        mainSplit.setDividerPositions(timelineDivider);

        menuBar = new MenuBarFactory(this).build();
        if (menuBar.isUseSystemMenuBar() && MenuBarFactory.MAC) {
            // the menus are in the system menu bar; the node must stay in the scene but takes no space
            menuBar.setMinHeight(0);
            menuBar.setPrefHeight(0);
            menuBar.setMaxHeight(0);
        }
        BorderPane root = new BorderPane(mainSplit);
        root.getStyleClass().add("main-window");
        root.setTop(new VBox(menuBar, toolBar, noticeBar));
        root.setBottom(statusBar);
        // no scene size: the stage is sized by the session before it shows
        scene = new Scene(root);
        theme.install(scene);

        for (var view : List.of(toolBar.documentProperty(), statusBar.documentProperty(),
                canvasView.documentProperty(), sidePanel.documentProperty(), timelineView.documentProperty())) {
            view.bind(workspace.activeDocumentProperty());
        }
        workspace.activeDocumentProperty().addListener((obs, oldDoc, newDoc) -> bindTitle(newDoc));
        bindTitle(null);
        installCanvas();
        installPanelToggles();
        installToolShortcuts();
        installFileDrop();

        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(560);
        stage.setOnCloseRequest(e -> shutdown());
        // The first layout of a window can run before the window has its real size, which moves the
        // dividers. So they are positioned once the window is up; from then on side panel and
        // timeline keep their size when the window is resized.
        stage.addEventHandler(WindowEvent.WINDOW_SHOWN, e -> Platform.runLater(() -> {
            if (isSidePanelVisible()) {
                upperSplit.setDividerPositions(sideDivider);
            }
            if (isTimelineVisible()) {
                mainSplit.setDividerPositions(timelineDivider);
            }
            SplitPane.setResizableWithParent(sideSlot, false);
            SplitPane.setResizableWithParent(timelineSlot, false);
            dividersPlaced = true;
        }));

        session = new SessionController(this, sessionStore, recoveryStore);
        session.restore();
        session.start();
    }

    public void show() {
        stage.show();
    }

    /**
     * Persists the session and the recovery copies of dirty documents, as quitting does. It does
     * not ask to save and does not hide the window; further calls do nothing.
     */
    public void shutdown() {
        session.shutdown();
        theme.dispose();
    }

    public Stage getStage() {
        return stage;
    }

    public Scene getScene() {
        return scene;
    }

    public Theme getTheme() {
        return theme;
    }

    public Workspace getWorkspace() {
        return workspace;
    }

    public FileActions getFileActions() {
        return fileActions;
    }

    public Dialogs getDialogs() {
        return dialogs;
    }

    public SessionController getSession() {
        return session;
    }

    public MenuBar getMenuBar() {
        return menuBar;
    }

    public ToolBarView getToolBar() {
        return toolBar;
    }

    public ToolRail getToolRail() {
        return toolRail;
    }

    public StatusBar getStatusBar() {
        return statusBar;
    }

    public NoticeBar getNoticeBar() {
        return noticeBar;
    }

    /**
     * The interactive canvas with its tools, overlay and playback.
     */
    public CanvasView getCanvasView() {
        return canvasView;
    }

    public SidePanel getSidePanel() {
        return sidePanel;
    }

    /**
     * The timeline with the layer tree, the ruler, the markers and the keyframe tracks.
     */
    public TimelineView getTimelineView() {
        return timelineView;
    }

    /**
     * Replaces what the centre region shows.
     */
    public void setCanvasContent(Node content) {
        canvasSlot.getChildren().setAll(content);
    }

    /**
     * Replaces what the right region shows.
     */
    public void setSidePanelContent(Node content) {
        sideSlot.getChildren().setAll(content);
    }

    /**
     * Replaces what the bottom region shows.
     */
    public void setTimelineContent(Node content) {
        timelineSlot.getChildren().setAll(content);
    }

    /**
     * The menu or menu item with that id, at any depth.
     *
     * @throws IllegalArgumentException if there is none
     */
    public MenuItem menuItem(String id) {
        MenuItem found = find(menuBar.getMenus(), id);
        if (found == null) {
            throw new IllegalArgumentException("No menu item with the id " + id);
        }
        return found;
    }

    /**
     * Whether the side panel is showing; hiding it gives its space to the canvas.
     *
     * @return the side-panel-visible property
     */
    public final BooleanProperty sidePanelVisibleProperty() {
        return sidePanelVisible;
    }

    public final boolean isSidePanelVisible() {
        return sidePanelVisible.get();
    }

    public final void setSidePanelVisible(boolean value) {
        sidePanelVisible.set(value);
    }

    /**
     * Whether the timeline is showing; hiding it gives its space to the canvas.
     *
     * @return the timeline-visible property
     */
    public final BooleanProperty timelineVisibleProperty() {
        return timelineVisible;
    }

    public final boolean isTimelineVisible() {
        return timelineVisible.get();
    }

    public final void setTimelineVisible(boolean value) {
        timelineVisible.set(value);
    }

    /**
     * Divider positions, collapsed panels and the active side-panel tab, as the session stores them.
     */
    public Layout getLayoutState() {
        if (dividersPlaced && isSidePanelVisible()) {
            sideDivider = upperSplit.getDividerPositions()[0];
        }
        if (dividersPlaced && isTimelineVisible()) {
            timelineDivider = mainSplit.getDividerPositions()[0];
        }
        return new Layout(sideDivider, timelineDivider, !isSidePanelVisible(), !isTimelineVisible(),
                sidePanel.getActiveTab());
    }

    public void applyLayout(Layout layout) {
        // hiding a panel remembers its current divider, so the new positions are set afterwards
        setSidePanelVisible(!layout.sidePanelCollapsed());
        setTimelineVisible(!layout.timelineCollapsed());
        sideDivider = clamp(layout.sideDivider(), Layout.DEFAULT.sideDivider());
        timelineDivider = clamp(layout.timelineDivider(), Layout.DEFAULT.timelineDivider());
        if (isSidePanelVisible()) {
            upperSplit.setDividerPositions(sideDivider);
        }
        if (isTimelineVisible()) {
            mainSplit.setDividerPositions(timelineDivider);
        }
        sidePanel.setActiveTab(layout.sideTab());
    }

    /** Called whenever a divider moves, a panel collapses or expands, or the side-panel tab changes. */
    void setOnLayoutChanged(Runnable handler) {
        onLayoutChanged = handler;
    }

    private static double clamp(double position, double fallback) {
        return position > 0.05 && position < 0.95 ? position : fallback;
    }

    /** Connects the canvas to the tool rail, the transport of the toolbar and the notice bar. */
    private void installCanvas() {
        canvasView.activeToolProperty().bindBidirectional(toolRail.activeToolProperty());
        canvasView.loopProperty().bind(toolBar.loopProperty());
        toolBar.playingProperty().bind(canvasView.playingProperty());
        toolBar.setOnPlayPause(canvasView::togglePlayback);
        canvasView.setNotifier(noticeBar::show);
        sidePanel.getInspector().section(MotionPathSection.class)
                .ifPresent(section -> section.setActions(canvasView::drawMotionPath, canvasView::editMotionPath));
    }

    private void installPanelToggles() {
        sidePanelVisible.addListener((obs, was, visible) -> {
            if (visible) {
                upperSplit.getItems().add(sideSlot);
                upperSplit.setDividerPositions(sideDivider);
            } else {
                if (dividersPlaced) {
                    sideDivider = upperSplit.getDividerPositions()[0];
                }
                upperSplit.getItems().remove(sideSlot);
            }
        });
        timelineVisible.addListener((obs, was, visible) -> {
            if (visible) {
                mainSplit.getItems().add(timelineSlot);
                mainSplit.setDividerPositions(timelineDivider);
            } else {
                if (dividersPlaced) {
                    timelineDivider = mainSplit.getDividerPositions()[0];
                }
                mainSplit.getItems().remove(timelineSlot);
            }
        });
        for (SplitPane split : List.of(upperSplit, mainSplit)) {
            split.getDividers().forEach(d -> d.positionProperty().addListener(layoutChanged));
            split.getDividers().addListener((ListChangeListener<SplitPane.Divider>) change -> {
                while (change.next()) {
                    change.getAddedSubList().forEach(d -> d.positionProperty().addListener(layoutChanged));
                }
                onLayoutChanged.run();
            });
        }
        sidePanel.getTabPane().getSelectionModel().selectedItemProperty().addListener(layoutChanged);
    }

    /**
     * A tool key pressed without modifiers selects the tool, unless the user is typing text or
     * is in the middle of drawing or editing a path.
     */
    private void installToolShortcuts() {
        scene.addEventHandler(KeyEvent.KEY_PRESSED, event -> {
            if (event.isShortcutDown() || event.isControlDown() || event.isAltDown() || event.isMetaDown()
                    || scene.getFocusOwner() instanceof TextInputControl || canvasView.blocksToolShortcuts()) {
                return;
            }
            ToolKind.forShortcut(event.getCode()).ifPresent(kind -> {
                toolRail.setActiveTool(kind);
                event.consume();
            });
        });
    }

    private void installFileDrop() {
        scene.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles() && event.getDragboard().getFiles().stream().anyMatch(MainWindow::isDocument)) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        scene.setOnDragDropped(event -> {
            boolean opened = false;
            if (event.getDragboard().hasFiles()) {
                for (File file : event.getDragboard().getFiles()) {
                    if (isDocument(file)) {
                        opened |= fileActions.open(file.toPath()).isPresent();
                    }
                }
            }
            event.setDropCompleted(opened);
            event.consume();
        });
    }

    private static boolean isDocument(File file) {
        return file.isFile() && file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".afx");
    }

    private void bindTitle(EditorDocument document) {
        stage.titleProperty().unbind();
        if (document == null) {
            stage.setTitle(APP_NAME);
        } else {
            stage.titleProperty().bind(Bindings.concat(APP_NAME, " — ", document.titleProperty()));
        }
    }

    private static MenuItem find(List<? extends MenuItem> items, String id) {
        for (MenuItem item : items) {
            if (id.equals(item.getId())) {
                return item;
            }
            if (item instanceof Menu menu) {
                MenuItem found = find(menu.getItems(), id);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
