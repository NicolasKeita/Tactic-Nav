package com.tacticnav.cockpit.domain;

public enum LinkStatus {
    CONNECTING,
    LIVE,
    DEGRADED,
    LOST,
    SIMULATED;

    public boolean isUsable() {
        return this == LIVE || this == DEGRADED || this == SIMULATED;
    }
}
