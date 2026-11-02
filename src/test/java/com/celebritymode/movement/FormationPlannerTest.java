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
    public void idleSpreadAndPersonalSpaceHaveVisibleEffectsWithoutMovingPlayer() {
        TestScene scene = new TestScene();
        FormationPlanner planner = new FormationPlanner(scene.config, scene.collision);
        MovementTrail trail = new MovementTrail();
        trail.update(scene.playerPosition);
        double[] distances = new double[2];
        for (int pass = 0; pass < 2; pass++) {
            scene.config.spread = pass == 0 ? 1 : 8;
            Set<WorldPoint> reserved = new HashSet<>();
            for (int i = 0; i < 20; i++) {
                WorldPoint point = planner.chooseTarget(fan(i), scene.playerPosition, 0, 1, trail, reserved, 500);
                assertTrue(point.distanceTo(scene.playerPosition) <= scene.config.spread);
                assertNotEquals(scene.playerPosition, point);
                distances[pass] += point.distanceTo(scene.playerPosition);
            }
            if (pass == 1) assertTrue("Varied approach points without assigned slots", reserved.size() >= 8);
        }
        assertTrue("Large spread creates a visibly wider idle crowd", distances[1] > distances[0] * 2);
        scene.config.spread = 2;
        scene.config.space = false;
        Set<WorldPoint> stacked = new HashSet<>();
        for (int i = 0; i < 20; i++) planner.chooseTarget(fan(i), scene.playerPosition, 0, 1, trail, stacked, 500);
        scene.config.space = true;
        Set<WorldPoint> spaced = new HashSet<>();
        for (int i = 0; i < 20; i++) planner.chooseTarget(fan(i), scene.playerPosition, 0, 1, trail, spaced, 500);
        assertTrue("Space uses more of the available patch", spaced.size() > stacked.size());
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
    public void stationaryArrivalsUseConfiguredSpreadWithoutAssignedSlots() {
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
            assertTrue("Spread does not inflate the stopped crowd", target.distanceTo(scene.playerPosition) <= 8);
        }
        assertTrue("Adjacent tiles remain available", adjacent >= 1);
        assertTrue("Many possible local approach tiles", reserved.size() >= 8);
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
                if (tick < deadline && planner.validSettledTarget(old, scene.playerPosition))
                    assertEquals("Pause between clicks", old, next);
                if (!old.equals(next)) moves++;
                friend.position = next;
            }
            assertTrue("Regular but infrequent small walks: " + moves, moves >= 1 && moves <= 18);
            assertTrue("Returns to friends rather than diffusing away", friend.position.distanceTo(center) <= 3);
        }
        assertTrue("Several congregations rather than a single pile", centers.size() >= 3);
    }

    @Test
    public void personalPauseHoldsPositionAndExistingWalkFinishesBeforeAnotherDecision() {
        TestScene scene = new TestScene();
        FormationPlanner planner = new FormationPlanner(scene.config, scene.collision);
        FanEntity friend = fan(0);
        friend.position = FormationPlanner.offset(scene.playerPosition, 2, 0);
        Random random = new Random(2);
        friend.nextWanderTick = 200;
        assertEquals(friend.position, planner.chooseIdleTarget(friend, scene.playerPosition,
                new HashSet<>(), random, 100));
        assertEquals("Personal pause is preserved", 200, friend.nextWanderTick);
        friend.idleTarget = FormationPlanner.offset(scene.playerPosition, 1, 1);
        assertEquals(friend.idleTarget, planner.chooseIdleTarget(friend, scene.playerPosition,
                new HashSet<>(), random, 100));
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

    @Test
    public void idleGatheringRemainsActiveWithoutMakingTheWholeTrailingCrowdMoveAtOnce() {
        TestScene scene = new TestScene();
        scene.holdVisitors = true;
        scene.config.size = 20;
        scene.config.arrival = ArrivalPace.INSTANT;
        scene.populate();
        for (int tick = 0; tick < 12; tick++) {
            scene.playerPosition = FormationPlanner.offset(scene.playerPosition, 0, 2);
            scene.tick();
        }
        int wanderingTicks = 0;
        for (int tick = 0; tick < 240; tick++) {
            scene.tick();
            int walkers = 0;
            for (FanEntity fan : scene.manager.getFans())
                if (fan.idleTarget != null && fan.movementPath != null) walkers++;
            assertTrue("No whole-crowd marching during idle wandering", walkers < 20);
            if (walkers > 0) wanderingTicks++;
        }
        assertTrue("Bored fans still wander and approach their groups", wanderingTicks > 10);
        scene.manager.cleanup();
    }

    @Test
    public void runAndStopLetsIndividualsLeaveTheTrailAndFindViewingSpots() {
        for (FormationStyle style : FormationStyle.values())
            for (int[] direction : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                TestScene scene = new TestScene();
                scene.holdVisitors = true;
                scene.config.size = 20;
                scene.config.arrival = ArrivalPace.INSTANT;
                scene.config.formation = style;
                scene.populate();
                for (int tick = 0; tick < 12; tick++) {
                    scene.playerPosition = FormationPlanner.offset(scene.playerPosition, direction[0] * 2, direction[1] * 2);
                    scene.tick();
                }
                Map<Integer, Integer> lastStep = new HashMap<>();
                Set<Integer> noticeTimes = new HashSet<>();
                boolean mixed = false;
                for (int tick = 0; tick < 23; tick++) {
                    Map<Integer, WorldPoint> before = new HashMap<>();
                    for (FanEntity fan : scene.manager.getFans()) before.put(fan.id, fan.position);
                    scene.tick();
                    int moving = 0;
                    for (FanEntity fan : scene.manager.getFans()) {
                        noticeTimes.add(fan.stopDecisionTick);
                        assertNotNull(fan.position);
                        assertTrue(scene.collision.canOccupy(fan.position));
                        if (!before.get(fan.id).equals(fan.position)) lastStep.put(fan.id, tick);
                        if (fan.movementPath != null) moving++;
                        assertNull("No crowd-wide social shuffle at the stop", fan.idleTarget);
                    }
                    mixed |= moving > 0 && moving < 20;
                }
                int behind = 0, close = 0;
                for (FanEntity fan : scene.manager.getFans()) {
                    int dx = fan.position.getX() - scene.playerPosition.getX();
                    int dy = fan.position.getY() - scene.playerPosition.getY();
                    if (dx * direction[0] + dy * direction[1] < 0) behind++;
                    if (fan.position.distanceTo(scene.playerPosition) <= 2) close++;
                    assertNull(fan.movementPath);
                }
                assertTrue("The stopped crowd is no longer locked behind the leader in " + style, behind < 16);
                assertTrue("Fans approach their own nearby viewing spots", close >= 15);
                assertTrue("Individual stop responses", noticeTimes.size() >= 5);
                assertTrue("Different completion times", new HashSet<>(lastStep.values()).size() >= 4);
                assertTrue("Some catch up while others are already settled", mixed);
                scene.manager.cleanup();
                assertTrue(scene.active.isEmpty());
            }
    }

    @Test
    public void stoppedTargetsUseNearbySpaceOnBothSidesOfThePlayer() {
        TestScene scene = new TestScene();
        scene.config.size = 30;
        scene.config.spread = 2;
        FormationPlanner planner = new FormationPlanner(scene.config, scene.collision);
        MovementTrail trail = new MovementTrail();
        for (int y = -20; y <= 0; y++) trail.update(FormationPlanner.offset(scene.playerPosition, 0, y));
        Set<WorldPoint> reserved = new HashSet<>();
        for (int i = 0; i < 30; i++) {
            FanEntity fan = new FanEntity(i, FanPersonality.EXCITED, "Fan" + i, 1 + i % 10, 0);
            WorldPoint target = planner.chooseTarget(fan, scene.playerPosition, 0, 1, trail, reserved, 10);
            assertTrue(planner.validSettledTarget(target, scene.playerPosition));
            assertNotEquals(scene.playerPosition, target);
        }
        assertTrue("Tile sharing is normal for following players", reserved.size() < 25);
        assertTrue(reserved.stream().anyMatch(p -> p.getY() > scene.playerPosition.getY()));
        assertTrue(reserved.stream().anyMatch(p -> p.getY() < scene.playerPosition.getY()));
    }
}
