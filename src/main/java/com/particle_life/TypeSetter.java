package com.particle_life;

import org.joml.Vector3d;

public interface TypeSetter {
    /**
     *
     * @param x
     * @param v
     * @param type   the previous type of the given particle
     * @param nTypes
     * @return the new type
     */
    int getType(Vector3d x, Vector3d v, int type, int nTypes);
}
