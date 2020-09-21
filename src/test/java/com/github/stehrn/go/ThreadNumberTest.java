package com.github.stehrn.go;

import java.io.IOException;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.github.stehrn.go.Routine.go;

/**
 * Command line utility to create a given number of threads
 * <p>
 * Connect JConsole to get a visual view of things
 */
public class ThreadNumberTest {

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

        final AtomicLong count = new AtomicLong(0);
        try {
            for (int i = 0; i < threadCount; i++) {
                go(() -> {
                    long current = count.incrementAndGet();
                    if (current % printCount == 0) {
                        System.out.println(current);
                    }
                    try {
                        TimeUnit.DAYS.sleep(100); // tie up thread
                    } catch (InterruptedException e) {
                    }
                });
            }
        } finally {
            System.out.println("Ending test, got to: " + count.incrementAndGet());
        }

        System.out.println("Press Ctrl-C to exit");
        TimeUnit.DAYS.sleep(100); // tie up thread
    }
}
