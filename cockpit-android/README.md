# TACTIC-NAV Cockpit Android

Native Java Android cockpit client for the embedded tablet side of TACTIC-NAV.

## Runtime posture

- Default source: deterministic simulated ATC track feed.
- Optional source: UDP listener on port `16001`, enabled by changing manifest metadata
  `com.tacticnav.cockpit.TRACK_SOURCE` from `SIMULATED` to `UDP`.
- Rendering: single full-screen `Canvas` view, no AndroidX dependency, no network map.
- Processing: pure Java geofencing and link-state logic before UI publication.

The current ATC server does not yet publish cockpit snapshots. The UDP decoder in this
module is therefore a provisional cockpit contract, kept isolated in `TrackDatagramDecoder`
so it can be replaced when the ATC output protocol is finalized.

## Build

```powershell
cd cockpit-android
gradle :app:testDebugUnitTest
gradle :app:checkHotPathAllocations :app:checkEmbeddedBudgets
gradle :app:assembleDebug
```

The build uses Android Gradle Plugin `9.2.0`, Gradle `9.4.1+`, JDK `17`, and
`compileSdk 36`. The app targets Android 13 (`targetSdk 33`, `minSdk 33`).

## Embedded budget checks

The cockpit has executable embedded gates in `embedded-budgets.properties`.

- `:app:checkHotPathAllocations` fails on known allocation-heavy APIs in render hot paths.
- `:app:checkEmbeddedBudgets` checks the debug APK against the configured size budget and writes `app/build/reports/embedded-budgets/embedded-budgets.json`.
- `:app:printEmbeddedBudgetSummary` prints the build-time embedded budget summary and explicitly reports that runtime heap/FPS were not measured.
- `:app:testDebugUnitTest` includes JVM stress tests for bounded datagrams, simulated snapshots, and processing loops.
- `:app:verifyCockpitRuntimeBudgets` runs the runtime heap/frame budget test on a connected emulator or device, pulls the JSON report, and prints measured results.

The default Maven verification command remains device-free and does not run the runtime benchmark:

```powershell
.\mvnw.cmd verify
```

Run measured heap/FPS verification with a connected Android emulator or device:

```powershell
.\mvnw.cmd verify -Pandroid-runtime-verify
```

## Important packages

- `com.tacticnav.cockpit.domain`: immutable cockpit situation model.
- `com.tacticnav.cockpit.data`: simulated and UDP track sources.
- `com.tacticnav.cockpit.processing`: geofencing and link-state classification.
- `com.tacticnav.cockpit.render`: map projection and canvas map engine.
- `com.tacticnav.cockpit.ui`: full-screen tactical cockpit display.

## Provisional UDP packet

Big-endian datagram:

```text
int32  magic "TNS1"
int64  sequenceNumber
int64  timestampMillis
uint16 trackCount

repeated trackCount times:
uint16 trackId
double latitude
double longitude
int32  altitudeFt
float  headingDeg
float  groundSpeedKt
float  verticalSpeedFpm
float  confidence
```

The decoder caps packets to 32 tracks to keep the datagram below common local MTU limits.
