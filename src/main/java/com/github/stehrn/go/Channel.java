package com.github.stehrn.go;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * When data needs to be shared between routines, a {@code Channel} will act as a facilitator, and guarantee the synchronous
 * communication and exchange of data between routines.
 * <p>
 * When declaring a {@code Channel}, the type of data that will be shared needs to be specified, and can be done using one
 * of the two {@code channel} factory methods, to create either a buffered or unbuffered channel.
 * <p>
 * Unbuffered channel of strings
 * <pre> {@code
 * Channel<String> unbuffered = channel();
 * }</pre>
 * <p>
 * Buffered channel of strings
 * <pre> {@code
 * Channel<String> buffered = channel(10);
 * }</pre>
 * <p>
 * To send a value into a channel use {@code send(T value)}:
 *
 * <pre> {@code
 * Channel<String> unbuffered = channel();
 * unbuffered.send("message");
 * }</pre>
 * <p>
 * To receive the above string from the channel use {@code receive()}:
 *
 * <pre> {@code
 * String message = unbuffered.receive();
 * assertThat(message, is("message"))
 * }</pre>
 *
 * An {@code ExecutorService} is used to help control closing the channel and the interruption of any threads
 * currently blocked; care is also taken not to interrupt the borrowed thread making the blocking call.
 */
public class Channel<T> {

    private final BlockingQueue<T> values;
    private final AtomicBoolean isClosed = new AtomicBoolean(false);
    private final ExecutorService service = Executors.newCachedThreadPool();

    private Channel(BlockingQueue<T> values) {
        this.values = values;
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

    /**
     * Send a value into the channel (equivalent to @code{channel <- "message"} in go)
     *
     * @param value value to send into channel
     */
    public void send(T value) {
        if (isClosed.get()) {
            throw new ChannelException("Channel is closed");
        }

        try {
            Future<Void> future = service.submit(put(value));
            future.get();
        } catch (InterruptedException | CancellationException | RejectedExecutionException e) {
            handleException(e);
        } catch (ExecutionException e) {
            throw new ChannelException(e.getMessage());
        }
    }

    /**
     * Receive a value from the channel (equivalent to @code{message := <-channel} in go)
     * @return value from channel
     */
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

    /**
     * Returns channel result from channel
     * @return channel result from channel
     */
    public ChannelResult<T> result() {
        T value = receive();
        return new ChannelResult<>(value, value == null);
    }

    /**
     * Close the channel, any threads blocking on one of methods to send or receive a value from the channel
     * will become unblocked.
     *
     * A call to {@code receive()} will immediately return null, whist a call to {@code result()} will return
     * a result with a null value and a call to {@code ChannelResult.isClosed()} will return {@code true}
     */
    public void close() {
        if (isClosed.getAndSet(true)) {
            throw new ChannelException("Channel already closed");
        }

        service.shutdownNow(); // will cause Thread blocked on items.take() and/or items.out() to be interrupted

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
                return values.take(); // this can block
            } catch (InterruptedException e) {
                return handleException(e);
            }
        };
    }

    private Callable<Void> put(T value) {
        return () -> {
            try {
                values.put(value); // this can block
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
