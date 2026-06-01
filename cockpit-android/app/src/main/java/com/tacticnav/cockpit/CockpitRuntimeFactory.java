package com.tacticnav.cockpit;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.tacticnav.cockpit.core.CockpitController;
import com.tacticnav.cockpit.data.AtcTrackSource;
import com.tacticnav.cockpit.data.SimulatedAtcTrackSource;
import com.tacticnav.cockpit.data.SimulatedTrackGenerator;
import com.tacticnav.cockpit.data.TrackDatagramDecoder;
import com.tacticnav.cockpit.data.UdpAtcTrackSource;
import com.tacticnav.cockpit.processing.SituationProcessor;
import com.tacticnav.cockpit.time.Clock;
import com.tacticnav.cockpit.time.SystemClock;

public final class CockpitRuntimeFactory {
    private static final String TRACK_SOURCE_KEY = "com.tacticnav.cockpit.TRACK_SOURCE";
    private static final String UDP_SOURCE = "UDP";

    private CockpitRuntimeFactory() {}

    public static CockpitController create(Context context) {
        Clock clock = new SystemClock();
        AtcTrackSource source = createSource(context.getApplicationContext(), clock);
        return new CockpitController(source, new SituationProcessor(clock));
    }

    private static AtcTrackSource createSource(Context context, Clock clock) {
        String mode = readSourceMode(context);
        if (UDP_SOURCE.equalsIgnoreCase(mode)) {
            SimulatedTrackGenerator generator = new SimulatedTrackGenerator(clock.nowMillis());
            return new UdpAtcTrackSource(
                    UdpAtcTrackSource.DEFAULT_PORT,
                    generator.zones(),
                    new TrackDatagramDecoder()
            );
        }
        return new SimulatedAtcTrackSource(clock);
    }

    private static String readSourceMode(Context context) {
        try {
            ApplicationInfo info = context.getPackageManager().getApplicationInfo(
                    context.getPackageName(),
                    PackageManager.GET_META_DATA
            );
            Bundle metadata = info.metaData;
            if (metadata != null) {
                return metadata.getString(TRACK_SOURCE_KEY, "SIMULATED");
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            return "SIMULATED";
        }
        return "SIMULATED";
    }
}
