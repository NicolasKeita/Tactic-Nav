# ATC Server - Data Flow

## Runtime Flow

```
UDP radar datagrams --> RadarListener
                           |
                           v
                    RadarPacketParser
                           |
                           v
                 RadarInputMessage queue
                           |
                           v
                   FusionOrchestrator
                           |
                           v
                   TrackFusionEngine
                           |
                           v
                 SituationStateStore
```

## Packet Handling

Each radar datagram is parsed as a fixed 28-byte binary packet:

```
[0-1]   Header 'R' 'D'
[2-3]   TrackId (short)
[4-7]   Azimuth (float)
[8-11]  Elevation (float)
[12-15] SlantRange (float)
[16-23] Timestamp (long)
[24-27] CRC32 over first 24 bytes
```

The parser validates structure, checksum and numeric ranges. A bad datagram is logged and discarded inside the listener. Only a valid `RadarInputMessage` enters the fusion queue.

## Fusion Step

For each queued message, the fusion orchestrator:

1. Reads the latest immutable situation snapshot.
2. Copies active tracks into a working map.
3. Calls `TrackFusionEngine.fuse(...)`.
4. Publishes a new immutable snapshot.

The fusion engine first expires stale tracks, then associates the incoming observation to an existing track by observation track ID or nearest predicted position inside the association gate. If no track matches, it creates a new one. Older observations for an already matched track are ignored.

## Backpressure

The fusion queue is bounded. Radar listeners call `offer`, not `put`, so they do not block when the fusion engine is behind. If the queue is full, the message is dropped and `droppedMessages` is incremented.

This is an explicit UDP-compatible policy: under overload, the ATC prefers the newest reachable stream over blocking all radar ingestion.

## Snapshot Consistency

`SituationStateStore` publishes complete `SituationSnapshot` instances by replacing a volatile reference. A reader always sees either the previous full snapshot or the next full snapshot, never a partially updated state.

Snapshots contain:

- timestamp
- sequence number
- active tracks
- active no-fly zones

The maps and lists inside snapshots are immutable copies.
