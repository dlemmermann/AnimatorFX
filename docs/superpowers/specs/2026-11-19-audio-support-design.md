# AnimatorFX Audio Support — Design

Date: 2026-11-19
Status: approved in brainstorming
Extends: `2026-11-18-animatorfx-design.md` (audio was listed as out of scope in §10 there; this spec lifts that for audio. Video stays out of scope.)

## 1. Goal

Animations can carry sound. Two forms, both stored in the `.afx` file and played by the runtime in applications:

1. **Audio layers** — sound clips placed on the timeline, playing in sync with the animation.
2. **Sound cues** — short sounds triggered at a point in time (for example at a marker's start).

## 2. Decisions taken

| Topic | Decision |
|---|---|
| Scope | Audio layers and sound cues, in the file and in the runtime |
| Runtime packaging | Built into `animatorfx-player`; the player now also requires `javafx.media` |
| Formats | What JavaFX media plays: MP3, WAV, AIFF, M4A/AAC |
| Clock | The animation clock is the master; audio follows it |
| Waveforms | Exact for WAV/AIFF; approximate background analysis through JavaFX for MP3/AAC; no third-party decoder |
| Features | Animatable volume and balance, clip offset, loop, mute per layer and cue, audio follows playback rate, global `muted`/`volume` on the runtime, audible scrubbing in the editor, Lottie audio import |

`animatorfx-model` stays `java.base` only. `animatorfx-player` still has no third-party dependency (`javafx.media` is part of JavaFX).

## 3. Model and file format (`animatorfx-model`)

- `Asset.Kind` gains `AUDIO`. Audio assets live under `assets/` like images and fonts, with extensions `mp3`, `wav`, `aif`, `aiff`, `m4a`, `aac`.
- New layer type `AudioLayer`:
  - `assetId` — an `AUDIO` asset.
  - `clipOffset` — seconds into the sound at which the layer's in-point starts (≥ 0).
  - `volume` — animatable, `0..1`, default 1.
  - `balance` — animatable, `-1` (left) .. `1` (right), default 0.
  - `loop` — when true the clip repeats until the out-point; otherwise it is silent after the clip ends.
  - `muted`.
  - It keeps the common layer members for uniformity; `inPoint`/`outPoint` define when it sounds; transform, opacity, mask, effects and motion path are ignored. It renders nothing.
- New `SoundCue` on the document (`Animation.cues`): `id`, `name`, `time` (seconds, ≥ 0), `assetId` (an `AUDIO` asset), `volume` (`0..1`, static), `muted`. Cue ids are unique.
- Animatable property keys for audio layers: `volume`, `balance`.
- `formatVersion` becomes `2`. Version-1 files load unchanged (migration is the identity). A version-1 reader rejects version-2 files, as designed. JSON: layer `"type": "audio"` with `asset`, `clipOffset`, `volume`, `balance`, `loop`, `muted`; document member `"cues": [{"id", "name", "t", "asset", "volume", "muted"}]`. Defaults are omitted on write.
- Reader validation: the referenced asset exists and is of kind `AUDIO`; `clipOffset` and cue `time` finite and ≥ 0; static volume values within `0..1` and balance within `-1..1`; duplicate cue ids are an error.

## 4. Runtime (`animatorfx-player`)

### 4.1 Behaviour

- Audio sounds only while the player's status is `PLAYING`. Pausing, stopping or finishing silences everything. A seek while playing repositions the audio.
- For an audio layer at animation time `t` with `inPoint ≤ t < outPoint`, the sound position is `clipOffset + (t − inPoint)`. Past the end of the clip: silent, or wrapped when `loop` is set.
- The animation clock is the master. Each audio layer has one media voice; a voice is repositioned when it drifts more than 80 ms from where it should be.
- Effective volume = layer volume × runtime `volume`; silent when the layer is muted, not visible, or the runtime is `muted`. Balance is applied per layer.
- Audio follows the playback rate for rates in `(0, 8]` (the JavaFX limit). Rate 0, negative rates, and the reverse leg of auto-reverse are silent.
- Cues fire when playback crosses their time going forward (including a cue exactly at the start time on the first tick after `play`), never on seeks or while reversing. A cue plays to its end independently; cue volume × runtime `volume`; skipped when the cue or the runtime is muted.
- Marker playback (`playMarker`) and cycles behave the same way within the active range; wrapping to the range start repositions audio.
- A player with no audio content creates no media objects.

### 4.2 API

On `AnimationPlayer`, delegated by `AnimationView`:

- `mutedProperty()` — boolean, default `false`.
- `volumeProperty()` — double `0..1`, default `1`.
- `scrub(double seconds)` — seeks to the time and, when not playing, plays a short snippet (about 100 ms) of the audio at that position. For tooling.
- `dispose()` — releases media resources and temporary files. Also happens automatically when the animation is replaced; temporary files are additionally removed at JVM exit.

### 4.3 Implementation constraints

- JavaFX media needs a URI, not bytes: audio assets are extracted lazily to a private temporary directory per loaded document.
- What should sound when is decided by a pure function (no JavaFX media types) that is unit-tested; the media output sits behind a small internal interface with a fake for tests.
- Failures never break playback: no audio device, an undecodable file or a media error is logged once per asset through `System.Logger` and the animation continues silently.
- The evaluator's layer state carries the sampled `volume` and `balance` for audio layers. The renderer creates no node content for audio layers.

## 5. Editor (`animatorfx-editor`)

- **Adding audio:** File ▸ Import ▸ Audio…, the Assets tab (audio assets listed with duration), and dropping audio files on the window. Importing creates an audio asset and an `AudioLayer` at the playhead whose out-point is the in-point plus the clip duration (clamped to the document duration), in one undo step.
- **Timeline:** audio layers are rows with a speaker icon; the eye toggle acts as mute; the layer bar shows the waveform, positioned according to `clipOffset` and repeated when looping. Dragging the bar moves in/out as for other layers; Alt-dragging the bar slides the clip under it (changes `clipOffset`). Volume and balance appear as property rows with keyframes.
- **Waveforms:** computed in the background and cached per asset for the session. WAV/AIFF are decoded exactly (`javax.sound.sampled`). MP3/AAC are analysed by playing the file silently through JavaFX at high rate and sampling its audio spectrum; the result is approximate and arrives after a short delay. Until a waveform is available, and if analysis fails, the bar is plain.
- **Cue lane:** below the marker lane; cues are shown as small speaker ticks with their name. Add at the playhead (header button) or at a marker's start (marker context menu); drag to move (frame snapping); Delete removes; selecting a cue shows it in the inspector.
- **Inspector:** an Audio section for audio layers (asset, clip offset, volume and balance with keyframe toggles, loop, mute, clip duration read-out) and a Cue section (name, time, asset, volume, mute, a preview button).
- **Playback:** editor playback sounds through the same runtime engine. A mute toggle in the toolbar sets the runtime `muted` flag for the editor and is stored in the session. Dragging the playhead scrubs audibly (through `scrub`) unless muted.
- **Canvas:** audio layers have no canvas presence and cannot be hit or selected there.
- **Lottie import:** audio layers (`ty` 6) with an embedded sound asset become `AudioLayer`s; sounds referenced by external path are skipped with a warning.
- Removing an audio asset is refused while a layer or cue uses it, as for other assets.

## 6. Samples and documentation

- The generator synthesises its sounds (sine-based chime and tick, written as WAV), so no third-party audio is shipped. `success.afx` gets a cue at the `success` marker and a short audio layer under the intro; the sample app gets mute and volume controls.
- README: audio in the feature list, the embedding guide (`muted`, `volume`, the `javafx.media` requirement), the format section and the limitations.
- The main spec's module table and §10 are updated to match.

## 7. Testing

- Model: JSON round-trip of audio layers and cues, version-1 file still loads, validation errors with JSON paths.
- Player: the pure scheduling function (positions, loop wrap, mute, rate, reverse, drift decision, cue crossing) against a manual clock and a fake output; property defaults and delegation; a smoke test with real JavaFX media that tolerates a missing audio device.
- Editor: commands for audio layers and cues; import flow; timeline bar/clip-offset maths; waveform decoding of a generated WAV; headless UI tests for the audio and cue sections and the cue lane.
- Importer: Lottie fixture with an embedded sound and one with an external sound.

## 8. Out of scope

- Video.
- Recording, audio editing, effects (EQ, reverb), per-channel mixing beyond balance.
- Reverse audio playback.
- Sample-accurate synchronisation; the target is tight enough for UI animation (tens of milliseconds).
- Exact waveforms for MP3/AAC.
