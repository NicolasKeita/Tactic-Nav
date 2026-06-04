package com.tacticnav.cockpit.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;

import com.tacticnav.cockpit.domain.LinkStatus;
import com.tacticnav.cockpit.domain.TacticalAlert;
import com.tacticnav.cockpit.domain.TacticalSnapshot;
import com.tacticnav.cockpit.domain.TacticalTrack;
import com.tacticnav.cockpit.render.CanvasTacticalMapEngine;
import com.tacticnav.cockpit.render.ColorPalette;
import com.tacticnav.cockpit.render.TacticalMapEngine;
import com.tacticnav.cockpit.render.TacticalProjection;

import java.util.Locale;

public final class TacticalDisplayView extends View {
    private static final double VIEWPORT_CENTER_LAT = 43.8915;
    private static final double VIEWPORT_CENTER_LON = -0.5007;
    private static final double VIEWPORT_LAT_SPAN = 0.245;
    private static final double VIEWPORT_LON_SPAN = 0.360;

    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF topBar = new RectF();
    private final RectF leftRail = new RectF();
    private final RectF rightPanel = new RectF();
    private final RectF mapBounds = new RectF();
    private final RectF scratch = new RectF();
    private final Path iconPath = new Path();
    private final TacticalProjection projection = new TacticalProjection();
    private final TacticalMapEngine mapEngine;

    private TacticalSnapshot snapshot;
    private String diagnostic = "";
    private OnSysSettingsListener sysSettingsListener;

    public interface OnSysSettingsListener {
        void onSysSettingsRequested();
    }

    public TacticalDisplayView(Context context) {
        super(context);
        density = context.getResources().getDisplayMetrics().density;
        mapEngine = new CanvasTacticalMapEngine(density);
        snapshot = TacticalSnapshot.empty(java.lang.System.currentTimeMillis());
        setFocusable(true);
        setWillNotDraw(false);
    }

    public void setOnSysSettingsListener(OnSysSettingsListener listener) {
        this.sysSettingsListener = listener;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            float x = event.getX();
            float y = event.getY();
            if (isSysButtonHit(x, y)) {
                if (sysSettingsListener != null) {
                    sysSettingsListener.onSysSettingsRequested();
                }
                return true;
            }
        }
        return super.onTouchEvent(event);
    }

    private boolean isSysButtonHit(float x, float y) {
        layoutRegions(getWidth(), getHeight());
        float itemHeight = dp(72.0f);
        int sysIndex = 4;
        float top = leftRail.top + sysIndex * itemHeight;
        return x >= leftRail.left && x <= leftRail.right
                && y >= top && y <= top + itemHeight;
    }

    public void setSnapshot(TacticalSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("snapshot cannot be null");
        }
        this.snapshot = snapshot;
        invalidate();
    }

    public void setDiagnostic(String diagnostic) {
        this.diagnostic = diagnostic == null ? "" : diagnostic;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        layoutRegions(getWidth(), getHeight());

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorPalette.BACKGROUND);
        canvas.drawRect(0, 0, getWidth(), getHeight(), paint);

        projection.setViewport(
                mapBounds,
                VIEWPORT_CENTER_LAT,
                VIEWPORT_CENTER_LON,
                VIEWPORT_LAT_SPAN,
                VIEWPORT_LON_SPAN
        );
        mapEngine.draw(canvas, snapshot, projection);

        drawTopHud(canvas);
        drawLeftRail(canvas);
        drawRightPanel(canvas);
        drawAlert(canvas, snapshot.alert());
        drawBottomStatus(canvas);
    }

    private void layoutRegions(int width, int height) {
        float top = dp(66.0f);
        float left = dp(76.0f);
        float right = dp(136.0f);
        float bottom = dp(30.0f);

        topBar.set(0.0f, 0.0f, width, top);
        leftRail.set(0.0f, top, left, height - bottom);
        rightPanel.set(width - right, top + dp(26.0f), width, height - bottom);
        mapBounds.set(left, top, width - right, height - bottom);
    }

    private void drawTopHud(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorPalette.PANEL);
        canvas.drawRect(topBar, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.0f));
        paint.setColor(ColorPalette.GRID);
        canvas.drawLine(topBar.left, topBar.bottom, topBar.right, topBar.bottom, paint);

        TacticalTrack primary = snapshot.tracks().isEmpty() ? null : snapshot.tracks().get(0);
        drawHudCell(canvas, 0, "GS", primary == null ? "---" : String.format(Locale.US, "%.0f", primary.groundSpeedKt()), "KT");
        drawHudCell(canvas, 1, "HDG", primary == null ? "---" : String.format(Locale.US, "%03.0f", primary.headingDeg()), "M");
        drawCompass(canvas);
        drawHudCell(canvas, 4, "ALT", primary == null ? "---" : String.format(Locale.US, "%,d", primary.altitudeFt()), "FT");
        drawHudCell(canvas, 5, "VS", primary == null ? "---" : String.format(Locale.US, "%+.0f", primary.verticalSpeedFpm()), "FT/MIN");
    }

    private void drawHudCell(Canvas canvas, int index, String label, String value, String unit) {
        float cellWidth = topBar.width() / 6.0f;
        scratch.set(index * cellWidth, topBar.top, (index + 1) * cellWidth, topBar.bottom);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.0f));
        paint.setColor(ColorPalette.GRID);
        canvas.drawRect(scratch, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(sp(10.0f));
        paint.setColor(ColorPalette.TEXT_MUTED);
        canvas.drawText(label, scratch.left + dp(14.0f), scratch.top + dp(21.0f), paint);
        paint.setTextSize(sp(21.0f));
        paint.setColor(index >= 4 ? ColorPalette.FRIENDLY : ColorPalette.TEXT_PRIMARY);
        canvas.drawText(value, scratch.left + dp(14.0f), scratch.top + dp(48.0f), paint);
        paint.setTextSize(sp(9.0f));
        paint.setColor(ColorPalette.TEXT_MUTED);
        canvas.drawText(unit, scratch.left + dp(76.0f), scratch.top + dp(47.0f), paint);
    }

    private void drawCompass(Canvas canvas) {
        float left = topBar.width() / 3.0f;
        float right = topBar.width() * 2.0f / 3.0f;
        float centerX = (left + right) / 2.0f;
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(sp(11.0f));
        paint.setColor(ColorPalette.TEXT_MUTED);
        canvas.drawText("SW", left + dp(40.0f), dp(30.0f), paint);
        canvas.drawText("240", left + dp(96.0f), dp(30.0f), paint);
        canvas.drawText("W", centerX, dp(30.0f), paint);
        canvas.drawText("300", right - dp(96.0f), dp(30.0f), paint);
        canvas.drawText("NW", right - dp(40.0f), dp(30.0f), paint);

        paint.setColor(ColorPalette.TEXT_PRIMARY);
        paint.setTextSize(sp(24.0f));
        canvas.drawText("268", centerX, dp(49.0f), paint);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorPalette.FRIENDLY);
        iconPath.reset();
        iconPath.moveTo(centerX, dp(7.0f));
        iconPath.lineTo(centerX - dp(8.0f), dp(20.0f));
        iconPath.lineTo(centerX + dp(8.0f), dp(20.0f));
        iconPath.close();
        canvas.drawPath(iconPath, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.0f));
        paint.setColor(ColorPalette.GRID);
        for (int i = 0; i <= 24; i++) {
            float x = left + (right - left) * i / 24.0f;
            float top = dp(i % 3 == 0 ? 35.0f : 40.0f);
            canvas.drawLine(x, top, x, dp(46.0f), paint);
        }
    }

    private void drawLeftRail(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorPalette.PANEL);
        canvas.drawRect(leftRail, paint);
        drawRailItem(canvas, 0, "MAP", true);
        drawRailItem(canvas, 1, "TRK", false);
        drawRailItem(canvas, 2, "WPT", false);
        drawRailItem(canvas, 3, "RTE", false);
        drawRailItem(canvas, 4, "SYS", false);
    }

    private void drawRailItem(Canvas canvas, int index, String label, boolean selected) {
        float itemHeight = dp(72.0f);
        float top = leftRail.top + index * itemHeight;
        scratch.set(leftRail.left, top, leftRail.right, top + itemHeight);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.0f));
        paint.setColor(ColorPalette.GRID);
        canvas.drawRect(scratch, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setColor(selected ? ColorPalette.FRIENDLY : ColorPalette.TEXT_PRIMARY);
        drawRailIcon(canvas, scratch, selected);
        paint.setTextSize(sp(10.0f));
        paint.setColor(selected ? ColorPalette.FRIENDLY : ColorPalette.TEXT_MUTED);
        canvas.drawText(label, scratch.centerX(), scratch.top + dp(54.0f), paint);
    }

    private void drawRailIcon(Canvas canvas, RectF bounds, boolean selected) {
        float centerX = bounds.centerX();
        float centerY = bounds.top + dp(24.0f);
        iconPath.reset();
        if (selected) {
            iconPath.addRect(centerX - dp(9.0f), centerY - dp(9.0f), centerX + dp(9.0f), centerY + dp(9.0f), Path.Direction.CW);
        } else {
            iconPath.moveTo(centerX, centerY - dp(11.0f));
            iconPath.lineTo(centerX - dp(10.0f), centerY + dp(10.0f));
            iconPath.lineTo(centerX + dp(10.0f), centerY + dp(10.0f));
            iconPath.close();
        }
        canvas.drawPath(iconPath, paint);
    }

    private void drawRightPanel(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorPalette.PANEL);
        canvas.drawRect(rightPanel, paint);

        drawRightMetric(canvas, 0, "LINK", labelFor(snapshot.linkStatus()), colorFor(snapshot.linkStatus()));
        drawRightMetric(canvas, 1, "TRACKS", Integer.toString(snapshot.trackCount()), ColorPalette.FRIENDLY);
        drawRightMetric(canvas, 2, "ALERTS", Integer.toString(snapshot.alertCount()), snapshot.alertCount() > 0 ? ColorPalette.CRITICAL : ColorPalette.FRIENDLY);
        drawRightMetric(canvas, 3, "SEQ", Long.toString(snapshot.sequenceNumber()), ColorPalette.TEXT_PRIMARY);
        drawRightMetric(canvas, 4, "MODE", snapshot.linkStatus() == LinkStatus.SIMULATED ? "SIM" : "UDP", ColorPalette.BLUE);
    }

    private void drawRightMetric(Canvas canvas, int index, String label, String value, int valueColor) {
        float itemHeight = rightPanel.height() / 5.0f;
        float top = rightPanel.top + index * itemHeight;
        scratch.set(rightPanel.left, top, rightPanel.right, top + itemHeight);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.0f));
        paint.setColor(ColorPalette.GRID);
        canvas.drawRect(scratch, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(sp(10.0f));
        paint.setColor(ColorPalette.TEXT_MUTED);
        canvas.drawText(label, scratch.left + dp(16.0f), scratch.top + dp(24.0f), paint);
        paint.setTextSize(sp(18.0f));
        paint.setColor(valueColor);
        canvas.drawText(value, scratch.left + dp(16.0f), scratch.top + dp(52.0f), paint);
    }

    private void drawAlert(Canvas canvas, TacticalAlert alert) {
        if (alert == null) {
            return;
        }
        float pulse = (float) (0.55 + 0.45 * Math.abs(Math.sin(SystemClock.uptimeMillis() / 150.0)));
        scratch.set(
                mapBounds.centerX() - dp(130.0f),
                mapBounds.centerY() - dp(58.0f),
                mapBounds.centerX() + dp(130.0f),
                mapBounds.centerY() + dp(40.0f)
        );
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorPalette.PANEL_DARK);
        canvas.drawRect(scratch, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.0f));
        paint.setColor(adjustAlpha(ColorPalette.CRITICAL, pulse));
        canvas.drawRect(scratch, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setColor(ColorPalette.CRITICAL);
        paint.setTextSize(sp(18.0f));
        canvas.drawText("ALERT", scratch.left + dp(50.0f), scratch.top + dp(30.0f), paint);
        paint.setTextSize(sp(10.0f));
        paint.setColor(ColorPalette.TEXT_PRIMARY);
        canvas.drawText(alert.trackId(), scratch.left + dp(50.0f), scratch.top + dp(54.0f), paint);
        paint.setColor(ColorPalette.CRITICAL);
        canvas.drawText(alert.zoneId(), scratch.left + dp(50.0f), scratch.top + dp(72.0f), paint);
        paint.setColor(ColorPalette.WARNING);
        canvas.drawText(alert.message(), scratch.left + dp(50.0f), scratch.top + dp(92.0f), paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.0f));
        paint.setColor(ColorPalette.CRITICAL);
        canvas.drawCircle(scratch.left + dp(24.0f), scratch.top + dp(26.0f), dp(10.0f), paint);
        canvas.drawLine(scratch.left + dp(24.0f), scratch.top + dp(12.0f), scratch.left + dp(24.0f), scratch.top + dp(40.0f), paint);
        canvas.drawLine(scratch.left + dp(10.0f), scratch.top + dp(26.0f), scratch.left + dp(38.0f), scratch.top + dp(26.0f), paint);
    }

    private void drawBottomStatus(Canvas canvas) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorPalette.PANEL);
        canvas.drawRect(0.0f, mapBounds.bottom, getWidth(), getHeight(), paint);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(sp(10.0f));
        paint.setColor(ColorPalette.TEXT_MUTED);
        canvas.drawText("GRID", dp(16.0f), getHeight() - dp(16.0f), paint);
        paint.setColor(ColorPalette.FRIENDLY);
        canvas.drawText("43N 000W", dp(58.0f), getHeight() - dp(16.0f), paint);
        if (!diagnostic.isEmpty()) {
            paint.setColor(ColorPalette.WARNING);
            canvas.drawText(diagnostic, dp(160.0f), getHeight() - dp(16.0f), paint);
        }
    }

    private String labelFor(LinkStatus status) {
        if (status == LinkStatus.SIMULATED) {
            return "SIM";
        }
        if (status == LinkStatus.LIVE) {
            return "LIVE";
        }
        if (status == LinkStatus.DEGRADED) {
            return "DEG";
        }
        if (status == LinkStatus.LOST) {
            return "LOST";
        }
        return "INIT";
    }

    private int colorFor(LinkStatus status) {
        if (status == LinkStatus.LOST) {
            return ColorPalette.CRITICAL;
        }
        if (status == LinkStatus.DEGRADED || status == LinkStatus.CONNECTING) {
            return ColorPalette.WARNING;
        }
        return ColorPalette.FRIENDLY;
    }

    private int adjustAlpha(int color, float alpha) {
        int a = Math.min(255, Math.max(0, (int) (255.0f * alpha)));
        return (color & 0x00FFFFFF) | (a << 24);
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return value * density;
    }
}