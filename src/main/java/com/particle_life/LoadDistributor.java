package com.particle_life;

import java.util.LinkedList;
import java.util.concurrent.*;

public class LoadDistributor {

    private final ExecutorService threadPool = Executors.newCachedThreadPool();

    public interface IndexProcessor {
        /**
         *
         * @param i the index that is to be processed
         * @return whether the execution should continue.
         */
        boolean process(int i);
    }

    private record BatchProcessor(int start, int stop, IndexProcessor indexProcessor) implements Runnable {

        @Override
        public void run() {
            for (int i = start; i < stop; i++) {
                if (!indexProcessor.process(i)) {
                    break;
                }
            }
        }
    }

    /**
     *
     * @param loadSize                 the number of indices that must be processed
     * @param preferredNumberOfThreads on how many threads the load should be distributed
     * @param indexProcessor           callback that will be invoked on each index in 0 ... loadSize - 1
     */
    public void distributeLoadEvenly(int loadSize, int preferredNumberOfThreads, IndexProcessor indexProcessor) {

        if (loadSize <= 0) return;

        LinkedList<Future<?>> futures = new LinkedList<>();  // needed later for waiting for all threads to finish
        int length = (int) Math.ceil(loadSize / (double) preferredNumberOfThreads);

        int start = 0;
        int stop = start + length;
        while (stop <= loadSize) {
            Future<?> future = threadPool.submit(new BatchProcessor(start, stop, indexProcessor));
            futures.add(future);
            // move interval by length
            start += length;
            stop += length;
        }
        if (start < loadSize) {
            Future<?> future = threadPool.submit(new BatchProcessor(start, loadSize, indexProcessor));
            futures.add(future);
        }

        // wait for all threads to finish
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Shutdown the internal thread pool.
     * Blocks until all tasks have completed execution.
     *
     * @param timeoutMilliseconds how long to wait for update threads to finish their execution (in milliseconds)
     * @return {@code true} if all tasks terminated and {@code false} if the timeout elapsed before termination
     */
    public boolean shutdown(long timeoutMilliseconds) throws InterruptedException {
        threadPool.shutdown();
        return threadPool.awaitTermination(timeoutMilliseconds, TimeUnit.MILLISECONDS);
    }
}
