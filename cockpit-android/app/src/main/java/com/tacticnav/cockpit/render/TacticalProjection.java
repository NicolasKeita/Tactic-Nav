package com.tacticnav.cockpit.render;

import android.graphics.PointF;
import android.graphics.RectF;

import com.tacticnav.cockpit.domain.GeoPoint;

public final class TacticalProjection {
    private final RectF bounds = new RectF();
    private double centerLatitude;
    private double centerLongitude;
    private double latitudeSpan;
    private double longitudeSpan;

    public void setViewport(
            RectF bounds,
            double centerLatitude,
            double centerLongitude,
            double latitudeSpan,
            double longitudeSpan
    ) {
        this.bounds.set(bounds);
        this.centerLatitude = centerLatitude;
        this.centerLongitude = centerLongitude;
        this.latitudeSpan = latitudeSpan;
        this.longitudeSpan = longitudeSpan;
    }

    public PointF project(GeoPoint point, PointF out) {
        double minLatitude = centerLatitude - latitudeSpan / 2.0;
        double maxLatitude = centerLatitude + latitudeSpan / 2.0;
        double minLongitude = centerLongitude - longitudeSpan / 2.0;
        double maxLongitude = centerLongitude + longitudeSpan / 2.0;

        float x = bounds.left + (float) ((point.longitude() - minLongitude) / (maxLongitude - minLongitude) * bounds.width());
        float y = bounds.bottom - (float) ((point.latitude() - minLatitude) / (maxLatitude - minLatitude) * bounds.height());
        out.set(x, y);
        return out;
    }

    public RectF bounds() {
        return bounds;
    }
}
