# AnimatorFX — Design

Date: 2026-11-18
Status: approved in brainstorming, awaiting spec review

## 1. Goal

AnimatorFX lets developers author animations in a desktop editor and play them
back inside JavaFX applications.

- A polished, technical editor with dark and light mode and preserved session state.
- Animations stored in files.
- A custom control that applications embed to play animations.
- Applications need only a small runtime library, never the tooling.

An animation can contain two kinds of content, freely mixed in one file:

1. **Vector content** — shapes, paths, text and images, self-contained in the file.
2. **Slots** — named placeholders that the host application binds to its own
   scene-graph nodes, which the animation then drives.

## 2. Decisions taken

| Topic | Decision |
|---|---|
| Animation model | Vector keyframe animation plus host-node animation, in one spec |
| Node binding | Named slots bound in code or by node id; FXML import in the editor creates slots |
| Runtime baseline | Java 21, JavaFX 21, no third-party dependencies, JPMS modules |
| Editor baseline | Java 26 (as in the existing pom), JavaFX 25, AtlantaFX, Ikonli |
| Runtime engine | Scene-graph renderer with an own evaluator, one `AnimationTimer` |
| File format | `.afx` zip container: `animation.json` plus `assets/` |
| Editor layout | Motion-tool layout: layer tree is the left column of the timeline |
| Session state | Window, layout, theme, open documents, per-document view, recent files, unsaved-change recovery |
| Undo history | Not persisted across restarts |

## 3. Modules

Multi-module Maven build. Parent `com.dlsc.animatorfx:animatorfx-parent`
replaces the current single-module pom.

| Artifact | JPMS module | Purpose | Depends on |
|---|---|---|---|
| `animatorfx-model` | `com.dlsc.animatorfx.model` | Immutable document model, interpolation, easing, `.afx` reader and writer, JSON parser | `java.base` |
| `animatorfx-player` | `com.dlsc.animatorfx.player` | Evaluator, scene-graph renderer, `AnimationPlayer`, `AnimationView`, slot binding | model, `javafx.graphics`, `javafx.controls` |
| `animatorfx-import` | `com.dlsc.animatorfx.importer` | SVG, Lottie and FXML importers producing model documents | model, `java.xml`, `javafx.fxml` |
| `animatorfx-editor` | `com.dlsc.animatorfx.editor` | The authoring tool | model, player, import, AtlantaFX, Ikonli |

Rules:

- `model` and `player` compile with `--release 21` and have no third-party
  runtime dependencies. A build check (maven-enforcer `bannedDependencies`)
  enforces this.
- `model` has no JavaFX dependency, so documents can be read, written and
  tested headless.
- The editor previews through the `player` renderer. There is no second
  rendering implementation.
- Applications depend on `animatorfx-player` only (it brings `model`
  transitively).

## 4. Document model (`animatorfx-model`)

All model types are immutable Java records. Edits create new instances
(structural sharing of unchanged subtrees).

### 4.1 Units and conventions

- Time is in seconds, as `double`. The frame rate is a document property used
  for timeline snapping and display only; playback is time-based, not
  frame-based.
- Coordinates are in document pixels, origin top-left, y down.
- Angles are in degrees, clockwise positive (JavaFX convention).
- Colours are RGBA with components in `0..1`, written as `#RRGGBBAA` in JSON.
- Opacity and trim values are `0..1`.
- Every layer, keyframe and asset has a stable string id (UUID) used for
  selection, session state and diffing.

### 4.2 Document

`Animation`: `formatVersion`, `name`, `width`, `height`, `frameRate`,
`duration`, `background` (colour, may be transparent), `markers`, `assets`,
`layers` (ordered bottom to top).

`Marker`: `name`, `start`, `end`. Names are unique per document.

`Asset`: `id`, `kind` (`IMAGE` or `FONT`), `path` (entry inside the zip),
`name`.

### 4.3 Layers

Common to all layers: `id`, `name`, `visible`, `locked`, `inPoint`, `outPoint`
(the layer renders only within this time range), `transform`, `opacity`,
`mask`, `effects`, `motionPath`.

`Transform`: `anchor`, `position`, `scale`, `rotation`, `skew` — each an
animatable property.

| Layer type | Additional content |
|---|---|
| `ShapeLayer` | `geometry` (rectangle with corner radius, ellipse, line, polygon, star, or Bezier path), `fill`, `stroke`, `trim` |
| `TextLayer` | `text`, `fontFamily`, `fontAsset` (optional), `fontSize`, `fontWeight`, `italic`, `alignment`, `fill`, `stroke` |
| `ImageLayer` | `asset`, `width`, `height` |
| `GroupLayer` | `children` (ordered layers); its transform, opacity, mask and effects apply to all children |
| `SlotLayer` | `slotName` (unique per document), `width`, `height` (reference size for preview and embedded layout), `restTransform` |

Parenting is expressed by nesting inside a `GroupLayer`. There is no separate
parent-pointer mechanism.

The `alignment` of a `TextLayer` is relative to the layer's local origin: the
text starts at x = 0 (`LEFT`), is centred on x = 0 (`CENTER`) or ends at x = 0
(`RIGHT`); the top of the text is at y = 0 in all three cases.

- `Fill`: none, solid colour, linear gradient, or radial gradient. Colours,
  gradient stops and gradient geometry are animatable.
- `Stroke`: paint (as fill), `width`, `lineCap`, `lineJoin`, `miterLimit`,
  `dashArray`, `dashOffset`. Paint, width and dash offset are animatable.
- `Trim`: `start`, `end`, `offset`, all animatable. Applies to the stroke of a
  shape layer.
- `Mask`: a reference to another layer in the same parent, with mode `CLIP`
  (geometry only) or `ALPHA` (the mask layer's alpha). A layer used as a mask
  is not rendered itself.
- `Effect`: `GaussianBlur` (radius), `DropShadow` (colour, radius, offsetX,
  offsetY, spread), `Glow` (level). All parameters are animatable. Effects are
  applied in list order.
- `MotionPath`: a Bezier path, an animatable `progress` in `0..1`, and
  `orientToPath`. When present it replaces `transform.position`; with
  `orientToPath` the path tangent angle is added to `transform.rotation`.

### 4.4 Animatable properties

`Animatable<T>` is either a static value or a non-empty keyframe list sorted by
time.

`Keyframe<T>`: `id`, `time`, `value`, `easing` (how to travel from this
keyframe to the next).

`Easing`: `LINEAR`, `HOLD`, or `CubicBezier(x1, y1, x2, y2)`. Named presets
(ease-in, ease-out, ease-in-out, and the standard sine/quad/cubic/quart/expo/back
families) are editor conveniences that resolve to cubic-bezier values; the file
stores only the three forms above.

Sampling at time `t`:

- Before the first keyframe: the first value. After the last: the last value.
- Between keyframes `a` and `b`: `interpolate(a.value, b.value, a.easing(u))`
  with `u = (t - a.time) / (b.time - a.time)`.

Interpolation per value type: numbers and points linearly; colours linearly in
RGBA; gradients stop by stop when stop counts match, otherwise hold; text and
other discrete values hold.

**Path morphing.** Path values interpolate vertex by vertex (anchor and both
control points). When the two paths differ in vertex count, the path with fewer
vertices is subdivided on its longest segments until the counts match. When one
path is closed and the other open, the keyframe behaves as `HOLD`.

### 4.5 File format

`.afx` is a zip archive:

```
animation.json     the document
assets/<id>.<ext>  images (PNG, JPEG) and fonts (TTF, OTF)
```

- `animation.json` is UTF-8, pretty-printed with stable key order, so files
  diff cleanly once unzipped.
- `formatVersion` starts at `1`. The reader migrates older versions forward on
  load and rejects files with a newer version than it supports.
- The reader also accepts a bare `animation.json` file (no zip) when the
  document references no assets. The editor always writes zips.
- Layers are polymorphic through a `type` field (`shape`, `text`, `image`,
  `group`, `slot`).
- An animatable is written as a plain value when static and as
  `{"keyframes": [...]}` when animated.

The JSON parser and writer are part of `model` (small, hand-written, no
dependency).

Errors: `AnimationFormatException` (checked, extends `IOException`) carries a
message and the JSON path of the offending element, for example
`layers[2].transform.position.keyframes[1].time`. Missing optional fields take
defaults. Unknown fields are ignored. A referenced asset that is missing from
the archive is an error.

Public entry points:

```java
AnimationFile file = AnimationIO.read(Path | URL | InputStream);   // the document plus its asset bytes
AnimationIO.write(file, Path | OutputStream);
```

## 5. Runtime (`animatorfx-player`)

### 5.1 Evaluator

`Evaluator.sample(Animation, double time)` is a pure function that returns the
resolved value of every animatable property at that time. It has no JavaFX
dependency in its logic and is unit-tested by sampling at fixed times.

### 5.2 Renderer

The renderer builds a JavaFX node tree once per document and afterwards only
updates values:

| Model | JavaFX |
|---|---|
| `GroupLayer` | `Group` |
| `ShapeLayer` | a `Group` of `Path`s: the outline (all geometry is converted to path elements) and, while trimmed, the stroke |
| `TextLayer` | `Text` |
| `ImageLayer` | `ImageView` |
| `SlotLayer` | container `Group` for an embedded node, or nothing in in-place mode |
| Transform | one `Affine` per layer |
| Mask `CLIP` | `Node.setClip` |
| Mask `ALPHA` | `Node.setClip` with the rendered mask layer, which JavaFX treats as an alpha mask |
| Effects | chained `javafx.scene.effect` instances |
| Trim | the trimmed stroke path, computed with `PathMeasure` |

Each frame, only values that changed since the previous frame are written to
nodes. Layers outside their `inPoint..outPoint` range are set invisible.

### 5.3 `AnimationPlayer`

A non-visual controller. It owns the clock and the evaluator, and it is what
drives in-place slot bindings.

- Properties: `animation`, `currentTime`, `rate` (may be negative),
  `cycleCount` (or `INDEFINITE`), `autoReverse`, `status`
  (`STOPPED`, `PLAYING`, `PAUSED`), `activeMarker` (read-only).
- Methods: `play()`, `pause()`, `stop()`, `seek(double)`,
  `playMarker(String)` (plays the marker's range, respecting `cycleCount`),
  `bindInPlace(String slot, Node)`, `unbind(String slot)`.
- Events: `onFinished`, `onMarkerReached` (fired when playback crosses a
  marker's start).
- One `AnimationTimer` per player, running only while `status` is `PLAYING`.

### 5.4 `AnimationView`

A resizable `Control` (with skin) that owns an `AnimationPlayer` and renders
the vector content.

```java
AnimationView view = new AnimationView(AnimationIO.read(url));
view.setCycleCount(AnimationPlayer.INDEFINITE);
view.play();
view.playMarker("intro");
view.bind("card", myNode);     // embedded
view.autoBind(sceneRoot);      // in place, by node id
```

- Exposes the player's properties and methods by delegation, plus `getPlayer()`.
- `fit`: `CONTAIN` (default), `COVER`, `NONE`. Content is scaled uniformly and
  centred; scaling is vector-crisp.
- `autoPlay` property (default `false`).
- Playback pauses automatically while the view is not part of a showing window,
  and resumes afterwards, unless the player has in-place bindings.
- Style class `animation-view`. Styleable properties `-fx-fit` and
  `-fx-auto-play`.
- All observable property accessors are `final`; property Javadoc lives on the
  property accessor method only.

### 5.5 Slots

A slot is bound in one of two modes:

- **Embedded** — `view.bind(name, node)`. The node becomes a child of the
  slot's container inside the view. If resizable, it is resized to the slot's
  reference size. The full sampled transform, opacity, mask and effects of the
  slot layer apply.
- **In place** — `player.bindInPlace(name, node)` or
  `view.autoBind(root)`. The node stays where it is in the application. The
  player drives it relative to the slot's `restTransform`:
  position difference → `translateX/Y`; scale ratio → `scaleX/Y`; rotation
  difference → `rotate`; opacity → multiplied with the node's original opacity;
  effects → `effect`. Masks and skew are ignored in this mode.

`autoBind(root)` looks up `#<slotName>` under `root` for every unbound slot and
binds matches in place. Because FXMLLoader does not copy `fx:id` to the node id,
`autoBind(root, Map<String, Object> namespace)` additionally resolves slot names
against an FXML namespace (`FXMLLoader.getNamespace()`).

`unbind` restores every node property the binding changed to its value at bind
time and, in embedded mode, removes the node from the view.

Error handling: an unbound slot is skipped and logged once at `WARNING` through
`System.Logger`. Binding an unknown slot name throws
`IllegalArgumentException`. Binding a slot twice replaces the earlier binding
after restoring it.

## 6. Importers (`animatorfx-import`)

Each importer returns an `ImportResult`: the produced document (or layers) plus
a list of warnings naming every feature that was skipped. Importers never fail
on unsupported features; they fail only on unreadable input.

- **SVG** → static layers. Supported: `g`, `path`, `rect`, `circle`, `ellipse`,
  `line`, `polyline`, `polygon`, `text`, `image` (embedded data URIs),
  `transform`, fill and stroke attributes and inline `style`, linear and radial
  gradients, `clipPath`, `opacity`. Not supported (warning): filters, patterns,
  CSS stylesheets, `use` of external files, SMIL animation.
- **Lottie JSON** → animated document. Supported: shape, solid, image, text,
  null and precomposition layers (precompositions are flattened into groups);
  transforms; shape groups with rectangle, ellipse, star and path; fill,
  stroke, gradient fill and stroke; trim paths; masks; track mattes (alpha);
  markers; keyframe easing; layer parenting (converted to group nesting).
  Not supported (warning): expressions, time remapping, repeaters, merge paths,
  layer effects other than blur and drop shadow, 3D layers, audio.
- **FXML** → slot layers. The file is loaded with `FXMLLoader` without a
  controller; each node with an `fx:id` becomes a `SlotLayer` named after it,
  positioned and sized from the node's laid-out bounds. The editor shows a
  snapshot image of each node as the slot's preview. The FXML itself is not
  stored in the `.afx` file. FXML that cannot load without its controller or
  custom classes is reported as an import error.

## 7. Editor (`animatorfx-editor`)

### 7.1 Layout

Motion-tool layout, as validated in the mockups:

- **Toolbar** (top): document tabs, transport (to start, play/pause, to end),
  timecode, frame rate, loop toggle, zoom, theme switch.
- **Tool rail** (left): select, rectangle, ellipse, line, polygon, star, pen,
  text, image, slot.
- **Canvas** (centre): artboard on a grid background; zoom and pan; selection
  handles for move, scale, rotate and anchor; path editing with vertex and
  control-point handles; motion-path editing; snapping to artboard and layer
  bounds.
- **Side panel** (right), tabbed: *Inspector* (properties of the selection,
  each animatable row with a keyframe diamond toggle; easing curve editor for
  the selected keyframes), *Assets* (images and fonts), *Document* (size,
  duration, frame rate, background, markers).
- **Timeline** (bottom): left column is the layer tree (visibility, lock,
  expand to property rows); right is the time ruler with markers, layer bars
  for in/out range, keyframe diamonds, and the playhead. Supports keyframe
  drag, multi-select, copy/paste, zoom and scroll.
- **Status bar**: document size, layer count, selection count, autosave state.

Look: dense, monospaced numerics, one blue accent, amber for keyframes, markers
and slots, red playhead. Built on AtlantaFX Primer Dark and Primer Light with a
single custom stylesheet using looked-up colours so both modes share one set of
rules. Theme setting: dark, light, or follow system
(`Platform.getPreferences().colorSchemeProperty()`).

### 7.2 Editing model

- An `EditorDocument` holds the current immutable `Animation`, its asset bytes,
  the file path, a dirty flag, the selection, and the undo and redo stacks.
- Every change is a `Command` — a named function from `Animation` to
  `Animation`. Undo and redo swap whole documents. Continuous gestures (drags,
  slider scrubs) coalesce into one undo step.
- With auto-key enabled, changing an animatable property at the playhead adds
  or updates a keyframe; otherwise it changes the static value or, for an
  already animated property, the keyframe at the playhead.
- The canvas shows the document through an `AnimationView` in a non-playing
  state, seeked to the playhead. Overlays (handles, guides) are drawn in a
  separate layer above it and never become part of the document.
- Slots without a preview are drawn as dashed amber rectangles with their name.

### 7.3 File handling

New, open, save, save as, close, revert; import SVG, Lottie, FXML and images;
drag and drop of files onto the window. Closing a dirty document asks to save.
Exporting a Java code snippet for loading the animation and binding its slots
is available from the Document tab.

### 7.4 Session state

Stored as JSON under the per-user configuration directory
(`~/Library/Application Support/AnimatorFX` on macOS, `%APPDATA%\AnimatorFX` on
Windows, `$XDG_CONFIG_HOME/animatorfx` on Linux):

- `session.json` — window bounds, maximized state and screen; divider
  positions and collapsed panels; active side-panel tab; theme setting; open
  documents and the active one; recent files (maximum 20); per document: canvas
  zoom and pan, playhead time, selected layer and keyframe ids, timeline zoom
  and scroll, expanded layer ids, auto-key state.
- `recovery/` — one `.afx` per dirty document plus a small index.

Behaviour:

- Session state is written debounced (500 ms after the last change) and on
  exit, atomically (write to a temporary file, then move).
- Dirty documents are autosaved to `recovery/` every 5 seconds while dirty. A
  successful save or an explicit discard deletes the recovery copy.
- On start, open documents are reopened. A document that has a recovery copy is
  restored from it and marked dirty, with a non-modal notice offering to
  discard the recovered changes. Untitled documents are restored the same way.
- A file that no longer exists is dropped from the session with a notice.
- A window position that is off every current screen falls back to centred
  defaults.
- A corrupt or unreadable `session.json` is renamed aside and defaults are
  used; the editor never fails to start because of session state.
- Undo history is not persisted.

### 7.5 Packaging

The editor runs through `mvn javafx:run` during development and is packaged
with `jlink` and `jpackage` into a native application image per platform.

## 8. Testing

JUnit 5 throughout.

- **model** — file round-trip (write, read, equals); migration; error paths
  with JSON path assertions; interpolation per value type; easing; path
  morphing with unequal vertex counts.
- **player** — evaluator sampled at fixed times against expected values;
  renderer node-tree structure and written values per layer type; player clock
  behaviour (rate, cycles, auto-reverse, markers) with an injected clock; slot
  bind, unbind and restore in both modes.
- **import** — fixture SVG, Lottie and FXML files with assertions on the
  produced layers and on the warnings.
- **editor** — commands and undo/redo as pure tests; session state and recovery
  persistence against a temporary directory, including corrupt-file and
  missing-file cases; TestFX for the key flows (create shape, add keyframe,
  scrub, save, restart restores state).

JavaFX-dependent tests run headless (Monocle or the JavaFX headless platform).

## 9. Milestones

The implementation plan is organised in these milestones. Each ends in a
working, tested state.

1. **Model and format** — records, JSON, `.afx` reader and writer,
   interpolation, easing.
2. **Player** — evaluator, renderer for shapes, text, images and groups,
   `AnimationPlayer`, `AnimationView`, slots in both modes.
3. **Editor shell and session** — layout, theming, document tabs, file
   handling, session state, recovery.
4. **Authoring** — canvas tools, pen tool, inspector, timeline, keyframes,
   easing editor, undo/redo, markers.
5. **Advanced features** — path morphing, masks, effects, trim path, motion
   path, across model, player and editor.
6. **Importers** — SVG, Lottie, FXML.
7. **Packaging** — jlink and jpackage, sample application embedding
   `AnimationView`, README.

## 10. Out of scope

- Expressions or scripting inside animations.
- Audio and video layers.
- State machines and interactive (input-driven) animations.
- Export to Lottie, GIF or video.
- 3D transforms.
- Collaboration and cloud storage.
- Persisting undo history across restarts.
