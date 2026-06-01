package com.tacticnav.cockpit.data;

import com.tacticnav.cockpit.domain.GeoPoint;
import com.tacticnav.cockpit.domain.LinkStatus;
import com.tacticnav.cockpit.domain.NoFlyZone;
import com.tacticnav.cockpit.domain.TacticalSnapshot;
import com.tacticnav.cockpit.domain.TacticalTrack;
import com.tacticnav.cockpit.domain.TrackStatus;
import com.tacticnav.cockpit.geo.GeoMath;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class SimulatedTrackGenerator {
    private static final GeoPoint MONT_DE_MARSAN = new GeoPoint(43.8915, -0.5007);
    private static final int BASE_ALTITUDE_FT = 12_000;

    private final long startMillis;
    private final List<TrackMotion> motions;
    private final List<NoFlyZone> zones;

    public SimulatedTrackGenerator(long startMillis) {
        if (startMillis < 0) {
            throw new IllegalArgumentException("startMillis must be non-negative");
        }
        this.startMillis = startMillis;
        this.motions = createMotions();
        this.zones = createZones();
    }

    public TacticalSnapshot snapshotAt(long nowMillis, long sequenceNumber) {
        double elapsedSeconds = Math.max(0.0, (nowMillis - startMillis) / 1000.0);
        List<TacticalTrack> tracks = new ArrayList<>(motions.size() + 1);
        for (TrackMotion motion : motions) {
            tracks.add(motion.trackAt(elapsedSeconds, nowMillis));
        }
        tracks.add(intruderAt(elapsedSeconds, nowMillis));

        return new TacticalSnapshot(
                nowMillis,
                sequenceNumber,
                tracks,
                zones,
                LinkStatus.SIMULATED,
                null
        );
    }

    public List<NoFlyZone> zones() {
        return zones;
    }

    private TacticalTrack intruderAt(double elapsedSeconds, long nowMillis) {
        double cycleSeconds = 92.0;
        double progress = (elapsedSeconds % cycleSeconds) / cycleSeconds;
        GeoPoint start = new GeoPoint(43.8750, -0.6350);
        GeoPoint end = new GeoPoint(43.9100, -0.3850);
        double lat = start.latitude() + (end.latitude() - start.latitude()) * progress;
        double lon = start.longitude() + (end.longitude() - start.longitude()) * progress;
        GeoPoint position = new GeoPoint(lat, lon);

        return new TacticalTrack(
                "track-77",
                "VIPER 77",
                position,
                14_200,
                (float) GeoMath.bearingDegrees(start, end),
                430.0f,
                -250.0f,
                0.92f,
                nowMillis,
                TrackStatus.NORMAL
        );
    }

    private static List<TrackMotion> createMotions() {
        List<TrackMotion> result = new ArrayList<>();
        result.add(new TrackMotion("track-11", "RAFALE 11", MONT_DE_MARSAN, 9.0, 20.0, 1.8, 17_500, 390.0f));
        result.add(new TrackMotion("track-12", "RAFALE 12", MONT_DE_MARSAN, 12.0, 72.0, 1.3, 18_100, 405.0f));
        result.add(new TrackMotion("track-21", "HAWK 21", new GeoPoint(43.9450, -0.6250), 8.0, 145.0, -1.6, 9_600, 260.0f));
        result.add(new TrackMotion("track-22", "HAWK 22", new GeoPoint(43.8120, -0.4500), 10.5, 231.0, 1.1, 11_300, 280.0f));
        result.add(new TrackMotion("track-31", "ECHO 31", new GeoPoint(43.9750, -0.4300), 7.2, 304.0, -1.9, 21_000, 455.0f));
        result.add(new TrackMotion("track-41", "TANGO 41", new GeoPoint(43.8350, -0.6250), 6.0, 12.0, 2.2, 8_500, 220.0f));
        result.add(new TrackMotion("track-52", "NATO 52", new GeoPoint(43.7850, -0.5400), 14.0, 88.0, -0.9, 24_500, 470.0f));
        result.add(new TrackMotion("track-64", "MISTRAL 64", new GeoPoint(43.9500, -0.5050), 5.0, 196.0, 2.6, 13_400, 330.0f));
        return Collections.unmodifiableList(result);
    }

    private static List<NoFlyZone> createZones() {
        NoFlyZone alpha = new NoFlyZone(
                "NFZ-ALPHA",
                "NFZ ALPHA",
                Arrays.asList(
                        new GeoPoint(43.8650, -0.5580),
                        new GeoPoint(43.9280, -0.5520),
                        new GeoPoint(43.9400, -0.4680),
                        new GeoPoint(43.8920, -0.4240),
                        new GeoPoint(43.8500, -0.4970)
                ),
                0,
                45_000
        );
        NoFlyZone bravo = new NoFlyZone(
                "NFZ-BRAVO",
                "NFZ BRAVO",
                Arrays.asList(
                        new GeoPoint(43.7950, -0.4350),
                        new GeoPoint(43.8380, -0.3850),
                        new GeoPoint(43.8020, -0.3050),
                        new GeoPoint(43.7480, -0.3360),
                        new GeoPoint(43.7360, -0.4080)
                ),
                0,
                28_000
        );
        return Collections.unmodifiableList(Arrays.asList(alpha, bravo));
    }

    private static final class TrackMotion {
        private final String id;
        private final String callsign;
        private final GeoPoint center;
        private final double radiusNm;
        private final double phaseDeg;
        private final double angularRateDegPerSecond;
        private final int altitudeFt;
        private final float speedKt;

        private TrackMotion(
                String id,
                String callsign,
                GeoPoint center,
                double radiusNm,
                double phaseDeg,
                double angularRateDegPerSecond,
                int altitudeFt,
                float speedKt
        ) {
            this.id = id;
            this.callsign = callsign;
            this.center = center;
            this.radiusNm = radiusNm;
            this.phaseDeg = phaseDeg;
            this.angularRateDegPerSecond = angularRateDegPerSecond;
            this.altitudeFt = altitudeFt;
            this.speedKt = speedKt;
        }

        private TacticalTrack trackAt(double elapsedSeconds, long nowMillis) {
            double radialBearing = GeoMath.normalizeHeading(phaseDeg + angularRateDegPerSecond * elapsedSeconds);
            GeoPoint position = GeoMath.destinationPoint(
                    center,
                    radialBearing,
                    radiusNm * GeoMath.METERS_PER_NAUTICAL_MILE
            );
            float heading = (float) GeoMath.normalizeHeading(
                    radialBearing + (angularRateDegPerSecond >= 0.0 ? 90.0 : -90.0)
            );
            float verticalSpeed = (float) (Math.sin(Math.toRadians(radialBearing)) * 420.0);

            return new TacticalTrack(
                    id,
                    callsign,
                    position,
                    altitudeFt + (int) (Math.cos(Math.toRadians(radialBearing)) * 450.0),
                    heading,
                    speedKt,
                    verticalSpeed,
                    0.88f,
                    nowMillis,
                    TrackStatus.NORMAL
            );
        }
    }
}
