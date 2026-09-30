package com.celebritymode.movement;

import static org.junit.Assert.*;

import com.celebritymode.TestScene;
import com.celebritymode.fan.*;

import net.runelite.api.*;
import net.runelite.api.coords.*;

import org.junit.Test;

import java.util.*;

public class FormationPlannerTest {
    private FanEntity fan(int id) {
        return new FanEntity(id, FanPersonality.EXCITED, "Fan" + id, id + 1, 0);
    }

    @Test
    public void thirtyUniqueSlotsAndPersonalSpace() {
        TestScene scene = new TestScene();
        FormationPlanner planner = new FormationPlanner(scene.config, scene.collision);
        for (FormationStyle style : FormationStyle.values()) {
            scene.config.formation = style;
            List<FanEntity> fans = new ArrayList<>();
            for (int i = 0; i < 30; i++) fans.add(fan(i));
            planner.reassignSlots(fans);
            MovementTrail trail = new MovementTrail();
            trail.update(scene.playerPosition);
            Set<WorldPoint> reserved = new HashSet<>();
            for (int i = 0; i < fans.size(); i++) {
                assertEquals(i, fans.get(i).formationSlot);
                WorldPoint target =
                        planner.chooseTarget(
                                fans.get(i), scene.playerPosition, 0, 1, trail, reserved);
                assertNotNull(target);
                assertTrue(target.distanceTo(scene.playerPosition) >= 2);
                assertTrue(scene.collision.canOccupy(target));
            }
            assertEquals("Unique positions for " + style, 30, reserved.size());
        }
    }

    @Test
    public void travellingCrowdUsesDifferentDepthsAndSubTilePositions() {
        TestScene scene = new TestScene();
        FormationPlanner planner = new FormationPlanner(scene.config, scene.collision);
        MovementTrail trail = new MovementTrail();
        for (int y = 3210; y <= 3232; y++) trail.update(new WorldPoint(3232, y, 0));
        Set<Integer> depths = new HashSet<>(), jitters = new HashSet<>();
        Set<WorldPoint> reserved = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            FanEntity follower = fan(i);
            WorldPoint target =
                    planner.chooseTarget(follower, scene.playerPosition, 0, 1, trail, reserved);
            depths.add(target.getY());
            jitters.add(follower.tileJitterX * 100 + follower.tileJitterY);
        }
        assertTrue(depths.size() > 5);
        assertEquals(30, jitters.size());
    }

    @Test(timeout = 2000)
    public void blockedOffsetsFallBackToBreadcrumbWithoutUnboundedSearch() {
        TestScene scene = new TestScene();
        for (int[] row : scene.flags) Arrays.fill(row, CollisionDataFlag.BLOCK_MOVEMENT_FULL);
        scene.flags[32][32] = 0;
        MovementTrail trail = new MovementTrail();
        trail.update(scene.playerPosition);
        FormationPlanner planner = new FormationPlanner(scene.config, scene.collision);
        assertEquals(
                scene.playerPosition,
                planner.chooseTarget(fan(0), scene.playerPosition, 0, 1, trail, new HashSet<>()));
        scene.flags[32][32] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
        assertNull(
                planner.chooseTarget(fan(0), scene.playerPosition, 0, 1, trail, new HashSet<>()));
    }

    @Test
    public void wallAndDiagonalCornerChecks() {
        TestScene scene = new TestScene();
        WorldPoint p = scene.playerPosition;
        scene.flags[33][32] = CollisionDataFlag.BLOCK_MOVEMENT_WEST;
        assertFalse(scene.collision.canStep(p, FormationPlanner.offset(p, 1, 0)));
        scene.flags[33][32] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
        assertFalse(scene.collision.canStep(p, FormationPlanner.offset(p, 1, 1)));
        assertFalse(scene.collision.canStep(p, FormationPlanner.offset(p, 2, 0)));
    }

    @Test
    public void boundedDetourAvoidsBlockedTile() {
        TestScene scene = new TestScene();
        scene.flags[33][32] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
        List<WorldPoint> path =
                scene.movement.route(
                        scene.playerPosition, FormationPlanner.offset(scene.playerPosition, 3, 0));
        assertFalse(path.isEmpty());
        WorldPoint previous = scene.playerPosition;
        for (WorldPoint step : path) {
            assertTrue(scene.collision.canStep(previous, step));
            previous = step;
        }
    }
}
