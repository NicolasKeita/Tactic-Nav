# ATC Server - Data Flow

## Runtime Flow

```
UDP ADS-B datagrams --> AdsbListener
                              |
                              v
                      AdsbPacketParser
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

Each ADS-B datagram is parsed as a fixed 112-byte binary packet:

```
[0-3]   Header 'A' 'D' 'S' 'B'
[4]     Version = 1
[5-7]   Reserved
[8-9]   TrackId (short)
[10-17] EmitterId (8-byte ASCII)
[18-33] Callsign (16-byte ASCII)
[34-37] Azimuth (float)
[38-41] Elevation (float)
[42-45] SlantRange (float)
[46-49] Heading (float)
[50-53] GroundSpeed (float)
[54-61] Timestamp (long)
[62-77] StationId (16-byte ASCII)
[78-107] Reserved
[108-111] CRC32 over first 108 bytes
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
