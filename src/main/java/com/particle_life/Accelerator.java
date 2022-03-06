package com.particle_life;

import org.joml.Vector3d;

public interface Accelerator {

    /**
     * Implementations of this interface are allowed to modify <code>pos</code>.
     * So, instead of allocating a new vector, they can modify and return <code>pos</code>.
     * @param a
     * @param pos position of the neighbor relative to the particle's own position,
     *            with its length divided by rmax. (So this vector will always have a length <= 1.)
     * @return the acceleration that should be applied to the particle's velocity.
     *         This is also interpreted as relative to rmax, that is, it will be scaled by rmax before it is applied to the particle.
     */
    Vector3d accelerate(double a, Vector3d pos);
}
