package com.github.stehrn.go;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Routine {

    private static final ExecutorService executor = Executors.newCachedThreadPool();

    static void go(Runnable r) {
        executor.execute(r);
    }

    static ExecutorService executor() {
        return executor;
    }
}
