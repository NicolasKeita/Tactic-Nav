package com.tacticnav.cockpit.domain;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class NoFlyZone {
    private final String id;
    private final String name;
    private final List<GeoPoint> vertices;
    private final int minAltitudeFt;
    private final int maxAltitudeFt;

    public NoFlyZone(String id, String name, List<GeoPoint> vertices, int minAltitudeFt, int maxAltitudeFt) {
        if (isBlank(id) || isBlank(name)) {
            throw new IllegalArgumentException("id and name are required");
        }
        if (vertices == null || vertices.size() < 3) {
            throw new IllegalArgumentException("a no-fly zone requires at least three vertices");
        }
        if (maxAltitudeFt < minAltitudeFt) {
            throw new IllegalArgumentException("maxAltitudeFt must be >= minAltitudeFt");
        }
        this.id = id;
        this.name = name;
        this.vertices = Collections.unmodifiableList(new ArrayList<>(vertices));
        this.minAltitudeFt = minAltitudeFt;
        this.maxAltitudeFt = maxAltitudeFt;
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public List<GeoPoint> vertices() {
        return vertices;
    }

    public int minAltitudeFt() {
        return minAltitudeFt;
    }

    public int maxAltitudeFt() {
        return maxAltitudeFt;
    }

    public boolean containsAltitude(int altitudeFt) {
        return altitudeFt >= minAltitudeFt && altitudeFt <= maxAltitudeFt;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
