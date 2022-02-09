# 🦠 Particle Life

A Java framework that can simulate a variety of particle systems. It uses space partitioning and parallelization to achieve a significant speedup.

It can be configured to use the rules of Particle Life, a particle system showing life-like behaviour.

Check out [github.com/tom-mohr/particle-life-app](https://github.com/tom-mohr/particle-life-app) to see this framework in action.

# History

In 2014 or earlier, [Jeffrey Ventrella](https://en.wikipedia.org/wiki/Jeffrey_Ventrella) created [Clusters](https://ventrella.com/Clusters/).
In 2018, [@HackerPoet](https://github.com/HackerPoet) aka [Code Parade](https://www.youtube.com/c/CodeParade)
released [a video](https://www.youtube.com/watch?v=Z_zmZ23grXE), describing the rules of Ventrella's particle system.
This video popularized the idea and introduced the name Particle Life, referring to [Conway's Game of Life](https://en.wikipedia.org/wiki/Conway%27s_Game_of_Life). 


# Usage

This repository is intended to be included in other projects as a [Gradle](https://gradle.org/) dependency.
Add the following to your `build.gradle` file:

```Gradle
repositories {
    maven { url "https://jitpack.io" }
}

dependencies {
    implementation "com.github.tom-mohr:particle-life:v1.0.0"
}
```

You can now import the framework:
```Java
import com.particle_life.*;
```

This allows you to use the `Physics` class, which is the most important class of the framework.
You can initialize it with the rules of *Particle Life* as follows:
```Java
Accelerator particleLife = (a, x) -> {
    double rmin = 0.3;
    double dist = x.length();
    double force = dist < rmin ? (dist / rmin - 1) : a * (1 - Math.abs(1 + rmin - 2 * dist) / (1 - rmin));
    return x.mul(force / dist);
};

Physics p = new Physics(particleLife);
```

To run the simulation, you have to choose between two updating schemes:

1. Synchronous updating:
    ```Java
    while (true) {

        p.update(0.02);  // run the simulaton

        // simulation step completed

        if (button.pressed()) {
            p.settings.wrap = false;  // modify state of physics now
        }
    }
    ```

2. Asynchronous updating:
    ```Java
    p.startLoop();
    
    while (true) {
    
        if (button.pressed()) {
            // simulation could be running right now
            p.enqueue(() -> p.settings.wrap = false);  // modify state as soon as the simulation step is completed
        }
    }
    
    p.stopLoop();
    ```

The second approach (asynchronous updating) is recommended if you have a UI that can take user input while the simulation is running.
