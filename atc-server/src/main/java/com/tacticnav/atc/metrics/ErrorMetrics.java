package com.tacticnav.atc.metrics;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Thread-safe metrics collector for tracking errors and other metrics.
 * Uses atomic counters for thread-safe increment operations.
 */
public final class ErrorMetrics {
    
    private final Map<String, AtomicLong> counters = new ConcurrentHashMap<>();
    
    // Predefined error counter keys for consistency
    public static final String PARSE_ERRORS = "parseErrors";
    public static final String PROCESSING_ERRORS = "processingErrors";
    public static final String NETWORK_ERRORS = "networkErrors";
    public static final String QUEUE_OVERFLOW = "queueOverflow";
    
    /**
     * Create a new ErrorMetrics instance.
     */
    public ErrorMetrics() {
        // Initialize predefined counters
        counters.put(PARSE_ERRORS, new AtomicLong(0));
        counters.put(PROCESSING_ERRORS, new AtomicLong(0));
        counters.put(NETWORK_ERRORS, new AtomicLong(0));
        counters.put(QUEUE_OVERFLOW, new AtomicLong(0));
    }
    
    /**
     * Increment a predefined error counter.
     * 
     * @param counterName name of the counter to increment
     * @throws IllegalArgumentException if counterName is null or empty
     */
    public void increment(String counterName) {
        if (counterName == null || counterName.isEmpty()) {
            throw new IllegalArgumentException("counterName cannot be null or empty");
        }
        counters.computeIfAbsent(counterName, k -> new AtomicLong(0)).incrementAndGet();
    }
    
    /**
     * Increment a predefined error counter by a specific amount.
     * 
     * @param counterName name of the counter to increment
     * @param amount amount to add to the counter
     */
    public void increment(String counterName, long amount) {
        if (counterName == null || counterName.isEmpty()) {
            throw new IllegalArgumentException("counterName cannot be null or empty");
        }
        counters.computeIfAbsent(counterName, k -> new AtomicLong(0)).addAndGet(amount);
    }
    
    /**
     * Get the value of a specific counter.
     * 
     * @param counterName name of the counter
     * @return current value of the counter, or 0 if counter doesn't exist
     */
    public long getCounterValue(String counterName) {
        if (counterName == null || counterName.isEmpty()) {
            throw new IllegalArgumentException("counterName cannot be null or empty");
        }
        AtomicLong counter = counters.get(counterName);
        return counter != null ? counter.get() : 0;
    }
    
    /**
     * Get the parse error count.
     * 
     * @return number of parse errors
     */
    public long getParseErrorCount() {
        return getCounterValue(PARSE_ERRORS);
    }
    
    /**
     * Get the processing error count.
     * 
     * @return number of processing errors
     */
    public long getProcessingErrorCount() {
        return getCounterValue(PROCESSING_ERRORS);
    }
    
    /**
     * Get the network error count.
     * 
     * @return number of network errors
     */
    public long getNetworkErrorCount() {
        return getCounterValue(NETWORK_ERRORS);
    }
    
    /**
     * Get the queue overflow count.
     * 
     * @return number of queue overflow events
     */
    public long getQueueOverflowCount() {
        return getCounterValue(QUEUE_OVERFLOW);
    }
    
    /**
     * Convenience method to increment parse error counter.
     */
    public void incrementParseErrors() {
        increment(PARSE_ERRORS);
    }
    
    /**
     * Convenience method to increment processing error counter.
     */
    public void incrementProcessingErrors() {
        increment(PROCESSING_ERRORS);
    }
    
    /**
     * Convenience method to increment network error counter.
     */
    public void incrementNetworkErrors() {
        increment(NETWORK_ERRORS);
    }
    
    /**
     * Convenience method to increment queue overflow counter.
     */
    public void incrementQueueOverflow() {
        increment(QUEUE_OVERFLOW);
    }
    
    /**
     * Get a snapshot of all metrics as a map.
     * 
     * @return unmodifiable map of all counter names to their values
     */
    public Map<String, Long> getMetrics() {
        return counters.entrySet().stream()
                .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        e -> e.getValue().get()
                ));
    }
    
    /**
     * Reset all counters to zero.
     */
    public void reset() {
        counters.values().forEach(counter -> counter.set(0));
    }
    
    /**
     * Reset a specific counter to zero.
     * 
     * @param counterName name of the counter to reset
     */
    public void reset(String counterName) {
        if (counterName == null || counterName.isEmpty()) {
            throw new IllegalArgumentException("counterName cannot be null or empty");
        }
        AtomicLong counter = counters.get(counterName);
        if (counter != null) {
            counter.set(0);
        }
    }
    
    @Override
    public String toString() {
        return String.format(
            "ErrorMetrics{parseErrors=%d, processingErrors=%d, networkErrors=%d, queueOverflow=%d}",
            getParseErrorCount(),
            getProcessingErrorCount(),
            getNetworkErrorCount(),
            getQueueOverflowCount()
        );
    }
}
