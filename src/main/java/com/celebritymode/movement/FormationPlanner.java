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
        WorldPoint anchor = trail.getDelayedPoint(delay);
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
                valid(preferred, player, reserved) && connected(anchor, preferred)
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
                            && connected(anchor, p)) best = p;
                }
        if (best == null && collision.canOccupy(anchor)) best = anchor;
        if (best == null && collision.canOccupy(player)) best = player;
        if (best != null) reserved.add(best);
        return best;
    }

    /** Stopped crowds ignore travelling formation and choose short, asynchronous local strolls. */
    public WorldPoint chooseIdleTarget(
            FanEntity fan, WorldPoint player, Set<WorldPoint> reserved, Random random, int tick) {
        if (fan.state == com.celebritymode.fan.FanState.REACTION) return fan.position;
        // Large crowds need room to gather; small crowds retain the original close radius.
        int crowdSize = Math.max(1, Math.min(com.celebritymode.fan.CrowdArrival.MAX_FANS, config.crowdSize()));
        int crowdRadius = crowdSize > 30 ? (int) Math.ceil(Math.sqrt(crowdSize / Math.PI)) : 2;
        int radius = Math.max(crowdRadius, Math.max(2, Math.min(8, config.maxSpread() + 1)));
        if (fan.idleTarget != null
                && fan.idleTarget.distanceTo(player) <= radius
                && collision.canOccupy(fan.idleTarget)
                && (tick < fan.nextWanderTick
                        || (fan.position != null && !fan.position.equals(fan.idleTarget)))) {
            reserved.add(fan.idleTarget);
            return fan.idleTarget;
        }
        WorldPoint preferred = null, shared = null;
        for (int attempt = 0; attempt < 40; attempt++) {
            int x = random.nextInt(2 * radius + 1) - radius;
            int y = random.nextInt(2 * radius + 1) - radius;
            if (x * x + y * y > radius * radius || (x == 0 && y == 0)) continue;
            WorldPoint candidate = offset(player, x, y);
            if (!collision.canOccupy(candidate) || !connected(player, candidate)) continue;
            if (shared == null) shared = candidate;
            if (reserved.contains(candidate)
                    || candidate.equals(fan.position)
                    || candidate.equals(fan.idleTarget)) continue;
            preferred = candidate;
            break;
        }
        if (preferred == null) preferred = shared;
        if (preferred == null && collision.canOccupy(player)) preferred = player;
        fan.idleTarget = preferred;
        // Brief pauses, varied independently; never pick a new destination every frame.
        fan.nextWanderTick =
                tick
                        + (fan.personality == com.celebritymode.fan.FanPersonality.QUIET
                                ? 5 + random.nextInt(5)
                                : 2 + random.nextInt(5));
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
