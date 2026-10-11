package com.celebritymode.movement;

import com.celebritymode.CelebrityModeConfig;
import com.celebritymode.fan.FanEntity;

import net.runelite.api.coords.WorldPoint;

import java.util.*;

import javax.inject.*;

@Singleton
public class FormationPlanner {
    private final CelebrityModeConfig config;
    private final CollisionService collision;

    @Inject
    public FormationPlanner(CelebrityModeConfig config, CollisionService collision) {
        this.config = config;
        this.collision = collision;
    }

    public void reassignSlots(List<FanEntity> fans) {
        for (int i = 0; i < fans.size(); i++) fans.get(i).formationSlot = i;
    }

    public WorldPoint chooseTarget(
            FanEntity fan,
            WorldPoint player,
            int dx,
            int dy,
            MovementTrail trail,
            Set<WorldPoint> reserved) {
        return chooseTarget(fan, player, dx, dy, trail, reserved, 0);
    }

    public WorldPoint chooseTarget(
            FanEntity fan, WorldPoint player, int dx, int dy, MovementTrail trail,
            Set<WorldPoint> reserved, int stationaryTicks) {
        int spread = Math.max(1, Math.min(8, config.maxSpread()));
        FormationStyle style = config.formationStyle();
        int delay =
                style == FormationStyle.TRAIL
                        ? fan.trailOffset
                        : style == FormationStyle.SWARM
                                ? 1 + fan.trailOffset / 5
                                : style == FormationStyle.LOOSE_CROWD
                                        ? 1 + fan.trailOffset / 2
                                        : 1 + fan.trailOffset / 3;
        // On stopping, choose the final destination once; do not release breadcrumbs on a timer.
        WorldPoint anchor = trail.getFollowingPoint(stationaryTicks > 0 ? 0 : delay);
        if (anchor == null) anchor = player;
        // Each follower has its own persistent angle and depth, independent of list index/rows.
        double angle = fan.id * 2.399963229728653 + 0.71;
        double lateral = Math.sin(angle) * spread * (style == FormationStyle.TRAIL ? 0.55 : 0.95);
        double behind =
                style == FormationStyle.SWARM
                        ? 0.5 + (1 + Math.cos(angle * 1.37)) * 0.6
                        : 0.75 + (1 + Math.cos(angle * 1.37)) * 1.1;
        int hx = Integer.signum(dx), hy = Integer.signum(dy);
        if (hx == 0 && hy == 0) hy = 1;
        WorldPoint preferred =
                offset(
                        anchor,
                        (int) Math.round(-hx * behind - hy * lateral),
                        (int) Math.round(-hy * behind + hx * lateral));
        WorldPoint best =
                valid(preferred, player, reserved)
                                && (stationaryTicks == 0 || preferred.distanceTo(player) <= spread + 3)
                                && connected(anchor, preferred)
                        ? preferred
                        : null;
        // Fixed search budget, independent of scene size; prefer unique tiles then sharing.
        for (int radius = 1; best == null && radius <= 4; radius++)
            for (int x = -radius; best == null && x <= radius; x++)
                for (int y = -radius; best == null && y <= radius; y++) {
                    if (Math.max(Math.abs(x), Math.abs(y)) != radius) continue;
                    WorldPoint p = offset(preferred, x, y);
                    int lateralDistance =
                            Math.abs(
                                    -hy * (p.getX() - anchor.getX())
                                            + hx * (p.getY() - anchor.getY()));
                    if (lateralDistance <= spread * (Math.abs(hx) + Math.abs(hy))
                            && valid(p, player, reserved)
                            && (stationaryTicks == 0 || p.distanceTo(player) <= spread + 3)
                            && connected(anchor, p)) best = p;
                }
        if (best == null && collision.canOccupy(anchor)) best = anchor;
        if (best == null && collision.canOccupy(player)) best = player;
        if (best != null) reserved.add(best);
        return best;
    }

    /** A stable minority gradually breaks away; the majority keeps following in formation. */
    public boolean shouldGather(FanEntity fan, int stationaryTicks) {
        return Math.floorMod(fan.id * 37, 100) < 30
                && stationaryTicks >= 25 + Math.floorMod(fan.id * 19, 80);
    }

    /** Hold a good viewing spot, with rare one-tile adjustments rather than radial wandering. */
    public WorldPoint chooseIdleTarget(
            FanEntity fan, WorldPoint player, Set<WorldPoint> reserved, Random random, int tick) {
        if (fan.state == com.celebritymode.fan.FanState.REACTION) {
            reserved.add(fan.position);
            return fan.position;
        }
        int crowdSize = Math.max(1, Math.min(com.celebritymode.fan.CrowdArrival.MAX_FANS, config.crowdSize()));
        int radius = Math.max(3, (int) Math.ceil(Math.sqrt(crowdSize / Math.PI)));
        if (fan.idleTarget != null && collision.canOccupy(fan.idleTarget)
                && fan.idleTarget.distanceTo(player) <= radius) {
            if (!fan.idleTarget.equals(fan.position) || tick < fan.nextWanderTick) {
                reserved.add(fan.idleTarget);
                return fan.idleTarget;
            }
            fan.nextWanderTick = tick + 100 + random.nextInt(200);
            // Most opportunities pass without moving, even after a long pause.
            if (random.nextInt(5) != 0) {
                reserved.add(fan.idleTarget);
                return fan.idleTarget;
            }
        }
        WorldPoint origin = fan.idleTarget == null ? player : fan.position;
        WorldPoint preferred = fan.position;
        for (int attempt = 0; attempt < 24; attempt++) {
            int reach = fan.idleTarget == null ? radius : 1;
            WorldPoint candidate = offset(origin,
                    random.nextInt(2 * reach + 1) - reach,
                    random.nextInt(2 * reach + 1) - reach);
            if (candidate.distanceTo(player) > radius
                    || candidate.equals(player)
                    || (config.respectPersonalSpace() && candidate.distanceTo(player) < 2)
                    || reserved.contains(candidate)
                    || !collision.canOccupy(candidate)
                    || !connected(origin, candidate)) continue;
            preferred = candidate;
            break;
        }
        fan.idleTarget = preferred;
        fan.nextWanderTick = tick + 100 + random.nextInt(200);
        if (preferred != null) reserved.add(preferred);
        return preferred;
    }

    /**
     * Fans enter on the outskirts and approach on foot, rather than materializing on the player.
     */
    public WorldPoint chooseArrivalPoint(
            FanEntity fan, WorldPoint player, WorldPoint fallback, Set<WorldPoint> reserved) {
        double angle = fan.id * 2.399963229728653;
        for (int attempt = 0; attempt < 24; attempt++) {
            int radius = 4 + (fan.id + attempt) % 3;
            double heading = angle + attempt * 0.45;
            WorldPoint candidate =
                    offset(
                            player,
                            (int) Math.round(Math.cos(heading) * radius),
                            (int) Math.round(Math.sin(heading) * radius));
            if (collision.canOccupy(candidate)
                    && !reserved.contains(candidate)
                    && connected(player, candidate)) {
                reserved.add(candidate);
                return candidate;
            }
        }
        return fallback;
    }

    private boolean valid(WorldPoint p, WorldPoint player, Set<WorldPoint> reserved) {
        return collision.canOccupy(p)
                && (!config.respectPersonalSpace()
                        || (p.distanceTo(player) >= 2 && !reserved.contains(p)));
    }

    private boolean connected(WorldPoint from, WorldPoint to) {
        WorldPoint p = from;
        for (int i = 0; i < 16 && !p.equals(to); i++) {
            WorldPoint next =
                    offset(
                            p,
                            Integer.signum(to.getX() - p.getX()),
                            Integer.signum(to.getY() - p.getY()));
            if (!collision.canStep(p, next)) return false;
            p = next;
        }
        return p.equals(to);
    }

    public static WorldPoint offset(WorldPoint p, int x, int y) {
        return new WorldPoint(p.getX() + x, p.getY() + y, p.getPlane());
    }
}
