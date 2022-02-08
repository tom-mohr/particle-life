package com.particle_life.multithreading;

public interface ParticleProcessor {
    /**
     *
     * @param i
     * @return whether the execution should continue.
     */
    boolean process(int i);
}
