package com.github.stehrn.go;

import com.github.stehrn.go.Channel.ChannelResult;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static com.github.stehrn.go.Channel.channel;
import static com.github.stehrn.go.Routine.go;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public class UnbufferedChannelTest {

    @Test(timeout = 500)
    public void sendAndReceive() {
        Channel<String> unbounded = channel();
        go(() -> unbounded.send("item"));
        assertThat(unbounded.receive(), is("item"));
    }

    @Test(timeout = 500)
    public void multipleSendAndReceive() throws InterruptedException {
        Channel<String> unbounded = channel();

        CountDownLatch latch1 = new CountDownLatch(1);
        CountDownLatch latch2 = new CountDownLatch(1);
        CountDownLatch latch3 = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(1);
        go(() -> {
            wait(latch1);
            unbounded.send("item 1");
            latch2.countDown();

        });
        go(() -> {
            wait(latch2);
            unbounded.send("item 2");
            latch3.countDown();
        });
        go(() -> {
            wait(latch3);
            unbounded.send("item 3");
        });

        latch1.countDown(); // start it off!

        go(() -> {
            wait(latch1);
            assertThat(unbounded.receive(), is("item 1"));
        });
        go(() -> {
            wait(latch2);
            assertThat(unbounded.receive(), is("item 2"));
        });
        go(() -> {
            wait(latch3);
            assertThat(unbounded.receive(), is("item 3"));
            done.countDown();
        });

        done.await();
    }

    @Test(timeout = 1000)
    public void multipleSendAndReceivePingPong() {
        CountDownLatch start = new CountDownLatch(1);
        Channel<Integer> unbounded = channel();
        CountDownLatch done = new CountDownLatch(1);
        List<String> play = Collections.synchronizedList(new ArrayList<>());
        go(() -> play("a", unbounded, done, start, play));
        go(() -> play("b", unbounded, done, start, play));
        unbounded.send(1);
        wait(done);
        assertThat(play.toString(), is("[a hits ball 1, b hits ball 2, a hits ball 3, b hits ball 4, a hits ball 5, a ends game]"));
    }

    private void play(String name, Channel<Integer> channel, CountDownLatch done, CountDownLatch start, List<String> play) {
        while (true) {
            if ("b".equals(name)) {
                wait(start);
            }
            ChannelResult<Integer> result = channel.result();
            if ("a".equals(name) && start.getCount() != 0) {
                start.countDown();
            }
            if (result.isClosed()) {
                break;
            }
            int item = result.result();
            play.add(name + " hits ball " + item);
            if (item == 5) {
                play.add(name + " ends game");
                channel.close();
                done.countDown();
                break;
            } else {
                channel.send(++item);
            }
        }
    }

    private void wait(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            // ignore
        }
    }

    @Test(timeout = 1000)
    public void singleReceiveWithNoSend() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Channel<String> unbounded = channel();
        go(() -> {
            assertNull(unbounded.receive());
            latch.countDown();
        });

        // confirm we get nothing back
        assertFalse(latch.await(500, TimeUnit.MILLISECONDS));

        // now close channel
        unbounded.close();
        // we expect to fall through to countdown
        latch.await();
    }

    @Test(timeout = 1000)
    public void multipleReceiveWithNoSend() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        Channel<String> unbounded = channel();
        go(() -> {
            assertNull(unbounded.receive());
            latch.countDown();
        });
        go(() -> {
            assertNull(unbounded.receive());
            latch.countDown();
        });

        // confirm we get nothing back
        assertFalse(latch.await(500, TimeUnit.MILLISECONDS));

        // now close channel
        unbounded.close();
        // we expect to fall through to countdown
        latch.await();
    }

    @Test(timeout = 1000)
    public void singleSendWithNoReceive() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        Channel<String> unbounded = channel();
        go(() -> {
            unbounded.send("item");
            latch.countDown();
        });

        // confirm we get nothing back
        assertFalse(latch.await(500, TimeUnit.MILLISECONDS));

        // now close channel
        unbounded.close();
        // we expect to fall through to countdown
        latch.await();
    }

    @Test(timeout = 1000)
    public void multipleSendWithNoReceive() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(2);
        Channel<String> unbounded = channel();
        go(() -> {
            unbounded.send("item 1");
            latch.countDown();
        });
        go(() -> {
            unbounded.send("item 2");
            latch.countDown();
        });

        // confirm we get nothing back
        assertFalse(latch.await(500, TimeUnit.MILLISECONDS));

        // now close channel
        unbounded.close();
        // we expect to fall through to countdown
        latch.await();
    }

    @Test(timeout = 1000)
    public void simpleTest() {
        Channel<String> unbounded = channel();
        go(() -> unbounded.send("A"));
        go(() -> unbounded.send("B"));
        go(() -> unbounded.send("C"));
        Set<String> results = new TreeSet<>();
        for (int i = 0; i < 3; i++) {
            results.add(unbounded.receive());
        }
        assertThat(results.toString(), is("[A, B, C]"));
    }
}
