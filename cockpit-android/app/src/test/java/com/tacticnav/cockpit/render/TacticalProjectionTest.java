package com.tacticnav.cockpit.render;

import android.graphics.PointF;
import android.graphics.RectF;

import com.tacticnav.cockpit.domain.GeoPoint;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class TacticalProjectionTest {
    @Test
    public void projectMapsCenterPointToViewportCenter() {
        TacticalProjection projection = new TacticalProjection();
        RectF bounds = new RectF();
        bounds.left = 0.0f;
        bounds.top = 0.0f;
        bounds.right = 200.0f;
        bounds.bottom = 100.0f;
        System.out.println("TEST viewport=left=" + bounds.left + ",top=" + bounds.top + ",right=" + bounds.right + ",bottom=" + bounds.bottom);
        projection.setViewport(bounds, 0.0, 0.0, 20.0, 20.0);

        PointF output = projection.project(new GeoPoint(0.0, 0.0), new PointF());

        assertEquals(100.0f, output.x, 0.001f);
        assertEquals(50.0f, output.y, 0.001f);
    }

    @Test
    public void projectMapsCornersCorrectly() {
        TacticalProjection projection = new TacticalProjection();
        RectF bounds = new RectF();
        bounds.left = 0.0f;
        bounds.top = 0.0f;
        bounds.right = 100.0f;
        bounds.bottom = 100.0f;
        projection.setViewport(bounds, 0.0, 0.0, 20.0, 20.0);

        PointF topLeft = projection.project(new GeoPoint(10.0, -10.0), new PointF());
        PointF bottomRight = projection.project(new GeoPoint(-10.0, 10.0), new PointF());

        assertEquals(0.0f, topLeft.x, 0.001f);
        assertEquals(0.0f, topLeft.y, 0.001f);
        assertEquals(100.0f, bottomRight.x, 0.001f);
        assertEquals(100.0f, bottomRight.y, 0.001f);
    }
}
