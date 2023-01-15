package com.particle_life;

import org.joml.Vector3d;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

public class Physics {

    private static final int DEFAULT_MATRIX_SIZE = 7;

    public PhysicsSettings settings = new PhysicsSettings();

    public Particle[] particles;

    // buffers for sorting by containers:
    private int[] containers;
    private int[][] containerNeighborhood;
    private Particle[] particlesBuffer;

    // container layout:
    private int nx;
    private int ny;
    private double containerSize = 0.13;//todo: implement makeContainerNeighborhood() to make this independent of rmax

    public Accelerator accelerator;
    public MatrixGenerator matrixGenerator;
    public PositionSetter positionSetter;
    public TypeSetter typeSetter;

    public int preferredNumberOfThreads = 12;
    private final LoadDistributor loadDistributor = new LoadDistributor();

    /**
     * This is used to stop the updating mid-particle.
     */
    private final AtomicBoolean updateThreadsShouldRun = new AtomicBoolean(false);


    // INITIALIZATION:

    /**
     * Shorthand constructor for {@link #Physics(Accelerator, PositionSetter, MatrixGenerator, TypeSetter)} using
     * <ul>
     *     <li>{@link DefaultPositionSetter}</li>
     *     <li>{@link DefaultMatrixGenerator}</li>
     *     <li>{@link DefaultTypeSetter}</li>
     * </ul>
     */
    public Physics(Accelerator accelerator) {
        this(accelerator, new DefaultPositionSetter(), new DefaultMatrixGenerator(), new DefaultTypeSetter());
    }

    /**
     * @param accelerator
     * @param positionSetter
     * @param matrixGenerator
     */
    public Physics(Accelerator accelerator,
                   PositionSetter positionSetter,
                   MatrixGenerator matrixGenerator,
                   TypeSetter typeSetter) {

        this.accelerator = accelerator;
        this.positionSetter = positionSetter;
        this.matrixGenerator = matrixGenerator;
        this.typeSetter = typeSetter;

        calcNxNy();
        makeContainerNeighborhood();

        generateMatrix();
        setParticleCount(10000);  // uses current position setter to create particles
    }

    private void calcNxNy() {
//        nx = (int) Math.ceil(2 * range.range.x / containerSize);
//        ny = (int) Math.ceil(2 * range.range.y / containerSize);

        // currently, "floor" is needed (because containerSize = rmax), so that rmax lies inside "simple" neighborhood
        nx = (int) Math.floor(2 / containerSize);
        ny = (int) Math.floor(2 / containerSize);
    }

    private void makeContainerNeighborhood() {
        containerNeighborhood = new int[][]{
                new int[]{-1, -1},
                new int[]{0, -1},
                new int[]{1, -1},
                new int[]{-1, 0},
                new int[]{0, 0},
                new int[]{1, 0},
                new int[]{-1, 1},
                new int[]{0, 1},
                new int[]{1, 1}
        };

        //todo:
        // top left corner
        // top right corner
        // bottom left corner
        // bottom right corner
    }

    /**
     * Calculate the next step in the simulation.
     * That is, it changes the velocity and position of each particle
     * in the particle array according to <code>this.settings</code>.
     */
    public void update() {
        updateParticles();
    }

    private void updateParticles() {

        updateThreadsShouldRun.set(true);

        makeContainers();

        loadDistributor.distributeLoadEvenly(particles.length, preferredNumberOfThreads, i -> {
            if (!updateThreadsShouldRun.get()) return false;
            updateVelocity(i, settings.dt);
            return true;
        });
        loadDistributor.distributeLoadEvenly(particles.length, preferredNumberOfThreads, i -> {
            if (!updateThreadsShouldRun.get()) return false;
            updatePosition(i, settings.dt);
            return true;
        });

        updateThreadsShouldRun.set(false);
    }

    /**
     * Can be used to forcibly stop execution of {@link #update()} mid-particle
     * from another thread.
     * This is safe to use.<br>
     * This method does not block until the execution is stopped, it just tells the
     * corresponding threads to stop as soon as possible.
     * That is, the {@link #update()} method may still run after this method has
     * been called, but it will stop after each thread has finished processing its
     * current particle.<br>
     * Note that the next call to {@link #update()} will as always start from
     * the beginning of the array, so some particles will have been simulated for one
     * more step than others. But you probably don't have to care about this.
     */
    public void forceUpdateStop() {
        updateThreadsShouldRun.set(false);
    }

    /**
     * Shutdown the internal thread pool.
     * Blocks until all tasks have completed execution.
     *
     * @param timeoutMilliseconds how long to wait for update threads to finish their execution (in milliseconds)
     * @return {@code true} if all tasks terminated and {@code false} if the timeout elapsed before termination
     */
    public boolean shutdown(long timeoutMilliseconds) throws InterruptedException {
        return loadDistributor.shutdown(timeoutMilliseconds);
    }

    // PUBLIC CONTROL METHODS:

    /**
     * Call this to initialize particles or when the particle count changed.
     * If the particle count changed, new particles will be created using the active position setter.
     */
    public void setPositions() {
        Arrays.stream(particles).forEach(this::setPosition);
    }

    public void generateMatrix() {

        int prevSize = settings.matrix != null ? settings.matrix.size() : DEFAULT_MATRIX_SIZE;
        settings.matrix = matrixGenerator.makeMatrix(prevSize);

        assert settings.matrix.size() == prevSize : "Matrix size should only change via setMatrixSize()";
    }

    // PRIVATE METHODS:

    /**
     * Set the size of the particle array.<br><br>
     * If the particle array is null, a new array will be created.
     * If n is greater than the current particle array size, new particles will be created.
     * If n is smaller than the current particle array size, random particles will be removed.
     * In that case, the order of the particles in the array will be random afterwards.<br>
     * New particles will be created using the active position setter.
     * 
     * @param n The new number of particles. Must be 0 or greater.
     */
    public void setParticleCount(int n) {
        if (particles == null) {
            particles = new Particle[n];
            for (int i = 0; i < n; i++) {
                particles[i] = generateParticle();
            }
        } else if (n != particles.length) {
            // strategy: if the array size changed, try to keep most of the particles

            Particle[] newParticles = new Particle[n];

            if (n < particles.length) {  // array becomes shorter

                // randomly shuffle particles first
                // (otherwise, the container layout becomes visible)
                shuffleParticles();

                // copy previous array as far as possible
                for (int i = 0; i < n; i++) {
                    newParticles[i] = particles[i];
                }

            } else {  // array becomes longer
                // copy old array and add particles to the end
                for (int i = 0; i < particles.length; i++) {
                    newParticles[i] = particles[i];
                }
                for (int i = particles.length; i < n; i++) {
                    newParticles[i] = generateParticle();
                }
            }
            particles = newParticles;
        }
    }

    /**
     * The matrix size should only be changed via this method.
     * <p>If <code>newSize</code> is smaller than the current size,
     * all particles with type <code>>= newSize</code> will have
     * their type changed to a type <code>< newSize</code> using
     * the current type setter ({@link #typeSetter Physics.typeSetter}).
     */
    public void setMatrixSize(int newSize) {
        Matrix prevMatrix = settings.matrix;
        int prevSize = prevMatrix.size();
        if (newSize == prevSize) return;  // keep previous matrix

        settings.matrix = matrixGenerator.makeMatrix(newSize);

        assert settings.matrix.size() == newSize;

        // copy as much as possible from previous matrix
        int commonSize = Math.min(prevSize, newSize);
        for (int i = 0; i < commonSize; i++) {
            for (int j = 0; j < commonSize; j++) {
                settings.matrix.set(i, j, prevMatrix.get(i, j));
            }
        }

        if (newSize < prevSize) {
            // need to change types of particles that are not in the new matrix
            for (Particle p : particles) {
                if (p.type >= newSize) {
                    setType(p);
                }
            }
        }
    }

    /**
     * Use this to avoid the container pattern showing
     * (i.e. if particles are treated differently depending on their position in the array).
     */
    private void shuffleParticles() {
        Collections.shuffle(Arrays.asList(particles));
    }

    /**
     * Creates a new particle and
     * <ol>
     *     <li>sets its type using the default type setter</li>
     *     <li>sets its position using the active position setter</li>
     * </ol>
     * (in that order) and returns it.
     */
    private Particle generateParticle() {
        Particle p = new Particle();
        setType(p);
        setPosition(p);
        return p;
    }

    protected final void setPosition(Particle p) {
        positionSetter.set(p.position, p.type, settings.matrix.size());
        Range.wrap(p.position);
        p.velocity.x = 0;
        p.velocity.y = 0;
        p.velocity.z = 0;
    }

    protected final void setType(Particle p) {
        p.type = typeSetter.getType(new Vector3d(p.position), new Vector3d(p.velocity), p.type, settings.matrix.size());
    }

    private void makeContainers() {

        // ensure that nx and ny are still OK
        containerSize = settings.rmax;//todo: in the future, containerSize should be independent of rmax
        calcNxNy();//todo: only change if containerSize (or range) changed
        //todo: (future) containerNeighborhood depends on rmax and containerSize.
        // if (rmax changed or containerSize changed) {
        //     makeContainerNeighborhood();
        // }

        // init arrays
        if (containers == null || containers.length != nx * ny) {
            containers = new int[nx * ny];
        }
        Arrays.fill(containers, 0);
        if (particlesBuffer == null || particlesBuffer.length != particles.length) {
            particlesBuffer = new Particle[particles.length];
        }

        // calculate container capacity
        for (Particle p : particles) {
            int ci = getContainerIndex(p.position);
            containers[ci]++;
        }

        // capacity -> index
        int offset = 0;
        for (int i = 0; i < containers.length; i++) {
            int cap = containers[i];
            containers[i] = offset;
            offset += cap;
        }

        // fill particles into containers
        for (Particle p : particles) {
            int ci = getContainerIndex(p.position);
            int i = containers[ci];
            particlesBuffer[i] = p;
            containers[ci]++;  // for next access
        }

        // swap buffers
        Particle[] h = particles;
        particles = particlesBuffer;
        particlesBuffer = h;
    }

    /**
     * Will fail if position is outside range!
     *
     * @param position must be in position range
     * @return index of the container containing <code>position</code>
     */
    private int getContainerIndex(Vector3d position) {
        int cx = (int) ((position.x + 1) / containerSize);
        int cy = (int) ((position.y + 1) / containerSize);

        // for solid borders
        if (cx == nx) {
            cx = nx - 1;
        }
        if (cy == ny) {
            cy = ny - 1;
        }

        return cx + cy * nx;
    }

    private int wrapContainerX(int cx) {
        if (cx < 0) {
            return cx + nx;
        } else if (cx >= nx) {
            return cx - nx;
        } else {
            return cx;
        }
    }

    private int wrapContainerY(int cy) {
        if (cy < 0) {
            return cy + ny;
        } else if (cy >= ny) {
            return cy - ny;
        } else {
            return cy;
        }
    }

    private void updateVelocity(int i, double dt) {
        Particle p = particles[i];

        // apply friction before adding new velocity
        p.velocity.mul(Math.pow(settings.friction, dt));

        int cx0 = (int) Math.floor((p.position.x + 1) / containerSize);
        int cy0 = (int) Math.floor((p.position.y + 1) / containerSize);

        for (int[] containerNeighbor : containerNeighborhood) {
            int cx = wrapContainerX(cx0 + containerNeighbor[0]);
            int cy = wrapContainerY(cy0 + containerNeighbor[1]);
            if (settings.wrap) {
                cx = wrapContainerX(cx);
                cy = wrapContainerX(cy);
            } else {
                if (cx < 0 || cx >= nx || cy < 0 || cy >= ny) {
                    continue;
                }
            }
            int ci = cx + cy * nx;

            int start = ci == 0 ? 0 : containers[ci - 1];
            int stop = containers[ci];

            for (int j = start; j < stop; j++) {
                if (i == j) continue;

                Particle q = particles[j];

                Vector3d relativePosition = connection(p.position, q.position);

                double distanceSquared = relativePosition.lengthSquared();
                // only check particles that are closer than or at rmax
                if (distanceSquared != 0 && distanceSquared <= settings.rmax * settings.rmax) {

                    relativePosition.div(settings.rmax);
                    Vector3d deltaV = accelerator.accelerate(settings.matrix.get(p.type, q.type), relativePosition);
                    // apply force as acceleration
                    p.velocity.add(deltaV.mul(settings.rmax * settings.force * dt));
                }
            }
        }
    }

    private void updatePosition(int i, double dt) {
        Particle p = particles[i];

        // pos += vel * dt
        p.velocity.mulAdd(dt, p.position, p.position);

        ensurePosition(p.position);
    }

    public Vector3d connection(Vector3d pos1, Vector3d pos2) {

        Vector3d delta = new Vector3d(pos2).sub(pos1);

        if (settings.wrap) {
            // wrapping the connection gives us the shortest possible distance
            Range.wrap(delta);
        }

        return delta;
    }

    /**
     * Calculates the distance between two positions.
     * If <code>settings.wrap == true</code>, this will
     * return the shortest possible distance, even if
     * that connection goes across the world's borders.
     * @return shortest possible distance between two points
     */
    public double distance(Vector3d pos1, Vector3d pos2) {
        return connection(pos1, pos2).length();
    }

    /**
     * Changes the coordinates of the given vector to ensures that they are in the correct range.
     * <ul>
     *     <li>
     *         If <code>settings.wrap == false</code>,
     *         the coordinates are simply clamped to [-1.0, 1.0].
     *     </li>
     *     <li>
     *         If <code>settings.wrap == true</code>,
     *         the coordinates are made to be inside [-1.0, 1.0) by adding or subtracting multiples of 2.
     *     </li>
     * </ul>
     * This method is called by {@link #update()} after changing the particles' positions.
     * It is just exposed for convenience.
     * That is, if you change the coordinates of the particles yourself,
     * you can use this to make sure that the coordinates are in the correct range before {@link #update()} is called.
     * @param position
     */
    public void ensurePosition(Vector3d position) {
        if (settings.wrap) {
            Range.wrap(position);
        } else {
            Range.clamp(position);
        }
    }

    // HANDY OPERATIONS:

    public void setTypes() {
        Arrays.stream(particles).forEach(p -> setType(p));
    }
}