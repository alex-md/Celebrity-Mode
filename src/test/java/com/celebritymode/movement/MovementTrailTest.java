package com.celebritymode.movement;

import static org.junit.Assert.*;

import net.runelite.api.coords.WorldPoint;

import org.junit.Test;

public class MovementTrailTest {
    private WorldPoint p(int x) {
        return new WorldPoint(x, 3200, 0);
    }

    @Test
    public void uniqueBoundedHistoryAndDelays() {
        MovementTrail trail = new MovementTrail(3);
        trail.update(p(1));
        trail.update(p(1));
        assertEquals(1, trail.size());
        trail.update(p(2));
        trail.update(p(3));
        trail.update(p(4));
        assertEquals(3, trail.size());
        assertEquals(p(4), trail.getLatest());
        assertEquals(p(3), trail.getDelayedPoint(1));
        assertEquals(p(2), trail.getDelayedPoint(100));
        assertEquals(p(4), trail.getDelayedPoint(-1));
        trail.clear();
        assertEquals(0, trail.size());
        assertNull(trail.getLatest());
        assertNull(trail.getDelayedPoint(1));
    }

    @Test
    public void planeChangeIsMeaningful() {
        MovementTrail t = new MovementTrail();
        t.update(p(1));
        t.update(new WorldPoint(1, 3200, 1));
        assertEquals(2, t.size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidCapacity() {
        new MovementTrail(0);
    }
}
