package com.github.stehrn.go;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import static com.github.stehrn.go.Routine.go;

/**
 * Command line utility to create a given number of threads
 * <p>
 * Connect JConsole to get a visual view of things
 */
public class ThreadNumberTest {

    private final AtomicLong count = new AtomicLong(0);

    private final Payload payload;
    private final int threadCount;
    private final int printCount;

    private ThreadNumberTest(Payload payload, int threadCount, int printCount) {
        this.payload = payload;
        this.threadCount = threadCount;
        this.printCount = printCount;
    }

    private void runTest() throws InterruptedException {

        if (threadCount == -1) {
            createAsManyThreadsAsWeCan();
        } else {
            createSetNumberOfThreads();
        }
    }

    private void createSetNumberOfThreads() throws InterruptedException {
        long start = System.currentTimeMillis();

        final CountDownLatch latch = new CountDownLatch(threadCount);

        try {
            for (int i = 0; i < threadCount; i++) {
                go(() -> {
                    incrementCount();
                    payload.run();
                    latch.countDown();
                });
            }
        } finally {
            latch.await();
            System.out.println("Ending test, got to: " + count.intValue());
            long end = System.currentTimeMillis();
            System.out.println("Test took: " + (end - start) + " ms");
            payload.close();
        }
    }

    private void createAsManyThreadsAsWeCan() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Ending test (via finalizer), got to: " + count.intValue());
        }));
        try {
            while (true) {
                go(() -> {
                    incrementCount();
                    payload.run();
                });
            }
        } finally {
            System.out.println("Ending test, got to: " + count.intValue());
        }
    }

    private void incrementCount() {
        long current = count.incrementAndGet();
        if (current % printCount == 0) {
            System.out.println(current);
        }
    }

    private static void sleepIndefinitely() {
        try {
            TimeUnit.DAYS.sleep(100); // tie up thread
        } catch (InterruptedException e) {
        }
    }

    interface Payload extends Runnable {
        void close() throws InterruptedException;
    }

    private static Payload getPayload(int threadCount, int iterations) {
        if (iterations != -1) {

            return new Payload() {

                final LongAdder sleepTotal = new LongAdder();

                @Override
                public void run() {
                    for (int i = 0; i < iterations; i++) {
                        long sleep = Math.abs(ThreadLocalRandom.current().nextInt(100));
                        sleepTotal.add(sleep);
                        try {
                            TimeUnit.MILLISECONDS.sleep((int) sleep); // tie up thread
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                public void close() {
                    System.out.println("Total sleep time: " + sleepTotal.longValue() + " ms, (average sleep time across all threads: " + (sleepTotal.longValue() / threadCount) + " ms)");
                }
            };

        } else {
            return new Payload() {
                @Override
                public void run() {
                    sleepIndefinitely();
                }

                @Override
                public void close() {
                }
            };
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {

        System.out.println("System available processors: " + Runtime.getRuntime().availableProcessors());


        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter number of threads to try and create (-1 for no limit):");
        int threadCount = scanner.nextInt();
        if (threadCount == -1) {
            threadCount = Integer.MAX_VALUE;
        }

        System.out.println("Enter number of sleep iterations (-1 for indefinite sleep):");
        int iterations = scanner.nextInt();
        Payload payload = getPayload(threadCount, iterations);

        System.out.println("Enter frequency to print out current thread count (e.g. 100)");
        int printCount = scanner.nextInt();

        System.out.println("Press any key to start test");
        System.in.read();
        System.out.println("Starting test!");

        new ThreadNumberTest(payload, threadCount, printCount).runTest();

        System.out.println("Press Ctrl-C to exit");
        TimeUnit.DAYS.sleep(100); // tie up thread
    }
}
