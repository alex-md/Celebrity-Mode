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

    public MovementTrail() {
        this(80);
    }

    public MovementTrail(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity");
        this.capacity = capacity;
    }

    public void update(WorldPoint point) {
        if (point == null || point.equals(points.peekFirst())) return;
        points.addFirst(point);
        while (points.size() > capacity) points.removeLast();
    }

    public WorldPoint getDelayedPoint(int delay) {
        if (points.isEmpty()) return null;
        int i = 0;
        for (WorldPoint p : points) if (i++ >= Math.max(0, delay)) return p;
        return points.peekLast();
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
    }
}
