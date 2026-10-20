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
    public void personalSpaceKeepsAdjacentFansWithoutForcingUniqueSlots() {
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
                assertNotEquals(scene.playerPosition, target);
                assertTrue(scene.collision.canOccupy(target));
            }
            assertTrue("Crowd shares tiles in " + style, reserved.size() < 30);
            assertTrue("Adjacent fans fill the old empty ring in " + style,
                    reserved.stream().anyMatch(p -> p.distanceTo(scene.playerPosition) == 1));
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
        assertTrue(depths.size() >= 4);
        assertEquals(30, jitters.size());
    }

    @Test
    public void maximumSpreadStillKeepsACloseCoreAndSpaceAllowsAdjacentTiles() {
        TestScene scene = new TestScene();
        scene.config.size = 18;
        scene.config.spread = 8;
        FormationPlanner planner = new FormationPlanner(scene.config, scene.collision);
        MovementTrail trail = new MovementTrail();
        trail.update(scene.playerPosition);
        Set<WorldPoint> reserved = new HashSet<>();
        int adjacent = 0;
        for (int i = 0; i < 18; i++) {
            WorldPoint target = planner.chooseTarget(fan(i), scene.playerPosition,
                    0, 1, trail, reserved, 20);
            if (target.distanceTo(scene.playerPosition) == 1) adjacent++;
            assertTrue("Spread does not inflate the stopped crowd", target.distanceTo(scene.playerPosition) <= 4);
        }
        assertTrue("No two-tile exclusion ring", adjacent >= 6);
        assertTrue("Natural tile sharing", reserved.size() < 18);
    }

    @Test
    public void idleFriendsStayNearTheirGroupWithShortWalksAndPauses() {
        TestScene scene = new TestScene();
        scene.config.size = 30;
        FormationPlanner planner = new FormationPlanner(scene.config, scene.collision);
        Random random = new Random(23);
        Set<WorldPoint> centers = new HashSet<>();
        for (int id = 0; id < 16; id++) {
            FanEntity friend = fan(id);
            friend.position = FormationPlanner.offset(scene.playerPosition, 4, 0);
            WorldPoint center = planner.idleGroupCenter(friend, scene.playerPosition);
            centers.add(center);
            assertEquals(center, planner.idleGroupCenter(fan(id / 4 * 4), scene.playerPosition));
            int moves = 0;
            for (int tick = 0; tick < 600; tick++) {
                WorldPoint old = friend.position;
                int deadline = friend.nextWanderTick;
                WorldPoint next = planner.chooseIdleTarget(friend, scene.playerPosition,
                        new HashSet<>(), random, tick);
                assertTrue(next.distanceTo(old) <= 2);
                assertTrue(next.distanceTo(scene.playerPosition) <= 6);
                assertNotEquals(scene.playerPosition, next);
                assertTrue(scene.collision.canOccupy(next));
                if (tick < deadline) assertEquals("Pause between clicks", old, next);
                if (!old.equals(next)) moves++;
                friend.position = next;
            }
            assertTrue("Regular but infrequent small walks: " + moves, moves >= 5 && moves <= 30);
            assertTrue("Returns to friends rather than diffusing away", friend.position.distanceTo(center) <= 3);
        }
        assertTrue("Several congregations rather than a single pile", centers.size() >= 3);
    }

    @Test
    public void fullIdleBudgetHoldsPositionsButLetsExistingWalkFinish() {
        TestScene scene = new TestScene();
        FormationPlanner planner = new FormationPlanner(scene.config, scene.collision);
        FanEntity friend = fan(0);
        friend.position = FormationPlanner.offset(scene.playerPosition, 2, 0);
        Random random = new Random(2);
        assertEquals(friend.position, planner.chooseIdleTarget(friend, scene.playerPosition,
                new HashSet<>(), random, 100, false));
        assertEquals("Denied starts do not postpone someone's chance", 0, friend.nextWanderTick);
        friend.idleTarget = FormationPlanner.offset(friend.position, 1, 0);
        assertEquals(friend.idleTarget, planner.chooseIdleTarget(friend, scene.playerPosition,
                new HashSet<>(), random, 100, false));
        friend.state = FanState.REACTION;
        assertEquals("Emoting fans hold still", friend.position,
                planner.chooseIdleTarget(friend, scene.playerPosition, new HashSet<>(), random, 100));
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
