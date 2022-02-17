package com.particle_life;

import com.particle_life.multithreading.ThreadUtility;
import org.joml.Vector3d;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

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

    public boolean pause = false;
    private final Clock clock = new Clock(60);

    private Thread updateThread = null;
    private AtomicBoolean threadShouldRun = new AtomicBoolean(false);

    /**
     * This is used to kill the thread.
     */
    private AtomicBoolean threadShouldAbort = new AtomicBoolean(false);

    private final LinkedBlockingDeque<Runnable> commandQueue = new LinkedBlockingDeque<>();
    private final AtomicReference<Runnable> once = new AtomicReference<>(null);

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
        ensureParticles();  // uses current position setter
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

    // COMMAND QUEUE

    /**
     * The passed command will be executed in the update thread.
     * Use this if you want to change anything from another thread.
     * The commands will be executed sequentially after / before the data is modified
     * so there won't be any race conditions.
     * <p> The order in which commands are added via this method is preserved.
     *
     * @param cmd the command to be executed in the update thread
     */
    public void enqueue(Runnable cmd) {
        //todo: debug print if some GUI elements spam commands
        commandQueue.addLast(cmd);
    }

    /**
     * The passed command will be executed in the update thread.
     * If this method is called again and the previous command could not yet be executed
     * the previous command will be dropped and only the most recent one (passed to this method) will be executed.
     *
     * @param cmd the command to be executed in the update thread
     */
    public void doOnce(Runnable cmd) {
        once.set(cmd);
    }

    private void processCommandQueue() {
        while (!commandQueue.isEmpty()) {
            Runnable cmd = commandQueue.removeFirst();
            cmd.run();

            // command could have changed n, so ensure correct particle array size
            ensureParticles();
        }
    }

    // LIFECYCLE METHODS:

    public synchronized void startLoop() {

        if (updateThread != null) throw new IllegalStateException("Update thread didn't finish properly (wasn't null).");

        threadShouldRun.set(true);

        updateThread = new Thread(() -> {
            while (threadShouldRun.get()) {

                clock.tick();

                double dt = settings.fallbackDt;
                if (settings.autoDt) {
                    dt = clock.getDtMillis() / 1000.0;
                    if (settings.maxDt >= 0) {
                        dt = Math.min(settings.maxDt, dt);
                    }
                }
                update(dt);
            }
        });
        updateThread.start();
    }

    /**
     * Blocks until update loop is stopped or timeout.
     * @return if the thread could be stopped.
     */
    public synchronized boolean stopLoop() {

        assert updateThread != null : "Thread is null";
        if (!updateThread.isAlive()) {
            throw new IllegalStateException("Thread is not running.");
        }

        threadShouldRun.set(false);
        try {
            updateThread.join(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (updateThread.isAlive()) {
            System.err.println("Physics update thread didn't react. Will try to abort...");

            threadShouldAbort.set(true);
            try {
                updateThread.join(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            threadShouldAbort.set(false);

            if (updateThread.isAlive()) {
                System.err.println("Physics update thread couldn't be aborted.");
                return false;
            } else {
                System.out.println("Physics update thread was successfully aborted.");
                updateThread = null;
                return true;
            }

        } else {
            updateThread = null;
            return true;
        }
    }

    /**
     * Calculates the next step in the simulation.
     * <p>You can either call this method yourself, if you want to update the physics synchronously,
     * or use {@link #startLoop()} and {@link #stopLoop()} in order to update asynchronously.
     * If you use asynchronous updating, use {@link #enqueue(Runnable)} to change settings etc.
     * instead of doing so directly (this could lead to race conditions).<br>
     * Example:
     * <pre>
     *     Physics p = new Physics();
     *     p.startLoop();
     *     while (true) {
     *         if (button.pressed()) {
     *             p.enqueue(() -> p.settings.wrap = false);
     *         }
     *     }
     *     p.stopLoop();
     * </pre>
     *
     * @param dt time passed since last call in seconds
     */
    public void update(double dt) {

        processCommandQueue();

        ensureParticles();  // for synchronous updating (when command queue is not used)

        Runnable onceCommand = once.getAndSet(null);
        if (onceCommand != null) onceCommand.run();

        if (!pause) {
            updateParticles(dt);
        }
    }

    private void updateParticles(double dt) {

        makeContainers();

        ThreadUtility.distributeLoadEvenly(settings.n, preferredNumberOfThreads, i -> {
            if (threadShouldAbort.get()) return false;
            updateV(i, dt);
            return true;
        });
        ThreadUtility.distributeLoadEvenly(settings.n, preferredNumberOfThreads, i -> {
            if (threadShouldAbort.get()) return false;
            updateX(i, dt);
            return true;
        });
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
     * Call this to initialize particles or when the particle count (n) changed.
     * Will keep as many particles unchanged as possible.
     * New particles will be created using the active position setter.
     */
    private void ensureParticles() {
        if (particles == null) {
            particles = new Particle[settings.n];
            for (int i = 0; i < settings.n; i++) {
                particles[i] = generateParticle();
            }
        } else if (settings.n != particles.length) {
            // strategy: if the array size changed, try to keep most of the particles

            Particle[] newParticles = new Particle[settings.n];

            if (settings.n < particles.length) {  // array becomes shorter

                // randomly shuffle particles first
                // (otherwise, the container layout becomes visible)
                shuffleParticles();

                // copy previous array as far as possible
                for (int i = 0; i < settings.n; i++) {
                    newParticles[i] = particles[i];
                }

            } else {  // array becomes longer
                // copy old array and add particles to the end
                for (int i = 0; i < particles.length; i++) {
                    newParticles[i] = particles[i];
                }
                for (int i = particles.length; i < settings.n; i++) {
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
        positionSetter.set(p.x, p.type, settings.matrix.size());
        Range.wrap(p.x);
        p.v.x = 0;
        p.v.y = 0;
        p.v.z = 0;
    }

    protected final void setType(Particle p) {
        p.type = typeSetter.getType(new Vector3d(p.x), new Vector3d(p.v), p.type, settings.matrix.size());
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
        if (particlesBuffer == null || particlesBuffer.length != settings.n) {
            particlesBuffer = new Particle[settings.n];
        }

        // calculate container capacity
        for (Particle p : particles) {
            int ci = getContainerIndex(p.x);
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
            int ci = getContainerIndex(p.x);
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
     * Will fail if x is outside range!
     *
     * @param x must be in position range
     * @return index of the container containing <code>x</code>
     */
    private int getContainerIndex(Vector3d x) {
        int cx = (int) ((x.x + 1) / containerSize);
        int cy = (int) ((x.y + 1) / containerSize);

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

    private void updateV(int i, double dt) {
        Particle p = particles[i];

        // apply friction before adding new velocity
        p.v.mul(Math.pow(settings.friction, dt));

        int cx0 = (int) Math.floor((p.x.x + 1) / containerSize);
        int cy0 = (int) Math.floor((p.x.y + 1) / containerSize);

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

                Vector3d relativeX = connection(p.x, q.x);

                double distanceSquared = relativeX.lengthSquared();
                // only check particles that are closer than rmax
                if (distanceSquared != 0 && distanceSquared < settings.rmax * settings.rmax) {

                    relativeX.div(settings.rmax);
                    Vector3d deltaV = accelerator.accelerate(settings.matrix.get(p.type, q.type), relativeX);
                    // apply force as acceleration
                    p.v.add(deltaV.mul(settings.forceFactor * dt));
                }
            }
        }
    }

    private void updateX(int i, double dt) {
        Particle p = particles[i];

        // x += v * dt
        p.v.mulAdd(dt, p.x, p.x);

        ensurePosition(p.x);
    }

    public Vector3d connection(Vector3d x1, Vector3d x2) {

        Vector3d delta = new Vector3d(x2).sub(x1);

        if (settings.wrap) {
            // wrapping the connection gives us the shortest possible distance
            Range.wrap(delta);
        }

        return delta;
    }

    public double distance(Vector3d x1, Vector3d x2) {
        return connection(x1, x2).length();
    }

    /**
     * Ensures that the coordinates of <code>x</code> are in [-1, 1].
     * If <code>settings.wrap</code> is <code>true</code>, <code>wrap</code> is used, otherwise <code>clamp</code>.
     * @param x
     */
    public void ensurePosition(Vector3d x) {
        if (settings.wrap) {
            Range.wrap(x);
        } else {
            Range.clamp(x);
        }
    }

    // HANDY OPERATIONS:

    public void setTypes() {
        Arrays.stream(particles).forEach(p -> setType(p));
    }

    // GETTERS AND SETTERS:

    /**
     * Returns how much time passed between the last two calls to {@link #update(double) update()}.
     * This will also work if {@link #update(double) update()} is being called automatically (see {@link #startLoop()}),
     * but keep in mind that the return value of this method can be very high if <code>{@link #pause} == true</code>.
     * @return how much time passed between the last two calls to {@link #update(double) update()}
     */
    public double getActualDt() {
        return clock.getDtMillis() / 1000.0;
    }

    /**
     * Average framerate over the last couple of frames.
     * See javadoc of {@link #getActualDt()}.
     * @return
     */
    public double getAvgFramerate() {
        return clock.getAvgFramerate();
    }
}