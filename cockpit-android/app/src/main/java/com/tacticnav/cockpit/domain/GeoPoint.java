package com.tacticnav.cockpit.domain;

public final class GeoPoint {
    private final double latitude;
    private final double longitude;

    public GeoPoint(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("latitude must be finite and in [-90, 90]");
        }
        if (!Double.isFinite(longitude) || longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("longitude must be finite and in [-180, 180]");
        }
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public double latitude() {
        return latitude;
    }

    public double longitude() {
        return longitude;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GeoPoint)) {
            return false;
        }
        GeoPoint that = (GeoPoint) other;
        return Double.compare(latitude, that.latitude) == 0
                && Double.compare(longitude, that.longitude) == 0;
    }

    @Override
    public int hashCode() {
        long latBits = Double.doubleToLongBits(latitude);
        long lonBits = Double.doubleToLongBits(longitude);
        int result = (int) (latBits ^ (latBits >>> 32));
        result = 31 * result + (int) (lonBits ^ (lonBits >>> 32));
        return result;
    }

    @Override
    public String toString() {
        return "GeoPoint{" + "latitude=" + latitude + ", longitude=" + longitude + '}';
    }
}
