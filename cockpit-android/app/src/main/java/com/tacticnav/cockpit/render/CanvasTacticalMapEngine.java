package com.tacticnav.cockpit.render;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PointF;
import android.graphics.RectF;

import com.tacticnav.cockpit.domain.GeoPoint;
import com.tacticnav.cockpit.domain.NoFlyZone;
import com.tacticnav.cockpit.domain.TacticalSnapshot;
import com.tacticnav.cockpit.domain.TacticalTrack;
import com.tacticnav.cockpit.domain.TrackStatus;

import java.util.List;
import java.util.Locale;

public final class CanvasTacticalMapEngine implements TacticalMapEngine {
    private final float density;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final PointF point = new PointF();
    private final PointF centroid = new PointF();

    public CanvasTacticalMapEngine(float density) {
        this.density = density;
        paint.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
    }

    @Override
    public void draw(Canvas canvas, TacticalSnapshot snapshot, TacticalProjection projection) {
        RectF bounds = projection.bounds();
        drawBaseMap(canvas, bounds);
        drawZones(canvas, snapshot.zones(), projection);
        drawTracks(canvas, snapshot.tracks(), projection);
        drawScale(canvas, bounds);
    }

    private void drawBaseMap(Canvas canvas, RectF bounds) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(ColorPalette.PANEL_DARK);
        canvas.drawRect(bounds, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1.0f));
        paint.setColor(ColorPalette.GRID);
        for (int i = 1; i < 8; i++) {
            float x = bounds.left + bounds.width() * i / 8.0f;
            canvas.drawLine(x, bounds.top, x, bounds.bottom, paint);
        }
        for (int i = 1; i < 5; i++) {
            float y = bounds.top + bounds.height() * i / 5.0f;
            canvas.drawLine(bounds.left, y, bounds.right, y, paint);
        }

        paint.setColor(ColorPalette.CONTOUR);
        paint.setStrokeWidth(dp(0.8f));
        for (int i = 0; i < 10; i++) {
            path.reset();
            float baseY = bounds.top + bounds.height() * (i + 0.4f) / 10.5f;
            for (int step = 0; step <= 72; step++) {
                float x = bounds.left + bounds.width() * step / 72.0f;
                float wave = (float) Math.sin(step * 0.36f + i * 0.8f) * dp(8.0f);
                float y = baseY + wave;
                if (step == 0) {
                    path.moveTo(x, y);
                } else {
                    path.lineTo(x, y);
                }
            }
            canvas.drawPath(path, paint);
        }

        drawMapLabel(canvas, "MONT-DE-MARSAN", bounds.left + bounds.width() * 0.43f, bounds.top + bounds.height() * 0.52f);
        drawMapLabel(canvas, "DAX", bounds.left + bounds.width() * 0.18f, bounds.top + bounds.height() * 0.22f);
        drawMapLabel(canvas, "ARMAGNAC", bounds.left + bounds.width() * 0.66f, bounds.top + bounds.height() * 0.74f);
    }

    private void drawMapLabel(Canvas canvas, String label, float x, float y) {
        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(sp(12.0f));
        paint.setColor(ColorPalette.TEXT_MUTED);
        canvas.drawText(label, x, y, paint);
    }

    private void drawZones(Canvas canvas, List<NoFlyZone> zones, TacticalProjection projection) {
        for (NoFlyZone zone : zones) {
            path.reset();
            float sumX = 0.0f;
            float sumY = 0.0f;
            List<GeoPoint> vertices = zone.vertices();
            for (int i = 0; i < vertices.size(); i++) {
                projection.project(vertices.get(i), point);
                sumX += point.x;
                sumY += point.y;
                if (i == 0) {
                    path.moveTo(point.x, point.y);
                } else {
                    path.lineTo(point.x, point.y);
                }
            }
            path.close();
            centroid.set(sumX / vertices.size(), sumY / vertices.size());

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(ColorPalette.NFZ_FILL);
            canvas.drawPath(path, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(1.4f));
            paint.setColor(ColorPalette.NFZ_STROKE);
            canvas.drawPath(path, paint);

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTextSize(sp(13.0f));
            paint.setColor(ColorPalette.NFZ_STROKE);
            canvas.drawText(zone.name(), centroid.x, centroid.y, paint);
        }
    }

    private void drawTracks(Canvas canvas, List<TacticalTrack> tracks, TacticalProjection projection) {
        for (TacticalTrack track : tracks) {
            projection.project(track.position(), point);
            int color = colorFor(track.status());

            canvas.save();
            canvas.rotate(track.headingDeg(), point.x, point.y);
            path.reset();
            float size = dp(track.status() == TrackStatus.INTRUDER ? 12.0f : 9.0f);
            path.moveTo(point.x, point.y - size);
            path.lineTo(point.x - size * 0.7f, point.y + size * 0.75f);
            path.lineTo(point.x + size * 0.7f, point.y + size * 0.75f);
            path.close();

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2.0f));
            paint.setColor(color);
            canvas.drawPath(path, paint);
            canvas.restore();

            if (track.status() == TrackStatus.INTRUDER) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(dp(1.4f));
                paint.setColor(ColorPalette.CRITICAL);
                canvas.drawCircle(point.x, point.y, dp(18.0f), paint);
            }

            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTextSize(sp(10.5f));
            paint.setColor(color);
            canvas.drawText(track.callsign(), point.x + dp(12.0f), point.y - dp(2.0f), paint);
            paint.setTextSize(sp(9.0f));
            paint.setColor(ColorPalette.TEXT_MUTED);
            canvas.drawText(String.format(Locale.US, "%d FT", track.altitudeFt()), point.x + dp(12.0f), point.y + dp(11.0f), paint);
        }
    }

    private void drawScale(Canvas canvas, RectF bounds) {
        float width = dp(96.0f);
        float x = bounds.right - width - dp(24.0f);
        float y = bounds.bottom - dp(22.0f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2.0f));
        paint.setColor(ColorPalette.TEXT_PRIMARY);
        canvas.drawLine(x, y, x + width, y, paint);
        canvas.drawLine(x, y - dp(5.0f), x, y + dp(5.0f), paint);
        canvas.drawLine(x + width, y - dp(5.0f), x + width, y + dp(5.0f), paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(sp(10.0f));
        canvas.drawText("10 NM", x + width / 2.0f, y - dp(8.0f), paint);
    }

    private int colorFor(TrackStatus status) {
        if (status == TrackStatus.INTRUDER) {
            return ColorPalette.CRITICAL;
        }
        if (status == TrackStatus.WARNING) {
            return ColorPalette.WARNING;
        }
        if (status == TrackStatus.STALE) {
            return ColorPalette.STALE;
        }
        return ColorPalette.FRIENDLY;
    }

    private float dp(float value) {
        return value * density;
    }

    private float sp(float value) {
        return value * density;
    }
}
