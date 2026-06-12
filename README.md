<div align="center">

# TACTIC-NAV
### Tactical Air Navigation & Air Traffic Control Center

**Language: English | [Français](README.fr.md)**

*Main objective: prevent aircraft collisions by providing a shared, real-time tactical situation.*

*Communication is simulated through the ADS-B protocol (Automatic Dependent Surveillance-Broadcast).*

---

![Java](https://img.shields.io/badge/Java-Core%20%2B%20Android%2013-ED8B00?style=flat-square&logo=openjdk&logoColor=white)
![Protocol](https://img.shields.io/badge/Protocol-UDP%20-0078D4?style=flat-square)
![RAM](https://img.shields.io/badge/Cockpit%20RAM-45%20MB%20Heap-2ea44f?style=flat-square)
![Latency](https://img.shields.io/badge/Cockpit%20compute-%3C%2030%20ms-blueviolet?style=flat-square)
![FPS](https://img.shields.io/badge/Rendering-30%20FPS%20min-red?style=flat-square)
![Offline](https://img.shields.io/badge/Map-100%25%20Offline-lightgrey?style=flat-square)

</div>

---

## 1. Project Context

As part of the modernization of military aircraft avionics systems, **TACTIC-NAV** is a prototype for a tactical display system and real-time cartographic data-flow management platform.

The system is based on **ADS-B** (*Automatic Dependent Surveillance-Broadcast*), a cooperative surveillance technology in which aircraft determine their position through satellite navigation (GPS) and periodically broadcast it omnidirectionally, without a targeted recipient, to nearby aircraft and ground stations.

To model this real-time ecosystem, the project architecture is split into three distinct entities:

- **Embedded Terminal (Aircraft A Cockpit)**: A native Java Android 13 application installed on a touchscreen tablet. It directly intercepts ADS-B frames broadcast in Air-Air mode by nearby aircraft (Aircraft B, C, D) to instantly display an anti-collision tactical situation. To guarantee full critical autonomy, the map background and No-Fly Zone boundaries are stored 100% locally and offline, removing any dependency on ATC.
- **Ground Stations (Receivers)**, implemented in the project as an independent program: Geographically distributed antennas capture the Air-Ground broadcast segment of ADS-B frames emitted by air traffic and continuously forward them to the control center through UDP.
- **Central Processing System (ATC)**: A pure Java Core server intended for ground operators. It aggregates data streams from multiple connected ground stations to provide a centralized supervision and air-traffic control console.

> **The military-context paradox:**
> In real life, military aircraft disable ADS-B during operational missions because the signal is public, unencrypted, and easy to spoof. A military aircraft on mission turns off ADS-B to remain stealthy and instead uses a **Tactical Data Link**, such as *Link 16*, which is secure, encrypted, and resistant to jamming for Air-Air and Air-Ground exchanges. ADS-B is used in this project as a civil-military technology demonstrator to validate surveillance-data aggregation and dynamic real-time mapping.

> **Why UDP and radio emulation:**
> In the real world, ADS-B uses neither UDP nor TCP in the sky, because there is no Internet or IP network between aircraft. Data travels through pure radio waves on the 1090 MHz frequency. In this software simulation, **UDP** is a good analogy for radio physics. Like a radio signal, UDP follows a *Fire and Forget* model: the aircraft broadcasts packets without caring who is listening and without waiting for acknowledgements. If a packet is lost, no retransmission is attempted, unlike TCP, because a delayed geographic position is obsolete and potentially dangerous. The system simply waits for the next frame, preserving the smoothness and very low latency required by critical systems.

### System Overview

<table width="100%">
  <tr>
    <td width="33%" align="center"><b>1. Embedded Terminal (Cockpit)</b></td>
    <td width="33%" align="center"><b>2. Data-Flow Architecture</b></td>
    <td width="33%" align="center"><b>3. Control Center (ATC)</b></td>
  </tr>

  <tr>
    <td>
      <img src="documentation/images/Android_app.png" width="100%">
    </td>
    <td>
      <img src="documentation/images/schema_architecture.png" width="100%">
    </td>
    <td>
      <img src="documentation/images/backlog_backend.png" width="100%">
    </td>
  </tr>

  <tr>
    <td><small><i>Native Android application using the <b>Mapsforge</b> engine for smooth, 100% offline tactical rendering.</i></small></td>
    <td><small><i>Synoptic data pipeline: direct Air-Air ADS-B broadcast to the tablet and Air-Ground forwarding through receivers to the ATC.</i></small></td>
    <td><small><i>Java Core console in action: real-time logs showing stream aggregation and traffic supervision.</i></small></td>
  </tr>
</table>

---

## 2. Technical Architecture & Network Diagram

The system relies on two independent components communicating inside a simulated private network through UDP data streams:

- **Central Processing System (ATC)**: A Java Core application developed without a heavyweight framework, such as Spring or Quarkus. It listens in parallel to streams from several ground receivers, aggregates received aircraft coordinates, and maintains global traffic tracking.
- **Embedded Terminal (Cockpit)**: A native Android application written in pure Java. It is designed for cockpit touchscreen tablets, directly intercepting surrounding traffic and graphically rendering the anti-collision tactical environment with full autonomy.

### Network Flow Diagram

```text
[ Aircraft B ] --(ADS-B Out/UDP)--> [ COCKPIT (Aircraft A): Android Terminal ]
[ Aircraft C ] --(ADS-B Out/UDP)---^          |
[ Aircraft D ] --(ADS-B Out/UDP)---|          | (Local anti-collision computation)
                                              v
                         Offline GIS Engine & Local No-Fly Zones

                         (Air-Ground broadcast)
[ Multiple Ground Receivers ] --(UDP)--> [ ATC: Traffic Supervision ]
                                           |
                                           v
                              Multi-threaded listeners
                              Global traffic tracking
```

## 3. Critical Embedded Constraints & Safety

These constraints primarily apply to the **Embedded Terminal (Cockpit)**, which must remain smooth on an Android tablet with an offline map and real-time rendering. The **ATC server**, running on a Java Core ground workstation, prioritizes clear layer separation, consistent snapshots, and a controlled UDP backpressure strategy.

### Cockpit: memory control and smoothness

The cockpit limits allocations inside rendering and geospatial-computation loops to avoid visible pauses caused by the *Garbage Collector*. Reusable structures are relevant for display, mapping, and embedded diagnostics.

### ATC: server robustness

The ATC rejects invalid ADS-B packets, maintains a coherent track model despite UDP's unordered delivery, and processes traffic snapshots without blocking the aggregation engine. Network and parsing errors are isolated and logged.

### Performance Indicators (KPIs)

| Metric | Threshold |
|---|---|
| **Startup time** | Application ready, map and No-Fly Zones loaded in **< 1.2 seconds** |
| **Cockpit RAM footprint** | Stabilized consumption below **45 MB heap** with a flat curve |
| **Cockpit latency** | Full processing of a received message, geospatial computation, and rendering in **< 30 milliseconds** |
| **ATC** | Deterministic aggregation, consistent snapshots, and non-blocking UDP processing |

---

## 4. Map Engine Architecture and Abstraction

To ensure compatibility with defense cartography standards, such as the industrial **Luciad** software suite, the project implements a highly decoupled architecture based on **Dependency Inversion** (SOLID principles):

- The Android application interacts exclusively with an abstraction interface named `TacticalMapEngine`.
- For this public demonstrator, the interface is implemented with the open-source **Mapsforge** or **Osmdroid** library, configured to locally read a pre-downloaded `.map` file for the Nouvelle-Aquitaine region.
- This modularity makes it possible to switch to any other proprietary map SDK through dependency injection, without modifying the underlying business logic.

---

## 5. Functional Specifications & Multithreading

The system handles data-flow processing asynchronously.

### Concurrent Collection (Ground ATC)

The Central Processing System dedicates one thread to each stream coming from ground receivers, allowing it to listen in parallel and intercept positioning data without interference.

### Display Pipeline (Cockpit)

| Thread | Role |
|---|---|
| **Network Thread** | Continuously intercepts ADS-B packets coming directly from aircraft within range. |
| **Worker Thread** | Decodes frames and computes geospatial intersections, using a *Point-in-Polygon* algorithm based on the **Haversine** formula, against locally stored No-Fly Zones to detect whether an aircraft enters a forbidden area. |
| **UI Thread** | Consumes ready-to-display data to update the map while maintaining a smooth refresh rate of at least **30 FPS**. |

---

## 6. Automated "Anti-Crash" Test Strategy

Stability and operational safety are validated through a demanding automated test suite.

### Network Data Fuzzing

A stress test continuously injects corrupted, truncated, or out-of-range data through concurrent streams. The system must reject these anomalies cleanly without propagating `NullPointerException`, `ArrayIndexOutOfBoundsException`, or fatal network errors into application processing.

### Robustness to Stream Interruptions

The test suite simulates total and intermittent signal loss. The UI must keep the map frozen on the last known stable state, without blocking the main interface thread.

---

## 7. Final Validation: "Operation MISTRAL"

This protocol validates the system behavior under realistic operating conditions through the demonstration scenario called **"Operation MISTRAL"**.

### Environment Configuration

**Demonstration architecture**

**Aircraft simulators**

- Periodically emit ADS-B frames through UDP broadcast.
- Represent aircraft present in the simulated airspace.

**Android tablet (Cockpit)**

- Directly receives simulated ADS-B transmissions.
- Displays traffic information on board the aircraft.
- Operates autonomously without depending on the ATC system.

**Ground receiving stations**

- Capture the same ADS-B transmissions as the Android tablet.
- Represent deployed ground-surveillance receivers.

**Central ATC system**

- Aggregates data from the receiving stations.
- Maintains aircraft track monitoring.
- Provides a global view of simulated air traffic.

```text
[Simulated aircraft]
        |
        | ADS-B (UDP)
        |
        +------------------> [Android Cockpit]
        |
        +--> [Ground Station 1] --\
        +--> [Ground Station 2] ----> [Central ATC]
        +--> [Ground Station 3] --/
```

<details>
<summary><b>Phase 1 - Cold Start</b>: Speed and Autonomy</summary>

| Item | Description |
|---|---|
| **Action** | Launch the application on a disconnected tablet. |
| **Result** | Immediate visual rendering (< 1s) of the Nouvelle-Aquitaine topography centered on the Mont-de-Marsan aeronautical area, including No-Fly Zone outlines, with 100% autonomous operation. |
| **Validation** | Autonomous GIS loading, local exclusion-zone reading, and startup speed are validated. |

</details>

<details>
<summary><b>Phase 2 - ADS-B Flow Activation</b>: Concurrency</summary>

| Item | Description |
|---|---|
| **Action** | Start the flight simulation for surrounding aircraft. Continuously emit ADS-B trajectories for **50 moving targets**. |
| **Result** | The 50 aircraft appear and move smoothly and asynchronously on the tablet map through the Air-Air feed, while the ATC displays the same global situation through ground receivers. |
| **Validation** | Asynchronous network reception through a dedicated thread and direct broadcast decoding operate without conflict. |

</details>

<details>
<summary><b>Phase 3 - Stress Interaction</b>: Zero Allocation and Smoothness</summary>

| Item | Description |
|---|---|
| **Action** | Intensively and rapidly interact with the touch UI through continuous zooming and map panning. |
| **Result** | Rendering remains perfectly responsive at a constant 30 FPS with no stutter. The performance profiler shows a flat memory curve locked below 45 MB. |
| **Validation** | The effectiveness of the `ObjectPool` recycling system is demonstrated. The *Garbage Collector* never interrupts the application. |

</details>

<details>
<summary><b>Phase 4 - Geospatial Intrusion Alert</b>: GIS Computation</summary>

| Item | Description |
|---|---|
| **Action** | The scenario brings an ADS-B track identified as suspicious (red) across the boundary of the locally stored No-Fly Zone. |
| **Result** | At the exact millisecond of the boundary crossing, the aircraft icon flashes intensely and a security alert appears instantly (< 30 ms) on the cockpit screen thanks to local embedded computation. |
| **Validation** | Low-latency projection and geospatial-intersection algorithms running autonomously in the cockpit are validated. |

</details>

<details>
<summary><b>Phase 5 - Resilience and Autonomy</b>: Safety Robustness</summary>

| Item | Description |
|---|---|
| **Action** | Abruptly shut down the Central ATC Processing System or trigger a ground-reception segment failure during operations. |
| **Result** | The Android cockpit application is not impacted, does not crash, and continues refreshing nearby aircraft positions in real time through the direct Air-Air ADS-B link. |
| **Validation** | The cockpit's critical independence from ATC for vital anti-collision functions is confirmed. |

</details>
