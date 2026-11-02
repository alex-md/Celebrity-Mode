package com.celebritymode.movement;

import com.celebritymode.fan.*;
import com.celebritymode.util.OrientationUtil;

import net.runelite.api.*;
import net.runelite.api.coords.*;

import java.util.*;

import javax.inject.*;

@Singleton
public final class FanMovementController {
    private final CollisionService collision;
    private final FanAnimations animations;

    @Inject
    public FanMovementController(CollisionService collision, FanAnimations animations) {
        this.collision = collision;
        this.animations = animations;
    }

    public void plan(
            FanEntity fan,
            WorldPoint target,
            MovementTrail trail,
            boolean playerRunning,
            WorldView view,
            int cycle) {
        plan(fan, target, trail, playerRunning, view, cycle, false);
    }

    public void plan(
            FanEntity fan,
            WorldPoint target,
            MovementTrail trail,
            boolean playerRunning,
            WorldView view,
            int cycle,
            boolean stoppedCrowd) {
        render(fan, view, cycle, true);
        fan.target = target;
        fan.socialMovement = stoppedCrowd;
        // Logical position is already the end of the active segment. Finish rendering it
        // before routing from that tile, even when GameTick arrives a little early.
        if (fan.movementPath != null || cycle < fan.nextMovementCycle) return;
        if (target == null || fan.position == null) {
            if (fan.state != FanState.REACTION) animations.setState(fan, FanState.IDLE);
            return;
        }
        if (fan.position.equals(target)) {
            fan.catchingUp = false;
            fan.movementPath = null;
            fan.stalledTicks = 0;
            if (fan.state != FanState.REACTION) animations.setState(fan, FanState.IDLE);
            return;
        }
        WorldPoint waypoint = target;
        // A distant follower advances along the actual player's route before restoring its offset.
        if (!stoppedCrowd && fan.position.distanceTo(target) > 3 && trail.size() > 1) {
            int closest = -1, distance = Integer.MAX_VALUE;
            for (int i = 0; i < trail.size(); i++) {
                WorldPoint point = trail.getDelayedPoint(i);
                int d = point.distanceTo(fan.position);
                if (d < distance) {
                    distance = d;
                    closest = i;
                }
            }
            if (closest >= 0) {
                WorldPoint near = trail.getDelayedPoint(closest);
                if (closest > 0
                        && !near.equals(target)
                        && near.distanceTo(target) > 1
                        && target.distanceTo(trail.getLatest())
                                < near.distanceTo(trail.getLatest()))
                    waypoint =
                            distance <= 1
                                    ? trail.getDelayedPoint(
                                            Math.max(
                                                    0,
                                                    closest
                                                            - (playerRunning
                                                                            || fan.position
                                                                                            .distanceTo(
                                                                                                    target)
                                                                                    >= 4
                                                                    ? 2
                                                                    : 1)))
                                    : near;
            }
        }
        List<WorldPoint> route = stoppedCrowd ? null : breadcrumbSteps(fan.position, target, trail);
        if (route == null) route = route(fan.position, waypoint);
        if (route.isEmpty()) {
            fan.stalledTicks++;
            fan.movementPath = null;
            if (fan.state != FanState.REACTION) animations.setState(fan, FanState.IDLE);
            return;
        }
        fan.stalledTicks = 0;
        int runThreshold = 3 + Math.floorMod(fan.id * 13, 3);
        int distanceToTarget = fan.position.distanceTo(target);
        // A follower commits to catching up, then eases back to a walk near its destination.
        // Separate start/stop distances avoid alternating gaits around one threshold.
        fan.catchingUp = !stoppedCrowd && distanceToTarget >= (fan.catchingUp ? 2 : runThreshold);
        boolean run = fan.catchingUp;
        int steps = Math.min(run ? 2 : 1, route.size());
        LocalPoint[] path = new LocalPoint[steps + 1];
        path[0] = fan.renderLocation;
        for (int i = 0; i < steps; i++) {
            path[i + 1] = LocalPoint.fromWorld(view, route.get(i));
            if (path[i + 1] == null) {
                fan.stalledTicks = 21;
                return;
            }
            path[i + 1] = path[i + 1].plus(fan.tileJitterX, fan.tileJitterY);
        }
        if (path[0] == null) {
            fan.stalledTicks = 21;
            return;
        }
        fan.movementPath = path;
        fan.movementStartCycle = cycle;
        fan.lastRenderCycle = cycle;
        WorldPoint end = route.get(steps - 1);
        fan.targetOrientation =
                OrientationUtil.facing(
                        route.get(0).getX() - fan.position.getX(),
                        route.get(0).getY() - fan.position.getY());
        fan.position = end;
        animations.setState(fan, run && steps > 1 ? FanState.RUNNING : FanState.WALKING);
    }

    /** Core followers traverse the actual visited tiles, including both legs of a running turn. */
    private List<WorldPoint> breadcrumbSteps(WorldPoint start, WorldPoint target, MovementTrail trail) {
        int from = -1, to = -1;
        for (int i = 0; i < trail.size(); i++) {
            WorldPoint point = trail.getDelayedPoint(i);
            if (from < 0 && point.equals(start)) from = i;
            if (to < 0 && point.equals(target)) to = i;
        }
        if (to < 0 || from <= to) return null;
        List<WorldPoint> steps = new ArrayList<>(2);
        WorldPoint previous = start;
        for (int i = from - 1; i >= to && steps.size() < 2; i--) {
            WorldPoint next = trail.getDelayedPoint(i);
            if (!collision.canStep(previous, next))
                return steps.isEmpty() ? route(start, next) : steps;
            steps.add(next);
            previous = next;
        }
        return steps;
    }

    /**
     * Greedy straight segment first; detours explore at most 96 nearby tiles, never the whole
     * scene.
     */
    public List<WorldPoint> route(WorldPoint start, WorldPoint goal) {
        if (start.equals(goal)) return Collections.emptyList();
        List<WorldPoint> direct = new ArrayList<>(2);
        WorldPoint cursor = start;
        for (int i = 0; i < 2 && !cursor.equals(goal); i++) {
            WorldPoint next =
                    FormationPlanner.offset(
                            cursor,
                            Integer.signum(goal.getX() - cursor.getX()),
                            Integer.signum(goal.getY() - cursor.getY()));
            if (!collision.canStep(cursor, next)) break;
            direct.add(next);
            cursor = next;
        }
        if (!direct.isEmpty()) return direct;
        java.util.Deque<WorldPoint> queue = new ArrayDeque<>();
        Map<WorldPoint, WorldPoint> parent = new HashMap<>();
        queue.add(start);
        parent.put(start, null);
        WorldPoint best = start;
        int explored = 0;
        while (!queue.isEmpty() && explored++ < 96) {
            WorldPoint p = queue.removeFirst();
            if (p.distanceTo(goal) < best.distanceTo(goal)) best = p;
            if (p.equals(goal)) {
                best = p;
                break;
            }
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++) {
                    if (dx == 0 && dy == 0) continue;
                    WorldPoint next = FormationPlanner.offset(p, dx, dy);
                    if (next.distanceTo(start) > 6
                            || parent.containsKey(next)
                            || parent.size() >= 96
                            || !collision.canStep(p, next)) continue;
                    parent.put(next, p);
                    queue.addLast(next);
                }
        }
        if (best.equals(start)) return Collections.emptyList();
        List<WorldPoint> result = new ArrayList<>();
        for (WorldPoint p = best; !p.equals(start); p = parent.get(p)) result.add(p);
        Collections.reverse(result);
        return result;
    }

    /** Continue this person's click on client cycles, even between server updates. */
    public void advance(FanEntity fan, MovementTrail trail, boolean playerRunning,
            WorldView view, int cycle) {
        // Planning renders and continues atomically so animation frames survive segment boundaries.
        // A blocked click retries on GameTick, rather than searching on every client cycle.
        if (fan.target != null && fan.position != null && !fan.position.equals(fan.target)
                && fan.stalledTicks == 0 && fan.state != FanState.REACTION) {
            plan(fan, fan.target, trail, playerRunning, view, cycle, fan.socialMovement);
        } else render(fan, view, cycle);
    }

    public void render(FanEntity fan, WorldView view, int cycle) {
        render(fan, view, cycle, false);
    }

    private void render(FanEntity fan, WorldView view, int cycle, boolean planning) {
        if (fan.object == null || fan.renderLocation == null) return;
        if (fan.lastRenderCycle < 0) fan.lastRenderCycle = cycle;
        if (cycle < fan.lastRenderCycle) return;
        // Replay client cycles, not paint calls. The bound covers a complete segment and
        // a half-turn after a stall, without making a long pause an unbounded loop.
        for (int step = Math.max(fan.lastRenderCycle + 1, cycle - 63); step <= cycle; step++) {
            if (fan.movementPath != null) {
                double progress = Math.max(0, Math.min(1, (step - fan.movementStartCycle) / (double) fan.movementDurationCycles));
                int count = fan.movementPath.length - 1;
                double scaled = progress * count;
                int segment = Math.min(count - 1, (int) scaled);
                double fraction = scaled - segment;
                LocalPoint a = fan.movementPath[segment], b = fan.movementPath[segment + 1];
                fan.targetOrientation = OrientationUtil.facing(b.getX() - a.getX(), b.getY() - a.getY());
                fan.renderLocation = new LocalPoint(
                        (int) Math.round(a.getX() + (b.getX() - a.getX()) * fraction),
                        (int) Math.round(a.getY() + (b.getY() - a.getY()) * fraction), view);
                if (progress >= 1) fan.movementPath = null;
            }
            if (fan.state != FanState.REACTION)
                fan.orientation = OrientationUtil.approach(fan.orientation, fan.targetOrientation);
        }
        fan.lastRenderCycle = cycle;
        // Let GameTick install a continuing segment without inserting idle and restarting
        // the walk/run sequence between otherwise continuous steps.
        if (!planning && fan.movementPath == null && cycle > fan.movementStartCycle + fan.movementDurationCycles
                && (fan.state == FanState.WALKING || fan.state == FanState.RUNNING))
            fan.state = FanState.IDLE;
        animations.updatePose(fan);
        fan.object.setOrientation(fan.orientation);
        fan.object.setLocation(fan.renderLocation, view.getPlane());
    }
}
