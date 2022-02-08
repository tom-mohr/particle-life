package com.particle_life.multithreading;

import java.util.LinkedList;

public class ThreadUtility {

    private record BatchProcessor(int start, int stop, ParticleProcessor particleProcessor) implements Runnable {

        @Override
        public void run() {
            for (int i = start; i < stop; i++) {
                if (!particleProcessor.process(i)) {
                    break;
                }
            }
        }
    }

    /**
     *
     * @param loadSize
     * @param preferredNumberOfThreads
     * @param particleProcessor
     * @return actual number of threads used
     */
    public static int distributeLoadEvenly(int loadSize, int preferredNumberOfThreads, ParticleProcessor particleProcessor) {

        if (loadSize <= 0) return 0;

        LinkedList<Thread> threads = new LinkedList<>();
        int length = (int) Math.ceil(loadSize / (double) preferredNumberOfThreads);

        int start = 0;
        int stop = start + length;
        while (stop <= loadSize) {
            Thread thread = new Thread(new BatchProcessor(start, stop, particleProcessor));
            threads.add(thread);
            thread.start();
            // move interval by length
            start += length;
            stop += length;
        }
        if (start < loadSize) {
            Thread thread = new Thread(new BatchProcessor(start, loadSize, particleProcessor));
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
