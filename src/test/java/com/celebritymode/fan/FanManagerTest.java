package com.celebritymode.fan;

import static org.junit.Assert.*;

import com.celebritymode.TestScene;
import com.celebritymode.movement.*;

import net.runelite.api.*;
import net.runelite.api.coords.*;

import org.junit.Test;

import java.util.*;

public class FanManagerTest {
    @Test
    public void sizesResizeAndCleanShutdown() {
        TestScene scene = new TestScene();
        for (int size : new int[] {1, 8, 30, 8, 1}) {
            scene.config.size = size;
            scene.populate();
            assertEquals(size, scene.active.size());
            assertEquals(size, scene.manager.getFans().size());
        }
        scene.manager.cleanup();
        assertTrue(scene.active.isEmpty());
        assertTrue(scene.manager.getFans().isEmpty());
        assertNull(scene.manager.getView());
    }

    @Test
    public void persistentModelsAndTargetedConfigChanges() {
        TestScene scene = new TestScene();
        scene.populate();
        int builds = scene.modelBuilds;
        RuneLiteObject object = scene.manager.getFans().get(0).object;
        for (int i = 0; i < 20; i++) scene.tick();
        assertEquals(builds, scene.modelBuilds);
        scene.manager.onConfigChanged("customQuotes");
        scene.manager.onConfigChanged("formationStyle");
        scene.tick();
        assertSame(object, scene.manager.getFans().get(0).object);
        assertEquals(builds, scene.modelBuilds);
        scene.config.size = 30;
        scene.manager.onConfigChanged("crowdSize");
        scene.tick();
        assertEquals(8, scene.active.size());
        scene.populate();
        assertEquals(30, scene.active.size());
        assertEquals(30, scene.modelBuilds);
        scene.manager.onConfigChanged("crowdGearTier");
        assertTrue(scene.active.isEmpty());
        scene.tick();
        assertEquals(30, scene.active.size());
        assertEquals(60, scene.modelBuilds);
        scene.manager.cleanup();
        assertTrue(scene.active.isEmpty());
    }

    @Test
    public void teleportPlaneAndWorldViewChanges() {
        TestScene scene = new TestScene();
        scene.populate();
        for (int transition = 0; transition < 4; transition++) {
            List<RuneLiteObject> old = new ArrayList<>();
            for (FanEntity fan : scene.manager.getFans()) old.add(fan.object);
            if (transition == 0)
                scene.playerPosition = FormationPlanner.offset(scene.playerPosition, 20, 0);
            if (transition == 1) {
                scene.plane = 1;
                scene.playerPosition =
                        new WorldPoint(scene.playerPosition.getX(), scene.playerPosition.getY(), 1);
            }
            if (transition == 2)
                scene.playerPosition = FormationPlanner.offset(scene.playerPosition, 15, 0);
            if (transition == 3) scene.viewId = 7;
            scene.tick();
            assertTrue(scene.active.isEmpty());
            assertEquals(1, scene.manager.getBreadcrumbs().size());
            for (RuneLiteObject object : old) assertFalse(object.isActive());
            scene.populate();
            assertEquals(8, scene.active.size());
            for (FanEntity fan : scene.manager.getFans())
                assertEquals(scene.viewId, fan.renderLocation.getWorldView());
        }
        scene.state = GameState.HOPPING;
        scene.manager.onClientTick();
        assertTrue(scene.active.isEmpty());
        for (FanEntity fan : scene.manager.getFans()) {
            assertNull(fan.renderLocation);
            assertNull(fan.movementPath);
        }
        scene.state = GameState.LOGGED_IN;
        scene.tick();
        assertTrue(scene.active.isEmpty());
        scene.populate();
        assertEquals(8, scene.active.size());
        scene.manager.onGameTick(null);
        assertTrue(scene.active.isEmpty());
        scene.populate();
        assertEquals(8, scene.active.size());
    }

    @Test
    public void regionLoadingRebasesWithoutReplacingObjectsOrRestartingArrival() {
        TestScene scene = new TestScene();
        scene.config.size = 30;
        scene.populate();
        List<RuneLiteObject> objects = new ArrayList<>();
        List<Integer> deadlines = new ArrayList<>();
        List<WorldPoint> positions = new ArrayList<>();
        for (FanEntity fan : scene.manager.getFans()) {
            objects.add(fan.object);
            deadlines.add(fan.arrivalTick);
            positions.add(fan.position);
        }
        int history = scene.manager.getBreadcrumbs().size(), builds = scene.modelBuilds;
        scene.state = GameState.LOADING;
        scene.manager.onGameStateChanged(GameState.LOADING);
        assertTrue(scene.active.isEmpty());
        for (FanEntity fan : scene.manager.getFans()) {
            assertTrue(fan.arrived);
            assertNull(fan.renderLocation);
            assertNotNull(fan.position);
        }
        scene.baseX += 8;
        scene.playerPosition = FormationPlanner.offset(scene.playerPosition, 2, 0);
        scene.state = GameState.LOGGED_IN;
        scene.tick();
        assertEquals(30, scene.active.size());
        assertTrue(scene.manager.getBreadcrumbs().size() > history);
        assertEquals(builds, scene.modelBuilds);
        for (int i = 0; i < 30; i++) {
            FanEntity fan = scene.manager.getFans().get(i);
            assertSame(objects.get(i), fan.object);
            assertEquals((int) deadlines.get(i), fan.arrivalTick);
            assertEquals(
                    LocalPoint.fromWorld(scene.view, positions.get(i)).getSceneX(),
                    fan.renderLocation.getSceneX());
        }
        // A base shift noticed first on ClientTick follows the same preservation path.
        scene.baseY += 8;
        scene.manager.onClientTick();
        assertTrue(scene.active.isEmpty());
        scene.tick();
        assertEquals(30, scene.active.size());
        for (int i = 0; i < 30; i++)
            assertSame(objects.get(i), scene.manager.getFans().get(i).object);
        scene.manager.cleanup();
        assertTrue(scene.active.isEmpty());
    }

    @Test
    public void longRunAcrossRepeatedRegionBoundariesKeepsTheSameCrowd() {
        TestScene scene = new TestScene();
        scene.config.size = 30;
        scene.populate();
        List<RuneLiteObject> objects = new ArrayList<>();
        for (FanEntity fan : scene.manager.getFans()) objects.add(fan.object);
        int builds = scene.modelBuilds;
        for (int tick = 0; tick < 100; tick++) {
            scene.playerPosition = FormationPlanner.offset(scene.playerPosition, 2, 0);
            if (tick % 4 == 3) {
                scene.state = GameState.LOADING;
                scene.manager.onGameStateChanged(GameState.LOADING);
                scene.baseX += 8;
                scene.state = GameState.LOGGED_IN;
            }
            scene.tick();
            assertEquals(30, scene.active.size());
            for (int i = 0; i < 30; i++)
                assertSame(
                        "tick="
                                + tick
                                + " fan="
                                + i
                                + " pos="
                                + scene.manager.getFans().get(i).position
                                + " target="
                                + scene.manager.getFans().get(i).target,
                        objects.get(i),
                        scene.manager.getFans().get(i).object);
        }
        assertEquals(builds, scene.modelBuilds);
        assertEquals(80, scene.manager.getBreadcrumbs().size());
        scene.manager.cleanup();
        assertTrue(scene.active.isEmpty());
    }

    @Test
    public void aRealTeleportAfterLoadingStillRestartsArrivals() {
        TestScene scene = new TestScene();
        scene.populate();
        scene.state = GameState.LOADING;
        scene.manager.onGameStateChanged(GameState.LOADING);
        scene.baseX += 8;
        scene.playerPosition = FormationPlanner.offset(scene.playerPosition, 30, 0);
        scene.state = GameState.LOGGED_IN;
        scene.tick();
        assertTrue(scene.active.isEmpty());
        assertEquals(1, scene.manager.getBreadcrumbs().size());
        scene.populate();
        assertEquals(8, scene.active.size());
    }

    @Test
    public void inactiveIndividualRepairsWithoutReplacingCrowd() {
        TestScene scene = new TestScene();
        scene.populate();
        RuneLiteObject survivor = scene.manager.getFans().get(1).object;
        scene.manager.getFans().get(0).object.setActive(false);
        scene.tick();
        assertEquals(8, scene.active.size());
        assertSame(survivor, scene.manager.getFans().get(1).object);
    }

    @Test
    public void cacheRetryAndEquipmentFallback() {
        TestScene scene = new TestScene();
        scene.bodyCacheReady = false;
        for (int i = 0; i < 8; i++) scene.tick();
        assertTrue(scene.active.isEmpty());
        scene.bodyCacheReady = true;
        scene.populate();
        assertEquals(8, scene.active.size());
        scene.config.gear = com.celebritymode.appearance.FanGearTier.BRONZE_NOOB;
        scene.unsupportedEquipment = true;
        scene.manager.onConfigChanged("crowdGearTier");
        scene.tick();
        assertEquals(8, scene.active.size());
        scene.manager.cleanup();
        assertTrue(scene.active.isEmpty());
    }

    @Test
    public void animationsReuseControllerAndCompleteReactions() {
        TestScene scene = new TestScene();
        scene.populate();
        FanEntity fan = scene.manager.getFans().get(0);
        scene.animations.setState(fan, FanState.IDLE);
        AnimationController controller = fan.animation;
        controller.setFrame(1);
        int resources = scene.loadedAnimations.size();
        scene.animations.setState(fan, FanState.IDLE);
        assertEquals(1, controller.getFrame());
        assertEquals(resources, scene.loadedAnimations.size());
        scene.animations.react(fan, false);
        assertEquals(FanState.REACTION, fan.state);
        assertSame(controller, fan.animation);
        assertTrue(
                scene.loadedAnimations.contains(net.runelite.api.gameval.AnimationID.EMOTE_WAVE));
        fan.object.tick(250);
        assertEquals(FanState.IDLE, fan.state);
        assertSame(controller, fan.animation);
        assertEquals(resources + 1, scene.loadedAnimations.size());
        scene.manager.cleanup();
    }

    @Test
    public void sustainedThirtyFanScene() {
        TestScene scene = new TestScene();
        scene.config.size = 30;
        scene.populate();
        for (int x = 35; x < 45; x++) scene.flags[x][43] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
        for (int round = 0; round < 4; round++) {
            int[][] corners = {{3242, 3232}, {3242, 3242}, {3232, 3242}, {3232, 3232}};
            for (int[] corner : corners)
                while (scene.playerPosition.getX() != corner[0]
                        || scene.playerPosition.getY() != corner[1]) {
                    int speed = round % 2 + 1;
                    int dx = Integer.signum(corner[0] - scene.playerPosition.getX()),
                            dy = Integer.signum(corner[1] - scene.playerPosition.getY());
                    int steps =
                            Math.min(
                                    speed,
                                    Math.max(
                                            Math.abs(corner[0] - scene.playerPosition.getX()),
                                            Math.abs(corner[1] - scene.playerPosition.getY())));
                    scene.playerPosition =
                            FormationPlanner.offset(scene.playerPosition, dx * steps, dy * steps);
                    scene.tick();
                    int cycle = scene.cycle;
                    for (int frame = 1; frame < 30; frame++) {
                        scene.cycle = cycle + frame;
                        scene.manager.onClientTick();
                    }
                    scene.cycle = cycle;
                    assertEquals(30, scene.active.size());
                    assertTrue(scene.manager.getBreadcrumbs().size() <= 80);
                    for (FanEntity fan : scene.manager.getFans())
                        assertTrue(scene.collision.canOccupy(fan.position));
                }
        }
        for (int i = 0; i < 100; i++) scene.tick();
        assertEquals(30, scene.modelBuilds);
        for (FanEntity fan : scene.manager.getFans())
            assertTrue(fan.position.distanceTo(scene.playerPosition) <= 3);
        scene.manager.cleanup();
        assertTrue(scene.active.isEmpty());
    }

    @Test
    public void wordOfMouthArrivalsApproachFromOutsideAndRestartOnEnable() {
        TestScene scene = new TestScene();
        scene.config.size = 30;
        scene.tick(); // The cascade begins at tick 1.
        assertTrue(scene.active.isEmpty());
        int early = 0;
        for (int elapsed = 1; elapsed <= 85; elapsed++) {
            scene.tick();
            int due = 0;
            for (int ordinal = 0; ordinal < 30; ordinal++)
                if (CrowdArrival.dueTick(0, ordinal) <= elapsed) due++;
            assertEquals("Visible arrivals after " + elapsed + " ticks", due, scene.active.size());
            if (elapsed == 4) {
                assertEquals(1, due);
                assertTrue(
                        scene.manager.getFans().get(0).position.distanceTo(scene.playerPosition)
                                >= 4);
            }
            if (elapsed == 15) assertEquals(1, due);
            if (elapsed == 30) {
                early = due;
                assertEquals(3, due);
            }
            if (elapsed == 60) assertTrue(due >= 7 && due < 20);
        }
        assertEquals(30, scene.active.size());
        assertTrue(30 - early > early);
        scene.manager.cleanup();
        scene.tick();
        assertTrue(scene.active.isEmpty());
        scene.populate();
        assertEquals(30, scene.active.size());
    }

    @Test
    public void regionReloadPreservesThePendingWordOfMouthCascade() {
        TestScene scene = new TestScene();
        scene.config.size = 30;
        for (int i = 0; i < 10; i++) scene.tick();
        assertEquals(1, scene.active.size());
        RuneLiteObject spotter = scene.manager.getFans().get(0).object;
        int[] deadlines =
                scene.manager.getFans().stream().mapToInt(fan -> fan.arrivalTick).toArray();
        scene.state = GameState.LOADING;
        scene.manager.onGameStateChanged(scene.state);
        scene.baseX += 8;
        scene.playerPosition = new WorldPoint(3234, 3232, 0);
        scene.state = GameState.LOGGED_IN;
        scene.tick();
        assertSame(spotter, scene.manager.getFans().get(0).object);
        assertEquals(1, scene.active.size());
        for (int i = 0; i < 30; i++)
            assertEquals(deadlines[i], scene.manager.getFans().get(i).arrivalTick);
        scene.populate();
        assertEquals(30, scene.active.size());
    }

    @Test
    public void idleCrowdsGatherAndWanderInEveryModeWithRareEmotes() {
        for (FormationStyle style : FormationStyle.values()) {
            TestScene scene = new TestScene();
            scene.config.size = 30;
            scene.config.formation = style;
            scene.populate();
            int steps = 0, reactions = 0;
            boolean front = false, back = false;
            Map<Integer, FanState> prior = new HashMap<>();
            for (int tick = 0; tick < 400; tick++) {
                Map<Integer, WorldPoint> before = new HashMap<>();
                for (FanEntity fan : scene.manager.getFans()) before.put(fan.id, fan.position);
                scene.tick();
                for (FanEntity fan : scene.manager.getFans()) {
                    if (!fan.position.equals(before.get(fan.id))) steps++;
                    if (fan.state == FanState.REACTION && prior.get(fan.id) != FanState.REACTION)
                        reactions++;
                    prior.put(fan.id, fan.state);
                    assertTrue(fan.position.distanceTo(scene.playerPosition) <= 3);
                    // Passing through another player tile is normal OSRS movement; resting targets
                    // avoid it.
                    assertNotEquals(scene.playerPosition, fan.target);
                    if (fan.position.getY() > scene.playerPosition.getY()) front = true;
                    if (fan.position.getY() < scene.playerPosition.getY()) back = true;
                }
            }
            assertTrue("Frequent wandering in " + style, steps > 1000);
            assertTrue(front && back);
            assertTrue("Rare crowd-wide emotes", reactions <= 4);
        }
    }

    @Test
    public void movingCrowdDoesNotStartEmotesAndStationaryReactionHoldsItsTile() {
        TestScene scene = new TestScene();
        scene.config.size = 8;
        scene.populate();
        for (int i = 0; i < 500; i++) {
            int offset = i % 20;
            scene.playerPosition =
                    new WorldPoint(3232 + (offset < 10 ? offset : 20 - offset), 3232, 0);
            scene.tick();
            for (FanEntity fan : scene.manager.getFans())
                assertNotEquals(FanState.REACTION, fan.state);
        }
        FanEntity fan = scene.manager.getFans().get(0);
        scene.animations.setState(fan, FanState.IDLE);
        fan.movementPath = null;
        scene.animations.react(fan, false);
        WorldPoint position = fan.position;
        for (int i = 0; i < 3; i++) {
            scene.tick();
            assertEquals(position, fan.position);
            assertEquals(FanState.REACTION, fan.state);
        }
        scene.manager.cleanup();
    }

    @Test
    public void runningTurnsInterpolateThroughIntermediateTile() {
        TestScene scene = new TestScene();
        scene.config.size = 1;
        scene.populate();
        FanEntity fan = scene.manager.getFans().get(0);
        LocalPoint start = fan.renderLocation;
        fan.movementPath =
                new LocalPoint[] {
                    start,
                    new LocalPoint(start.getX() + 128, start.getY(), scene.view),
                    new LocalPoint(start.getX() + 128, start.getY() + 128, scene.view)
                };
        fan.movementStartCycle = scene.cycle;
        scene.movement.render(fan, scene.view, scene.cycle + 15);
        assertEquals(start.getX() + 128, fan.renderLocation.getX());
        assertEquals(start.getY(), fan.renderLocation.getY());
        scene.movement.render(fan, scene.view, scene.cycle + 23);
        assertEquals(start.getX() + 128, fan.renderLocation.getX());
        assertTrue(fan.renderLocation.getY() > start.getY());
    }

    @Test
    public void walkRunAndInterpolation() {
        TestScene scene = new TestScene();
        scene.config.size = 1;
        scene.populate();
        FanEntity fan = scene.manager.getFans().get(0);
        // Complete the previous independently scheduled wander before testing a fresh segment.
        scene.movement.render(fan, scene.view, scene.cycle + 30);
        WorldPoint start = fan.position;
        MovementTrail trail = new MovementTrail();
        trail.update(start);
        scene.movement.plan(
                fan, FormationPlanner.offset(start, 1, 0), trail, false, scene.view, scene.cycle);
        assertEquals(FanState.WALKING, fan.state);
        int x = fan.renderLocation.getX();
        scene.movement.render(fan, scene.view, scene.cycle + 15);
        assertEquals(x + 64, fan.renderLocation.getX());
        scene.movement.render(fan, scene.view, scene.cycle + 30);
        assertEquals(x + 128, fan.renderLocation.getX());
        start = fan.position;
        scene.movement.plan(
                fan,
                FormationPlanner.offset(start, 2, 0),
                trail,
                true,
                scene.view,
                scene.cycle + 30);
        assertEquals(FanState.RUNNING, fan.state);
        assertEquals(3, fan.movementPath.length);
        scene.movement.render(fan, scene.view, scene.cycle + 60);
        assertEquals(x + 384, fan.renderLocation.getX());
    }
}
