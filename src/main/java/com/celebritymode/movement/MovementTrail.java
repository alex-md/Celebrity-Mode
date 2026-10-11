package com.celebritymode.movement;

import net.runelite.api.coords.WorldPoint;

import java.util.*;

/**
 * Scene-space world points: never canonical instance-template coordinates. Cleared on scene
 * changes.
 */
public final class MovementTrail {
    private final Deque<WorldPoint> points = new ArrayDeque<>();
    private final int capacity;
    private int travelLength;

    public MovementTrail() {
        this(80);
    }

    public MovementTrail(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
        travelLength = capacity;
    }

    public void update(WorldPoint point) {
        if (point == null || point.equals(points.peekFirst())) return;
        points.addFirst(point);
        travelLength = Math.min(capacity, travelLength + 1);
        while (points.size() > capacity) points.removeLast();
    }

    public WorldPoint getDelayedPoint(int delay) {
        if (points.isEmpty()) return null;
        int i = 0;
        for (WorldPoint p : points) if (i++ >= Math.max(0, delay)) return p;
        return points.peekLast();
    }

    /** Start formation delays at the stop position without discarding collision-routing history. */
    public void beginTravelLeg() {
        travelLength = points.isEmpty() ? 0 : 1;
    }

    public WorldPoint getFollowingPoint(int delay) {
        return getDelayedPoint(Math.min(Math.max(0, delay), Math.max(0, travelLength - 1)));
    }

    public WorldPoint getLatest() {
        return points.peekFirst();
    }

    public int size() {
        return points.size();
    }

    public List<WorldPoint> snapshot() {
        return new ArrayList<>(points);
    }

    public void clear() {
        points.clear();
        travelLength = capacity;
    }
}
