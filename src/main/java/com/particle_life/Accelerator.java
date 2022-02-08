package com.particle_life;

import org.joml.Vector3d;

public interface Accelerator {

    /**
     * Implementations of this interface are allowed to modify <code>x</code>.
     * So, instead of allocating a new vector, they can modify and return <code>x</code>.
     * @param a
     * @param x position of the neighbor relative to the particle's own position, with its length normalized to rmax
     * @return
     */
    Vector3d accelerate(double a, Vector3d x);
}
