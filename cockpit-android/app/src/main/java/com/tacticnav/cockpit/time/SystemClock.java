package com.tacticnav.cockpit.time;

public final class SystemClock implements Clock {
    @Override
    public long nowMillis() {
        return java.lang.System.currentTimeMillis();
    }
}
