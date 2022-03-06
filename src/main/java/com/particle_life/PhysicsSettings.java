package com.particle_life;

public class PhysicsSettings {

    /**
     * Allows particles to move and interact across the world's borders (-1.0, +1.0).
     */
    public boolean wrap = true;

    /**
     * no interaction between particles that are further apart than rmax
     */
    public double rmax = 0.04;

    /**
     * velocity will be multiplied with this factor every second
     */
    public double friction = 0.0000001f;

    /**
     * force is scaled by arbitrary factor
     */
    public double forceFactor = 0.5f;

    /**
     * Time that is assumed to have passed between each simulation step, in seconds.
     */
    public double dt = 0.02f;
    public Matrix matrix = new DefaultMatrix(3);

    public PhysicsSettings() {
    }

    public PhysicsSettings deepCopy() {
        PhysicsSettings p = new PhysicsSettings();

        p.wrap = wrap;
        p.rmax = rmax;
        p.friction = friction;
        p.forceFactor = forceFactor;
        p.dt = dt;
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
            if (s.dt != dt) return false;
            if (!s.matrix.equals(matrix)) return false;

            return true;
        } else {
            return false;
        }
    }
}
