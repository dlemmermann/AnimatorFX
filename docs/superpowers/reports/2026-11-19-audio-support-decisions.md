# AnimatorFX audio support — decisions taken and open items

Extracted from the execution ledger on 2026-11-19. Rulings are decisions taken on your behalf during autonomous execution; deferred minors are review findings that were not fixed.

## Not done

- The final whole-feature audio review and the re-review of the last Task 4 fix round did not run: the review subagents failed with a quota error (402). The controller spot-checked the two important Task 4 fixes and the Lottie levels change in the source and ran the full build (1264 tests, 0 failures).
- Nobody has listened to any audio output. All audio behaviour is verified through recording fakes, media-time assertions and a muted diagnostic.

## Rulings

- Ruling: Task 3 verifies scrub/mute through observable player state (e.g. a public read-only hook or the playback player's muted property) rather than the package-private fake; if a seam is needed, Task 2 exposes a minimal public-but-internal factory hook in the non-exported `internal` package reachable by the editor via qualified export — decided at Task 2 dispatch — cost: small API surface in an internal package.
- Ruling: tasks run sequentially 1..4; reviews run concurrently with the next implementer as in the first plan; subagent model left to harness default.
- Ruling: spec 4.1 contradiction ("stopping silences everything" vs "a cue plays to its end independently") — explicit stop(), setMuted(true), dispose() and replacing the animation cut sounding cues; pause, natural finish and view suspension let a started cue ring out — cost: a long cue keeps sounding briefly after pause.
- Ruling: dispose() is terminal for sound until the animation is replaced or play() is called again — cost: none expected.
- Ruling (Task 3 reported deviations): analyser at rate 2 (real time) — accepted, measured; sound preview audible while the app is muted — accepted (explicit user action); tests silenced through -Danimatorfx.audio=off in surefire — accepted, but the editor must default to silent when running under a test harness is NOT required; M handled by the window not as menu accelerator; Alt slide semantics on audio bars; row speaker follows visible+muted; no late length correction after timeout; File > Save on FX thread (~270 ms for 10 MB) — accepted for v1.
- Ruling: Lottie `au.lv` is read as a PERCENTAGE (default 100, volume = value/100 clamped 0..1), not decibels — lottie-web's audio element initialises the levels property with default [100] and multiplier 0.01; my dispatch's dB reading was wrong — cost if wrong: imported Lottie audio levels off; flagged as unverified against real exports in the README.
- Ruling (Task 4 reported deviations): no balance from Lottie levels; out-of-range audio layers clamped rather than dropped; third synthesised sound "rise"; extra MIME aliases; regenerated screenshots — accepted.
- Final whole-feature audio review: NOT RUN — subagent failed (402 monthly quota exceeded). Ruling: hand over without the final review rather than stall — cost: cross-task audio issues (real media path, resource lifecycle) are unreviewed; nobody has listened to any output.

## Deferred minor findings

- Task 1: minor (deferred): AssetsView "used by N layers and cues" wording; group fallback box includes audio child origin; unreachable AssertionError branch in LayerProperties; ungroup bakes transform into audio child; setMotionPath/addMotionPath accept audio layer ids directly; RendererTest logs a WARNING; carry to Task 3: commands/fields must clamp volume, balance, clipOffset, cue time; carry to Task 4: README/spec still say formatVersion 1.
- Task 2: minor (deferred): per-tick small allocations; test-only accessors/counters in MediaAudioOutput; no real-media looping test; no-audio-device path never exercised on this machine; TDD RED after the fact.
- Task 3: minor (deferred): SessionController written-set complexity; duplicated code in AudioSection/CueSection; SpectrumAnalyserTest wall-clock; Delete with canvas focus and cues selected does nothing.
- Task 2: minor (deferred): playCue no longer skips volume 0 (allocates media inaudibly); possible blip when a voice gets ready after snippet patience; outputs created on a detached view not auto-released; spec 4.2 dispose wording not updated (carry to Task 4 docs); AudioEngine disposes on output exception leaving audio off until animation replaced.
- Task 3: minor (deferred): shutdown drain-timeout path (>10 s) loses latest content / queued discard, untested; redundant write after undo-to-clean then redo; RecoveryStore.discard swallows IOException; opening a document probes every compressed asset at once without a cap.
- Task 4: minor (deferred): skipped audio layers consume the layer budget; third hand-written WAV writer; sr on audio layers ignored without warning; embedded bytes not checked for playability.
- Task 3: minor (deferred): MainWindow.shutdown catch list misses other Errors; playback/preview stopped only after session.shutdown (sound may continue while quitting); Selection guard does not reconcile outside listener changes; SessionController.shutdown skips saveNow if autosaveNow throws; no recovery copies written if the writer does not drain in time.
