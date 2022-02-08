package com.particle_life;

import org.joml.Vector3d;

public class DefaultTypeSetter implements TypeSetter {

    @Override
    public int getType(Vector3d x, Vector3d v, int type, int nTypes) {
        return (int) Math.floor(Math.random() * nTypes);
    }
}
