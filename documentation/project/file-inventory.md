# TACTIC-NAV File Inventory

## Active Maven Modules

| Module | Purpose |
|---|---|
| `nav-protocol/` | Shared binary datagram protocol and CRC utilities |
| `ground-station/` | Ground station simulator executable (ADS-B relay) |
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
| `AdsbListener.java` | UDP listener for the ATC ADS-B input stream |
| `AdsbPacketParser.java` | Converts custom 112-byte ADS-B datagrams into ATC input messages |
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

## Ground Station Simulator (`ground-station/src/main/java/com/tacticnav/groundstation/`)

| File | Purpose |
|---|---|
| `Main.java` | Simulator entry point |
| `GroundStation.java` | Simulates reception of ADS-B and forwards UDP packets to ATC |

---

## Configuration

| File | Purpose |
|---|---|
| `atc-server/src/main/resources/atc-config.properties` | ATC UDP bind address and listen port |
| `ground-station/src/main/resources/ground-station.properties` | Ground station defaults |

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
| `bin/ground-station-0.1.0-jar-with-dependencies.jar` | Ground station simulator |

---

## Documentation

| File | Purpose |
|---|---|
| `../../README.md` | Project overview |
| `../atc/architecture.md` | ATC architecture and design notes |
| `../atc/design-summary.md` | Design summary and trade-offs |
| `../atc/dataflow.md` | Message flow and concurrency diagrams |
| `../atc/quickstart.md` | Build, run and troubleshooting guide |
