package com.tacticnav.cockpit.geo;

import com.tacticnav.cockpit.domain.GeoPoint;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public final class GeoMathTest {
    @Test
    public void pointInPolygonReturnsTrueForInteriorAndBoundary() {
        List<GeoPoint> polygon = Arrays.asList(
                new GeoPoint(0.0, 0.0),
                new GeoPoint(0.0, 1.0),
                new GeoPoint(1.0, 1.0),
                new GeoPoint(1.0, 0.0)
        );

        assertTrue(GeoMath.pointInPolygon(new GeoPoint(0.5, 0.5), polygon));
        assertTrue(GeoMath.pointInPolygon(new GeoPoint(0.0, 0.5), polygon));
        assertFalse(GeoMath.pointInPolygon(new GeoPoint(1.5, 0.5), polygon));
    }

    @Test
    public void haversineMetersIsCloseToOneNauticalMileAtEquator() {
        GeoPoint origin = new GeoPoint(0.0, 0.0);
        GeoPoint east = new GeoPoint(0.0, 0.016655);

        assertEquals(GeoMath.METERS_PER_NAUTICAL_MILE, GeoMath.haversineMeters(origin, east), 8.0);
    }

    @Test
    public void destinationPointMovesAlongBearing() {
        GeoPoint origin = new GeoPoint(43.8915, -0.5007);

        GeoPoint destination = GeoMath.destinationPoint(origin, 90.0, GeoMath.METERS_PER_NAUTICAL_MILE);

        assertEquals(origin.latitude(), destination.latitude(), 0.001);
        assertTrue(destination.longitude() > origin.longitude());
    }
}
