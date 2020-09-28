package com.github.stehrn.go;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.github.stehrn.go.Routine.go;

/**
 * Command line utility to create a given number of threads
 * <p>
 * Connect JConsole to get a visual view of things
 */
public class ThreadNumberTest {

    private final AtomicLong count = new AtomicLong(0);
    private final int threadCount;
    private final int printCount;

    private ThreadNumberTest(int threadCount, int printCount) {
        this.threadCount = threadCount;
        this.printCount = printCount;
    }

    private void runTest() throws InterruptedException {

        if (threadCount == -1) {
            createAsManyThreadsAsWeCan();
        } else {
            createSetNumberOfThreads();
        }

        System.out.println("Press Ctrl-C to exit");
        TimeUnit.DAYS.sleep(100); // tie up thread
    }

    private void createSetNumberOfThreads() throws InterruptedException {
        final CountDownLatch latch = new CountDownLatch(threadCount);

        try {
            for (int i = 0; i < threadCount; i++) {
                go(() -> {
                    incrementCount();
                    latch.countDown();
                    sleep();
                });
            }
        } finally {
            latch.await();
            System.out.println("Ending test, got to: " + count.intValue());
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
                    sleep();
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

    private void sleep() {
        try {
            TimeUnit.DAYS.sleep(100); // tie up thread
        } catch (InterruptedException e) {
        }
    }

    public static void main(String[] args) throws InterruptedException, IOException {

        Scanner scanner = new Scanner(System.in);
        System.out.println("Enter number of threads to try and create (-1 for no limit):");
        int threadCount = scanner.nextInt();
        if (threadCount == -1) {
            threadCount = Integer.MAX_VALUE;
        }
        System.out.println("Enter frequency to print out current thread count (e.g. 100)");
        int printCount = scanner.nextInt();

        System.out.println("Press any key to start test");
        System.in.read();
        System.out.println("Starting test!");

        new ThreadNumberTest(threadCount, printCount).runTest();
    }
}
