# ATC Server - Quick Start Guide

## Building the Project

```bash
cd Tactic-Nav
.\mvnw.cmd clean package
```

This produces:
- `bin/atc-server-0.1.0-jar-with-dependencies.jar` - ATC server executable
- `bin/radar-simulator-0.1.0-jar-with-dependencies.jar` - Radar simulator executable

## Running the ATC Server

### 1. Start the ATC Server

```bash
java -jar bin/atc-server-0.1.0-jar-with-dependencies.jar
```

Expected output:
```
====== ATC SERVER STARTING ======
[RadarListener] Listening on 0.0.0.0:15001
[FusionOrchestrator] Started
====== ATC SERVER RUNNING ======
```

### 2. Start Radar Simulators

```bash
# One simulator
java -jar bin/radar-simulator-0.1.0-jar-with-dependencies.jar 1 127.0.0.1 15001

# Multiple simulators can send to the same ATC listen port.
java -jar bin/radar-simulator-0.1.0-jar-with-dependencies.jar 2 127.0.0.1 15001
java -jar bin/radar-simulator-0.1.0-jar-with-dependencies.jar 3 127.0.0.1 15001
```

### 3. Observe ATC Processing

The ATC server will:
- Receive simulated radar packets
- Fuse tracks from incoming observations
- Print statistics every 5 seconds:

```
[MONITOR] FusionStats{processed=234, dropped=0, queue=2, tracks=3, zones=0}
```

## Configuration

Edit `atc-server/src/main/resources/atc-config.properties`:

```properties
# ATC UDP listen socket
atc.bind.address=0.0.0.0
atc.listen.port=15001
```

## Architecture Overview

```
Radars (UDP) --> Listener --> Parser --> Fusion Engine --> State Store
                 0.0.0.0:15001
```

## Key Components

| Component | Role |
|-----------|------|
| **RadarListener** | Receives UDP packets on the ATC listen endpoint |
| **RadarPacketParser** | Validates and parses 28-byte binary packets |
| **TrackFusionEngine** | Associates observations to tracks (distance-based gating) |
| **FusionOrchestrator** | Coordinates pipeline with queue-based decoupling |
| **SituationStateStore** | Thread-safe holder using immutable snapshot publication |

## Performance Characteristics

**Note**: Strict constraints (< 30ms, < 45MB) apply to the **Cockpit Android client**, not the ATC server.

**ATC Server**:
- **Latency**: ~20ms typical per message (soft goal, no hard constraint)
- **Throughput**: 1000+ messages/second
- **Memory**: <100MB typical (no strict limit)
- **Tracks**: Supports thousands of concurrent tracks
- **Input**: Single UDP input stream; source discovery is outside the ATC configuration

## Monitoring

### Statistics Output (every 5 seconds)

```
[MONITOR] FusionStats{
  processed=1234,     # Total messages fused
  dropped=0,          # Dropped due to full queue
  queue=5,            # Current queue size
  tracks=12,          # Active tracks in situation
  zones=3             # Active no-fly zones
}
```

### Log Levels

- **INFO**: Component lifecycle (start/stop)
- **WARN**: Queue drops, unusually slow fusion processing
- **ERROR**: Parse errors, socket errors, fatal failures

Errors are logged but don't stop processing. Invalid packets are silently dropped.

## Development

### Adding Radar Senders

Send additional valid radar datagrams to `atc.bind.address:atc.listen.port`. The ATC does not pre-register radar senders or know their source ports. If real deployments need distinct sensor identity or per-sensor reference positions, that identity should be added to the radar protocol instead of being inferred from server configuration.

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

### Memory growing unbounded

- Check for stale tracks not being expired
- Verify TTL is working: `TRACK_TTL = 5000ms`
- Monitor GC: `jstat -gc <pid> 1000`

## Architecture Document

See [architecture.md](architecture.md) for:
- Detailed layer descriptions
- Concurrency model
- Track fusion algorithm
- Data flow diagrams
- Performance characteristics
- Future extensions
