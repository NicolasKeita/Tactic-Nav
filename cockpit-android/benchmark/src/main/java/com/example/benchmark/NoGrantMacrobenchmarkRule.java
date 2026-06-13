package com.example.benchmark;

import android.os.ParcelFileDescriptor;

import androidx.benchmark.macro.junit4.MacrobenchmarkRule;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

/**
 * Composition-based wrapper around {@link MacrobenchmarkRule} that pre-grants
 * POST_NOTIFICATIONS permission before the internal GrantPermissionRule runs.
 *
 * On API 33+ the benchmark library internally adds a {@code GrantPermissionRule}
 * for {@code POST_NOTIFICATIONS}. On Gradle managed device emulators this rule
 * can fail even when the permission is declared in the manifest. By pre-granting
 * the permission before the rule chain executes, the downstream
 * GrantPermissionRule sees it as already granted and passes.
 */
public final class NoGrantMacrobenchmarkRule implements TestRule {

    private static final String PACKAGE_NAME = "com.tacticnav.cockpit";
    private static final String POST_NOTIFICATIONS =
            "android.permission.POST_NOTIFICATIONS";

    private final MacrobenchmarkRule delegate = new MacrobenchmarkRule();

    @Override
    public Statement apply(Statement base, Description description) {
        preGrantPostNotifications();
        return delegate.apply(base, description);
    }

    /**
     * Returns the underlying {@link MacrobenchmarkRule} for use in test methods.
     * Call {@code benchmarkRule.getDelegate().measureRepeated(...)} from tests.
     */
    public MacrobenchmarkRule getDelegate() {
        return delegate;
    }

    private void preGrantPostNotifications() {
        ParcelFileDescriptor pfd = null;
        try {
            pfd = InstrumentationRegistry.getInstrumentation()
                    .getUiAutomation()
                    .executeShellCommand(
                            "pm grant " + PACKAGE_NAME + " " + POST_NOTIFICATIONS);
        } catch (Exception ignored) {
            // Permission may not be declared in manifest — safe to ignore
        } finally {
            if (pfd != null) {
                try {
                    pfd.close();
                } catch (Exception ignored) {
                }
            }
        }
    }
}