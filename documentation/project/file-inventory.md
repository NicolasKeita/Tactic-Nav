# TACTIC-NAV File Inventory

## Active Maven Modules

| Module | Purpose |
|---|---|
| `nav-protocol/` | Shared binary radar datagram protocol and CRC utilities |
| `radar-simulator/` | Radar packet simulator executable |
| `atc-server/` | ATC UDP ingestion, fusion and state store |

---

## ATC Server (`atc-server/src/main/java/com/tacticnav/atc/`)

### Domain Model (`domain/`)

| File | Purpose |
|---|---|
| `TrackId.java` | Immutable unique identifier for fused tracks |
| `Position.java` | 3D Cartesian coordinates with timestamp and confidence |
| `Velocity.java` | 3D velocity vector and smoothing helpers |
| `RadarInputMessage.java` | Normalized radar observation passed into fusion |
| `Track.java` | Consolidated fused track state |
| `NoFlyZone.java` | Restricted area model |
| `SituationSnapshot.java` | Immutable tactical snapshot for readers |

### Network (`network/`)

| File | Purpose |
|---|---|
| `RadarListener.java` | UDP listener for the ATC input stream |
| `RadarPacketParser.java` | Converts shared protocol observations into ATC input messages |
| `CoordinateTransformer.java` | Radar-centric spherical to Cartesian conversion |

### Fusion (`fusion/`)

| File | Purpose |
|---|---|
| `TrackFusionEngine.java` | Track association, update and expiration logic |
| `FusionOrchestrator.java` | Queue-based single-writer fusion loop |
### State and Bootstrap

| File | Purpose |
|---|---|
| `state/SituationStateStore.java` | Thread-safe snapshot publication |
| `AtcServer.java` | ATC composition, configuration and lifecycle |

---

## Shared Protocol (`nav-protocol/src/main/java/com/tacticnav/protocol/`)

| File | Purpose |
|---|---|
| `RadarPacketCodec.java` | Encodes and decodes fixed-size radar datagrams |
| `RadarObservation.java` | Shared radar observation record |
| `Crc32Util.java` | CRC32 helper |
| `ProtocolException.java` | Protocol validation error |

---

## Radar Simulator (`radar-simulator/src/main/java/com/tacticnav/radar/`)

| File | Purpose |
|---|---|
| `Main.java` | Simulator entry point |
| `RadarOptions.java` | CLI and resource configuration parsing |
| `RadarSimulator.java` | UDP packet generation loop |

---

## Configuration

| File | Purpose |
|---|---|
| `atc-server/src/main/resources/atc-config.properties` | ATC UDP bind address and listen port |
| `radar-simulator/src/main/resources/radar-config.properties` | Radar simulator defaults |

---

## Build and Validation

```bash
mvn test
mvn clean package
```

`mvn clean package` writes executable jars to `bin/`:

| Artifact | Purpose |
|---|---|
| `bin/atc-server-0.1.0-jar-with-dependencies.jar` | ATC server |
| `bin/radar-simulator-0.1.0-jar-with-dependencies.jar` | Radar simulator |

---

## Documentation

| File | Purpose |
|---|---|
| `../../README.md` | Project overview |
| `../atc/architecture.md` | ATC architecture and design notes |
| `../atc/design-summary.md` | Design summary and trade-offs |
| `../atc/dataflow.md` | Message flow and concurrency diagrams |
| `../atc/quickstart.md` | Build, run and troubleshooting guide |
