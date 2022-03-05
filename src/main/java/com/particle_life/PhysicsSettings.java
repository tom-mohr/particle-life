package com.particle_life;

public class PhysicsSettings {

    public boolean wrap = true;
    public double rmax = 0.04;          // no interaction between particles that are further apart than rmax
    public double friction = 0.0000001f;      // velocity will be multiplied with this factor every second
    public double forceFactor = 0.5f;   // force is scaled by arbitrary factor
    public boolean autoDt = true;

    /**
     * Time step used if <code>{@link #autoDt} == false</code>, in seconds.
     */
    public double fallbackDt = 0.02f;   //

    /**
     * Upper limit for time step used if <code>{@link #autoDt} == true</code>, in seconds.
     * <p>If this is negative (e.g. -1.0), there will be no limit.
     * <p>This won't have any effect on the actual framerate returned by {@link Physics#getActualDt()}.
     */
    public double maxDt = 1.0 / 20.0;
    public Matrix matrix = new DefaultMatrix(3);

    public PhysicsSettings() {
    }

    public PhysicsSettings deepCopy() {
        PhysicsSettings p = new PhysicsSettings();

        p.wrap = wrap;
        p.rmax = rmax;
        p.friction = friction;
        p.forceFactor = forceFactor;
        p.autoDt = autoDt;
        p.fallbackDt = fallbackDt;
        p.maxDt = maxDt;
        p.matrix = matrix.deepCopy();

        return p;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PhysicsSettings s) {

            if (s.wrap != wrap) return false;
            if (s.rmax != rmax) return false;
            if (s.friction != friction) return false;
            if (s.forceFactor != forceFactor) return false;
            if (s.autoDt != autoDt) return false;
            if (s.fallbackDt != fallbackDt) return false;
            if (s.maxDt != maxDt) return false;
            if (!s.matrix.equals(matrix)) return false;

            return true;
        } else {
            return false;
        }
    }
}
