package com.tacticnav.cockpit.data;

import com.tacticnav.cockpit.domain.TacticalSnapshot;

public interface AtcTrackSource {
    void start(Listener listener);

    void stop();

    interface Listener {
        void onSnapshot(TacticalSnapshot snapshot);

        void onSourceError(Throwable error);
    }
}
