package com.example.benchmark;

import android.Manifest;
import android.app.UiAutomation;
import android.content.Context;
import android.os.Build;

import androidx.test.platform.app.InstrumentationRegistry;

final class CockpitBenchmarkSetup {
    private static final String PREFS_NAME = "cockpit_prefs";
    private static final String PREFS_CONFIGURED = "adsb_configured";
    private static final String PACKAGE_NAME = "com.tacticnav.cockpit";
    private static final String GRANT_PERMISSION = "pm grant %s %s";

    private CockpitBenchmarkSetup() {}

    static void prepareLaunch() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(PREFS_CONFIGURED, true)
                .commit();

        grantPermissions();
    }

    private static void grantPermissions() {
        UiAutomation uiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        // POST_NOTIFICATIONS required on Android 13+ for apps targeting SDK 33+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            tryGrant(uiAutomation, Manifest.permission.POST_NOTIFICATIONS);
        }
    }

    private static void tryGrant(UiAutomation uiAutomation, String permission) {
        try {
            String cmd = String.format(GRANT_PERMISSION, PACKAGE_NAME, permission);
            uiAutomation.executeShellCommand(cmd);
        } catch (Exception ignored) {
            // Permission may not be declared in manifest — that's fine
        }
    }
}