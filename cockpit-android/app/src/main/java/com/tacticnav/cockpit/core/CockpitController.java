package com.tacticnav.cockpit.core;

import com.tacticnav.cockpit.data.AtcTrackSource;
import com.tacticnav.cockpit.domain.TacticalSnapshot;
import com.tacticnav.cockpit.processing.SituationProcessor;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CockpitController implements AtcTrackSource.Listener {
    private final AtcTrackSource source;
    private final SituationProcessor processor;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private volatile ExecutorService worker;
    private volatile Listener listener;

    public CockpitController(AtcTrackSource source, SituationProcessor processor) {
        this.source = Objects.requireNonNull(source, "source cannot be null");
        this.processor = Objects.requireNonNull(processor, "processor cannot be null");
    }

    public void start(Listener listener) {
        this.listener = Objects.requireNonNull(listener, "listener cannot be null");
        if (!running.compareAndSet(false, true)) {
            return;
        }
        this.worker = Executors.newSingleThreadExecutor(new NamedThreadFactory("cockpit-situation-worker"));
        source.start(this);
    }

    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        source.stop();
        ExecutorService currentWorker = worker;
        worker = null;
        listener = null;
        if (currentWorker != null) {
            currentWorker.shutdownNow();
        }
    }

    @Override
    public void onSnapshot(TacticalSnapshot snapshot) {
        ExecutorService currentWorker = worker;
        Listener currentListener = listener;
        if (!running.get() || currentWorker == null || currentListener == null) {
            return;
        }
        currentWorker.execute(() -> {
            try {
                TacticalSnapshot processed = processor.process(snapshot);
                if (running.get()) {
                    currentListener.onSituation(processed);
                }
            } catch (RuntimeException error) {
                onSourceError(error);
            }
        });
    }

    @Override
    public void onSourceError(Throwable error) {
        Listener currentListener = listener;
        if (running.get() && currentListener != null) {
            currentListener.onError(error);
        }
    }

    public interface Listener {
        void onSituation(TacticalSnapshot snapshot);

        void onError(Throwable error);
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
