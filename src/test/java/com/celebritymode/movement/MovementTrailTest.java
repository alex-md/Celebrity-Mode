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
    public void resumedFormationDelaysStayOnNewLegWhileOldRoutingPointsSurvive() {
        MovementTrail trail = new MovementTrail(5);
        for (int x = 1; x <= 5; x++) trail.update(p(x));
        trail.beginTravelLeg();
        trail.update(p(5)); // An idle tick must not lengthen the new leg.
        assertEquals(p(5), trail.getFollowingPoint(100));
        assertEquals(p(1), trail.getDelayedPoint(100));
        trail.update(p(6));
        trail.update(p(7));
        assertEquals(p(7), trail.getFollowingPoint(0));
        assertEquals(p(6), trail.getFollowingPoint(1));
        assertEquals(p(5), trail.getFollowingPoint(100));
        assertEquals(p(3), trail.getDelayedPoint(100));
        for (int x = 8; x <= 12; x++) trail.update(p(x));
        assertEquals(p(8), trail.getFollowingPoint(100));
        trail.clear();
        assertNull(trail.getFollowingPoint(100));
        trail.update(p(1));
        trail.update(p(2));
        assertEquals(p(1), trail.getFollowingPoint(100));
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
