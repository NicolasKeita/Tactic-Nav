package com.tacticnav.atc.fusion;

import com.tacticnav.atc.domain.SituationSnapshot;

/**
 * Observer interface for fusion events.
 * Called when tracks are created, updated, or expired.
 */
public interface FusionObserver {
    /**
     * Called when a fusion event occurs.
     * 
     * @param event the event (track created/updated/expired)
     * @param snapshot current situation after update
     */
    void onFusionEvent(TrackFusionEngine.FusionEvent event, SituationSnapshot snapshot);
}
