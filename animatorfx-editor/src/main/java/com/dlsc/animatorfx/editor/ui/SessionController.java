package com.dlsc.animatorfx.editor.ui;

import java.util.Collections;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javafx.application.Platform;
import com.dlsc.animatorfx.editor.doc.EditorDocument;
import com.dlsc.animatorfx.editor.doc.ViewState;
import com.dlsc.animatorfx.editor.session.RecoveryStore;
import com.dlsc.animatorfx.editor.session.RecoveryStore.Recovered;
import com.dlsc.animatorfx.editor.session.RecoveryStore.Unreadable;
import com.dlsc.animatorfx.editor.session.SessionState;
import com.dlsc.animatorfx.editor.session.SessionState.DocumentState;
import com.dlsc.animatorfx.editor.session.SessionState.WindowState;
import com.dlsc.animatorfx.editor.session.SessionStore;
import com.dlsc.animatorfx.editor.ui.WindowPlacement.Placement;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.model.io.AnimationFormatException;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.beans.InvalidationListener;
import javafx.beans.value.ChangeListener;
import javafx.collections.ListChangeListener;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Keeps the session state and the recovery copies of a window up to date: restores them on
 * start, writes {@code session.json} 500 ms after the last change and on shutdown, and stores a
 * recovery copy of every dirty document every 5 seconds (on a background thread) and on shutdown. A document that becomes clean or is
 * closed loses its recovery copy. A recovery copy that cannot be read on start is kept under
 * another name and reported.
 */
public final class SessionController {

    private static final System.Logger LOG = System.getLogger(SessionController.class.getName());
    private static final Duration SAVE_DELAY = Duration.millis(500);
    private static final Duration AUTOSAVE_INTERVAL = Duration.seconds(5);

    private final MainWindow window;
    private final Workspace workspace;
    private final SessionStore sessionStore;
    private final RecoveryStore recoveryStore;

    private final PauseTransition saveTimer = new PauseTransition(SAVE_DELAY);
    private final Timeline autosaveTimer = new Timeline(new KeyFrame(AUTOSAVE_INTERVAL, e -> autosaveInBackground()));
    private final InvalidationListener changed = obs -> scheduleSave();
    /** For properties: unlike an invalidation listener it is told about every change, read or not. */
    private final ChangeListener<Object> valueChanged = (obs, oldValue, newValue) -> scheduleSave();
    private final ChangeListener<Boolean> dirtyChanged = (obs, wasDirty, isDirty) -> dirtyChanged();
    /** The content that the recovery copy of a document holds, by document id. */
    private final Map<String, AnimationFile> stored = new HashMap<>();
    private final Map<String, LocalTime> autosavedAt = new HashMap<>();
    /** The content that could not be read back as a recovery copy, by document id; it is not tried again. */
    private final Map<String, AnimationFile> unstorable = new HashMap<>();
    /** The documents whose failing recovery copy the user has been told about. */
    private final Set<String> reportedStoreFailures = new HashSet<>();
    /** The documents whose "recovered" notice still offers to discard them. */
    private final Map<EditorDocument, RecoveryNotice> recoveryNotices = new HashMap<>();

    private record RecoveryNotice(Runnable dismiss, ChangeListener<AnimationFile> contentListener) {
    }

    private record AutosaveJob(EditorDocument document, String id, AnimationFile content, Path path) {
    }

    /** Writes the periodic recovery copies of all windows, one after the other. */
    private static final ExecutorService AUTOSAVER = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "animatorfx-autosave");
        thread.setDaemon(true);
        return thread;
    });
    private CompletableFuture<Void> backgroundAutosave;
    /** The background runs whose files are on disk. */
    private final Set<CompletableFuture<Void>> written = Collections.newSetFromMap(new WeakHashMap<>());
    private WindowState normalBounds;
    private boolean closed;

    SessionController(MainWindow window, SessionStore sessionStore, RecoveryStore recoveryStore) {
        this.window = window;
        this.workspace = window.getWorkspace();
        this.sessionStore = sessionStore;
        this.recoveryStore = recoveryStore;
        saveTimer.setOnFinished(e -> saveNow());
        autosaveTimer.setCycleCount(Animation.INDEFINITE);
    }

    /**
     * Applies the stored session to the window and reopens its documents; must run before the
     * window shows. Never fails because of the stored state.
     */
    void restore() {
        SessionState state = sessionStore.load();
        window.getTheme().setMode(Theme.parseMode(state.theme(), Theme.Mode.SYSTEM));
        placeWindow(state.window());
        window.applyLayout(state.layout());
        window.getToolBar().setMuted(state.audioMuted());
        workspace.getRecentFiles().setAll(state.recentFiles());

        Set<String> seen = new HashSet<>();
        EditorDocument active = null;
        for (DocumentState saved : state.documents()) {
            if (!seen.add(saved.id())) {
                continue;
            }
            EditorDocument document = reopen(saved);
            if (document != null) {
                apply(saved, document);
                adopt(document);
                if (saved.id().equals(state.activeDocumentId())) {
                    active = document;
                }
            }
        }
        // copies the session does not know about are left over from a crash
        for (Recovered recovered : recoveryStore.all()) {
            if (seen.add(recovered.docId())) {
                adopt(recovered(recovered, recovered.originalPath()));
            }
        }
        for (Unreadable copy : recoveryStore.takeUnreadable()) {
            String name = copy.originalPath() == null || copy.originalPath().getFileName() == null
                    ? "an unsaved document" : copy.originalPath().getFileName().toString();
            window.getNoticeBar().show("The recovery copy of " + name + " could not be read. It was kept as "
                    + copy.movedTo() + ".");
        }
        if (workspace.getDocuments().isEmpty()) {
            workspace.add(EditorDocument.untitled());
        } else if (active != null) {
            workspace.setActiveDocument(active);
        }
    }

    /**
     * Starts following the window: from now on changes are saved.
     */
    void start() {
        Stage stage = window.getStage();
        ChangeListener<Object> bounds = (obs, oldValue, newValue) -> {
            rememberBounds();
            scheduleSave();
        };
        stage.xProperty().addListener(bounds);
        stage.yProperty().addListener(bounds);
        stage.widthProperty().addListener(bounds);
        stage.heightProperty().addListener(bounds);
        stage.maximizedProperty().addListener(valueChanged);
        window.getTheme().modeProperty().addListener(valueChanged);
        window.getToolBar().mutedProperty().addListener(valueChanged);
        window.setOnLayoutChanged(this::scheduleSave);
        workspace.getRecentFiles().addListener(changed);
        workspace.activeDocumentProperty().addListener((obs, oldDocument, newDocument) -> {
            scheduleSave();
            updateStatus();
        });
        workspace.getDocuments().addListener((ListChangeListener<EditorDocument>) change -> {
            while (change.next()) {
                change.getRemoved().forEach(this::untrack);
                change.getAddedSubList().forEach(this::track);
            }
            scheduleSave();
        });
        workspace.getDocuments().forEach(this::track);
        autosaveTimer.play();
        updateStatus();
        // restoring may have dropped documents; the stored session should not keep them until the next change
        scheduleSave();
    }

    /**
     * Writes {@code session.json} now instead of after the delay.
     */
    public void saveNow() {
        saveTimer.stop();
        try {
            sessionStore.save(snapshot());
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Could not save the session state", e);
        }
    }

    /**
     * Stores a recovery copy of every dirty document that changed since its last copy, now, on
     * the calling thread. A document whose copy could not be read back keeps its previous copy;
     * the user is told once per document, and the same content is not tried again.
     */
    public void autosaveNow() {
        awaitBackgroundAutosave();
        for (EditorDocument document : workspace.getDocuments()) {
            AutosaveJob job = jobFor(document);
            if (job != null) {
                stored(job, write(job));
            }
        }
        updateStatus();
    }

    /**
     * What the timer does: like {@link #autosaveNow()}, but the copies are written on a
     * background thread, because serialising, reading back and writing a document with a large
     * sound takes a few hundred milliseconds. The results are taken note of on the JavaFX
     * application thread. While one run is under way the next one is skipped.
     *
     * @return completes when the copies have been written; done already when there was nothing to do
     */
    public CompletableFuture<Void> autosaveInBackground() {
        if (backgroundAutosave != null && !backgroundAutosave.isDone()) {
            return backgroundAutosave;
        }
        List<AutosaveJob> jobs = new ArrayList<>();
        for (EditorDocument document : workspace.getDocuments()) {
            AutosaveJob job = jobFor(document);
            if (job != null) {
                jobs.add(job);
            }
        }
        if (jobs.isEmpty() || closed) {
            updateStatus();
            return CompletableFuture.completedFuture(null);
        }
        CompletableFuture<Void> done = new CompletableFuture<>();
        backgroundAutosave = done;
        AUTOSAVER.execute(() -> {
            List<RuntimeException> failures = new ArrayList<>();
            for (AutosaveJob job : jobs) {
                failures.add(write(job));
            }
            Platform.runLater(() -> {
                for (int i = 0; i < jobs.size(); i++) {
                    AutosaveJob job = jobs.get(i);
                    stored(job, failures.get(i));
                    // closed or saved while its copy was being written: the copy must not outlive that
                    if (!workspace.getDocuments().contains(job.document()) || !job.document().isDirty()) {
                        forget(job.document());
                    }
                }
                updateStatus();
                done.complete(null);
            });
            // for a caller that blocks the JavaFX thread to wait for the files
            markWritten(done);
        });
        return done;
    }

    /** The copy to write for a document; {@code null} when it needs none. */
    private AutosaveJob jobFor(EditorDocument document) {
        String id = document.getId();
        AnimationFile content = document.getFileContent();
        if (!document.isDirty() || stored.get(id) == content || unstorable.get(id) == content) {
            return null;
        }
        return new AutosaveJob(document, id, content, document.getPath());
    }

    /** Writes the copy; may run on any thread. Returns what went wrong, or {@code null}. */
    private RuntimeException write(AutosaveJob job) {
        try {
            recoveryStore.store(job.id(), job.content(), job.path());
            return null;
        } catch (RuntimeException e) {
            return e;
        }
    }

    /** Takes note of a written, or failed, copy; on the JavaFX application thread. */
    private void stored(AutosaveJob job, RuntimeException failure) {
        String id = job.id();
        if (failure == null) {
            stored.put(id, job.content());
            unstorable.remove(id);
            autosavedAt.put(id, LocalTime.now());
            return;
        }
        Throwable cause = failure.getCause() == null ? failure : failure.getCause();
        if (cause instanceof AnimationFormatException) {
            unstorable.put(id, job.content());
        }
        if (reportedStoreFailures.add(id)) {
            LOG.log(System.Logger.Level.WARNING, "Could not store a recovery copy of " + job.document().getTitle(), failure);
            String reason = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
            window.getNoticeBar().show("Recovery copy could not be written: " + reason + " ("
                    + job.document().getTitle() + ")");
        }
    }

    private void markWritten(CompletableFuture<Void> run) {
        synchronized (written) {
            written.add(run);
            written.notifyAll();
        }
    }

    /** Blocks until the files of a background run are on disk; its results are taken note of later. */
    private void awaitBackgroundAutosave() {
        CompletableFuture<Void> running = backgroundAutosave;
        if (running == null || running.isDone()) {
            return;
        }
        long deadline = System.nanoTime() + 30_000_000_000L;
        synchronized (written) {
            while (!written.contains(running) && System.nanoTime() < deadline) {
                try {
                    written.wait(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    /**
     * Persists everything for the next start: recovery copies of the dirty documents and the
     * session state. Further calls do nothing.
     */
    void shutdown() {
        if (closed) {
            return;
        }
        closed = true;
        autosaveTimer.stop();
        autosaveNow();
        saveNow();
    }

    SessionState snapshot() {
        List<DocumentState> documents = new ArrayList<>();
        for (EditorDocument document : workspace.getDocuments()) {
            ViewState view = document.getViewState();
            documents.add(new DocumentState(document.getId(), document.getPath(), view.getCanvasZoom(),
                    view.getCanvasPanX(), view.getCanvasPanY(), document.getPlayhead(),
                    document.getSelection().layerIds(), List.copyOf(document.getSelection().keyframes()),
                    view.getTimelineZoom(), view.getTimelineScroll(), List.copyOf(view.getExpandedLayerIds()),
                    document.isAutoKey(), view.isShowAllProperties()));
        }
        EditorDocument active = workspace.getActiveDocument();
        WindowState bounds = normalBounds == null ? null : new WindowState(normalBounds.x(), normalBounds.y(),
                normalBounds.width(), normalBounds.height(), window.getStage().isMaximized(), screenIndex());
        return new SessionState(bounds, window.getLayoutState(), window.getTheme().getMode().name(), documents,
                active == null ? null : active.getId(), workspace.getRecentFiles(), window.getToolBar().isMuted());
    }

    private void scheduleSave() {
        if (!closed) {
            saveTimer.playFromStart();
        }
    }

    private void placeWindow(WindowState saved) {
        List<Rectangle2D> screens = new ArrayList<>();
        screens.add(Screen.getPrimary().getVisualBounds());
        for (Screen screen : Screen.getScreens()) {
            if (!screen.equals(Screen.getPrimary())) {
                screens.add(screen.getVisualBounds());
            }
        }
        Placement placement = WindowPlacement.resolve(saved, screens);
        Stage stage = window.getStage();
        stage.setX(placement.x());
        stage.setY(placement.y());
        stage.setWidth(placement.width());
        stage.setHeight(placement.height());
        normalBounds = new WindowState(placement.x(), placement.y(), placement.width(), placement.height(), false, 0);
        stage.setMaximized(placement.maximized());
    }

    /** The bounds to come back to are those of the window while it is not maximized. */
    private void rememberBounds() {
        Stage stage = window.getStage();
        if (stage.isMaximized() || stage.isFullScreen() || stage.isIconified()
                || Double.isNaN(stage.getX()) || Double.isNaN(stage.getWidth())) {
            return;
        }
        normalBounds = new WindowState(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight(), false, 0);
    }

    private int screenIndex() {
        Stage stage = window.getStage();
        if (Double.isNaN(stage.getX()) || Double.isNaN(stage.getWidth())) {
            return 0;
        }
        List<Screen> on = Screen.getScreensForRectangle(stage.getX(), stage.getY(), stage.getWidth(), stage.getHeight());
        return on.isEmpty() ? 0 : Math.max(0, Screen.getScreens().indexOf(on.get(0)));
    }

    /** The document of a session entry, or {@code null} (with a notice) when it cannot come back. */
    private EditorDocument reopen(DocumentState saved) {
        Recovered copy = recoveryStore.load(saved.id()).orElse(null);
        if (copy != null) {
            return recovered(copy, saved.path() != null ? saved.path() : copy.originalPath());
        }
        Path file = saved.path();
        if (file == null) {
            return EditorDocument.untitled(saved.id());
        }
        if (!Files.exists(file)) {
            window.getNoticeBar().show(file.getFileName() + " no longer exists and was removed from the session.");
            return null;
        }
        try {
            return EditorDocument.open(saved.id(), file);
        } catch (IOException | RuntimeException e) {
            window.getNoticeBar().show("Could not reopen " + file.getFileName() + ": " + e.getMessage());
            return null;
        }
    }

    private EditorDocument recovered(Recovered copy, Path file) {
        EditorDocument document = EditorDocument.recovered(copy.docId(), copy.file(), file);
        stored.put(document.getId(), document.getFileContent());
        Runnable dismiss = window.getNoticeBar().show("Unsaved changes to " + document.getTitle() + " were recovered.",
                "Discard recovered changes", () -> discardRecovered(document));
        // Discarding is only offered for the recovered content itself: once the document is edited,
        // saved or closed, the offer would throw away more than the recovered changes.
        ChangeListener<AnimationFile> contentListener = (obs, oldContent, newContent) -> retireRecoveryNotice(document);
        document.fileContentProperty().addListener(contentListener);
        recoveryNotices.put(document, new RecoveryNotice(dismiss, contentListener));
        return document;
    }

    private void retireRecoveryNotice(EditorDocument document) {
        RecoveryNotice notice = recoveryNotices.remove(document);
        if (notice != null) {
            document.fileContentProperty().removeListener(notice.contentListener());
            notice.dismiss().run();
        }
    }

    private void discardRecovered(EditorDocument document) {
        if (recoveryNotices.get(document) == null || !workspace.getDocuments().contains(document)) {
            return;
        }
        retireRecoveryNotice(document);
        Path file = document.getPath();
        if (file != null && Files.exists(file)) {
            try {
                document.revert();
                return;
            } catch (IOException | RuntimeException e) {
                window.getNoticeBar().show("Could not revert " + document.getTitle() + ": " + e.getMessage());
            }
        }
        window.getFileActions().discard(document);
    }

    private static void apply(DocumentState saved, EditorDocument document) {
        ViewState view = document.getViewState();
        view.setCanvasZoom(saved.canvasZoom() > 0 ? saved.canvasZoom() : 1);
        view.setCanvasPanX(saved.canvasPanX());
        view.setCanvasPanY(saved.canvasPanY());
        view.setTimelineZoom(saved.timelineZoom() > 0 ? saved.timelineZoom() : 1);
        view.setTimelineScroll(saved.timelineScroll());
        view.getExpandedLayerIds().addAll(saved.expandedLayerIds());
        view.setShowAllProperties(saved.showAllProperties());
        document.setPlayhead(saved.playhead());
        document.setAutoKey(saved.autoKey());
        document.getSelection().layerIds().setAll(saved.selectedLayerIds());
        document.getSelection().keyframes().addAll(saved.selectedKeyframes());
        document.pruneSelection();
    }

    /** Adds a restored document without touching the recent files order more than needed. */
    private void adopt(EditorDocument document) {
        List<Path> recent = List.copyOf(workspace.getRecentFiles());
        workspace.add(document);
        workspace.getRecentFiles().setAll(recent);
    }

    private void track(EditorDocument document) {
        ViewState view = document.getViewState();
        document.pathProperty().addListener(valueChanged);
        document.playheadProperty().addListener(valueChanged);
        document.autoKeyProperty().addListener(valueChanged);
        document.getSelection().layerIds().addListener(changed);
        document.getSelection().keyframes().addListener(changed);
        view.canvasZoomProperty().addListener(valueChanged);
        view.canvasPanXProperty().addListener(valueChanged);
        view.canvasPanYProperty().addListener(valueChanged);
        view.timelineZoomProperty().addListener(valueChanged);
        view.timelineScrollProperty().addListener(valueChanged);
        view.showAllPropertiesProperty().addListener(valueChanged);
        view.getExpandedLayerIds().addListener(changed);
        document.dirtyProperty().addListener(dirtyChanged);
    }

    private void untrack(EditorDocument document) {
        ViewState view = document.getViewState();
        document.pathProperty().removeListener(valueChanged);
        document.playheadProperty().removeListener(valueChanged);
        document.autoKeyProperty().removeListener(valueChanged);
        document.getSelection().layerIds().removeListener(changed);
        document.getSelection().keyframes().removeListener(changed);
        view.canvasZoomProperty().removeListener(valueChanged);
        view.canvasPanXProperty().removeListener(valueChanged);
        view.canvasPanYProperty().removeListener(valueChanged);
        view.timelineZoomProperty().removeListener(valueChanged);
        view.timelineScrollProperty().removeListener(valueChanged);
        view.showAllPropertiesProperty().removeListener(valueChanged);
        view.getExpandedLayerIds().removeListener(changed);
        document.dirtyProperty().removeListener(dirtyChanged);
        retireRecoveryNotice(document);
        forget(document);
    }

    private void dirtyChanged() {
        for (EditorDocument document : workspace.getDocuments()) {
            if (!document.isDirty()) {
                retireRecoveryNotice(document);
                forget(document);
            }
        }
        updateStatus();
    }

    /** Deletes the recovery copy even when this window did not write it: a stale copy would shadow the file on the next start. */
    private void forget(EditorDocument document) {
        stored.remove(document.getId());
        unstorable.remove(document.getId());
        reportedStoreFailures.remove(document.getId());
        recoveryStore.discard(document.getId());
        autosavedAt.remove(document.getId());
    }

    private void updateStatus() {
        EditorDocument active = workspace.getActiveDocument();
        window.getStatusBar().setAutosaveState(active == null ? ""
                : Formats.autosaveState(active.isDirty(), autosavedAt.get(active.getId())));
    }
}
