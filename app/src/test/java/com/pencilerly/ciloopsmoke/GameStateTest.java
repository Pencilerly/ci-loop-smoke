package com.pencilerly.ciloopsmoke;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Random;

import org.junit.Test;

/**
 * Unit tests for {@link GameState}. Runs on the plain JVM (testDebugUnitTest).
 */
public class GameStateTest {

    @Test
    public void initialScoreIsZero() {
        GameState state = new GameState(new Random(0L));
        assertEquals(0, state.getScore());
    }

    @Test
    public void tappingIncrementsScore() {
        GameState state = new GameState(new Random(0L));

        assertEquals(1, state.tap(100, 100));
        assertEquals(2, state.tap(100, 100));
        assertEquals(3, state.tap(100, 100));

        assertEquals(3, state.getScore());
    }

    @Test
    public void tappingMovesTargetWithinBounds() {
        GameState state = new GameState(new Random(42L));

        for (int i = 0; i < 50; i++) {
            state.tap(200, 150);
            assertTrue(state.getTargetX() >= 0 && state.getTargetX() < 200);
            assertTrue(state.getTargetY() >= 0 && state.getTargetY() < 150);
        }
    }

    @Test
    public void randomizeTargetDoesNotChangeScore() {
        GameState state = new GameState(new Random(7L));
        state.randomizeTarget(80, 80);
        assertEquals(0, state.getScore());
    }
}
