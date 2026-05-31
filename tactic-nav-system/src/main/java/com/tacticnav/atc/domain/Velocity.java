package com.tacticnav.atc.domain;

/**
 * Estimated velocity of a track in 3D space.
 * 
 * @param vx velocity component in X direction (meters/second)
 * @param vy velocity component in Y direction (meters/second)
 * @param vz velocity component in Z direction (meters/second, positive = climbing)
 */
public record Velocity(double vx, double vy, double vz) {
    
    /**
     * Zero velocity (stationary object).
     */
    public static final Velocity ZERO = new Velocity(0.0, 0.0, 0.0);

    /**
     * Calculate speed magnitude (ignoring direction).
     */
    public double magnitude() {
        return Math.sqrt(vx * vx + vy * vy + vz * vz);
    }

    /**
     * Create velocity from two positions and the time elapsed between them.
     * 
     * @param oldPos earlier position
     * @param newPos later position
     * @return velocity estimate (or ZERO if positions have same timestamp)
     */
    public static Velocity fromPositions(Position oldPos, Position newPos) {
        long dt_ms = newPos.timestamp() - oldPos.timestamp();
        if (dt_ms <= 0) {
            return ZERO;
        }
        double dt_s = dt_ms / 1000.0;
        return new Velocity(
            (newPos.x() - oldPos.x()) / dt_s,
            (newPos.y() - oldPos.y()) / dt_s,
            (newPos.z() - oldPos.z()) / dt_s
        );
    }

    /**
     * Smooth velocity estimate using exponential moving average.
     * Reduces jitter from noisy position measurements.
     * 
     * @param oldVelocity previous velocity estimate
     * @param newVelocity candidate new velocity
     * @param alpha smoothing factor [0.0, 1.0]; higher = more weight on new value
     * @return smoothed velocity
     */
    public static Velocity smooth(Velocity oldVelocity, Velocity newVelocity, double alpha) {
        if (alpha < 0.0 || alpha > 1.0) {
            throw new IllegalArgumentException("alpha must be in [0.0, 1.0]");
        }
        return new Velocity(
            oldVelocity.vx * (1 - alpha) + newVelocity.vx * alpha,
            oldVelocity.vy * (1 - alpha) + newVelocity.vy * alpha,
            oldVelocity.vz * (1 - alpha) + newVelocity.vz * alpha
        );
    }
}
