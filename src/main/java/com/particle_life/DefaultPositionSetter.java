package com.particle_life;

import org.joml.Vector3d;

public class DefaultPositionSetter implements PositionSetter {

    @Override
    public void set(Vector3d position, int type, int nTypes) {
        position.set(
                Math.random() * 2 - 1,
                Math.random() * 2 - 1,
                Math.random() * 2 - 1
        );
    }
}
