package com.celebritymode.fan;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class CrowdPopulationTest {
    @Test
    public void slowTurnoverNeverExceedsMaximumAndMovesInBothDirections() {
        CrowdPopulation population = new CrowdPopulation();
        Random random = new Random(103);
        for (int cap : new int[] {1, 8, 30, 100}) {
            population.clear();
            int previous = cap, changes = 0;
            boolean joined = false, left = false;
            for (int tick = 0; tick < 20000; tick++) {
                int count = population.update(tick, cap, tick % 4000 < 2000 ? 0 : 40,
                        tick % 4000 < 2000 ? 4 : 20, random);
                assertTrue(count >= Math.max(1, cap - (int) Math.ceil(cap * 0.08)));
                assertTrue(count <= cap);
                if (tick < 600) assertEquals(cap, count);
                assertTrue(Math.abs(count - previous) <= 1);
                if (count != previous) changes++;
                joined |= count > previous;
                left |= count < previous;
                previous = count;
            }
            if (cap > 1) {
                assertTrue(joined && left);
                assertTrue("Turnover is subtle", changes < 250);
            }
        }
        assertEquals(3, population.update(21000, 3, 0, 4, random));
        assertEquals(100, population.update(21001, Integer.MAX_VALUE, 0, 4, random));
    }

    @Test
    public void localActivityAndTimeOfDayInfluenceAttendance() {
        CrowdPopulation busy = new CrowdPopulation(), quiet = new CrowdPopulation();
        Random busyRandom = new Random(17), quietRandom = new Random(17);
        int busyTotal = 0, quietTotal = 0;
        for (int tick = 0; tick < 10000; tick++) {
            busyTotal += busy.update(tick, 100, 40, 20, busyRandom);
            quietTotal += quiet.update(tick, 100, 0, 8, quietRandom);
        }
        assertTrue(busyTotal > quietTotal);
    }
}
