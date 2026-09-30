package com.celebritymode.fan;

import static org.junit.Assert.*;

import org.junit.Test;

import java.util.*;

public class CrowdArrivalTest {
    @Test
    public void spotterFriendsAndBranchingWavesMatchOsrsTiming() {
        assertEquals(104, CrowdArrival.dueTick(100, 0));
        int[][] waves = {{1, 2, 18, 29}, {3, 6, 34, 49}, {7, 14, 50, 66}, {15, 29, 66, 83}};
        int latest = 0;
        for (int[] wave : waves) {
            for (int ordinal = wave[0]; ordinal <= wave[1]; ordinal++) {
                int delay = CrowdArrival.dueTick(100, ordinal) - 100;
                assertTrue(
                        "Fan " + ordinal + " delay " + delay, delay >= wave[2] && delay <= wave[3]);
                latest = Math.max(latest, delay);
            }
        }
        assertTrue("Full crowd at 45–50 seconds", latest >= 75 && latest <= 85);
    }

    @Test
    public void jitterIsBoundedDeterministicAndDoesNotConsumeRandomState() {
        Set<Integer> jitters = new HashSet<>();
        Set<Integer> lateTicks = new HashSet<>();
        for (int ordinal = 1; ordinal < CrowdArrival.MAX_FANS; ordinal++) {
            int generation = 31 - Integer.numberOfLeadingZeros(ordinal + 1);
            int genStart = (1 << generation) - 1;
            int base =
                    4
                            + 16 * generation
                            + (int)
                                    Math.round(
                                            (double) (ordinal - genStart) / (1 << generation) * 14);
            int due = CrowdArrival.dueTick(0, ordinal);
            int jitter = due - base;
            assertTrue(jitter >= -2 && jitter <= 2);
            assertEquals(due, CrowdArrival.dueTick(0, ordinal));
            assertEquals(due + 5000, CrowdArrival.dueTick(5000, ordinal));
            assertTrue(due > CrowdArrival.dueTick(0, 0));
            jitters.add(jitter);
            if (ordinal >= 15) lateTicks.add(due);
        }
        assertTrue("Varied reaction delays", jitters.size() >= 4);
        assertTrue("Compounding final wave includes shared ticks", lateTicks.size() < 15);
    }

    @Test
    public void rejectsOrdinalsOutsideTheCrowdLimit() {
        for (int ordinal : new int[] {-1, 30, Integer.MAX_VALUE}) {
            try {
                CrowdArrival.dueTick(0, ordinal);
                fail("Accepted invalid ordinal " + ordinal);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains("0 and 29"));
            }
        }
    }
}
