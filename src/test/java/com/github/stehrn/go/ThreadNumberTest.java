package com.github.stehrn.go;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static com.github.stehrn.go.Routine.go;

public class ThreadNumberTest {

    // just keep going until we get a OutOfMemoryError: unable to create native thread
    public static void main(String[] args) {

        final AtomicLong count = new AtomicLong(0);
        try {
            while (true) {
                go(() -> {
                    count.incrementAndGet();
                    try {
                        TimeUnit.DAYS.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                });
            }
        } finally {
            System.out.println("Got to: " + count.incrementAndGet());
        }
    }
}
