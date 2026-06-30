package com.tacticnav.atc.fusion;

import com.tacticnav.atc.domain.*;
import com.tacticnav.atc.metrics.ErrorMetrics;
import com.tacticnav.atc.state.SituationStateStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.*;
import java.util.concurrent.*;

/**
 * Orchestrates the complete track fusion pipeline.
 * 
 * Pipeline:
 *   1. Receive RadarInputMessage from network listeners
 *   2. Apply track fusion logic (associate to tracks, create new)
 *   3. Update state store with new snapshot
 * 
 * Single-threaded design: processes messages sequentially.
 * Uses a bounded queue to decouple network listeners from track fusion.
 * 
 * Backpressure strategy:
 *   - If the queue is full, drop the newest submitted message and count it
 *   - Track fusion processes as fast as possible
 *   - Network listeners are unblocked (fire-and-forget enqueue)
 */
public class TrackFusionOrchestrator implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(TrackFusionOrchestrator.class);
    
    // Polling timeout: longer timeout reduces CPU usage but increases latency
    private static final long POLL_TIMEOUT_MS = 500;  // Increased from 100ms to reduce CPU usage
    
    private final BlockingQueue<RadarInputMessage> messageQueue;
    private final TrackFusionEngine trackFusionEngine;
    private final SituationStateStore stateStore;
    private final ErrorMetrics errorMetrics;
    
    private volatile boolean running = false;
    private volatile long processedMessages = 0;
    private volatile long droppedMessages = 0;
    
    // Working map owned by the single track fusion thread.
    private Map<TrackId, Track> workingTracks = new HashMap<>();

    /**
     * Create the track fusion orchestrator.
     * 
     * @param trackFusionEngine the track fusion logic
     * @param stateStore the situation snapshot holder
     * @param queueSize capacity of input queue
     */
    public TrackFusionOrchestrator(
            TrackFusionEngine trackFusionEngine,
            SituationStateStore stateStore,
            int queueSize
    ) {
        this(trackFusionEngine, stateStore, queueSize, null);
    }
    
    /**
     * Create the track fusion orchestrator with error metrics.
     * 
     * @param trackFusionEngine the track fusion logic
     * @param stateStore the situation snapshot holder
     * @param queueSize capacity of input queue
     * @param errorMetrics the error metrics collector (can be null)
     */
    public TrackFusionOrchestrator(
            TrackFusionEngine trackFusionEngine,
            SituationStateStore stateStore,
            int queueSize,
            ErrorMetrics errorMetrics
    ) {
        this.trackFusionEngine = trackFusionEngine;
        this.stateStore = stateStore;
        this.messageQueue = new LinkedBlockingQueue<>(queueSize);
        this.errorMetrics = errorMetrics;
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
            if (errorMetrics != null) {
                errorMetrics.incrementQueueOverflow();
            }
            log.warn("Queue full, dropped observation track {} (total dropped: {}, queue overflow: {})",
                    message.trackId(),
                    droppedMessages,
                    errorMetrics != null ? errorMetrics.getQueueOverflowCount() : "N/A");
            return false;
        }
    }

    /**
     * Main track fusion loop.
     * Processes messages and updates state continuously.
     */
    @Override
    public void run() {
        running = true;
        log.info("Track fusion orchestrator started");

        try {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    RadarInputMessage message = messageQueue.poll(POLL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    
                    if (message == null) {
                        continue;
                    }

                    SituationSnapshot snapshot = stateStore.getSnapshot();
                    workingTracks.clear();
                    workingTracks.putAll(snapshot.tracks());

                    TrackFusionEngine.TrackFusionResult result = trackFusionEngine.fuse(
                        message,
                        workingTracks,
                        System.currentTimeMillis()
                    );

                    stateStore.update(
                        result.tracks(),
                        snapshot.zones()
                    );

                    processedMessages++;

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    if (errorMetrics != null) {
                        errorMetrics.incrementProcessingErrors();
                    }
                    log.error("Error during track fusion (total processing errors: {}): {}",
                            errorMetrics != null ? errorMetrics.getProcessingErrorCount() : "N/A",
                            e.getMessage(),
                            e);
                }
            }
        } finally {
            running = false;
            log.info("Track fusion orchestrator stopped (processed={}, dropped={})",
                processedMessages, droppedMessages);
        }
    }

    /**
     * Stop the track fusion orchestrator.
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
     * Get the error metrics for this orchestrator.
     * 
     * @return the error metrics, or null if not configured
     */
    public ErrorMetrics getErrorMetrics() {
        return errorMetrics;
    }

    /**
     * Get statistics.
     */
    public TrackFusionStats getStats() {
        return new TrackFusionStats(
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
    public record TrackFusionStats(
            long processedMessages,
            long droppedMessages,
            int queueSize,
            int activeTracksCount,
            int activeZonesCount
    ) {
        @Override
        public String toString() {
            return String.format(
                "TrackFusionStats{processed=%d, dropped=%d, queue=%d, tracks=%d, zones=%d}",
                processedMessages, droppedMessages, queueSize, activeTracksCount, activeZonesCount
            );
        }
    }
}
