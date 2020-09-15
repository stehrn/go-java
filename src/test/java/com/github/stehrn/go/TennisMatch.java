package com.github.stehrn.go;

import com.github.stehrn.go.Channel.ChannelResult;

import java.util.concurrent.ThreadLocalRandom;

import static com.github.stehrn.go.Channel.channel;
import static com.github.stehrn.go.Routine.go;

/**
 * A simple tennis game demonstrating use of an unbuffered channel
 */
public class TennisMatch {

    private final Channel<Integer> court = channel();

    private void start(String player1, String player2) {
        // set up the 2 players
        go(() -> player(player1, court));
        go(() -> player(player2, court));
        // start the game
        court.send(1);
    }

    private void player(String name, Channel<Integer> court) {
        while (true) {
            // wait for ball to be hit to us
            ChannelResult<Integer> result = court.result();
            if (result.isClosed()) {
                System.out.println("Player " + name + " won");
                break;
            }

            // see if we miss the ball
            if (ThreadLocalRandom.current().nextInt(100) % 13 == 0) {
                System.out.println("Player " + name + " missed");
                court.close();
                break;
            }

            int ball = result.result();
            System.out.println("Player " + name + " hit ball " + ball);

            // hit the ball back
            court.send(++ball);
        }
    }

    private void rain() {
        court.close();
    }

    public static void main(String[] args) {
        TennisMatch match = new TennisMatch();
        match.start("Nik", "Jeff");
    }
}
