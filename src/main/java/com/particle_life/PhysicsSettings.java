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
     * The time in seconds after which half the velocity of a particle
     * should be lost due to friction.
     * The actual friction factor <code>f</code> that the velocity is
     * multiplied with in every time step is calculated on the basis of
     * this value according to the following formula:
     * <code>f = Math.pow(0.5, dt / frictionHalfLife)</code>
     */
    public double velocityHalfLife = 0.043;

    /**
     * Scaled force by an arbitrary factor.
     */
    public double force = 1.0f;

    /**
     * Time that is assumed to have passed between each simulation step, in seconds.
     */
    public double dt = 0.02f;
    public Matrix matrix = new DefaultMatrix(6);

    public PhysicsSettings() {
    }

    public PhysicsSettings deepCopy() {
        PhysicsSettings p = new PhysicsSettings();

        p.wrap = wrap;
        p.rmax = rmax;
        p.velocityHalfLife = velocityHalfLife;
        p.force = force;
        p.dt = dt;
        p.matrix = matrix.deepCopy();

        return p;
    }

    @Override
    public boolean equals(Object o) {
        if (o instanceof PhysicsSettings s) {

            if (s.wrap != wrap) return false;
            if (s.rmax != rmax) return false;
            if (s.velocityHalfLife != velocityHalfLife) return false;
            if (s.force != force) return false;
            if (s.dt != dt) return false;
            if (!s.matrix.equals(matrix)) return false;

            return true;
        } else {
            return false;
        }
    }
}
