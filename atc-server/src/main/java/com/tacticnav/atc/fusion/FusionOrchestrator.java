package com.tacticnav.atc.fusion;

import com.tacticnav.atc.domain.*;
import com.tacticnav.atc.state.SituationStateStore;
import java.util.*;
import java.util.concurrent.*;

/**
 * Orchestrates the complete fusion pipeline.
 * 
 * Pipeline:
 *   1. Receive RadarInputMessage from network listeners
 *   2. Apply fusion logic (associate to tracks, create new)
 *   3. Update state store with new snapshot
 *   4. Notify observers (broadcast layer)
 * 
 * Single-threaded design: processes messages sequentially.
 * Uses a bounded queue to decouple network listeners from fusion.
 * 
 * Backpressure strategy:
 *   - If the queue is full, drop the newest submitted message and count it
 *   - Fusion processes as fast as possible
 *   - Network listeners are unblocked (fire-and-forget enqueue)
 */
public class FusionOrchestrator implements Runnable {
    
    private final BlockingQueue<RadarInputMessage> messageQueue;
    private final TrackFusionEngine fusionEngine;
    private final SituationStateStore stateStore;
    private final List<FusionObserver> observers = new CopyOnWriteArrayList<>();
    
    private volatile boolean running = false;
    private volatile long processedMessages = 0;
    private volatile long droppedMessages = 0;
    
    // Working map owned by the single fusion thread.
    private Map<TrackId, Track> workingTracks = new HashMap<>();

    /**
     * Create the fusion orchestrator.
     * 
     * @param fusionEngine the track fusion logic
     * @param stateStore the situation snapshot holder
     * @param queueSize capacity of input queue
     */
    public FusionOrchestrator(
            TrackFusionEngine fusionEngine,
            SituationStateStore stateStore,
            int queueSize
    ) {
        this.fusionEngine = fusionEngine;
        this.stateStore = stateStore;
        this.messageQueue = new LinkedBlockingQueue<>(queueSize);
    }

    /**
     * Register an observer for fusion events.
     */
    public void addObserver(FusionObserver observer) {
        observers.add(observer);
    }

    /**
     * Remove an observer.
     */
    public void removeObserver(FusionObserver observer) {
        observers.remove(observer);
    }

    /**
     * Submit a radar message for processing.
     * Non-blocking; returns false if queue is full.
     * 
     * @return true if queued, false if dropped
     */
    public boolean submitMessage(RadarInputMessage message) {
        if (messageQueue.offer(message)) {
            return true;
        } else {
            droppedMessages++;
            System.err.printf("[FusionOrchestrator] Queue full, dropped message from radar %d%n", message.radarId());
            return false;
        }
    }

    /**
     * Main fusion loop.
     * Processes messages and updates state continuously.
     */
    @Override
    public void run() {
        running = true;
        System.out.println("[FusionOrchestrator] Started");

        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    RadarInputMessage message = messageQueue.poll(100, TimeUnit.MILLISECONDS);
                    
                    if (message == null) {
                        continue;
                    }

                    long startTime = System.nanoTime();
                    
                    SituationSnapshot snapshot = stateStore.getSnapshot();
                    workingTracks.clear();
                    workingTracks.putAll(snapshot.tracks());

                    TrackFusionEngine.FusionResult result = fusionEngine.fuse(
                        message,
                        workingTracks,
                        System.currentTimeMillis()
                    );

                    SituationSnapshot newSnapshot = stateStore.update(
                        result.tracks(),
                        snapshot.zones()
                    );

                    long elapsedNanos = System.nanoTime() - startTime;
                    long elapsedMs = elapsedNanos / 1_000_000;

                    for (TrackFusionEngine.FusionEvent event : result.events()) {
                        for (FusionObserver observer : observers) {
                            observer.onFusionEvent(event, newSnapshot);
                        }
                    }

                    processedMessages++;

                    if (elapsedMs > 30) {
                        System.out.printf("[FusionOrchestrator] Slow fusion: %dms%n", elapsedMs);
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.printf("[FusionOrchestrator] Error during fusion: %s%n", e.getMessage());
                    e.printStackTrace();
                }
            }
        } finally {
            running = false;
            System.out.printf("[FusionOrchestrator] Stopped (processed: %d, dropped: %d)%n", 
                processedMessages, droppedMessages);
        }
    }

    /**
     * Stop the fusion orchestrator.
     */
    public void stop() {
        running = false;
    }

    /**
     * Check if orchestrator is running.
     */
    public boolean isRunning() {
        return running;
    }

    /**
     * Get statistics.
     */
    public FusionStats getStats() {
        return new FusionStats(
            processedMessages,
            droppedMessages,
            messageQueue.size(),
            stateStore.getTrackCount(),
            stateStore.getZoneCount()
        );
    }

    /**
     * Statistics record.
     */
    public record FusionStats(
            long processedMessages,
            long droppedMessages,
            int queueSize,
            int activeTracksCount,
            int activeZonesCount
    ) {
        @Override
        public String toString() {
            return String.format(
                "FusionStats{processed=%d, dropped=%d, queue=%d, tracks=%d, zones=%d}",
                processedMessages, droppedMessages, queueSize, activeTracksCount, activeZonesCount
            );
        }
    }
}
