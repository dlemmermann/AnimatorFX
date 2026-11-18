package com.dlsc.animatorfx.editor.doc;

import com.dlsc.animatorfx.editor.doc.Selection.KeyframeRef;
import com.dlsc.animatorfx.model.Animatable;
import com.dlsc.animatorfx.model.Animation;
import com.dlsc.animatorfx.model.Asset;
import com.dlsc.animatorfx.model.Keyframe;
import com.dlsc.animatorfx.model.Layer;
import com.dlsc.animatorfx.model.LayerProperties;
import com.dlsc.animatorfx.model.Layers;
import com.dlsc.animatorfx.model.PropertyRef;
import com.dlsc.animatorfx.model.io.AnimationFile;
import com.dlsc.animatorfx.model.io.AnimationIO;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanWrapper;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.beans.property.SimpleObjectProperty;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * An open document: the current immutable {@link Animation} with its asset bytes, the file it
 * belongs to, the selection, the playhead and the undo history. Works without a running JavaFX
 * toolkit; when used from a user interface, call it on the FX application thread.
 */
public final class EditorDocument {

    public static final String UNTITLED = "Untitled";

    /** Asset bytes are keyed by {@link Asset#id()}; both parts are immutable. */
    private record State(Animation animation, Map<String, byte[]> assets) {

        AnimationFile file() {
            return new AnimationFile(animation, assets);
        }
    }

    private record Step(String name, State before, State after) {
    }

    private final String id;
    private final ReadOnlyObjectWrapper<Animation> animation = new ReadOnlyObjectWrapper<>(this, "animation");
    private final ReadOnlyObjectWrapper<AnimationFile> fileContent = new ReadOnlyObjectWrapper<>(this, "fileContent");
    private final ObjectProperty<Path> path = new SimpleObjectProperty<>(this, "path");
    private final ReadOnlyBooleanWrapper dirty = new ReadOnlyBooleanWrapper(this, "dirty");
    private final ReadOnlyStringWrapper title = new ReadOnlyStringWrapper(this, "title", UNTITLED);
    private final DoubleProperty playhead = new SimpleDoubleProperty(this, "playhead");
    private final BooleanProperty autoKey = new SimpleBooleanProperty(this, "autoKey");
    private final ReadOnlyBooleanWrapper canUndo = new ReadOnlyBooleanWrapper(this, "canUndo");
    private final ReadOnlyBooleanWrapper canRedo = new ReadOnlyBooleanWrapper(this, "canRedo");
    private final ReadOnlyStringWrapper undoName = new ReadOnlyStringWrapper(this, "undoName");
    private final ReadOnlyStringWrapper redoName = new ReadOnlyStringWrapper(this, "redoName");
    private final Selection selection = new Selection();
    private final ViewState viewState = new ViewState();

    private final Deque<Step> undoStack = new ArrayDeque<>();
    private final Deque<Step> redoStack = new ArrayDeque<>();
    private State state;
    /** The state at the last save or open; {@code null} when the content has never been saved as it is. */
    private State saved;
    private Object gestureKey;

    private EditorDocument(String id, State state, Path path, boolean matchesFile) {
        this.id = Objects.requireNonNull(id, "id");
        this.path.addListener((obs, oldPath, newPath) -> updateTitle());
        this.path.set(path);
        this.saved = matchesFile ? state : null;
        setState(state);
    }

    /**
     * A new document with the default artboard: 800 × 600 pixels, 3 seconds, 60 frames per second.
     */
    public static EditorDocument untitled() {
        return untitled(UUID.randomUUID().toString());
    }

    /**
     * A new untitled document with a known id, used when the session restores an untouched
     * untitled document.
     */
    public static EditorDocument untitled(String id) {
        State state = new State(Animation.create(UNTITLED, 800, 600, 3), Map.of());
        return new EditorDocument(id, state, null, true);
    }

    public static EditorDocument open(Path file) throws IOException {
        return open(UUID.randomUUID().toString(), file);
    }

    /**
     * Opens a file with a known id, used when the session reopens a document.
     */
    public static EditorDocument open(String id, Path file) throws IOException {
        AnimationFile content = AnimationIO.read(file);
        return new EditorDocument(id, new State(content.animation(), content.assets()), file, true);
    }

    /**
     * A document restored from a recovery copy. It is dirty until it is saved or reverted.
     *
     * @param originalPathOrNull the file the document belongs to, or {@code null} for an untitled one
     */
    public static EditorDocument recovered(String id, AnimationFile content, Path originalPathOrNull) {
        return new EditorDocument(id, new State(content.animation(), content.assets()), originalPathOrNull, false);
    }

    /**
     * An id that stays the same across restarts of the editor; the session and the recovery
     * store refer to the document by it.
     */
    public String getId() {
        return id;
    }

    /**
     * The current document content. It changes with every command, undo and redo.
     *
     * @return the animation property
     */
    public final ReadOnlyObjectProperty<Animation> animationProperty() {
        return animation.getReadOnlyProperty();
    }

    public final Animation getAnimation() {
        return animation.get();
    }

    /**
     * The current document content together with its asset bytes, as an {@code AnimationView} takes it.
     *
     * @return the file content property
     */
    public final ReadOnlyObjectProperty<AnimationFile> fileContentProperty() {
        return fileContent.getReadOnlyProperty();
    }

    public final AnimationFile getFileContent() {
        return fileContent.get();
    }

    /**
     * The file of the document, or {@code null} for an untitled document.
     *
     * @return the path property
     */
    public final ObjectProperty<Path> pathProperty() {
        return path;
    }

    public final Path getPath() {
        return path.get();
    }

    public final void setPath(Path value) {
        path.set(value);
    }

    /**
     * Whether the content differs from the last saved or opened content. Undoing back to the
     * saved content clears the flag.
     *
     * @return the dirty property
     */
    public final ReadOnlyBooleanProperty dirtyProperty() {
        return dirty.getReadOnlyProperty();
    }

    public final boolean isDirty() {
        return dirty.get();
    }

    /**
     * The file name, or "Untitled" for a document without a file.
     *
     * @return the title property
     */
    public final ReadOnlyStringProperty titleProperty() {
        return title.getReadOnlyProperty();
    }

    public final String getTitle() {
        return title.get();
    }

    /**
     * The current time in seconds that the canvas shows and at which edits apply.
     *
     * @return the playhead property
     */
    public final DoubleProperty playheadProperty() {
        return playhead;
    }

    public final double getPlayhead() {
        return playhead.get();
    }

    public final void setPlayhead(double value) {
        playhead.set(value);
    }

    /**
     * Whether changing an animatable property adds or updates a keyframe at the playhead.
     *
     * @return the auto-key property
     */
    public final BooleanProperty autoKeyProperty() {
        return autoKey;
    }

    public final boolean isAutoKey() {
        return autoKey.get();
    }

    public final void setAutoKey(boolean value) {
        autoKey.set(value);
    }

    /**
     * Whether there is a step to undo.
     *
     * @return the can-undo property
     */
    public final ReadOnlyBooleanProperty canUndoProperty() {
        return canUndo.getReadOnlyProperty();
    }

    public final boolean isCanUndo() {
        return canUndo.get();
    }

    /**
     * Whether there is a step to redo.
     *
     * @return the can-redo property
     */
    public final ReadOnlyBooleanProperty canRedoProperty() {
        return canRedo.getReadOnlyProperty();
    }

    public final boolean isCanRedo() {
        return canRedo.get();
    }

    /**
     * The name of the command that undo would revert, or {@code null}.
     *
     * @return the undo name property
     */
    public final ReadOnlyStringProperty undoNameProperty() {
        return undoName.getReadOnlyProperty();
    }

    public final String getUndoName() {
        return undoName.get();
    }

    /**
     * The name of the command that redo would apply again, or {@code null}.
     *
     * @return the redo name property
     */
    public final ReadOnlyStringProperty redoNameProperty() {
        return redoName.getReadOnlyProperty();
    }

    public final String getRedoName() {
        return redoName.get();
    }

    public Selection getSelection() {
        return selection;
    }

    public ViewState getViewState() {
        return viewState;
    }

    /**
     * Applies the command as one undo step and clears the redo history. A command that returns
     * the animation it was given changes nothing: no undo step, no dirty flag.
     */
    public void execute(Command command) {
        gestureKey = null;
        apply(command, false);
    }

    /**
     * Applies the command as part of a continuous gesture such as a drag: a call with the same
     * key as the previous call replaces the top undo step instead of adding one, so the whole
     * gesture undoes at once. {@link #endGesture()}, {@link #execute}, undo and redo end the gesture.
     */
    public void executeCoalesced(Object gestureKey, Command command) {
        Objects.requireNonNull(gestureKey, "gestureKey");
        boolean continues = gestureKey.equals(this.gestureKey) && !undoStack.isEmpty();
        if (apply(command, continues)) {
            this.gestureKey = gestureKey;
        }
    }

    public void endGesture() {
        gestureKey = null;
    }

    public void undo() {
        gestureKey = null;
        if (undoStack.isEmpty()) {
            return;
        }
        Step step = undoStack.pop();
        redoStack.push(step);
        setState(step.before());
    }

    public void redo() {
        gestureKey = null;
        if (redoStack.isEmpty()) {
            return;
        }
        Step step = redoStack.pop();
        undoStack.push(step);
        setState(step.after());
    }

    /**
     * Adds an asset with its content to the document, as one undo step.
     *
     * @throws IllegalArgumentException if the document already has an asset with that id
     */
    public void addAsset(Asset asset, byte[] bytes) {
        Objects.requireNonNull(asset, "asset");
        Objects.requireNonNull(bytes, "bytes");
        if (state.animation().assets().stream().anyMatch(a -> a.id().equals(asset.id()))) {
            throw new IllegalArgumentException("Asset id is already in use: " + asset.id());
        }
        gestureKey = null;
        List<Asset> assets = new ArrayList<>(state.animation().assets());
        assets.add(asset);
        Map<String, byte[]> content = new HashMap<>(state.assets());
        content.put(asset.id(), bytes.clone());
        push("Add Asset", new State(state.animation().withAssets(assets), Map.copyOf(content)), false);
    }

    /**
     * @throws IllegalStateException if the document has no file yet
     */
    public void save() throws IOException {
        Path target = getPath();
        if (target == null) {
            throw new IllegalStateException("The document has no file; use saveAs");
        }
        AnimationIO.write(state.file(), target);
        saved = state;
        updateDirty();
    }

    /**
     * Saves to the file and makes it the file of the document. When writing fails the document
     * keeps its previous file.
     */
    public void saveAs(Path file) throws IOException {
        Objects.requireNonNull(file, "file");
        AnimationIO.write(state.file(), file);
        setPath(file);
        saved = state;
        updateDirty();
    }

    /**
     * Reloads the document from its file and drops the undo history.
     *
     * @throws IllegalStateException if the document has no file
     */
    public void revert() throws IOException {
        Path source = getPath();
        if (source == null) {
            throw new IllegalStateException("The document has no file to revert to");
        }
        AnimationFile content = AnimationIO.read(source);
        gestureKey = null;
        undoStack.clear();
        redoStack.clear();
        saved = new State(content.animation(), content.assets());
        setState(saved);
    }

    private boolean apply(Command command, boolean replaceTop) {
        Animation before = state.animation();
        Animation after = Objects.requireNonNull(command.apply(before), "animation");
        if (after == before) {
            return false;
        }
        push(command.name(), new State(after, assetsFor(after)), replaceTop);
        return true;
    }

    private void push(String name, State after, boolean replaceTop) {
        State before = state;
        if (replaceTop) {
            Step top = undoStack.pop();
            before = top.before();
            name = top.name();
        }
        undoStack.push(new Step(name, before, after));
        redoStack.clear();
        setState(after);
    }

    /** The asset bytes for a new animation: the same map unless the command removed an asset. */
    private Map<String, byte[]> assetsFor(Animation animation) {
        Map<String, byte[]> current = state.assets();
        if (animation.assets() == state.animation().assets()) {
            return current;
        }
        Set<String> ids = new HashSet<>();
        for (Asset asset : animation.assets()) {
            ids.add(asset.id());
        }
        if (ids.containsAll(current.keySet())) {
            return current;
        }
        Map<String, byte[]> kept = new HashMap<>(current);
        kept.keySet().retainAll(ids);
        return Map.copyOf(kept);
    }

    private void setState(State newState) {
        state = newState;
        canUndo.set(!undoStack.isEmpty());
        canRedo.set(!redoStack.isEmpty());
        undoName.set(undoStack.isEmpty() ? null : undoStack.peek().name());
        redoName.set(redoStack.isEmpty() ? null : redoStack.peek().name());
        updateDirty();
        boolean changed = animation.get() != newState.animation();
        fileContent.set(newState.file());
        animation.set(newState.animation());
        if (changed) {
            pruneSelection(newState.animation());
        }
    }

    private void updateDirty() {
        dirty.set(saved == null || state.animation() != saved.animation() || state.assets() != saved.assets());
    }

    private void updateTitle() {
        Path file = getPath();
        title.set(file == null || file.getFileName() == null ? UNTITLED : file.getFileName().toString());
    }

    /**
     * Removes layers and keyframes that the document does not have from the selection. The
     * document does this itself after every change of its content.
     */
    public void pruneSelection() {
        pruneSelection(state.animation());
    }

    private void pruneSelection(Animation current) {
        if (selection.layerIds().isEmpty() && selection.keyframes().isEmpty()) {
            return;
        }
        Map<String, Layer> layers = new HashMap<>();
        for (Layer layer : Layers.flatten(current)) {
            layers.put(layer.id(), layer);
        }
        selection.layerIds().removeIf(layerId -> !layers.containsKey(layerId));
        selection.keyframes().removeIf(ref -> !exists(layers.get(ref.layerId()), ref));
    }

    private static boolean exists(Layer layer, KeyframeRef ref) {
        if (layer == null) {
            return false;
        }
        for (PropertyRef property : LayerProperties.list(layer)) {
            if (property.key().equals(ref.propertyKey())) {
                Animatable<?> value = property.value();
                for (Keyframe<?> keyframe : value.keyframes()) {
                    if (keyframe.id().equals(ref.keyframeId())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
