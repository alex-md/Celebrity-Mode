package com.celebritymode.movement;

import com.celebritymode.TestScene;
import com.celebritymode.fan.*;
import com.celebritymode.util.OrientationUtil;
import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public class FanMovementControllerTest {
    private TestScene scene() {
        TestScene scene = new TestScene();
        scene.holdVisitors = true;
        scene.config.size = 1;
        scene.config.arrival = ArrivalPace.INSTANT;
        scene.tick();
        return scene;
    }

    private FanEntity fan(TestScene scene, int facing) {
        FanEntity fan = scene.manager.getFans().get(0);
        fan.position = scene.playerPosition;
        fan.renderLocation = LocalPoint.fromWorld(scene.view, fan.position).plus(fan.tileJitterX, fan.tileJitterY);
        fan.movementPath = null;
        fan.nextMovementCycle = 0;
        fan.movementDurationCycles = 30;
        fan.orientation = fan.targetOrientation = facing;
        fan.lastRenderCycle = scene.cycle;
        scene.animations.setState(fan, FanState.IDLE);
        return fan;
    }

    @Test
    public void leftAndRightTurningGaitsMatchJagexAngleDirections() {
        TestScene scene = scene();
        FanEntity fan = fan(scene, 0);
        scene.movement.plan(fan, FormationPlanner.offset(fan.position, -1, 0), new MovementTrail(), false, scene.view, scene.cycle);
        assertEquals(AnimationID.HUMAN_WALK_R, fan.animationId);
        fan = fan(scene, 0);
        scene.movement.plan(fan, FormationPlanner.offset(fan.position, 1, 0), new MovementTrail(), false, scene.view, scene.cycle);
        assertEquals(AnimationID.HUMAN_WALK_L, fan.animationId);
    }

    @Test
    public void blockedDiagonalUsesAConnectedDetourInsteadOfClippingTheCorner() {
        TestScene scene = scene();
        FanEntity fan = fan(scene, 1536);
        LocalPoint start = LocalPoint.fromWorld(scene.view, fan.position);
        scene.flags[start.getSceneX() + 1][start.getSceneY()] = CollisionDataFlag.BLOCK_MOVEMENT_FULL;
        WorldPoint target = FormationPlanner.offset(fan.position, 1, 1);
        scene.movement.plan(fan, target, new MovementTrail(), false, scene.view, scene.cycle);
        assertEquals(FormationPlanner.offset(scene.playerPosition, 0, 1), fan.position);
        assertEquals(1024, fan.targetOrientation);
        scene.movement.render(fan, scene.view, scene.cycle + 30);
        scene.movement.plan(fan, target, new MovementTrail(), false, scene.view, scene.cycle + 30);
        assertEquals(target, fan.position);
        assertEquals(1536, fan.targetOrientation);
    }

    @Test
    public void stoppedDiagonalArrivalKeepsItsTravelHeadingThroughChatAndEmotes() {
        TestScene scene = scene();
        FanEntity fan = fan(scene, 1280);
        WorldPoint stop = FormationPlanner.offset(scene.playerPosition, 1, 1);
        scene.movement.plan(fan, stop, new MovementTrail(), false, scene.view, scene.cycle);
        scene.movement.render(fan, scene.view, scene.cycle + 30);
        scene.movement.plan(fan, stop, new MovementTrail(), false, scene.view, scene.cycle + 30);
        int heading = OrientationUtil.facing(1, 1);
        assertEquals(heading, fan.orientation);
        assertEquals(heading, fan.targetOrientation);
        scene.animations.react(fan, false);
        for (int cycle = scene.cycle + 31; cycle < scene.cycle + 100; cycle++) {
            fan.overheadText = "talking to somebody nearby";
            scene.movement.render(fan, scene.view, cycle);
            assertEquals(heading, fan.orientation);
            assertEquals(heading, fan.targetOrientation);
        }
    }

    @Test
    public void turnRateIsIndependentOfRenderFrequencyAndDuplicateCalls() {
        TestScene dense = scene(), sparse = scene();
        FanEntity a = fan(dense, 0), b = fan(sparse, 0);
        WorldPoint target = FormationPlanner.offset(dense.playerPosition, 0, 5);
        dense.movement.plan(a, target, new MovementTrail(), false, dense.view, dense.cycle);
        sparse.movement.plan(b, target, new MovementTrail(), false, sparse.view, sparse.cycle);
        for (int cycle = 1; cycle <= 30; cycle++) {
            int prior = a.orientation;
            dense.movement.render(a, dense.view, dense.cycle + cycle);
            assertTrue(Math.abs(OrientationUtil.delta(prior, a.orientation)) <= 32);
            int once = a.orientation;
            for (int repeat = 0; repeat < 5; repeat++)
                dense.movement.render(a, dense.view, dense.cycle + cycle);
            assertEquals(once, a.orientation);
            if (cycle % 6 == 0) {
                sparse.movement.render(b, sparse.view, sparse.cycle + cycle);
                assertEquals(a.orientation, b.orientation);
                assertEquals(a.renderLocation, b.renderLocation);
                assertEquals(a.animationId, b.animationId);
            }
        }
    }

    @Test
    public void followingTracesBothTilesOfARunningCornerAndFacesEachLeg() {
        TestScene scene = scene();
        FanEntity fan = fan(scene, 1536);
        WorldPoint start = fan.position;
        MovementTrail trail = new MovementTrail();
        trail.update(start);
        trail.update(FormationPlanner.offset(start, 1, 0));
        trail.update(FormationPlanner.offset(start, 1, 1));
        trail.update(FormationPlanner.offset(start, 1, 2));
        WorldPoint target = FormationPlanner.offset(start, 1, 3);
        trail.update(target);
        scene.movement.plan(fan, target, trail, true, scene.view, scene.cycle);
        assertEquals(3, fan.movementPath.length);
        assertEquals(FormationPlanner.offset(start, 1, 1), fan.position);
        LocalPoint beginning = fan.renderLocation;
        for (int cycle = 1; cycle <= 30; cycle++) {
            scene.movement.render(fan, scene.view, scene.cycle + cycle);
            if (cycle < 15) {
                assertEquals(beginning.getY(), fan.renderLocation.getY());
                assertEquals(1536, fan.targetOrientation);
            } else {
                assertEquals(beginning.getX() + 128, fan.renderLocation.getX());
                assertEquals(1024, fan.targetOrientation);
            }
        }
        assertEquals(1024, fan.orientation);
        assertNull(fan.movementPath);
    }

    @Test
    public void earlyRetargetDoesNotCancelTheVisibleStepOrSlideFromItsLogicalEndpoint() {
        TestScene scene = scene();
        FanEntity fan = fan(scene, 1536);
        WorldPoint start = fan.position, first = FormationPlanner.offset(start, 1, 0);
        LocalPoint location = fan.renderLocation;
        scene.movement.plan(fan, first, new MovementTrail(), false, scene.view, scene.cycle);
        LocalPoint[] path = fan.movementPath;
        scene.movement.plan(fan, fan.position, new MovementTrail(), false, scene.view, scene.cycle + 10);
        assertSame(path, fan.movementPath);
        assertEquals(FanState.WALKING, fan.state);
        assertEquals(location.getX() + 43, fan.renderLocation.getX());
        WorldPoint next = FormationPlanner.offset(first, 0, 1);
        scene.movement.plan(fan, next, new MovementTrail(), false, scene.view, scene.cycle + 20);
        assertSame(path, fan.movementPath);
        assertEquals(first, fan.position);
        scene.movement.plan(fan, next, new MovementTrail(), false, scene.view, scene.cycle + 30);
        assertEquals(location.getX() + 128, fan.renderLocation.getX());
        assertEquals(location.getY(), fan.renderLocation.getY());
        assertEquals(next, fan.position);
    }

    @Test
    public void locomotionAnimationDoesNotRestartBetweenContinuousStraightSteps() {
        TestScene scene = scene();
        FanEntity fan = fan(scene, 1536);
        WorldPoint start = fan.position;
        scene.movement.plan(fan, FormationPlanner.offset(start, 1, 0), new MovementTrail(), false, scene.view, scene.cycle);
        AnimationController controller = fan.animation;
        controller.setFrame(1);
        scene.movement.plan(fan, FormationPlanner.offset(start, 2, 0), new MovementTrail(), false, scene.view, scene.cycle + 30);
        assertSame(controller, fan.animation);
        assertEquals(1, controller.getFrame());
        assertEquals(AnimationID.HUMAN_WALK_F, fan.animationId);
        assertEquals(FanState.WALKING, fan.state);
    }

    @Test
    public void aboutFaceUsesTurningGaitsAndEmotesWaitForASettledBody() {
        TestScene scene = scene();
        FanEntity fan = fan(scene, 0);
        scene.movement.plan(fan, FormationPlanner.offset(fan.position, 0, 1), new MovementTrail(), false, scene.view, scene.cycle);
        assertEquals(AnimationID.HUMAN_WALK_B, fan.animationId);
        scene.animations.react(fan, false);
        assertEquals(FanState.WALKING, fan.state);
        scene.movement.render(fan, scene.view, scene.cycle + 31);
        assertEquals(FanState.IDLE, fan.state);
        assertEquals(AnimationID.HUMAN_TURNONSPOT, fan.animationId);
        scene.animations.react(fan, false);
        assertEquals(FanState.IDLE, fan.state);
        scene.movement.render(fan, scene.view, scene.cycle + 32);
        assertEquals(fan.targetOrientation, fan.orientation);
        assertEquals(AnimationID.HUMAN_READY, fan.animationId);
        scene.animations.react(fan, false);
        assertEquals(FanState.REACTION, fan.state);
    }
    @Test
    public void catchupKeepsRunningBelowItsStartThresholdThenWalksTheLastTile() {
        TestScene scene = scene();
        FanEntity fan = fan(scene, 1536);
        WorldPoint target = FormationPlanner.offset(fan.position, 4, 0);
        MovementTrail trail = new MovementTrail();
        scene.movement.plan(fan, target, trail, true, scene.view, scene.cycle);
        assertEquals(FanState.RUNNING, fan.state);
        assertEquals(2, fan.position.distanceTo(target));
        scene.movement.plan(fan, target, trail, false, scene.view, scene.cycle + 30);
        assertEquals(FanState.RUNNING, fan.state);
        assertEquals(target, fan.position);
        target = FormationPlanner.offset(target, 1, 0);
        scene.movement.plan(fan, target, trail, false, scene.view, scene.cycle + 60);
        assertEquals(FanState.WALKING, fan.state);
        assertEquals(target, fan.position);
        // An idle social walk always uses walking, even after a catch-up run.
        scene.movement.plan(fan, FormationPlanner.offset(target, 5, 0), trail,
                false, scene.view, scene.cycle + 90, true);
        assertEquals(FanState.WALKING, fan.state);
        scene.manager.cleanup();
    }

    @Test
    public void earlyServerTickDoesNotInsertAWholeTickPauseAtTheEndOfAStep() {
        TestScene scene = scene();
        FanEntity fan = fan(scene, 1536);
        fan.movementDurationCycles = 32;
        WorldPoint target = FormationPlanner.offset(fan.position, 8, 0);
        MovementTrail trail = new MovementTrail();
        scene.movement.plan(fan, target, trail, true, scene.view, scene.cycle);
        scene.movement.plan(fan, target, trail, true, scene.view, scene.cycle + 29);
        LocalPoint almost = fan.renderLocation;
        scene.movement.advance(fan, trail, true, scene.view, scene.cycle + 32);
        assertEquals(scene.cycle + 32, fan.movementStartCycle);
        assertNotNull(fan.movementPath);
        assertEquals(FanState.RUNNING, fan.state);
        scene.movement.advance(fan, trail, true, scene.view, scene.cycle + 33);
        assertTrue(fan.renderLocation.getX() > almost.getX());
        scene.manager.cleanup();
    }

}
