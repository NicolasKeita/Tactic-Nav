package com.tacticnav.cockpit.data;

import com.tacticnav.cockpit.time.Clock;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public final class SimulatedAtcTrackSource implements AtcTrackSource {
    private static final long PERIOD_MILLIS = 250L;

    private final Clock clock;
    private final SimulatedTrackGenerator generator;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong sequence = new AtomicLong(1L);

    private ScheduledExecutorService executor;

    public SimulatedAtcTrackSource(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock cannot be null");
        }
        this.clock = clock;
        this.generator = new SimulatedTrackGenerator(clock.nowMillis());
    }

    @Override
    public void start(Listener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener cannot be null");
        }
        if (!running.compareAndSet(false, true)) {
            return;
        }

        executor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("cockpit-sim-source"));
        executor.scheduleAtFixedRate(
                () -> emitSnapshot(listener),
                0L,
                PERIOD_MILLIS,
                TimeUnit.MILLISECONDS
        );
    }

    @Override
    public void stop() {
        running.set(false);
        ScheduledExecutorService current = executor;
        executor = null;
        if (current != null) {
            current.shutdownNow();
        }
    }

    private void emitSnapshot(Listener listener) {
        if (!running.get()) {
            return;
        }
        try {
            long now = clock.nowMillis();
            listener.onSnapshot(generator.snapshotAt(now, sequence.getAndIncrement()));
        } catch (RuntimeException error) {
            listener.onSourceError(error);
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String name;

        private NamedThreadFactory(String name) {
            this.name = name;
        }

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, name);
            thread.setDaemon(true);
            return thread;
        }
    }
}
