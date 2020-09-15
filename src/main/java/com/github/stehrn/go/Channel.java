package com.github.stehrn.go;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provide a mechanism to synchronize routines that need to send and receive the resources they need to share between
 * each other. A channel will guarantee synchronous communication and exchange of data between routines.
 * <p>
 * When declaring a channel, the type of data that will be shared needs to be specified, and can be done using one
 * of the two factory methods, to create either a buffered or unbuffered channel.
 * <p>
 * Unbuffered channel of strings
 * <code>
 * Channel<String> unbuffered = channel();
 * </code>
 * <p>
 * Buffered channel of strings
 * <code>
 * Channel<String> buffered = channel(10);
 * </code>
 * <p>
 * To send a value into a channel use send(T item):
 *
 * <code>
 * Channel<String> unbuffered = channel();
 * unbuffered.send("Sample message");
 * </code>
 * <p>
 * To receive the above string from the channel use receive():
 *
 * <code>
 * String message = unbuffered.receive();
 * assertThat(message, is("Sample message"))
 * </code>
 */
public class Channel<T> {

    private final BlockingQueue<T> items;
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final ExecutorService service = Executors.newCachedThreadPool();

    private Channel(BlockingQueue<T> items) {
        this.items = items;
    }

    /**
     * Create an unbuffered channel
     * <p>
     * Internally, this uses a SynchronousQueue, given a thread that wants to add an item to a SynchronousQueue must wait
     * until another thread is ready to simultaneously take it off, with the same being true in reverse.
     *
     * @param <T>
     * @return A channel that is unbuffered
     */
    public static <T> Channel<T> channel() {
        return new Channel<>(new SynchronousQueue<>());
    }

    public static <T> Channel<T> channel(int size) {
        return new Channel<>(new ArrayBlockingQueue<>(size));
    }

    // equivalent to <- operator in go
    public void send(T item) {
        if (isClosed.get()) {
            throw new ChannelException("Channel is closed");
        }

        try {
            Future<Void> future = service.submit(put(item));
            future.get();
        } catch (InterruptedException | CancellationException | RejectedExecutionException e) {
            handleException(e);
        } catch (ExecutionException e) {
            throw new ChannelException(e.getMessage());
        }
    }

    // equivalent to -> operator in go
    public T receive() {
        if (isClosed.get()) {
            return null;
        }

        try {
            Future<T> future = service.submit(take());
            return future.get();
        } catch (InterruptedException | CancellationException | RejectedExecutionException e) {
            return handleException(e);
        } catch (ExecutionException e) {
            throw new ChannelException(e.getMessage());
        }
    }

    public ChannelResult<T> result() {
        T item = receive();
        return new ChannelResult<>(item, item == null);
    }

    public void close() {
        if (isClosed.getAndSet(true)) {
            throw new ChannelException("Channel already closed");
        }

        service.shutdownNow(); // will cause Thread blocked on items.take() to be interrupted

        try {
            boolean isShutdown = service.awaitTermination(1000, TimeUnit.MILLISECONDS);
            if (!isShutdown) {
                throw new ChannelException("Failed to cleanly shut down channel: executor could not be terminated");
            }
        } catch (InterruptedException e) {
            throw new ChannelException("Failed to cleanly shut down channel: " + e.getMessage());
        }
    }

    private Callable<T> take() {
        return () -> {
            try {
                return items.take();
            } catch (InterruptedException e) {
                return handleException(e);
            }
        };
    }

    private Callable<Void> put(T item) {
        return () -> {
            try {
                items.put(item);
                return null;
            } catch (InterruptedException e) {
                handleException(e);
                return null;
            }
        };
    }

    private T handleException(Exception e) {
        if (isClosed.get()) {
            return null;
        } else {
            throw new ChannelException("Channel failure: " + e.getMessage());
        }
    }

    public static class ChannelResult<T> {

        private final T result;
        private final boolean isClosed;

        public ChannelResult(T result, boolean isClosed) {
            this.result = result;
            this.isClosed = isClosed;
        }

        public T result() {
            return result;
        }

        public boolean isClosed() {
            return isClosed;
        }

    }
}
