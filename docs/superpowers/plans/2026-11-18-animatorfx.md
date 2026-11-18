# AnimatorFX Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build AnimatorFX: a JavaFX animation editor, an `.afx` file format, and a dependency-free runtime (`AnimationView`) that plays the files inside applications.

**Architecture:** Four Maven modules. `model` is a JavaFX-free immutable document model with its own JSON reader/writer. `player` samples the document with a pure evaluator and writes values onto a JavaFX node tree, driven by one `AnimationTimer`. `import` converts SVG, Lottie and FXML into model documents. `editor` is the authoring tool; it previews through `player` and edits by replacing immutable documents.

**Tech Stack:** Java 21 (`model`, `player`), Java 26 (`import`, `editor`), JavaFX 21.0.13 (`player`) and 25.0.5 (`editor`), AtlantaFX 3.0.0, Ikonli 12.4.0 (Material2 pack), JUnit Jupiter 6.1.3, TestFX 4.0.18, Maven 3.9.

**Spec:** `docs/superpowers/specs/2026-11-18-animatorfx-design.md` — read it before starting any task. Where this plan and the spec disagree, the spec wins unless the plan says "deviation".

## Global Constraints

- Group id `com.dlsc.animatorfx`, version `1.0-SNAPSHOT`. Base package equals the JPMS module name (`com.dlsc.animatorfx.model`, `.player`, `.importer`, `.editor`).
- `animatorfx-model` and `animatorfx-player` compile with `--release 21` and have **no third-party runtime dependencies**. `model` depends on `java.base` only.
- All model types are immutable records / sealed interfaces. Lists are stored with `List.copyOf`.
- Time in seconds (`double`); coordinates in document pixels, origin top-left, y down; angles in degrees clockwise; colours RGBA `0..1`, JSON form `#RRGGBBAA`; opacity and trim `0..1`; scale `1.0` = 100 %.
- JavaFX observable property accessor methods (`xxxProperty()`, getters, setters) are `final`. Property Javadoc goes on the `xxxProperty()` method only.
- Tests: JUnit Jupiter. Write the failing test first, see it fail, implement, see it pass. Tests that need a JavaFX toolkit must run headless.
- `javac` on this machine prints German diagnostics. Always run Maven as `MAVEN_OPTS="-Duser.language=en -Duser.country=US" mvn -q ...`.
- Do not trust the Maven exit code alone for tests: confirm with the `Tests run:` summary lines.
- Git: work on `main`. Commit after each task with a conventional message and the trailer `Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>`. Never push.
- Only comment code that needs clarification.

## Deviations from the spec's milestone order

The spec's milestone 5 ("advanced features": path morphing, masks, effects, trim path, motion path) is folded into the tasks that own each layer (model in Tasks 1–2, rendering in Task 4, editing UI in Task 9), so no layer of the system is built twice.

## File structure

```
pom.xml                               parent, dependencyManagement, plugin versions
animatorfx-model/    src/main/java/com/dlsc/animatorfx/model/
    Vec2, Rgba, Easing, Keyframe, Animatable, Interpolation        values + sampling
    PathData (Contour, Vertex), PathMorph, PathMeasure             Bezier paths
    Paint (NoPaint, Solid, LinearGradient, RadialGradient, Stop)
    Stroke, Trim, Mask, Effect, MotionPath, Transform
    Geometry (Rect, Ellipse, Line, Polygon, Star, PathGeometry), Geometries
    Layer, LayerBase, ShapeLayer, TextLayer, ImageLayer, GroupLayer, SlotLayer
    Animation, Marker, Asset, Layers, LayerProperties, PropertyRef
    io/ Json, JsonWriter, AnimationJson, AnimationIO, AnimationFile, AnimationFormatException
animatorfx-player/   src/main/java/com/dlsc/animatorfx/player/
    Evaluator, Frame, LayerState                                   pure sampling
    render/ Renderer, PathConverter, PaintConverter                node tree
    AnimationPlayer, AnimationView, AnimationViewSkin, Fit, SlotMode
    internal/ Clock, SlotBinding
animatorfx-import/   src/main/java/com/dlsc/animatorfx/importer/
    ImportResult, SvgImporter, SvgPathParser, LottieImporter, FxmlImporter
animatorfx-editor/   src/main/java/com/dlsc/animatorfx/editor/
    AnimatorApp, Launcher
    doc/     EditorDocument, Command, Commands, Selection
    session/ SessionState, SessionStore, RecoveryStore, AppDirs
    ui/      MainWindow, ToolBarView, ToolRail, StatusBar, Theme
    canvas/  CanvasView, Overlay, tools/*
    timeline/ TimelineView, LayerTreeColumn, TrackPane, TimeRuler
    inspector/ InspectorView, AssetsView, DocumentView, EasingEditor, fields/*
    resources: com/dlsc/animatorfx/editor/editor.css
samples/             animatorfx-sample-app, example .afx files
```

---

## Milestone 1 — Model and format

### Task 1: Build skeleton, value types, sampling

**Files:** root `pom.xml` (rewrite as parent with modules `animatorfx-model`, `animatorfx-player`, `animatorfx-import`, `animatorfx-editor`; create each module with a `pom.xml` and `module-info.java` so the reactor builds; delete the root `src/`), `animatorfx-model/src/main/java/com/dlsc/animatorfx/model/{Vec2,Rgba,Easing,Keyframe,Animatable,Interpolation,PathData,PathMorph,PathMeasure,Paint}.java`, tests under `animatorfx-model/src/test/java/...`.

**Produces:**

```java
public record Vec2(double x, double y) { ZERO, ONE; plus(Vec2), minus(Vec2), times(double), lerp(Vec2, double) }
public record Rgba(double r, double g, double b, double a) {
    static Rgba parse(String hex);   // "#RRGGBB" or "#RRGGBBAA"
    String toHex();                  // always "#RRGGBBAA", upper case
    Rgba lerp(Rgba o, double u);
    BLACK, WHITE, TRANSPARENT
}
public sealed interface Easing permits Easing.Linear, Easing.Hold, Easing.CubicBezier {
    double apply(double u);          // Hold returns 0 for u < 1
    Easing LINEAR, HOLD;
    record CubicBezier(double x1, double y1, double x2, double y2) implements Easing {}
    static Map<String, Easing> presets();   // ordered: "Ease", "Ease In", "Ease Out", "Ease In Out", sine/quad/cubic/quart/expo/back in/out/in-out
}
public record Keyframe<T>(String id, double time, T value, Easing easing) {}
public sealed interface Animatable<T> permits Animatable.Static, Animatable.Keyframed {
    static <T> Animatable<T> of(T value);
    static <T> Animatable<T> keyframed(List<Keyframe<T>> keyframes); // sorts by time, rejects empty
    T at(double time);
    boolean isAnimated();
    List<Keyframe<T>> keyframes();   // empty for Static
    Animatable<T> withKeyframe(double time, T value, Easing easing); // adds or replaces (|dt| < 1e-6); Static becomes Keyframed
    Animatable<T> withoutKeyframe(String keyframeId);                // last keyframe removed -> Static of its value
}
public final class Interpolation { static <T> T lerp(T a, T b, double u); }
public record PathData(List<Contour> contours) {
    record Contour(List<Vertex> vertices, boolean closed) {}
    record Vertex(Vec2 point, Vec2 in, Vec2 out) {}   // in/out are tangents RELATIVE to point; ZERO = corner
    static PathData EMPTY;
}
public final class PathMorph { static PathData morph(PathData a, PathData b, double u); }
public final class PathMeasure {              // flattening-based arc length
    PathMeasure(PathData path);
    double length();
    Vec2 pointAt(double fraction);            // 0..1 along the whole path
    double angleAt(double fraction);          // tangent, degrees
    PathData trim(double start, double end, double offset); // sub-path between fractions, wraps when closed
}
public sealed interface Paint permits Paint.None, Paint.Solid, Paint.LinearGradient, Paint.RadialGradient {
    record None() implements Paint {}
    record Solid(Rgba color) implements Paint {}
    record Stop(double offset, Rgba color) {}
    record LinearGradient(Vec2 start, Vec2 end, List<Stop> stops) implements Paint {}   // layer-local coords
    record RadialGradient(Vec2 center, double radius, List<Stop> stops) implements Paint {}
}
```

`Interpolation.lerp` rules: `Double`, `Vec2`, `Rgba` linear; `Paint` component-wise when both are the same record type with equal stop counts, otherwise returns `a` for `u < 1` and `b` at `u >= 1`; `PathData` via `PathMorph`; any other type holds.

`PathMorph`: contour counts differ, or a contour pair differs in `closed` → hold. Otherwise per contour pair, split the longest segment of the shorter contour (de Casteljau at 0.5) until vertex counts match, then lerp `point`, `in`, `out`.

- [ ] **Step 1:** Create the reactor (parent + four module poms + `module-info.java` each) and confirm `mvn -q validate` succeeds. Add maven-enforcer `bannedDependencies` in `model` and `player` that fails on any compile/runtime dependency outside `com.dlsc.animatorfx` and `org.openjfx`.
- [ ] **Step 2:** Write failing tests: `Rgba` hex round-trip (`#11223344`, 6-digit input gets alpha `FF`); `Easing.CubicBezier(0.25,0.1,0.25,1).apply(0.5)` ≈ 0.8024 (±1e-3), endpoints exact, `LINEAR.apply(0.3) == 0.3`, `HOLD.apply(0.99) == 0`; `Animatable` before-first / after-last / midpoint / eased midpoint / hold; `withKeyframe` replace-at-same-time and Static→Keyframed; `withoutKeyframe` last→Static; `Interpolation` on `Double`, `Vec2`, `Rgba`, matching and non-matching gradients, `String` hold; `PathMorph` equal counts midpoint, 3→4 vertices yields 4 vertices and `morph(a,b,0)` traces the same shape as `a`, open↔closed holds; `PathMeasure` on a 100×100 square: length 400, `pointAt(0.25)` = (100,0), `trim(0,0.5,0)` length 200.
- [ ] **Step 3:** Run, see them fail. Implement. Run, see them pass.
- [ ] **Step 4:** Commit `feat(model): value types, easing, keyframe sampling, path maths`.

### Task 2: Layers, document, property access

**Files:** `animatorfx-model/.../model/{Stroke,Trim,Mask,Effect,MotionPath,Transform,Geometry,Geometries,Layer,LayerBase,ShapeLayer,TextLayer,ImageLayer,GroupLayer,SlotLayer,Animation,Marker,Asset,Layers,LayerProperties,PropertyRef}.java` + tests.

**Consumes:** everything from Task 1.

**Produces:**

```java
public record Transform(Animatable<Vec2> anchor, Animatable<Vec2> position, Animatable<Vec2> scale,
                        Animatable<Double> rotation, Animatable<Vec2> skew) {
    static Transform IDENTITY;  static Transform at(double x, double y);
    double[] matrixAt(double time);  // {mxx, mxy, tx, myx, myy, ty}: translate(position) * rotate * skew * scale * translate(-anchor)
}
public record Stroke(Animatable<Paint> paint, Animatable<Double> width, Cap cap, Join join, double miterLimit,
                     List<Double> dashArray, Animatable<Double> dashOffset) { enum Cap {BUTT, ROUND, SQUARE} enum Join {MITER, ROUND, BEVEL} }
public record Trim(Animatable<Double> start, Animatable<Double> end, Animatable<Double> offset) {}
public record Mask(String layerId, Mode mode) { enum Mode {CLIP, ALPHA} }
public sealed interface Effect {
    record Blur(Animatable<Double> radius) implements Effect {}
    record DropShadow(Animatable<Rgba> color, Animatable<Double> radius, Animatable<Double> offsetX,
                      Animatable<Double> offsetY, Animatable<Double> spread) implements Effect {}
    record Glow(Animatable<Double> level) implements Effect {}
}
public record MotionPath(PathData path, Animatable<Double> progress, boolean orientToPath) {}
public sealed interface Geometry {
    record Rect(Animatable<Vec2> size, Animatable<Double> cornerRadius) implements Geometry {}   // spans (0,0)..size
    record Ellipse(Animatable<Vec2> size) implements Geometry {}                                 // spans (0,0)..size
    record Line(Animatable<Vec2> start, Animatable<Vec2> end) implements Geometry {}
    record Polygon(int sides, Animatable<Double> radius) implements Geometry {}                  // centred on (radius, radius)
    record Star(int points, Animatable<Double> outerRadius, Animatable<Double> innerRadius) implements Geometry {} // centred on (outer, outer)
    record PathGeometry(Animatable<PathData> path) implements Geometry {}
}
public final class Geometries { static PathData toPath(Geometry g, double time); }
public record LayerBase(String id, String name, boolean visible, boolean locked, double inPoint, double outPoint,
                        Transform transform, Animatable<Double> opacity, Mask mask /*nullable*/,
                        List<Effect> effects, MotionPath motionPath /*nullable*/) {
    static LayerBase create(String name, double duration);   // random UUID, visible, unlocked, 0..duration, identity, opacity 1
}
public sealed interface Layer permits ShapeLayer, TextLayer, ImageLayer, GroupLayer, SlotLayer {
    LayerBase base();  Layer withBase(LayerBase base);
    default String id() { return base().id(); }  default String name() { return base().name(); }
}
public record ShapeLayer(LayerBase base, Geometry geometry, Animatable<Paint> fill, Stroke stroke /*nullable*/, Trim trim /*nullable*/) implements Layer {}
public record TextLayer(LayerBase base, String text, String fontFamily, String fontAssetId /*nullable*/, Animatable<Double> fontSize,
                        Weight fontWeight, boolean italic, Align alignment, Animatable<Paint> fill, Stroke stroke /*nullable*/) implements Layer {
    enum Weight {NORMAL, BOLD}  enum Align {LEFT, CENTER, RIGHT} }
public record ImageLayer(LayerBase base, String assetId, double width, double height) implements Layer {}
public record GroupLayer(LayerBase base, List<Layer> children) implements Layer {}
public record SlotLayer(LayerBase base, String slotName, double width, double height, Transform restTransform) implements Layer {}
public record Marker(String name, double start, double end) {}
public record Asset(String id, Kind kind, String path, String name) { enum Kind {IMAGE, FONT} }
public record Animation(int formatVersion, String name, double width, double height, double frameRate, double duration,
                        Rgba background, List<Marker> markers, List<Asset> assets, List<Layer> layers) {
    static final int CURRENT_FORMAT = 1;
    static Animation create(String name, double width, double height, double duration);  // 60 fps, transparent background
    Optional<Marker> marker(String name);
    // plus withX(...) copy methods for every component
}
public final class Layers {
    static Optional<Layer> find(Animation a, String id);
    static List<Layer> flatten(Animation a);                         // depth-first, parents before children
    static Optional<GroupLayer> parentOf(Animation a, String id);
    static Animation replace(Animation a, String id, UnaryOperator<Layer> fn);
    static Animation remove(Animation a, String id);
    static Animation insert(Animation a, String parentIdOrNull, int index, Layer layer);
    static Animation move(Animation a, String id, String newParentIdOrNull, int index);
    static List<SlotLayer> slots(Animation a);
}
public record PropertyRef(String key, String label, ValueType type, Animatable<?> value) { enum ValueType {NUMBER, VEC2, COLOR, PAINT, PATH} }
public final class LayerProperties {
    static List<PropertyRef> list(Layer layer);       // only properties that exist on this layer
    static Animatable<?> get(Layer layer, String key);              // IllegalArgumentException on unknown key
    static Layer set(Layer layer, String key, Animatable<?> value);
}
```

Property keys: `transform.anchor`, `transform.position`, `transform.scale`, `transform.rotation`, `transform.skew`, `opacity`, `fill`, `stroke.paint`, `stroke.width`, `stroke.dashOffset`, `trim.start`, `trim.end`, `trim.offset`, `geometry.size`, `geometry.cornerRadius`, `geometry.start`, `geometry.end`, `geometry.radius`, `geometry.outerRadius`, `geometry.innerRadius`, `geometry.path`, `fontSize`, `motionPath.progress`, `effects[i].radius|color|offsetX|offsetY|spread|level`.

- [ ] **Step 1:** Failing tests: `Transform.matrixAt` for pure translation, 90° rotation about an anchor, and scale; `Geometries.toPath` bounds for each geometry (rect with radius has 8 vertices, ellipse 4 vertices with non-zero tangents, star `points*2` vertices); `Layers` find/replace/remove/insert/move in nested groups, including that untouched siblings keep identity (`assertSame`); `LayerProperties.list` contains exactly the expected keys for a rect shape layer with stroke, trim and one blur; `get`/`set` round-trip for every key above; unknown key throws.
- [ ] **Step 2:** Fail → implement → pass.
- [ ] **Step 3:** Commit `feat(model): layers, document and property access`.

### Task 3: JSON and `.afx` I/O

**Files:** `animatorfx-model/.../model/io/{Json,JsonWriter,AnimationJson,AnimationIO,AnimationFile,AnimationFormatException}.java` + tests + `src/test/resources` fixtures.

**Consumes:** Tasks 1–2.

**Produces:**

```java
public final class Json {                    // values: Map<String,Object> (LinkedHashMap), List<Object>, String, Double, Boolean, null
    static Object parse(String text) throws AnimationFormatException;
}
public final class JsonWriter { static String write(Object value); }   // pretty, 2-space indent, insertion order, integers without ".0"
public class AnimationFormatException extends IOException { String jsonPath(); }
public record AnimationFile(Animation animation, Map<String, byte[]> assets) {}   // key = Asset.id
public final class AnimationJson {
    static Map<String, Object> toJson(Animation a);
    static Animation fromJson(Object json) throws AnimationFormatException;
}
public final class AnimationIO {
    static AnimationFile read(Path p) throws IOException;
    static AnimationFile read(URL u) throws IOException;
    static AnimationFile read(InputStream in) throws IOException;     // sniffs zip magic "PK", else bare JSON
    static void write(AnimationFile file, Path p) throws IOException; // atomic: temp file + move
    static void write(AnimationFile file, OutputStream out) throws IOException;
}
```

Format rules are in spec §4.5. Additional: static animatable = plain value, animated = `{"keyframes":[{"id","t","v","e"}]}`; easing = `"linear"`, `"hold"` or `[x1,y1,x2,y2]`; `Vec2` = `[x,y]`; colour = hex string; paint = `{"type":"none|solid|linear|radial",...}`; path = `{"contours":[{"closed":bool,"v":[[px,py,inx,iny,outx,outy],...]}]}`; layer has `"type"`. Omit null/default-valued optional members on write. Reject `formatVersion` > `CURRENT_FORMAT`; a `migrate(Map, int fromVersion)` hook exists and is the identity for version 1.

- [ ] **Step 1:** Failing tests: `Json` parse of nested objects, escapes (`\n`, `\"`, `\u00e9`), numbers (`-1.5e3`), and error with position on malformed input; writer/parser round-trip; a "kitchen sink" `Animation` (every layer type, every geometry, gradients, stroke, trim, mask, three effects, motion path, markers, animated path) survives `fromJson(toJson(a))` with `equals`; `.afx` zip round-trip including asset bytes; bare-JSON read; missing asset entry → `AnimationFormatException`; bad keyframe time → exception whose `jsonPath()` is `layers[0].transform.position.keyframes[1].t`; newer `formatVersion` rejected; unknown fields ignored; a minimal document `{"formatVersion":1,"width":100,"height":100,"duration":1,"layers":[]}` loads with defaults.
- [ ] **Step 2:** Fail → implement → pass.
- [ ] **Step 3:** Commit `feat(model): JSON and .afx reader/writer`.

---

## Milestone 2 — Player

### Task 4: Evaluator and renderer

**Files:** `animatorfx-player/.../player/{Evaluator,Frame,LayerState}.java`, `.../player/render/{Renderer,PathConverter,PaintConverter}.java` + tests. Player test dependencies: JUnit, TestFX, `org.testfx:openjfx-monocle:21.0.2`; surefire runs with `-Dtestfx.headless=true -Dglass.platform=Monocle -Dmonocle.platform=Headless -Dprism.order=sw -Djava.awt.headless=true` and the needed `--add-exports`/`--add-opens` for Monocle. If Monocle cannot start on this JDK, switch the player's *test-scope* JavaFX to 26.0.2 and use `-Dglass.platform=Headless`; record which was used in the commit message.

**Consumes:** model.

**Produces:**

```java
public final class Evaluator { static Frame sample(Animation animation, double time); }
public record Frame(double time, Map<String, LayerState> layers) { LayerState get(String layerId); }
public record LayerState(boolean visible, double[] matrix, double opacity, PathData path /*shape only, already trimmed*/,
                         Paint fill, Paint strokePaint, double strokeWidth, double dashOffset, double fontSize,
                         List<EffectState> effects) { record EffectState(Effect source, double[] values, Rgba color) {} }
public final class Renderer {
    Renderer();
    Group getRoot();                                   // document-space node tree, clipped to width x height, background rect first
    void setDocument(AnimationFile file);              // rebuilds ONLY if structure changed (layer ids, types, order, masks, assets)
    void apply(Frame frame);                           // writes only changed values
    Node getLayerNode(String layerId);                 // null if unknown
    Group getSlotContainer(String slotName);           // where embedded slot nodes go
}
```

Rendering table: spec §5.2. Motion path: position comes from `PathMeasure.pointAt(progress)`; with `orientToPath` add `angleAt`. Visibility: `base.visible && inPoint <= t < outPoint` (and `t == duration` counts as inside when `outPoint == duration`). Trim: evaluator applies `PathMeasure.trim` to the stroke path; when a trimmed shape also has a fill, render fill and stroke as two `Path` nodes inside a `Group`. Fonts from font assets are loaded with `Font.loadFont` once per document. Images are created from asset bytes.

- [ ] **Step 1:** Failing evaluator tests (no toolkit): position keyframes sampled at three times; in/out-point visibility; group transform is not baked into children (child matrix is local); motion path overrides position and adds tangent rotation; trim halves the path length; effect values sampled.
- [ ] **Step 2:** Failing renderer tests (headless toolkit): node type per layer type; z-order follows layer order; `apply` moves a node (`getLayerNode(id).getBoundsInParent()`); clip mask sets `getClip()`; masked layer's mask node is not in the visible tree; blur + drop shadow produce a chained effect; `setDocument` with only a changed keyframe value keeps the same node instances, a new layer rebuilds; slot container exists.
- [ ] **Step 3:** Fail → implement → pass. Commit `feat(player): evaluator and scene-graph renderer`.

### Task 5: `AnimationPlayer`, `AnimationView`, slots

**Files:** `animatorfx-player/.../player/{AnimationPlayer,AnimationView,AnimationViewSkin,Fit,SlotMode}.java`, `.../player/internal/{Clock,SlotBinding}.java`, `animation-view.css` + tests.

**Consumes:** Task 4.

**Produces:** the API in spec §5.3–§5.5, plus:

```java
public class AnimationPlayer {
    public static final int INDEFINITE = -1;
    AnimationPlayer();  AnimationPlayer(AnimationFile file);
    ObjectProperty<AnimationFile> animationProperty();   DoubleProperty currentTimeProperty();
    DoubleProperty rateProperty();  IntegerProperty cycleCountProperty();  BooleanProperty autoReverseProperty();
    ReadOnlyObjectProperty<Status> statusProperty();     ReadOnlyStringProperty activeMarkerProperty();
    ObjectProperty<EventHandler<ActionEvent>> onFinishedProperty();
    ObjectProperty<Consumer<Marker>> onMarkerReachedProperty();
    ReadOnlyObjectProperty<Frame> frameProperty();       // last sampled frame; updates on time or animation change
    void play(); void pause(); void stop(); void seek(double seconds); void playMarker(String name);
    void bindInPlace(String slotName, Node node); void unbind(String slotName); Set<String> getBoundSlots();
    enum Status {STOPPED, PLAYING, PAUSED}
}
public class AnimationView extends Control {
    AnimationView(); AnimationView(AnimationFile file);
    AnimationPlayer getPlayer();
    // delegates: animation, currentTime, rate, cycleCount, autoReverse, status, play/pause/stop/seek/playMarker
    ObjectProperty<Fit> fitProperty();  BooleanProperty autoPlayProperty();
    void bind(String slotName, Node node);            // embedded
    void autoBind(Node root);  void autoBind(Node root, Map<String, Object> fxmlNamespace);   // in place
    void unbind(String slotName);
    Node getLayerNode(String layerId);                // for tooling: the rendered node of a layer
    Transform getContentTransform();                  // document -> view local coordinates (fit scale + centring)
}
public enum Fit {CONTAIN, COVER, NONE}
```

The clock is injectable for tests (`internal.Clock` with a package-private constructor on the player taking a manual clock). `stop()` seeks to 0 (or to the marker start during `playMarker`). At the natural end the player stays on the last frame and fires `onFinished`. Negative `rate` plays backwards. Preferred size = document size.

- [ ] **Step 1:** Failing tests with a manual clock: time advances by `rate × dt`; stops at duration and fires `onFinished` once; `cycleCount = 2` wraps once; `autoReverse` returns to 0; `INDEFINITE` never finishes; `playMarker` plays exactly the marker range; `onMarkerReached` fires when crossing a marker start; `seek` clamps; pause/resume keeps time.
- [ ] **Step 2:** Failing slot tests: in-place binding writes `translateX/Y`, `scaleX/Y`, `rotate`, `opacity` relative to `restTransform` and multiplies the node's original opacity; `unbind` restores every changed property; embedded binding parents the node into the slot container and resizes a `Region` to the slot size; unknown slot → `IllegalArgumentException`; rebinding restores the first node; `autoBind` matches by node id and by namespace map; an unbound slot logs once and playback continues.
- [ ] **Step 3:** Failing view tests: `CONTAIN`/`COVER`/`NONE` content transform for a 200×100 document in a 400×400 view; pref size; `-fx-fit` styleable; all `*Property` methods are `final` (reflection test over `AnimationView` and `AnimationPlayer`).
- [ ] **Step 4:** Fail → implement → pass. Commit `feat(player): AnimationPlayer, AnimationView and slot binding`.

---

## Milestone 3 — Editor shell and session

### Task 6: Editor core, shell, theming, session state

**Files:** `animatorfx-editor/pom.xml` (JavaFX 25.0.5 declared directly, AtlantaFX, Ikonli, javafx-maven-plugin with main class `com.dlsc.animatorfx.editor.AnimatorApp`), `.../editor/{AnimatorApp,Launcher}.java`, `doc/{EditorDocument,Command,Commands,Selection}.java`, `session/{SessionState,SessionStore,RecoveryStore,AppDirs}.java`, `ui/{MainWindow,ToolBarView,ToolRail,StatusBar,Theme}.java`, `editor.css` + tests.

**Consumes:** model, player.

**Produces:**

```java
public interface Command { String name(); Animation apply(Animation before); }
public final class EditorDocument {
    static EditorDocument untitled();  static EditorDocument open(Path file) throws IOException;
    String getId();                                    // stable across restarts (stored in session)
    ReadOnlyObjectProperty<Animation> animationProperty();
    ReadOnlyObjectProperty<AnimationFile> fileContentProperty();   // animation + asset bytes, for AnimationView
    ObjectProperty<Path> pathProperty();  ReadOnlyBooleanProperty dirtyProperty();  ReadOnlyStringProperty titleProperty();
    Selection getSelection();
    DoubleProperty playheadProperty();  BooleanProperty autoKeyProperty();
    void execute(Command c);                           // pushes undo, clears redo, marks dirty
    void executeCoalesced(Object gestureKey, Command c);  // same key as previous call => replaces the top undo entry
    void endGesture();
    void undo(); void redo(); ReadOnlyBooleanProperty canUndoProperty(); ReadOnlyBooleanProperty canRedoProperty();
    void addAsset(Asset asset, byte[] bytes);
    void save() throws IOException; void saveAs(Path p) throws IOException;
}
public final class Selection { ObservableList<String> layerIds(); ObservableSet<KeyframeRef> keyframes();
    record KeyframeRef(String layerId, String propertyKey, String keyframeId) {} }
public final class Commands {    // each returns a Command; all pure
    static Command addLayer(String parentIdOrNull, int index, Layer layer);
    static Command removeLayers(Collection<String> ids);
    static Command updateLayer(String name, String id, UnaryOperator<Layer> fn);
    static Command setProperty(String id, String key, Animatable<?> value);
    static Command setValueAt(String id, String key, double time, Object value, boolean autoKey); // spec §7.2 auto-key rule
    static Command addKeyframe(String id, String key, double time);    // value = current sampled value
    static Command removeKeyframes(Collection<Selection.KeyframeRef> refs);
    static Command moveKeyframes(Collection<Selection.KeyframeRef> refs, double deltaSeconds);
    static Command setEasing(Collection<Selection.KeyframeRef> refs, Easing easing);
    static Command moveLayer(String id, String newParentIdOrNull, int index);
    static Command setDocument(String name, UnaryOperator<Animation> fn);
}
public record SessionState(...)          // every item of spec §7.4
public final class SessionStore { SessionStore(Path dir); SessionState load(); void save(SessionState s); }
public final class RecoveryStore { RecoveryStore(Path dir); void store(String docId, AnimationFile f, Path originalPathOrNull);
    Optional<Recovered> load(String docId); void discard(String docId); List<Recovered> all(); }
public final class AppDirs { static Path configDir(); }   // spec §7.4; overridable with -Danimatorfx.configDir=...
```

UI: `MainWindow` builds the motion-tool layout from spec §7.1 with placeholder panes for canvas, side panel and timeline (filled in Tasks 7–9), nested `SplitPane`s, a `TabPane`-like document strip, menu bar (File, Edit, View, Help) with accelerators, toolbar, tool rail, status bar. `Theme` applies AtlantaFX `PrimerDark`/`PrimerLight` plus `editor.css`, with modes `DARK`, `LIGHT`, `SYSTEM` (`Platform.getPreferences().colorSchemeProperty()`). `editor.css` defines the palette as looked-up colours on `.root` (`-afx-bg`, `-afx-panel`, `-afx-panel-alt`, `-afx-line`, `-afx-fg`, `-afx-muted`, `-afx-accent`, `-afx-key` amber, `-afx-playhead` red, `-afx-canvas`) with a `.root.light` override; values from the approved mockup: dark `#0d1117/#151b23/#1c232d/#2a3340/#d7dee8/#7d8a9b/#4c9aff/#ffb454/#ff5c5c/#090c10`, light `#f3f5f8/#ffffff/#eef1f5/#d5dbe3/#1d2733/#6b7888/#1f6feb/#d9730d/#e5484d/#e4e8ee`. Numeric text uses a monospaced font; base font size 12px; dense paddings.

Session behaviour: exactly spec §7.4 (debounced 500 ms atomic save, 5 s recovery autosave while dirty, restore on start, recovery notice, missing-file notice, off-screen fallback, corrupt file renamed to `session.json.corrupt-<timestamp>`).

- [ ] **Step 1:** Failing pure tests: every `Commands` factory against small documents; `setValueAt` in all four cases (static/animated × autoKey on/off); `EditorDocument` undo/redo, dirty flag cleared by save and restored by undo past the save point, coalescing collapses 10 drag updates into one undo step.
- [ ] **Step 2:** Failing session tests against a temp dir: `SessionState` JSON round-trip; corrupt file → defaults + renamed file; missing file → defaults; `RecoveryStore` store/load/discard incl. untitled documents; atomic write leaves no temp file.
- [ ] **Step 3:** Fail → implement → pass.
- [ ] **Step 4:** Build the shell and theming. Verify by launching (`mvn -q -pl animatorfx-editor -am install -DskipTests && mvn -q -pl animatorfx-editor javafx:run`) and by a headless TestFX smoke test: the window shows, theme switch toggles the `light` style class, closing and restarting with the same config dir restores window size, divider positions, theme and open documents; a dirty untitled document is recovered after restart.
- [ ] **Step 5:** Commit `feat(editor): core document model, shell, theming and session state`.

---

## Milestone 4 — Authoring

### Task 7: Canvas and shape tools

**Files:** `canvas/{CanvasView,Overlay,Snapper}.java`, `canvas/tools/{Tool,SelectTool,ShapeTool,TextTool,ImageTool,SlotTool}.java`, wire into `MainWindow`/`ToolRail` + tests.

**Consumes:** `EditorDocument`, `Commands`, `AnimationView` (`getLayerNode`, `getContentTransform`).

**Behaviour:** `CanvasView` shows the artboard (an `AnimationView` with `Fit.NONE`, seeked to the playhead, never auto-playing) on the grid background with zoom (25 %–3200 %, Cmd/Ctrl+scroll and pinch, Cmd+0 fit, Cmd+1 100 %) and pan (space-drag, scroll). `Overlay` draws above it in view pixels: selection bounds with 8 scale handles, rotate handle, anchor point, hover outline, slot rectangles (dashed amber + name), snap guides. Tools: select (click, shift-click, marquee; drag = move; handles = scale/rotate; arrow keys nudge 1 px, shift 10 px; Delete removes; Cmd+D duplicates; Cmd+G groups, Cmd+Shift+G ungroups; Cmd+]/[ reorder), rectangle/ellipse/line/polygon/star (drag to create, shift constrains), text (click to place, inline edit), image (file chooser → asset + layer), slot (drag a rectangle, prompt for a unique name). All gestures go through `executeCoalesced` and write via `Commands.setValueAt` so auto-key applies. Hit testing uses the rendered layer nodes, top-most first, skipping locked and hidden layers. Playback (space bar / toolbar) drives the playhead through an `AnimationPlayer`.

- [ ] **Step 1:** Failing tests: pure geometry helpers (handle drag → new scale/position about the opposite corner, rotation from pointer angle, snapping); headless TestFX: drag with the rectangle tool creates one `ShapeLayer` of the dragged size and selects it; dragging it moves `transform.position`; one undo reverts the whole drag; Delete removes it; with auto-key on at t = 1 s a move creates a keyframe.
- [ ] **Step 2:** Fail → implement → pass → launch and check visually. Commit `feat(editor): canvas, selection and shape tools`.

### Task 8: Timeline, inspector, side panel

**Files:** `timeline/{TimelineView,LayerTreeColumn,TrackPane,TimeRuler}.java`, `inspector/{InspectorView,AssetsView,DocumentView,EasingEditor}.java`, `inspector/fields/{NumberField,Vec2Field,ColorField,PaintField,KeyframeToggle}.java` + tests.

**Consumes:** `EditorDocument`, `Commands`, `LayerProperties`, `Easing.presets()`.

**Behaviour:** as spec §7.1. Timeline: layer rows (icon by type, name — double-click renames, visibility, lock, expand) with animated properties as child rows (toggle to show all properties); drag rows to reorder/re-parent; track side shows the ruler (seconds + frames, snapping to frames), marker ranges (create/rename/resize/delete), in/out bars (drag ends and body), keyframe diamonds (click, shift-click, marquee select; drag moves; Delete removes; copy/paste at playhead), playhead (drag ruler to scrub); horizontal zoom and scroll. Inspector: sections per layer type generated from `LayerProperties.list`, each row with a typed field and a `KeyframeToggle` diamond (filled when a keyframe exists at the playhead; click adds/removes); `NumberField` supports drag-scrubbing and arrow keys; `PaintField` edits none/solid/linear/radial with stops; non-animatable members (text, font, stroke caps, dash array, polygon sides, names, in/out) as plain fields; `EasingEditor` shows the curve of the selected keyframes with two draggable handles, a preset combo, and Linear/Hold buttons. Assets tab lists images and fonts with add/remove. Document tab edits name, size, duration, frame rate, background, markers, and shows a "Copy Java snippet" button (spec §7.3). Multi-selection edits apply to all selected layers.

- [ ] **Step 1:** Failing tests: time↔pixel mapping and frame snapping; easing-editor handle → `CubicBezier` values clamped to x ∈ [0,1]; snippet generator output for a document with two slots; headless TestFX: editing position X in the inspector updates the document; toggling the diamond adds then removes a keyframe; dragging a keyframe changes its time; scrubbing the ruler moves the playhead and the canvas.
- [ ] **Step 2:** Fail → implement → pass → launch and check visually in dark and light. Commit `feat(editor): timeline, inspector, assets and document panels`.

### Task 9: Pen tool and advanced-feature editing

**Files:** `canvas/tools/{PenTool,PathEditTool,MotionPathTool}.java`, `inspector/{EffectsSection,MaskSection,TrimSection,MotionPathSection}.java` + tests.

**Behaviour:** pen tool (click = corner, drag = smooth vertex, click first vertex closes, Enter/Esc finishes); path edit on double-click of a path layer (drag vertices and tangent handles, Alt-drag breaks tangent symmetry, click segment inserts a vertex, Delete removes); "Convert to path" for primitive shapes; path keyframes via auto-key so shapes morph; motion-path tool draws/edits a layer's motion path and the inspector animates `progress` with an orient toggle; effects section adds/removes/reorders blur, drop shadow and glow; mask section picks a sibling layer and mode; trim section enables trim with start/end/offset.

- [ ] **Step 1:** Failing tests: pure path-edit operations (insert vertex on segment keeps the shape, delete vertex, symmetric tangent update); headless TestFX: three pen clicks + Enter create a path layer with three vertices; adding a blur effect through the inspector adds `Effect.Blur` and the property row.
- [ ] **Step 2:** Fail → implement → pass → launch and check. Commit `feat(editor): pen tool, path editing, masks, effects, trim and motion paths`.

---

## Milestone 6 — Importers

### Task 10: SVG, Lottie and FXML import

**Files:** `animatorfx-import/.../importer/{ImportResult,SvgImporter,SvgPathParser,LottieImporter,FxmlImporter}.java` + fixtures and tests; editor menu entries File ▸ Import and drag-and-drop.

**Consumes:** model (`Json` for Lottie). The module can be built in parallel with Milestones 2–4; the editor wiring is the last step.

**Produces:**

```java
public record ImportResult(AnimationFile file, List<String> warnings) {}
public final class SvgImporter { static ImportResult read(InputStream in, String name) throws IOException; }
public final class SvgPathParser { static PathData parse(String d); }   // all commands incl. relative, H/V, S/T, arcs (converted to cubics)
public final class LottieImporter { static ImportResult read(InputStream in, String name) throws IOException; }
public final class FxmlImporter {
    record SlotPreview(String slotName, byte[] pngSnapshot) {}
    record Result(AnimationFile file, List<SlotPreview> previews, List<String> warnings) {}
    static Result read(URL fxml) throws IOException;      // must run on the FX thread
}
```

Supported and unsupported features: spec §6. Lottie specifics: time = frame / `fr`; `ip`/`op` → in/out points; Lottie Bezier `i`/`o` easing handles → `CubicBezier(o.x, o.y, i.x, i.y)`; hold keyframes (`h: 1`) → `HOLD`; scale and opacity are percent in Lottie; `parent` → nest under a group carrying the parent's transform; precomps flattened into groups; multi-dimensional easing uses the first component with a warning when components differ.

- [ ] **Step 1:** Failing tests: `SvgPathParser` on `M0 0 L10 0 10 10z`, relative commands, a cubic, a quadratic, and an arc (end point exact, bounds within tolerance); SVG fixture with nested groups + transforms + gradient + clipPath → expected layer tree and paints, filter element → one warning; Lottie fixture (shape layer with animated position, rotation, trim path, fill; one precomp; one expression) → expected keyframe times/values/easing, marker, and one warning; FXML fixture with three `fx:id` nodes → three slots with laid-out sizes, FXML requiring a missing controller class → `IOException`.
- [ ] **Step 2:** Fail → implement → pass. Wire into the editor (import opens a new untitled document; SVG can also be imported into the current document as a group; FXML previews show in slot rectangles; warnings appear in a dismissible notice). Commit `feat(import): SVG, Lottie and FXML importers`.

---

## Milestone 7 — Packaging and samples

### Task 11: Samples, packaging, README

**Files:** `samples/animatorfx-sample-app/` (module depending on `animatorfx-player` only, proving the runtime works without the tooling: one window showing an `AnimationView` with an embedded slot and an in-place bound button), `samples/animations/*.afx` (at least: a loader spinner using trim path, a success check with path morph and markers, a card with a slot), `animatorfx-editor` `jlink`/`jpackage` profile `-Ppackage`, `README.md`.

- [ ] **Step 1:** Generate the sample `.afx` files with a small test-scope generator using the model API so they stay valid; add a test that every sample loads and renders a frame.
- [ ] **Step 2:** Sample app + test that its module graph contains no editor, AtlantaFX or Ikonli module.
- [ ] **Step 3:** `mvn -Ppackage -pl animatorfx-editor -am package` produces an app image under `animatorfx-editor/target/dist`; launch it once.
- [ ] **Step 4:** README: what it is, module table, embedding snippet, slot binding, running the editor, file format summary, limitations. Commit `feat: samples, packaging and README`.

---

## Final verification

- [ ] `MAVEN_OPTS="-Duser.language=en -Duser.country=US" mvn clean verify` from the root; sum the `Tests run` lines; zero failures and errors.
- [ ] Launch the editor, create an animation exercising every tool, save, restart (state restored), load the file in the sample app.
- [ ] Walk the spec section by section and confirm each requirement against the running product; list anything missing.
