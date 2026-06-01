package com.tacticnav.cockpit.geo;

import com.tacticnav.cockpit.domain.GeoPoint;

import java.util.List;

public final class GeoMath {
    public static final double EARTH_RADIUS_METERS = 6_371_000.0;
    public static final double METERS_PER_NAUTICAL_MILE = 1852.0;

    private static final double BOUNDARY_EPSILON = 1.0e-10;

    private GeoMath() {}

    public static double haversineMeters(GeoPoint a, GeoPoint b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude() - a.longitude());

        double sinLat = Math.sin(dLat / 2.0);
        double sinLon = Math.sin(dLon / 2.0);
        double h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        return 2.0 * EARTH_RADIUS_METERS * Math.atan2(Math.sqrt(h), Math.sqrt(1.0 - h));
    }

    public static double bearingDegrees(GeoPoint from, GeoPoint to) {
        double lat1 = Math.toRadians(from.latitude());
        double lat2 = Math.toRadians(to.latitude());
        double dLon = Math.toRadians(to.longitude() - from.longitude());
        double y = Math.sin(dLon) * Math.cos(lat2);
        double x = Math.cos(lat1) * Math.sin(lat2)
                - Math.sin(lat1) * Math.cos(lat2) * Math.cos(dLon);
        return normalizeHeading(Math.toDegrees(Math.atan2(y, x)));
    }

    public static double normalizeHeading(double headingDeg) {
        double normalized = headingDeg % 360.0;
        return normalized < 0.0 ? normalized + 360.0 : normalized;
    }

    public static GeoPoint destinationPoint(GeoPoint start, double bearingDeg, double distanceMeters) {
        double angularDistance = distanceMeters / EARTH_RADIUS_METERS;
        double bearing = Math.toRadians(bearingDeg);
        double lat1 = Math.toRadians(start.latitude());
        double lon1 = Math.toRadians(start.longitude());

        double lat2 = Math.asin(
                Math.sin(lat1) * Math.cos(angularDistance)
                        + Math.cos(lat1) * Math.sin(angularDistance) * Math.cos(bearing)
        );
        double lon2 = lon1 + Math.atan2(
                Math.sin(bearing) * Math.sin(angularDistance) * Math.cos(lat1),
                Math.cos(angularDistance) - Math.sin(lat1) * Math.sin(lat2)
        );

        return new GeoPoint(Math.toDegrees(lat2), normalizeLongitude(Math.toDegrees(lon2)));
    }

    public static boolean pointInPolygon(GeoPoint point, List<GeoPoint> polygon) {
        if (polygon == null || polygon.size() < 3) {
            return false;
        }

        boolean inside = false;
        int previous = polygon.size() - 1;
        for (int current = 0; current < polygon.size(); current++) {
            GeoPoint a = polygon.get(previous);
            GeoPoint b = polygon.get(current);
            if (isPointOnSegment(point, a, b)) {
                return true;
            }

            boolean crossesLatitude = (b.latitude() > point.latitude()) != (a.latitude() > point.latitude());
            if (crossesLatitude) {
                double intersectionLon = (a.longitude() - b.longitude())
                        * (point.latitude() - b.latitude())
                        / (a.latitude() - b.latitude())
                        + b.longitude();
                if (point.longitude() < intersectionLon) {
                    inside = !inside;
                }
            }
            previous = current;
        }
        return inside;
    }

    public static double distanceToPolygonMeters(GeoPoint point, List<GeoPoint> polygon) {
        if (polygon == null || polygon.isEmpty()) {
            return Double.POSITIVE_INFINITY;
        }
        if (pointInPolygon(point, polygon)) {
            return 0.0;
        }

        double best = Double.POSITIVE_INFINITY;
        int previous = polygon.size() - 1;
        for (int current = 0; current < polygon.size(); current++) {
            double candidate = distanceToSegmentMeters(point, polygon.get(previous), polygon.get(current));
            if (candidate < best) {
                best = candidate;
            }
            previous = current;
        }
        return best;
    }

    private static boolean isPointOnSegment(GeoPoint point, GeoPoint a, GeoPoint b) {
        double cross = (point.latitude() - a.latitude()) * (b.longitude() - a.longitude())
                - (point.longitude() - a.longitude()) * (b.latitude() - a.latitude());
        if (Math.abs(cross) > BOUNDARY_EPSILON) {
            return false;
        }
        double dot = (point.longitude() - a.longitude()) * (b.longitude() - a.longitude())
                + (point.latitude() - a.latitude()) * (b.latitude() - a.latitude());
        if (dot < -BOUNDARY_EPSILON) {
            return false;
        }
        double squaredLength = squared(b.longitude() - a.longitude()) + squared(b.latitude() - a.latitude());
        return dot - squaredLength <= BOUNDARY_EPSILON;
    }

    private static double distanceToSegmentMeters(GeoPoint point, GeoPoint start, GeoPoint end) {
        double[] startMeters = toLocalMeters(point, start);
        double[] endMeters = toLocalMeters(point, end);
        double segmentX = endMeters[0] - startMeters[0];
        double segmentY = endMeters[1] - startMeters[1];
        double lengthSquared = segmentX * segmentX + segmentY * segmentY;
        if (lengthSquared == 0.0) {
            return Math.sqrt(startMeters[0] * startMeters[0] + startMeters[1] * startMeters[1]);
        }

        double t = -(startMeters[0] * segmentX + startMeters[1] * segmentY) / lengthSquared;
        double clamped = Math.max(0.0, Math.min(1.0, t));
        double closestX = startMeters[0] + clamped * segmentX;
        double closestY = startMeters[1] + clamped * segmentY;
        return Math.sqrt(closestX * closestX + closestY * closestY);
    }

    private static double[] toLocalMeters(GeoPoint origin, GeoPoint value) {
        double latRad = Math.toRadians(origin.latitude());
        double x = Math.toRadians(value.longitude() - origin.longitude()) * EARTH_RADIUS_METERS * Math.cos(latRad);
        double y = Math.toRadians(value.latitude() - origin.latitude()) * EARTH_RADIUS_METERS;
        return new double[]{x, y};
    }

    private static double normalizeLongitude(double longitudeDeg) {
        double lon = longitudeDeg;
        while (lon > 180.0) {
            lon -= 360.0;
        }
        while (lon < -180.0) {
            lon += 360.0;
        }
        return lon;
    }

    private static double squared(double value) {
        return value * value;
    }
}
