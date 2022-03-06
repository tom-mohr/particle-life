package com.particle_life;

import java.util.LinkedList;

public class ThreadUtility {

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
     * @return actual number of threads used
     */
    public static int distributeLoadEvenly(int loadSize, int preferredNumberOfThreads, IndexProcessor indexProcessor) {

        if (loadSize <= 0) return 0;

        LinkedList<Thread> threads = new LinkedList<>();
        int length = (int) Math.ceil(loadSize / (double) preferredNumberOfThreads);

        int start = 0;
        int stop = start + length;
        while (stop <= loadSize) {
            Thread thread = new Thread(new BatchProcessor(start, stop, indexProcessor));
            threads.add(thread);
            thread.start();
            // move interval by length
            start += length;
            stop += length;
        }
        if (start < loadSize) {
            Thread thread = new Thread(new BatchProcessor(start, loadSize, indexProcessor));
            threads.add(thread);
            thread.start();
        }

        int actualNumberOfThreads = threads.size();

        while (!threads.isEmpty()) {
            Thread thread = threads.removeFirst();
            try {
                thread.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        return actualNumberOfThreads;
    }
}
