package com.tacticnav.cockpit;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.tacticnav.cockpit.core.CockpitController;
import com.tacticnav.cockpit.domain.TacticalSnapshot;
import com.tacticnav.cockpit.ui.TacticalDisplayView;

public final class CockpitActivity extends Activity {
    private static final long FRAME_INTERVAL_NANOS = 33_333_333L;
    private static final String PREFS_NAME = "cockpit_prefs";
    private static final String PREFS_HOST = "adsb_host";
    private static final String PREFS_PORT = "adsb_port";
    private static final String PREFS_CONFIGURED = "adsb_configured";
    private static final String DEFAULT_HOST = "192.168.1.109";
    private static final String DEFAULT_PORT = "9876";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Choreographer.FrameCallback frameCallback = new Choreographer.FrameCallback() {
        private long lastFrameNanos;

        @Override
        public void doFrame(long frameTimeNanos) {
            if (!rendering) {
                return;
            }
            if (frameTimeNanos - lastFrameNanos >= FRAME_INTERVAL_NANOS) {
                lastFrameNanos = frameTimeNanos;
                displayView.invalidate();
            }
            Choreographer.getInstance().postFrameCallback(this);
        }
    };

    private TacticalDisplayView displayView;
    private CockpitController controller;
    private boolean rendering;

    private final CockpitController.Listener controllerListener = new CockpitController.Listener() {
        @Override
        public void onSituation(TacticalSnapshot snapshot) {
            mainHandler.post(() -> displayView.setSnapshot(snapshot));
        }

        @Override
        public void onError(Throwable error) {
            mainHandler.post(() -> {
                String msg = error.getClass().getSimpleName();
                String detail = error.getMessage();
                if (detail != null && !detail.isEmpty()) {
                    msg = msg + ": " + detail;
                }
                // Limiter la longueur pour affichage
                if (msg.length() > 80) {
                    msg = msg.substring(0, 77) + "...";
                }
                displayView.setDiagnostic(msg);
            });
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        displayView = new TacticalDisplayView(this);
        displayView.setOnSysSettingsListener(this::showSysSettingsDialog);
        controller = CockpitRuntimeFactory.create(this);
        setContentView(displayView);
        applyImmersiveMode();
    }

    @Override
    protected void onStart() {
        super.onStart();
        controller.start(controllerListener);
        rendering = true;
        Choreographer.getInstance().postFrameCallback(frameCallback);

        // Demander la configuration au premier lancement
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(PREFS_CONFIGURED, false)) {
            mainHandler.post(this::showSysSettingsDialog);
        }
    }

    @Override
    protected void onStop() {
        rendering = false;
        Choreographer.getInstance().removeFrameCallback(frameCallback);
        controller.stop();
        super.onStop();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
        }
    }

    private void applyImmersiveMode() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        );
    }

    void showSysSettingsDialog() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedHost = prefs.getString(PREFS_HOST, DEFAULT_HOST);
        String savedPort = prefs.getString(PREFS_PORT, DEFAULT_PORT);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 24);

        EditText hostInput = new EditText(this);
        hostInput.setHint("Adresse IP (ex: 192.168.1.109)");
        hostInput.setText(savedHost);
        layout.addView(hostInput);

        EditText portInput = new EditText(this);
        portInput.setHint("Port (ex: 9876)");
        portInput.setText(savedPort);
        portInput.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        layout.addView(portInput);

        new AlertDialog.Builder(this)
                .setTitle("Configuration ADS-B")
                .setMessage("Configurez la destination UDP pour l'envoi des données ADS-B")
                .setView(layout)
                .setPositiveButton("Démarrer", (dialog, which) -> {
                    String host = hostInput.getText().toString().trim();
                    String port = portInput.getText().toString().trim();
                    if (host.isEmpty()) host = DEFAULT_HOST;
                    if (port.isEmpty()) port = DEFAULT_PORT;

                    prefs.edit()
                            .putString(PREFS_HOST, host)
                            .putString(PREFS_PORT, port)
                            .putBoolean(PREFS_CONFIGURED, true)
                            .apply();

                    boolean wasRunning = rendering;
                    if (wasRunning) {
                        controller.stop();
                        rendering = false;
                    }

                    controller = CockpitRuntimeFactory.create(this, host, Integer.parseInt(port));

                    if (wasRunning) {
                        controller.start(controllerListener);
                        rendering = true;
                    }

                    displayView.setDiagnostic("ADS-B → " + host + ":" + port);
                })
                .setNegativeButton("Annuler", null)
                .show();
    }
}