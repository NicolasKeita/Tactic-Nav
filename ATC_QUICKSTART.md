# ATC Server - Quick Start Guide

## Building the Project

```bash
cd Tactic-Nav
.\mvnw.cmd clean package
```

This produces:
- `tactic-nav-system/target/radar-simulator-0.1.0.jar` - Radar simulator executable
- Compiled ATC server classes

## Running the ATC Server

### 1. Start the ATC Server

```bash
java -cp tactic-nav-system/target/classes:tactic-nav-system/src/main/resources \
  com.tacticnav.atc.AtcServer
```

Expected output:
```
====== ATC SERVER STARTING ======
[RadarListener-1] Listening on UDP port 15001
[RadarListener-2] Listening on UDP port 15002
[RadarListener-3] Listening on UDP port 15003
[FusionOrchestrator] Started
[BroadcastService] Started
[BroadcastService] Client added: 127.0.0.1:16000
====== ATC SERVER RUNNING ======
```

### 2. Start Radar Simulators (in separate terminals)

```bash
# Terminal 1: Radar 1
java -cp tactic-nav-system/target/classes:tactic-nav-system/src/main/resources \
  com.tacticnav.radar.Main 1 15001 127.0.0.1

# Terminal 2: Radar 2
java -cp tactic-nav-system/target/classes:tactic-nav-system/src/main/resources \
  com.tacticnav.radar.Main 2 15002 127.0.0.1

# Terminal 3: Radar 3
java -cp tactic-nav-system/target/classes:tactic-nav-system/src/main/resources \
  com.tacticnav.radar.Main 3 15003 127.0.0.1
```

### 3. Observe ATC Processing

The ATC server will:
- Receive simulated radar packets
- Fuse tracks from all 3 sources
- Broadcast consolidated state
- Print statistics every 5 seconds:

```
[MONITOR] FusionStats{processed=234, dropped=0, queue=2, tracks=3, zones=0}, broadcasts=23
```

## Configuration

Edit `tactic-nav-system/src/main/resources/radar-config.properties`:

```properties
# Radar input ports
atc.radar.ports=15001,15002,15003

# Reference coordinates (latitude, longitude, altitude)
atc.radar.lat=40.7128
atc.radar.lon=-74.0060
atc.radar.alt=100.0

# Broadcast settings
atc.broadcast.port=15000
atc.broadcast.interval=100   # milliseconds

# Cockpit client addresses
atc.cockpit.addresses=127.0.0.1:16000
```

## Architecture Overview

```
Radars (UDP) ──→ Listeners ──→ Parser ──→ Fusion Engine ──→ State Store ──→ Broadcast ──→ Cockpits (UDP)
  15001-15003      (network)   (binary)   (track fusion)    (snapshot)    (UDP binary)  (16000+)
```

## Key Components

| Component | Role |
|-----------|------|
| **RadarListener** | Receives UDP packets from a single radar |
| **RadarPacketParser** | Validates and parses 28-byte binary packets |
| **TrackFusionEngine** | Associates observations to tracks (distance-based gating) |
| **FusionOrchestrator** | Coordinates pipeline with queue-based decoupling |
| **SituationStateStore** | Thread-safe holder using immutable snapshot publication |
| **BroadcastService** | Sends consolidated state to cockpit clients |

## Performance Characteristics

**Note**: Strict constraints (< 30ms, < 45MB) apply to the **Cockpit Android client**, not the ATC server.

**ATC Server**:
- **Latency**: ~20ms typical per message (soft goal, no hard constraint)
- **Throughput**: 1000+ messages/second
- **Memory**: <100MB typical (no strict limit)
- **Tracks**: Supports thousands of concurrent tracks
- **Sources**: Unlimited radar sources (tested with 3)

## Monitoring

### Statistics Output (every 5 seconds)

```
[MONITOR] FusionStats{
  processed=1234,     # Total messages fused
  dropped=0,          # Dropped due to full queue
  queue=5,            # Current queue size
  tracks=12,          # Active tracks in situation
  zones=3             # Active no-fly zones
}, broadcasts=456     # Total broadcasts sent
```

### Log Levels

- **INFO**: Component lifecycle (start/stop), client connections
- **WARN**: Queue drops, unusually slow fusion processing
- **ERROR**: Parse errors, socket errors, fatal failures

Errors are logged but don't stop processing. Invalid packets are silently dropped.

## Development

### Adding a New Radar Source

Edit `radar-config.properties`:
```properties
atc.radar.ports=15001,15002,15003,15004  # Add port
```

Then start a new simulator on port 15004. The ATC will automatically accept it.

### Adjusting Track Association

In `TrackFusionEngine.java`:
```java
private static final double ASSOCIATION_GATE_DISTANCE = 500.0;  // meters
private static final long TRACK_TTL = 5000;  // milliseconds
```

Lower gate = stricter association (more new tracks)
Higher TTL = tracks persist longer after dropout

### Tuning EMA Smoothing

In `TrackFusionEngine.updateTrack()`:
```java
double alpha = 0.5;  // Position blending (0.0-1.0)
double velocity_alpha = 0.3;  // Velocity smoothing
```

Higher alpha = more responsive to new measurements
Lower alpha = more smoothing (less jitter)

## Troubleshooting

### "Queue full" errors

Increase queue size in `FusionOrchestrator` constructor:
```java
new FusionOrchestrator(fusionEngine, stateStore, 5000)  // was 1000
```

Or reduce message rate from radar simulators.

### Slow fusion warnings

- Check CPU availability
- Reduce number of active tracks (decrease TTL or gate)
- Profile with JFR/JVM flags

### No broadcasts received

- Verify cockpit address in config
- Check network connectivity: `ping 127.0.0.1`
- Ensure broadcast service is running (check logs)

### Memory growing unbounded

- Check for stale tracks not being expired
- Verify TTL is working: `TRACK_TTL = 5000ms`
- Monitor GC: `jstat -gc <pid> 1000`

## Architecture Document

See [ARCHITECTURE.md](../ARCHITECTURE.md) for:
- Detailed layer descriptions
- Concurrency model
- Track fusion algorithm
- Data flow diagrams
- Performance characteristics
- Future extensions
