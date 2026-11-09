package com.pencilerly.ciloopsmoke;

import java.util.Objects;
import java.util.Random;

/**
 * Pure-Java game state for the tap-the-target game.
 *
 * <p>This class deliberately has no Android dependencies so that its logic can be
 * exercised by plain JVM unit tests (see {@code GameStateTest}).

 * <p>Scoring rule: every tap increments the score by one and relocates the target
 * to a new random position within the supplied bounds.
 */
public final class GameState {

    private final Random random;

    private int score;
    private int targetX;
    private int targetY;

    public GameState() {
        this(new Random());
    }

    /** Visible for tests so that target placement can be made deterministic. */
    public GameState(Random random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /** @return the current score, starting at 0. */
    public int getScore() {
        return score;
    }

    public int getTargetX() {
        return targetX;
    }

    public int getTargetY() {
        return targetY;
    }

    /**
     * Register a successful hit: increase the score by one and move the target.
     *
     * @param boundX upper bound (exclusive) for the horizontal position; non-positive keeps X at 0
     * @param boundY upper bound (exclusive) for the vertical position; non-positive keeps Y at 0
     * @return the updated score
     */
    public int tap(int boundX, int boundY) {
        score++;
        randomizeTarget(boundX, boundY);
        return score;
    }

    /**
     * Relocate the target without changing the score. Used for the initial placement.
     */
    public void randomizeTarget(int boundX, int boundY) {
        targetX = boundX > 0 ? random.nextInt(boundX) : 0;
        targetY = boundY > 0 ? random.nextInt(boundY) : 0;
    }
}
