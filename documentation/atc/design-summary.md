# ATC Server - Design Summary

## Scope

The ATC server is the ground-side Java Core component of TACTIC-NAV. It receives UDP radar feeds, normalizes valid packets, fuses observations into stable tracks, publishes immutable situation snapshots, and broadcasts those snapshots to cockpit clients over UDP.

The strict embedded constraints in the global README belong mainly to the Android cockpit. The ATC keeps soft latency and throughput goals, but its priorities are correctness, determinism, operational clarity, and clean separation of concerns.

## Implemented Architecture

| Layer | Responsibility | Main Classes |
|---|---|---|
| Network ingestion | One UDP listener per radar source, no fusion logic | `RadarListener` |
| Parsing / normalization | Validate packet envelope, CRC and field ranges | `RadarPacketParser`, `RadarInputMessage` |
| Fusion / tracking | Associate observations, update tracks, expire stale tracks | `TrackFusionEngine` |
| State store | Publish complete immutable snapshots for concurrent readers | `SituationStateStore`, `SituationSnapshot` |
| Broadcast | Send periodic and event-triggered state to cockpit clients | `BroadcastService` |

## Concurrency Model

The ATC uses a single-writer fusion model:

```
Radar listener threads -> bounded queue -> FusionOrchestrator -> SituationStateStore
                                                         |
                                                         v
                                                BroadcastService thread
```

Network listeners submit messages with a non-blocking `offer`. If the queue is full, the message is dropped and counted. Fusion processes messages sequentially, which keeps association deterministic and avoids shared mutable track state.

The state store publishes a complete `SituationSnapshot` through a volatile reference. Readers get a consistent immutable snapshot without blocking the fusion writer.

Broadcast is decoupled from fusion. Fusion events only mark the latest snapshot as pending; the broadcast thread performs serialization and UDP sends. This prevents a slow client or socket send from blocking track fusion.

## Tracking Strategy

For each accepted radar observation:

1. Expire stale tracks using a TTL.
2. Convert azimuth/elevation/slant range into Cartesian coordinates.
3. Prefer the same radar-local track ID if it is already active.
4. Otherwise, associate to the nearest predicted track position inside the distance gate.
5. Update position with simple EMA smoothing and estimate velocity from position deltas.
6. Create a new track when no association is found.
7. Ignore out-of-order observations for an already matched track.

This is intentionally simple and deterministic. Future improvements can replace the nearest-neighbor heuristic with Kalman filtering plus multi-hypothesis assignment if the scenario requires dense airspace handling.

## UDP Protocol

Radar input packets are fixed 28-byte binary datagrams:

```
[0-1]   Header 'R' 'D'
[2-3]   TrackId (short)
[4-7]   Azimuth (float)
[8-11]  Elevation (float)
[12-15] SlantRange (float)
[16-23] Timestamp (long)
[24-27] CRC32 over first 24 bytes
```

Invalid packets are discarded by the listener after logging the parse reason. They do not reach the fusion engine.

Cockpit output is a compact binary snapshot containing timestamp, sequence number, track count, zone count, serialized tracks, and serialized zones. Packets that exceed the configured UDP payload limit are skipped and logged instead of blocking fusion.

## Current Status

Implemented and covered by targeted tests for:

- Valid and invalid radar packet parsing
- Immutable state snapshot publication
- Stale track expiration
- Out-of-order observation handling

The ATC is close to complete for the prototype. The most useful next steps are integration tests with the radar simulator and a cockpit-side decoder test for the broadcast snapshot format.
