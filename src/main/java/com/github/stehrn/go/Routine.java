package com.github.stehrn.go;

import static java.util.concurrent.CompletableFuture.runAsync;

/**
 * A very basic POC
 */
public class Routine {

    static void go(Runnable r) {
        runAsync(r);
    }
}
