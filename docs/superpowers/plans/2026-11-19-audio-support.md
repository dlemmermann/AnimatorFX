# AnimatorFX Audio Support Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add audio layers and sound cues to the `.afx` format, play them in sync in the runtime, and author them in the editor.

**Architecture:** The model gains an `AUDIO` asset kind, an `AudioLayer` and a `SoundCue` list (format version 2). In the player a pure scheduler decides what should sound when; a small internal output interface backed by JavaFX media (`MediaPlayer` per audio layer, `AudioClip` per cue) follows the animation clock. The editor adds import, waveform bars and a cue lane in the timeline, inspector sections, audible playback and scrubbing.

**Tech Stack:** Java 21 (`model`, `player`), Java 26 (`import`, `editor`), JavaFX 21.0.13 / 25.0.5 incl. `javafx-media`, `javax.sound.sampled` (editor, WAV/AIFF decoding), JUnit Jupiter, TestFX + Monocle headless.

**Spec:** `docs/superpowers/specs/2026-11-19-audio-support-design.md` (binding), extending `docs/superpowers/specs/2026-11-18-animatorfx-design.md`.

## Global Constraints

- `animatorfx-model` depends on `java.base` only. `animatorfx-player` has no third-party runtime dependency; `javafx.media` (org.openjfx) is allowed and now required.
- The sample app still depends only on `animatorfx-player`; its independence test and enforcer rule stay green.
- Model types are immutable records / sealed interfaces; lists stored with `List.copyOf`.
- Time in seconds; volume `0..1`; balance `-1..1`.
- JavaFX property accessor methods are `final`; property Javadoc on `xxxProperty()` only.
- All UI colours via the looked-up colours in `editor.css`; no hard-coded colours in Java.
- Every editor document change goes through a `Command`; one undo step per user action; continuous gestures coalesce.
- Every document the editor or an importer produces must survive `AnimationIO.write` → `AnimationIO.read`.
- Tests first (failing test, then implementation). JavaFX tests run headless. Tests must pass on a machine without an audio device.
- Never capture the whole screen; application snapshots via `Node.snapshot` only. Never touch the user's real config directory (`-Danimatorfx.configDir=<temp>`).
- Maven: `JAVA_HOME=$HOME/.sdkman/candidates/java/26.0.1-zulu MAVEN_OPTS="-Duser.language=en -Duser.country=US" mvn ...`; confirm results with the `Tests run:` lines.
- Git: branch `feature/animatorfx`; conventional commits with the trailer `Co-authored-by: Copilot <223556219+Copilot@users.noreply.github.com>`; never push; do not touch tracked `.idea/` files.

---

### Task 1: Model and file format

**Files:** `animatorfx-model/.../model/{Asset,AudioLayer,SoundCue,Animation,Layer,Layers,LayerProperties}.java`, `.../model/io/{AnimationJson,AnimationJsonReader,AnimationJsonWriter,Defaults,AnimationIO}.java` + tests; minimal compile fixes in `animatorfx-player`, `animatorfx-import`, `animatorfx-editor`, `samples/` for the new sealed subtype and the new `Animation` component.

**Produces:**

```java
public record Asset(String id, Kind kind, String path, String name) { enum Kind {IMAGE, FONT, AUDIO} }
public record AudioLayer(LayerBase base, String assetId, double clipOffset, Animatable<Double> volume,
                         Animatable<Double> balance, boolean loop, boolean muted) implements Layer {
    static AudioLayer create(String name, String assetId, double inPoint, double outPoint);  // volume 1, balance 0, offset 0
    // withX copy methods for every component
}
public record SoundCue(String id, String name, double time, String assetId, double volume, boolean muted) {
    static SoundCue create(String name, double time, String assetId);   // random UUID, volume 1
    // withX copy methods
}
public record Animation(int formatVersion, String name, double width, double height, double frameRate, double duration,
                        Rgba background, List<Marker> markers, List<Asset> assets, List<Layer> layers, List<SoundCue> cues) {
    static final int CURRENT_FORMAT = 2;
    // keep a 10-argument constructor (without cues -> empty list) so existing callers compile; add withCues(List<SoundCue>)
}
```

`Layer` permits `AudioLayer`. `LayerProperties` lists exactly `volume` ("Volume", NUMBER) and `balance` ("Balance", NUMBER) for an audio layer — no transform/opacity/effect keys. JSON and validation: spec §3. `Animation.create` produces `formatVersion` 2; reading a version-1 document yields a document with `formatVersion` 2 and empty cues.

- [ ] **Step 1:** Failing tests: kitchen-sink document extended with an audio asset, an audio layer with keyframed volume and static balance, loop and mute, and two cues survives `fromJson(toJson(a))` with `equals`; `.afx` zip round-trip keeps the audio bytes; a version-1 fixture (no cues, `formatVersion` 1) loads; exact pretty-printed JSON of a minimal audio layer and cue (defaults omitted); errors with JSON paths for: audio layer whose asset is an image (`layers[0].asset`), cue referencing a missing asset (`cues[0].asset`), negative `clipOffset`, static volume 1.5, balance −2, negative cue time, duplicate cue ids; `LayerProperties.list/get/set` for audio layers; `Layers` operations treat audio layers like other leaf layers.
- [ ] **Step 2:** Fail → implement → pass in the model module.
- [ ] **Step 3:** Make the whole reactor compile and pass again with the smallest change per call site: every exhaustive `switch` over `Layer` handles `AudioLayer` as a non-visual leaf (player renderer: an empty invisible node; evaluator: a state with no geometry; editor canvas/hit testing/overlay: ignored; importers' `DocumentCheck`: validated like other layers; editor duplicate/paste/asset-usage code: audio layers reference their asset). No new behaviour beyond not crashing — later tasks add it.
- [ ] **Step 4:** Full reactor build green. Commit `feat(model): audio assets, audio layers and sound cues (format version 2)`.

### Task 2: Runtime audio engine

**Files:** `animatorfx-player/pom.xml` + `module-info.java` (`requires transitive javafx.media`? no — `requires javafx.media;`), `.../player/{Evaluator,LayerState,AnimationPlayer,AnimationView}.java`, new `.../player/internal/audio/{AudioSchedule,VoiceTarget,CueCrossing,AudioOutput,MediaAudioOutput,AudioFiles,AudioEngine}.java` + tests; sample app pom/module-info if they need `javafx-media` declared.

**Consumes:** Task 1.

**Produces:** the API in spec §4.2 (`mutedProperty()`, `volumeProperty()`, `scrub(double)`, `dispose()` on `AnimationPlayer`; `muted`, `volume` delegated on `AnimationView` plus `scrub`), `LayerState` with sampled `volume`/`balance` for audio layers, and internally:

```java
record VoiceTarget(String layerId, String assetId, boolean audible, double mediaTime, double volume, double balance, double rate) {}
final class AudioSchedule {      // pure: no JavaFX media types
    static List<VoiceTarget> voices(Animation a, Frame frame, boolean playing, double playbackRate, boolean reversing,
                                    boolean muted, double masterVolume, Map<String, Double> clipDurations);
    static List<SoundCue> crossed(Animation a, double from, double to, boolean firstTickAfterPlay);   // forward only
    static boolean needsSeek(double actualMediaTime, double targetMediaTime);                        // drift > 0.08 s
}
interface AudioOutput {          // one per player; implemented by MediaAudioOutput and by a test fake
    OptionalDouble duration(String assetId);                 // empty until known
    void apply(List<VoiceTarget> targets);                   // start/stop/seek/volume/balance/rate voices to match
    void playCue(String assetId, double volume);
    void snippet(List<VoiceTarget> targets, double seconds); // scrubbing
    void silence();
    void dispose();
}
```

Behaviour: spec §4.1 and §4.3. `MediaAudioOutput` extracts asset bytes lazily to a private temp directory (`AudioFiles`), creates `Media`/`MediaPlayer` per audio layer and `AudioClip` per cue asset, logs each failing asset once and carries on silently, and creates nothing for a document without audio content. A package-private `AnimationPlayer` constructor accepts an `AudioOutput` for tests.

- [ ] **Step 1:** Failing pure tests for `AudioSchedule`: media time from in-point and clip offset; silent outside in/out; silent past clip end without loop, wrapped with loop; layer mute, `visible=false`, runtime mute, master volume scaling; rate passed through for `(0, 8]`, silent for 0, negative and reversing; `needsSeek` at 0.07 s vs 0.09 s; cues crossed forward once, cue exactly at the start on the first tick, none on backward movement, muted cue skipped.
- [ ] **Step 2:** Failing player tests with the manual clock and a recording fake `AudioOutput`: playing applies targets every tick; pause/stop/finish call `silence()`; seek while playing repositions; cycle wrap repositions; `playMarker` range; cue fired once per crossing and again on the next cycle; `muted`/`volume` defaults and effect; `scrub` while paused requests a snippet and while playing does not; replacing the animation disposes the old output; a document without audio never touches the output factory; `*Property` methods final (extend the existing reflection test).
- [ ] **Step 3:** Fail → implement → pass. Add a smoke test with real JavaFX media playing a generated 0.3 s WAV that passes whether or not an audio device exists (assert no exception and that failures are logged, not thrown).
- [ ] **Step 4:** Full reactor build green incl. the sample app's independence test. Commit `feat(player): audio layers and sound cues in the runtime`.

### Task 3: Editor audio authoring

**Files:** `animatorfx-editor/.../doc/Commands.java` (audio/cue factories), `.../ui/{ImportActions,MenuBarFactory,ToolBarView,SessionController}.java`, `.../session/SessionState.java` (mute flag), `.../inspector/AssetsView.java`, new `.../inspector/sections/{AudioSection,CueSection}.java`, `.../timeline/{CueLane,WaveformPainter}.java` and changes in the timeline row/track classes, new `.../audio/{Waveform,WaveformDecoder,SpectrumAnalyser,WaveformCache,AudioInfo}.java`, `.../canvas/Playback.java`, `editor.css` + tests.

**Consumes:** Tasks 1–2.

**Behaviour:** spec §5 (all of it except the Lottie import bullet). Commands (pure, unit-tested, each one undo step): `addAudioLayer`, `setClipOffset`, `addCue`, `updateCue`, `moveCue`, `removeCues`; removing an audio asset in use is refused. Selection of cues: extend `Selection` with a cue id set, kept consistent on undo/redo. Waveform model: `Waveform(double duration, float[] peaks)` with a fixed number of peaks per second; `WaveformDecoder` handles WAV/AIFF through `javax.sound.sampled` (8/16/24-bit PCM, mono/stereo); `SpectrumAnalyser` produces an approximate waveform through JavaFX media in the background, off the document's undo history, and degrades to "no waveform" on any failure or timeout.

- [ ] **Step 1:** Failing pure tests: the commands above; clip-offset slide maths; waveform decoding of generated WAV data (silence, full-scale sine, stereo, 8/16/24-bit) and peak bucketing; cue time snapping; bar → waveform window mapping incl. loop repetition.
- [ ] **Step 2:** Failing headless UI tests: importing a WAV through the injectable picker creates one audio asset and one audio layer at the playhead in one undo step; the audio row shows the speaker icon and its eye toggle mutes; Alt-dragging the bar changes `clipOffset`, plain dragging changes in/out; adding a cue at the playhead and at a marker; dragging a cue changes its time; Delete removes the selected cue; the Audio and Cue inspector sections edit volume (with keyframe toggle), balance, loop, mute, clip offset, cue name/time/volume; the toolbar mute toggle sets the playback player's `muted` and is restored from the session; scrubbing the ruler calls the player's `scrub` (verified with the fake output); audio layers cannot be hit on the canvas.
- [ ] **Step 3:** Fail → implement → pass. Look at application snapshots of the timeline with an audio layer (waveform), the cue lane and the two inspector sections in dark and light; iterate until they match the rest of the UI.
- [ ] **Step 4:** Full reactor build green. Commit `feat(editor): audio layers, sound cues, waveforms and audible playback`.

### Task 4: Lottie audio, samples, packaging, docs

**Files:** `animatorfx-import/.../importer/Lottie*.java` + fixtures/tests; sample generator and `samples/animations/*.afx`; sample app UI; `animatorfx-editor/pom.xml` packaging profile if `javafx.media` needs adding to the runtime image; `README.md`; both spec files.

- [ ] **Step 1:** Lottie: failing tests with a fixture containing an audio layer (`ty` 6) whose asset embeds a small base64 WAV (→ audio asset + `AudioLayer` with in/out from `ip`/`op`) and one whose asset is an external path (→ skipped with one warning); the unsupported-features fixture no longer warns about audio in general. Implement; the result passes `DocumentCheck` and the layer/depth limits.
- [ ] **Step 2:** Samples: the generator synthesises a chime and a tick as WAV; `success.afx` gets a cue at the `success` marker and a short audio layer; tests assert both and that every sample still loads and renders. Regenerate the committed samples with the documented command.
- [ ] **Step 3:** Sample app: mute toggle and volume slider bound to the view; smoke test.
- [ ] **Step 4:** Packaging: `mvn -Ppackage -pl animatorfx-editor -am package -DskipTests` succeeds with `javafx.media` in the image; start the packaged app once with a temporary config dir, confirm a clean log for ~10 s, terminate by PID.
- [ ] **Step 5:** Docs: README (features, embedding guide incl. `muted`/`volume` and the `javafx.media` requirement, format section, limitations incl. approximate MP3/AAC waveforms and silent reverse playback); main spec §3 module table, §4 and §10 updated; a README test for any new snippet.
- [ ] **Step 6:** Full reactor build green. Commit(s) `feat(import): Lottie audio layers`, `feat(samples): sound in the samples and the sample app`, `docs: audio support`.

## Final verification

- [ ] Full `mvn clean install`; sum the `Tests run` lines; zero failures and errors.
- [ ] Launch the packaged editor, play `success.afx` and hear the chime; scrub; mute.
- [ ] Walk the audio spec section by section against the product; list anything missing.
