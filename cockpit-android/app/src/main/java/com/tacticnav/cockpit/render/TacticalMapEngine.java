package com.tacticnav.cockpit.render;

import android.graphics.Canvas;

import com.tacticnav.cockpit.domain.TacticalSnapshot;

public interface TacticalMapEngine {
    void draw(Canvas canvas, TacticalSnapshot snapshot, TacticalProjection projection);
}
