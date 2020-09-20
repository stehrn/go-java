package com.github.stehrn.go;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * A very basic POC
 *
 * Using CompletableFuture.runAsync will limit number of threads
 */
public class Routine {

    static final ExecutorService service = Executors.newCachedThreadPool();

    static void go(Runnable r) {
        service.submit(r);
    }
}
