# Cockpit Android Embedded Constraint Test Plan

## Current Status

The embedded constraints for `cockpit-android` were documented but were not enforced by automated tests when this plan was written. The first automation pass now adds Gradle budget gates, JVM constraint tests, and an Android instrumented runtime-budget test.

Existing baseline coverage included JVM unit tests under `cockpit-android/app/src/test`, plus the Maven/Gradle build path. The repo now also contains a first `src/androidTest` budget suite and build-time gates for APK size and render hot-path allocation patterns.

The README defines the intended cockpit posture:

- Keep cockpit heap usage below 45 MB with a flat memory curve.
- Keep the UI smooth at 30 FPS or better.
- Limit allocations inside rendering and geospatial-computation loops.
- Avoid visible Garbage Collector pauses in operational cockpit flows.

The README also mentions an `ObjectPool` validation, but the current codebase does not contain an `ObjectPool` implementation or a related automated validation.

## Constraints To Make Executable

The first implementation should turn the documented goals into explicit build thresholds:

| Constraint | Proposed Gate | Test Type |
| --- | --- | --- |
| Runtime Java heap | Stable post-warm-up heap below 45 MB | Instrumented Android test |
| Memory stability | No meaningful upward heap slope after warm-up | Instrumented Android test |
| UI smoothness | 30 FPS minimum, with frame-time percentiles reported | Instrumented Android test |
| Hot-path allocation discipline | No accidental allocations in render, projection, geospatial, and UDP receive loops | Static rule plus targeted stress tests |
| APK footprint | Debug/release APK size stays under an agreed budget | Gradle build check |
| Datagram bounds | Packet and track caps remain fixed and enforced | Existing JVM tests plus budget assertions |

The APK-size budget is not documented yet, so the first run should establish a baseline and then define a threshold with margin.

## Implementation Plan

### 1. Add A Fast Gradle Budget Check

Create a Gradle task in `cockpit-android/app/build.gradle`, for example `checkEmbeddedBudgets`, that runs after APK assembly and fails when static build artifacts exceed configured budgets.

Initial checks:

- APK file size for `app-debug.apk` and, later, release APK.
- DEX count and total DEX byte size if the project grows beyond the current pure-Java/no-AndroidX posture.
- Resource package size if offline maps or bitmap assets are added.
- Optional generated JSON report under `app/build/reports/embedded-budgets/`.

This gives CI a cheap first guard without requiring a device.

### 2. Add Static Rules For Allocation-Sensitive Paths

Add a static-analysis rule to flag suspicious allocations in cockpit hot paths.

Target methods and classes:

- `TacticalDisplayView.onDraw`
- `CanvasTacticalMapEngine.draw` and its draw helpers
- `TacticalProjection.project`
- `SituationProcessor.process`
- UDP receive and packet decode loops

Initial prohibited patterns:

- `String.format(...)` inside drawing methods.
- `new` object creation inside drawing loops.
- `new byte[...]` inside recurring send/receive loops.
- `ByteBuffer.wrap(...)` in decode paths unless explicitly accepted.
- Collection creation inside per-frame or per-packet loops unless the method is intentionally immutable-boundary code.

The current code already contains useful candidates for this gate, such as `String.format(...)` calls in rendering code and per-call `ArrayList` creation in `SituationProcessor.process`. Those may be acceptable temporarily, but they should be made explicit with allowlist comments or refactored before turning the rule strict.

### 3. Add JVM Stress Tests For Deterministic Hot Paths

Add repeatable JVM tests for pure Java logic where Android runtime measurement is not required.

Recommended tests:

- Run `SituationProcessor.process(...)` over thousands of snapshots at maximum track and zone counts.
- Run `TrackDatagramDecoder.decode(...)` repeatedly with maximum-size datagrams.
- Run `SimulatedTrackGenerator.snapshotAt(...)` over a long simulated mission.
- Assert that packet size, maximum track count, and snapshot sizes remain bounded.
- Record average and maximum execution time as informational output, but avoid strict timing failures in normal CI because shared runners are noisy.

These tests cannot prove real Android heap behavior, but they are excellent regression tripwires for unbounded collections, packet growth, or accidental algorithmic blowups.

### 4. Add Instrumented Android Memory And Frame Tests

Create `cockpit-android/app/src/androidTest` and add a deterministic cockpit runtime test that runs on an emulator or physical tablet.

Test scenario:

- Launch `CockpitActivity` with the simulated source.
- Warm up for 30 to 60 seconds.
- Drive the app for 3 to 5 minutes.
- Sample heap with Android runtime APIs such as `Debug.MemoryInfo`, `Runtime.totalMemory()`, and `Runtime.freeMemory()`.
- Record frame timings with `FrameMetrics` on API 24+ or a `Choreographer`-based test hook.
- Fail if the post-warm-up heap exceeds 45 MB or continues climbing beyond the chosen tolerance.
- Fail if frame timing falls below the 30 FPS target beyond the agreed percentile.

The test should emit a small JSON or CSV report containing heap samples, frame statistics, and device metadata. CI can upload that report as an artifact.

### 5. Split CI Into Fast And Device Gates

Keep the normal pull-request pipeline fast:

- JVM unit tests.
- Static allocation rules.
- APK/static size budgets.

Run the Android instrumented memory/frame suite separately:

- Nightly on a pinned emulator image, or
- On demand before release, or
- On a physical target tablet if the real embedded device has stricter behavior than the emulator.

This avoids making every PR dependent on emulator stability while still giving the project an executable embedded-constraint gate.

### 6. Document And Ratchet Budgets

Add a checked-in budget file, for example `cockpit-android/embedded-budgets.properties`, with values such as:

```properties
heap.maxMb=45
heap.postWarmupGrowthMb=1
frame.minFps=30
apk.debug.maxMb=TODO_BASELINE
```

After the first measured baseline, replace `TODO_BASELINE` with a real value and leave a small margin. When the app legitimately grows, update the budget in the same change that justifies the growth.

## Limits Of Automation

These tests can provide strong regression protection, but they cannot prove universal embedded behavior across every Android tablet. Heap, GC, and frame timing depend on device model, OS build, thermal state, background services, and graphics driver behavior.

For that reason, the strongest setup is layered:

- Static and JVM gates on every pull request.
- Instrumented emulator tests as a regular automated signal.
- Release validation on the actual cockpit target tablet.

That gives the project a practical, enforceable safety net without pretending that JVM-only tests can certify real Android runtime memory.
