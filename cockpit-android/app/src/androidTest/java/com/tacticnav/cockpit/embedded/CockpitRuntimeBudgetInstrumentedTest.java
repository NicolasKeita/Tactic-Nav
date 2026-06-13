package com.tacticnav.cockpit.embedded;

import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.Debug;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.view.FrameMetrics;
import android.view.Window;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.tacticnav.cockpit.CockpitActivity;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RunWith(AndroidJUnit4.class)
public final class CockpitRuntimeBudgetInstrumentedTest {
    @Rule
    public ActivityScenarioRule<CockpitActivity> activityRule = new ActivityScenarioRule<>(CockpitActivity.class);

    private static final long BYTES_PER_MB = 1024L * 1024L;
    private static final long DEFAULT_WARMUP_MS = 30_000L;
    private static final long DEFAULT_DURATION_MS = 180_000L;
    private static final long DEFAULT_HEAP_MAX_MB = 45L;
    private static final long DEFAULT_HEAP_GROWTH_MB = 1L;
    private static final long DEFAULT_MIN_FPS = 30L;
    private static final long SAMPLE_INTERVAL_MS = 1_000L;
    private static final String PREFS_NAME = "cockpit_prefs";
    private static final String PREFS_CONFIGURED = "adsb_configured";

    @Test
    public void testCockpitRuntimeStaysWithinEmbeddedBudgets() throws Exception {
        Context targetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        targetContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREFS_CONFIGURED, true)
                .commit();

        long warmupMs = longArgument("warmupMs", DEFAULT_WARMUP_MS);
        long durationMs = longArgument("durationMs", DEFAULT_DURATION_MS);
        long heapMaxBytes = longArgument("heapMaxMb", DEFAULT_HEAP_MAX_MB) * BYTES_PER_MB;
        long heapGrowthBytes = longArgument("heapGrowthMb", DEFAULT_HEAP_GROWTH_MB) * BYTES_PER_MB;
        long minFps = longArgument("minFps", DEFAULT_MIN_FPS);

        HandlerThread metricsThread = new HandlerThread("cockpit-frame-metrics");
        metricsThread.start();
        Handler metricsHandler = new Handler(metricsThread.getLooper());
        FrameStats frameStats = new FrameStats(1_000_000_000L / Math.max(1L, minFps));
        Window.OnFrameMetricsAvailableListener listener = (window, frameMetrics, dropCount) -> {
            long durationNanos = frameMetrics.getMetric(FrameMetrics.TOTAL_DURATION);
            frameStats.record(durationNanos);
        };

        ActivityScenario<CockpitActivity> scenario = activityRule.getScenario();
        scenario.onActivity(activity -> activity.getWindow().addOnFrameMetricsAvailableListener(listener, metricsHandler));

        SystemClock.sleep(warmupMs);
        frameStats.reset();

        long baselineHeapBytes = usedJavaHeapBytes();
        long peakHeapBytes = baselineHeapBytes;
        long sampleStartMs = SystemClock.uptimeMillis();
        long sampleEndMs = sampleStartMs + durationMs;
        int samples = 0;

        while (SystemClock.uptimeMillis() < sampleEndMs) {
            SystemClock.sleep(SAMPLE_INTERVAL_MS);
            long usedHeapBytes = usedJavaHeapBytes();
            if (usedHeapBytes > peakHeapBytes) {
                peakHeapBytes = usedHeapBytes;
            }
            samples++;
        }

        long actualDurationMs = Math.max(1L, SystemClock.uptimeMillis() - sampleStartMs);
        scenario.onActivity(activity -> activity.getWindow().removeOnFrameMetricsAvailableListener(listener));
        metricsThread.quitSafely();

        double averageFps = frameStats.frames() * 1000.0 / actualDurationMs;
        long heapGrowthAfterWarmupBytes = peakHeapBytes - baselineHeapBytes;
        writeReport(
                targetContext,
                warmupMs,
                actualDurationMs,
                samples,
                baselineHeapBytes,
                peakHeapBytes,
                heapGrowthAfterWarmupBytes,
                frameStats,
                averageFps
        );

        assertTrue("Expected at least one rendered frame", frameStats.frames() > 0);
        // Frame timing budgets are measured by CockpitMacrobenchmark (androidx.benchmark.macro).
        System.out.println("Measured average FPS: " + averageFps);
        assertTrue("Peak Java heap over embedded budget: " + peakHeapBytes, peakHeapBytes <= heapMaxBytes);
        assertTrue("Post-warm-up Java heap growth over budget: " + heapGrowthAfterWarmupBytes,
                heapGrowthAfterWarmupBytes <= heapGrowthBytes);
    }

    private long longArgument(String key, long defaultValue) {
        Bundle arguments = InstrumentationRegistry.getArguments();
        String value = arguments == null ? null : arguments.getString(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return Long.parseLong(value);
    }

    private static long usedJavaHeapBytes() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    private static void writeReport(
            Context context,
            long warmupMs,
            long durationMs,
            int samples,
            long baselineHeapBytes,
            long peakHeapBytes,
            long heapGrowthAfterWarmupBytes,
            FrameStats frameStats,
            double averageFps
    ) throws IOException {
        Debug.MemoryInfo memoryInfo = new Debug.MemoryInfo();
        Debug.getMemoryInfo(memoryInfo);

        File report = new File(context.getFilesDir(), "cockpit-runtime-budget-report.json");
        String json = "{\n"
                + "  \"warmupMs\": " + warmupMs + ",\n"
                + "  \"durationMs\": " + durationMs + ",\n"
                + "  \"samples\": " + samples + ",\n"
                + "  \"baselineJavaHeapBytes\": " + baselineHeapBytes + ",\n"
                + "  \"peakJavaHeapBytes\": " + peakHeapBytes + ",\n"
                + "  \"heapGrowthAfterWarmupBytes\": " + heapGrowthAfterWarmupBytes + ",\n"
                + "  \"totalPssKb\": " + memoryInfo.getTotalPss() + ",\n"
                + "  \"frames\": " + frameStats.frames() + ",\n"
                + "  \"slowFrames\": " + frameStats.slowFrames() + ",\n"
                + "  \"maxFrameNanos\": " + frameStats.maxFrameNanos() + ",\n"
                + "  \"averageFps\": " + averageFps + "\n"
                + "}\n";

        try (FileOutputStream output = new FileOutputStream(report)) {
            output.write(json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static final class FrameStats {
        private final long frameBudgetNanos;
        private int frames;
        private int slowFrames;
        private long maxFrameNanos;

        private FrameStats(long frameBudgetNanos) {
            this.frameBudgetNanos = frameBudgetNanos;
        }

        private synchronized void record(long durationNanos) {
            if (durationNanos <= 0L) {
                return;
            }
            frames++;
            if (durationNanos > frameBudgetNanos) {
                slowFrames++;
            }
            if (durationNanos > maxFrameNanos) {
                maxFrameNanos = durationNanos;
            }
        }

        private synchronized void reset() {
            frames = 0;
            slowFrames = 0;
            maxFrameNanos = 0L;
        }

        private synchronized int frames() {
            return frames;
        }

        private synchronized int slowFrames() {
            return slowFrames;
        }

        private synchronized long maxFrameNanos() {
            return maxFrameNanos;
        }
    }
}
