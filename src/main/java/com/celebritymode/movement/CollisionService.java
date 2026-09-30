package com.celebritymode.movement;

import net.runelite.api.*;
import net.runelite.api.coords.*;

import javax.inject.Singleton;

@Singleton
public class CollisionService {
    private WorldView view;

    public void bind(WorldView view) {
        this.view = view;
    }

    public boolean canOccupy(WorldPoint point) {
        if (view == null || point == null || point.getPlane() != view.getPlane()) return false;
        LocalPoint p = LocalPoint.fromWorld(view, point);
        CollisionData[] maps = view.getCollisionMaps();
        if (p == null
                || maps == null
                || maps.length <= point.getPlane()
                || maps[point.getPlane()] == null) return false;
        int[][] flags = maps[point.getPlane()].getFlags();
        int x = p.getSceneX(), y = p.getSceneY();
        return x >= 0
                && x < flags.length
                && y >= 0
                && y < flags[x].length
                && (flags[x][y] & CollisionDataFlag.BLOCK_MOVEMENT_FULL) == 0;
    }

    public boolean canStep(WorldPoint from, WorldPoint to) {
        if (!canOccupy(from) || !canOccupy(to) || from.getPlane() != to.getPlane()) return false;
        int dx = to.getX() - from.getX(), dy = to.getY() - from.getY();
        if (Math.abs(dx) > 1 || Math.abs(dy) > 1) return false;
        return new WorldArea(from, 1, 1).canTravelInDirection(view, dx, dy);
    }
}
