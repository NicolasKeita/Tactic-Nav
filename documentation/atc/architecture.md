# TACTIC-NAV Architecture

## System Overview

TACTIC-NAV is split into two independent components:

- **ATC server**: Java Core application running on the ground station. It binds one local UDP socket, ingests radar datagrams sent to that address and port, fuses tracks, and maintains the current tactical situation.
- **Cockpit client**: Android application running on the onboard tablet. It is a separate runtime concern and is not contacted by the current ATC server.

The ATC and cockpit share a UDP-based data contract but keep separate runtime concerns. Cockpit constraints such as tight heap budgets and frame-rate guarantees should not force unnecessary ATC server patterns.

## ATC Layers

| Layer | Responsibility |
|---|---|
| Network ingestion | Dedicated UDP listener on the ATC listen endpoint. Receives datagrams and forwards only valid normalized messages. |
| Parsing / normalization | Validates packet size, header, CRC32 and numeric ranges, then creates `RadarInputMessage`. |
| Fusion / tracking | Single-writer track association, smoothing, velocity estimation and stale-track expiration. |
| State store | Publishes complete immutable `SituationSnapshot` instances through atomic reference replacement. |

## ATC Data Flow

```
[Radar UDP datagrams]
        |
        v
RadarListener
        |
        v
RadarPacketParser -> RadarInputMessage
        |
        v
Bounded queue, non-blocking offer
        |
        v
FusionOrchestrator, single writer
        |
        v
SituationStateStore, immutable snapshot
```

## Concurrency

Radar listeners are isolated by thread and socket. They never run fusion logic.

The fusion engine processes one message at a time from a bounded queue. This keeps track association deterministic and provides a clear backpressure policy: when the queue is full, new radar messages are dropped and counted.

The state store does not use a global read/write lock. It publishes complete immutable snapshots via a volatile reference, so monitoring reads are fast and consistent.

## Track Fusion

The current tracking algorithm is intentionally simple:

1. Remove tracks older than the TTL.
2. Convert radar-centric spherical coordinates to Cartesian coordinates.
3. Use the same observation track ID when already active.
4. Otherwise compare the observation with predicted track positions and select the nearest candidate inside the association gate.
5. Update position and velocity using exponential smoothing.
6. Create a new track if no association is found.
7. Ignore out-of-order observations for an existing track.

This gives deterministic behavior suitable for the prototype. Dense traffic scenarios can later justify Kalman filtering, spatial indexing or multi-hypothesis assignment.

## UDP Contracts

Radar input datagrams are fixed 28-byte binary packets:

```
[0-1]   Header 'R' 'D'
[2-3]   TrackId (short)
[4-7]   Azimuth (float)
[8-11]  Elevation (float)
[12-15] SlantRange (float)
[16-23] Timestamp (long)
[24-27] CRC32 over first 24 bytes
```

UDP input is treated as unordered and unreliable. The ATC ignores out-of-order radar observations for already matched tracks.

## Performance Posture

The ATC has soft latency and throughput goals, not hard embedded limits. The code avoids obvious waste, but it does not enforce zero-allocation, object pools, or a global functional error container. The cockpit remains the component where strict heap and frame-time constraints matter most.

## Key Files

| File | Role |
|---|---|
| `atc-server/src/main/java/com/tacticnav/atc/AtcServer.java` | Main ATC composition and lifecycle |
| `atc-server/src/main/java/com/tacticnav/atc/network/RadarListener.java` | UDP listener for the ATC input stream |
| `atc-server/src/main/java/com/tacticnav/atc/network/RadarPacketParser.java` | Binary packet validation and normalization through `nav-protocol` |
| `nav-protocol/src/main/java/com/tacticnav/protocol/RadarPacketCodec.java` | Shared radar datagram codec |
| `atc-server/src/main/java/com/tacticnav/atc/fusion/TrackFusionEngine.java` | Track association and lifecycle |
| `atc-server/src/main/java/com/tacticnav/atc/fusion/FusionOrchestrator.java` | Queue consumer and snapshot publisher |
| `atc-server/src/main/java/com/tacticnav/atc/state/SituationStateStore.java` | Immutable snapshot publication |
