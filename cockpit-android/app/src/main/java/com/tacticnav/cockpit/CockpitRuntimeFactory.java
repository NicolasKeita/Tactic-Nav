package com.tacticnav.cockpit;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;

import com.tacticnav.cockpit.core.CockpitController;
import com.tacticnav.cockpit.data.AdsbUdpBroadcaster;
import com.tacticnav.cockpit.data.AtcTrackSource;
import com.tacticnav.cockpit.data.SimulatedAtcTrackSource;
import com.tacticnav.cockpit.data.SimulatedTrackGenerator;
import com.tacticnav.cockpit.data.TrackDatagramDecoder;
import com.tacticnav.cockpit.data.UdpAtcTrackSource;
import com.tacticnav.cockpit.processing.SituationProcessor;
import com.tacticnav.cockpit.time.Clock;
import com.tacticnav.cockpit.time.SystemClock;

public final class CockpitRuntimeFactory {

    private CockpitRuntimeFactory() {}

    public static CockpitController create(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(CockpitConstants.PREFS_NAME, Context.MODE_PRIVATE);
        String host = prefs.getString(CockpitConstants.PREFS_HOST, CockpitConstants.DEFAULT_HOST);
        int port = parseIntOrDefault(prefs.getString(CockpitConstants.PREFS_PORT, CockpitConstants.DEFAULT_PORT), CockpitConstants.DEFAULT_PORT_INT);
        return create(context, host, port);
    }

    public static CockpitController create(Context context, String adsbHost, int adsbPort) {
        Clock clock = new SystemClock();
        AtcTrackSource source = createSource(context.getApplicationContext(), clock, adsbHost, adsbPort);
        return new CockpitController(source, new SituationProcessor(clock));
    }

    private static AtcTrackSource createSource(Context context, Clock clock, String adsbHost, int adsbPort) {
        String mode = readSourceMode(context);
        if (CockpitConstants.ADSB_SOURCE.equalsIgnoreCase(mode)) {
            return new AdsbUdpBroadcaster(adsbHost, adsbPort);
        }
        if (CockpitConstants.UDP_SOURCE.equalsIgnoreCase(mode)) {
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
                return metadata.getString(CockpitConstants.TRACK_SOURCE_KEY, CockpitConstants.SIMULATED_SOURCE);
            }
        } catch (PackageManager.NameNotFoundException ignored) {
            return CockpitConstants.SIMULATED_SOURCE;
        }
        return CockpitConstants.SIMULATED_SOURCE;
    }

    private static int parseIntOrDefault(String value, int defaultValue) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}