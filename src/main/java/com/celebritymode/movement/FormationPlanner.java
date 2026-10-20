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
        boolean train = config.formationStyle() == FormationStyle.TRAIL;
        int delay = train ? 1 + fan.trailOffset : 1 + fan.trailOffset / 3;
        WorldPoint anchor = trail.getFollowingPoint(stationaryTicks > 0 ? 0 : delay);
        if (anchor == null) anchor = player;
        int width = train ? 1 : spread;
        WorldPoint preferred;
        if (stationaryTicks > 0) {
            // Independent clicks into a dense patch, not assigned positions on a ring.
            int reach = Math.min(6, Math.max(2,
                    (int) Math.ceil(Math.sqrt(config.crowdSize() / 6.0))));
            int x = (int) Math.round((unit(fan.id, 17) - unit(fan.id, 43)) * reach);
            int y = (int) Math.round((unit(fan.id, 71) - unit(fan.id, 97)) * reach);
            preferred = offset(player, x, y);
        } else {
            // Most followers use the same breadcrumbs. Spread is an upper bound for the
            // minority who click alongside the pack, rather than a minimum separation.
            int lateral = unit(fan.id, 13) < (train ? 0.8 : 0.6) ? 0
                    : (int) Math.round((unit(fan.id, 29) - unit(fan.id, 53)) * width);
            int hx = Integer.signum(dx), hy = Integer.signum(dy);
            preferred = offset(anchor, -hy * lateral, hx * lateral);
        }
        if (stationaryTicks == 0 && fan.position != null
                && (preferred.getX() - fan.position.getX()) * dx
                        + (preferred.getY() - fan.position.getY()) * dy < 0
                && collision.canOccupy(fan.position)) {
            reserved.add(fan.position);
            return fan.position;
        }
        WorldPoint best = null;
        double bestScore = Double.MAX_VALUE;
        // Sharing is normal in OSRS. Space only adds a small preference for nearby free
        // tiles; it never excludes an entire radius or forces a large crowd to fan out.
        for (int x = -2; x <= 2; x++)
            for (int y = -2; y <= 2; y++) {
                WorldPoint candidate = offset(preferred, x, y);
                if (!collision.canOccupy(candidate) || !connected(anchor, candidate)
                        || (stationaryTicks > 0 && candidate.distanceTo(player) > 6)) continue;
                if (stationaryTicks == 0) {
                    int lateralDistance = Math.abs(-dy * (candidate.getX() - anchor.getX())
                            + dx * (candidate.getY() - anchor.getY()));
                    if (lateralDistance > width * (Math.abs(dx) + Math.abs(dy))) continue;
                }
                double score = x * x + y * y + unit(fan.id, x * 31 + y * 101) * 0.3;
                if (config.respectPersonalSpace()) {
                    if (candidate.equals(player)) score += 3;
                    if (reserved.contains(candidate)) score += 0.65;
                }
                if (score < bestScore) {
                    best = candidate;
                    bestScore = score;
                }
            }
        if (best == null && collision.canOccupy(anchor)) best = anchor;
        if (best == null && collision.canOccupy(player)) best = player;
        if (best != null) reserved.add(best);
        return best;
    }

    private static double unit(int id, int salt) {
        int hash = id * 0x45d9f3b + salt * 0x119de1f3;
        hash = (hash ^ (hash >>> 16)) * 0x45d9f3b;
        hash ^= hash >>> 16;
        return (hash & 0x7fffffff) / (double) Integer.MAX_VALUE;
    }

    /** Some watch from their spot; others gradually join small social groups after a pause. */
    public boolean shouldGather(FanEntity fan, int stationaryTicks) {
        return Math.floorMod(fan.id * 37, 100) < 60
                && stationaryTicks >= 25 + Math.floorMod(fan.id * 19, 50);
    }

    /** Stable groups of up to four share a loose patch, never assigned rows or a ring. */
    WorldPoint idleGroupCenter(FanEntity fan, WorldPoint player) {
        int group = fan.id / 4;
        int reach = Math.min(4, Math.max(2,
                (int) Math.ceil(Math.sqrt(Math.max(1, config.crowdSize()) / 8.0))));
        int x = (int) Math.round((unit(group, 107) - unit(group, 131)) * reach);
        int y = (int) Math.round((unit(group, 157) - unit(group, 181)) * reach);
        return offset(player, x, y);
    }

    public WorldPoint chooseIdleTarget(
            FanEntity fan, WorldPoint player, Set<WorldPoint> reserved, Random random, int tick) {
        return chooseIdleTarget(fan, player, reserved, random, tick, true);
    }

    /** Short walks separated by real pauses; the manager limits simultaneous wanderers. */
    public WorldPoint chooseIdleTarget(
            FanEntity fan, WorldPoint player, Set<WorldPoint> reserved, Random random, int tick,
            boolean mayStartWalk) {
        if (fan.position == null) return null;
        if (fan.state == com.celebritymode.fan.FanState.REACTION) return fan.position;
        if (fan.idleTarget != null && collision.canOccupy(fan.idleTarget)
                && fan.idleTarget.distanceTo(player) <= 6) {
            // Finish a walk even when the budget is full. A denied start must not
            // abandon its destination or reset the person's own pause timer.
            if (!fan.idleTarget.equals(fan.position) || tick < fan.nextWanderTick)
                return holdIdleTarget(fan, reserved);
        }
        if (!mayStartWalk || tick < fan.nextWanderTick) {
            reserved.add(fan.position);
            return fan.position;
        }
        boolean quiet = fan.personality == com.celebritymode.fan.FanPersonality.QUIET
                || fan.personality == com.celebritymode.fan.FanPersonality.SCREENSHOTTER;
        int pause = quiet ? 45 + random.nextInt(56) : 22 + random.nextInt(44);
        fan.nextWanderTick = tick + pause;
        // Occasionally keep chatting/watching instead of treating the timer as a command.
        if (fan.idleTarget != null && random.nextInt(5) == 0)
            return holdIdleTarget(fan, reserved);
        WorldPoint center = idleGroupCenter(fan, player);
        WorldPoint home = offset(center,
                (int) Math.round(unit(fan.id, 211) * 2 - 1),
                (int) Math.round(unit(fan.id, 239) * 2 - 1));
        WorldPoint best = fan.position;
        double bestScore = Double.MAX_VALUE;
        for (int x = -2; x <= 2; x++)
            for (int y = -2; y <= 2; y++) {
                WorldPoint candidate = offset(fan.position, x, y);
                if (candidate.equals(fan.position) || candidate.equals(player)
                        || candidate.distanceTo(player) > 6
                        || candidate.distanceTo(home) > Math.max(2, fan.position.distanceTo(home))
                        || !collision.canOccupy(candidate)
                        || !connected(fan.position, candidate)) continue;
                double score = candidate.distanceTo(home) * 1.4
                        + candidate.distanceTo(fan.position) * 0.3 + random.nextDouble() * 2.0;
                if (config.respectPersonalSpace() && reserved.contains(candidate)) score += 0.9;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        fan.idleTarget = best;
        return holdIdleTarget(fan, reserved);
    }

    private WorldPoint holdIdleTarget(FanEntity fan, Set<WorldPoint> reserved) {
        reserved.add(fan.idleTarget);
        return fan.idleTarget;
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
