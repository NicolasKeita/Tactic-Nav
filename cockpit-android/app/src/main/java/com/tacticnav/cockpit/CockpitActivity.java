package com.tacticnav.cockpit;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Choreographer;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.tacticnav.cockpit.core.CockpitController;
import com.tacticnav.cockpit.domain.TacticalSnapshot;
import com.tacticnav.cockpit.ui.TacticalDisplayView;

public final class CockpitActivity extends Activity {
    private static final long FRAME_INTERVAL_NANOS = 33_333_333L;

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
            mainHandler.post(() -> displayView.setDiagnostic(error.getClass().getSimpleName()));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        displayView = new TacticalDisplayView(this);
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
}
