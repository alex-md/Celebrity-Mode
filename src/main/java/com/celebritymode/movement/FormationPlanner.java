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
        int spread = spread();
        boolean train = config.formationStyle() == FormationStyle.TRAIL;
        int delay = train ? 1 + fan.trailOffset : 1 + fan.trailOffset / 2;
        WorldPoint anchor = trail.getFollowingPoint(delay);
        if (anchor == null) anchor = player;
        int width = train ? 1 : spread;
        // Orient offsets along this part of the route, not the leader's latest turn.
        WorldPoint behind = trail.getFollowingPoint(delay + 1);
        int hx = behind == null ? dx : Integer.signum(anchor.getX() - behind.getX());
        int hy = behind == null ? dy : Integer.signum(anchor.getY() - behind.getY());
        if (hx == 0 && hy == 0) { hx = dx; hy = dy; }
        WorldPoint preferred;
        boolean followingRoute = stationaryTicks == 0 && trail.size() > 1;
        if (stationaryTicks > 0 && !followingRoute) {
            // Each person chooses a nearby viewing spot after noticing the stop.
            // Travel breadcrumbs guide routes; they are not permanent resting slots.
            anchor = player;
            if (fan.position != null && validSettledTarget(fan.position, player)
                    && collision.canOccupy(fan.position) && unit(fan.id, 307) < 0.35) {
                reserved.add(fan.position);
                return fan.position;
            }
            double reach = spread * 0.9;
            int x = (int) Math.round((unit(fan.id, 17) - unit(fan.id, 43)) * reach);
            int y = (int) Math.round((unit(fan.id, 71) - unit(fan.id, 97)) * reach);
            preferred = offset(player, x, y);
        } else {
            // Each person retains a preferred lateral click; Trail favors shared breadcrumbs.
            // Spread bounds that preference rather than enforcing minimum separation.
            int lateral = unit(fan.id, 13) < (train ? 0.8 : 0.15) ? 0
                    : (int) Math.round((unit(fan.id, 29) - unit(fan.id, 53)) * width);
            preferred = offset(anchor, -hy * lateral, hx * lateral);
        }
        if (fan.position != null && (stationaryTicks == 0 || followingRoute)
                && (preferred.getX() - fan.position.getX()) * dx
                        + (preferred.getY() - fan.position.getY()) * dy < 0
                && collision.canOccupy(fan.position)
                && (!config.respectPersonalSpace() || !fan.position.equals(player))) {
            reserved.add(fan.position);
            return fan.position;
        }
        WorldPoint best = null;
        double bestScore = Double.MAX_VALUE;
        // Personal space is local etiquette, not a requirement to allocate every fan a unique tile.
        // Stopped fans can use the whole nearby patch instead of retaining marching slots.
        int search = stationaryTicks > 0 && !followingRoute ? spread : 1;
        for (int x = -search; x <= search; x++)
            for (int y = -search; y <= search; y++) {
                WorldPoint candidate = offset(preferred, x, y);
                if (!collision.canOccupy(candidate) || !connected(anchor, candidate)
                        || (stationaryTicks > 0 && !followingRoute && !insideSocialPatch(candidate, player))) continue;
                if (followingRoute) {
                    if ((candidate.getX() - player.getX()) * dx
                            + (candidate.getY() - player.getY()) * dy > 0) continue;
                }
                if (stationaryTicks == 0 || followingRoute) {
                    int lateralDistance = Math.abs(-hy * (candidate.getX() - anchor.getX())
                            + hx * (candidate.getY() - anchor.getY()));
                    if (lateralDistance > width * (Math.abs(hx) + Math.abs(hy))) continue;
                }
                double score = x * x + y * y + unit(fan.id, x * 31 + y * 101) * 0.3;
                if (stationaryTicks > 0 && fan.position != null)
                    score += candidate.distanceTo(fan.position) * 0.25;
                if (stationaryTicks == 0 && candidate.equals(fan.followTarget)) score -= 0.8;
                if (config.respectPersonalSpace()) {
                    if (candidate.equals(player)) score += 10000;
                    if (reserved.contains(candidate)) score += 1.05;
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
        int reach = spread();
        int x = (int) Math.round((unit(group, 107) - unit(group, 131)) * reach);
        int y = (int) Math.round((unit(group, 157) - unit(group, 181)) * reach);
        return offset(player, x, y);
    }

    /** Each fan owns its destination and pause; there is no shared walking quota. */
    public WorldPoint chooseIdleTarget(
            FanEntity fan, WorldPoint player, Set<WorldPoint> reserved, Random random, int tick) {
        if (fan.position == null) return null;
        if (fan.state == com.celebritymode.fan.FanState.REACTION) return fan.position;
        if (fan.stalledTicks >= 3) {
            fan.idleTarget = null;
            fan.nextWanderTick = 0;
        }
        if (fan.idleTarget != null && collision.canOccupy(fan.idleTarget)
                && (validSettledTarget(fan.idleTarget, player)
                        || fan.idleTarget.distanceTo(player) < fan.position.distanceTo(player))) {
            // Finish this person's click before considering another destination.
            if (!fan.idleTarget.equals(fan.position) || tick < fan.nextWanderTick)
                return holdIdleTarget(fan, reserved);
        }
        boolean correcting = !validSettledTarget(fan.position, player);
        if (!correcting && tick < fan.nextWanderTick) {
            reserved.add(fan.position);
            return fan.position;
        }
        boolean quiet = fan.personality == com.celebritymode.fan.FanPersonality.QUIET
                || fan.personality == com.celebritymode.fan.FanPersonality.SCREENSHOTTER;
        int pause = quiet ? 65 + random.nextInt(86) : 35 + random.nextInt(66);
        fan.nextWanderTick = tick + pause;
        WorldPoint center = idleGroupCenter(fan, player);
        WorldPoint home = offset(center,
                (int) Math.round(unit(fan.id, 211) * 2 - 1),
                (int) Math.round(unit(fan.id, 239) * 2 - 1));
        // Once near friends, watching is the default. Timers offer a chance to move;
        // they do not require a lap around an assigned home tile.
        if (!correcting && fan.position.distanceTo(home) <= 2 && random.nextInt(4) != 0) {
            fan.idleTarget = fan.position;
            return holdIdleTarget(fan, reserved);
        }
        WorldPoint best = fan.position;
        double bestScore = Double.MAX_VALUE;
        for (int x = -2; x <= 2; x++)
            for (int y = -2; y <= 2; y++) {
                WorldPoint candidate = offset(fan.position, x, y);
                if (candidate.equals(fan.position) || candidate.equals(player)
                        || (!insideSocialPatch(candidate, player)
                                && candidate.distanceTo(player) >= fan.position.distanceTo(player))
                        || candidate.distanceTo(home) > Math.max(2, fan.position.distanceTo(home))
                        || !collision.canOccupy(candidate)
                        || !connected(fan.position, candidate)) continue;
                double score = candidate.distanceTo(home) * 1.4
                        + candidate.distanceTo(fan.position) * 0.3 + random.nextDouble() * 2.0;
                if (candidate.distanceTo(player) > spread()) score += 100;
                if (config.respectPersonalSpace() && reserved.contains(candidate)) score += 1.05;
                if (score < bestScore) {
                    bestScore = score;
                    best = candidate;
                }
            }
        fan.idleTarget = best;
        return holdIdleTarget(fan, reserved);
    }

    private int spread() { return Math.max(1, Math.min(8, config.maxSpread())); }

    public boolean validSettledTarget(WorldPoint target, WorldPoint player) {
        return target != null && insideSocialPatch(target, player)
                && (!config.respectPersonalSpace() || !target.equals(player));
    }

    private boolean insideSocialPatch(WorldPoint target, WorldPoint player) {
        int x = target.getX() - player.getX(), y = target.getY() - player.getY();
        return target.getPlane() == player.getPlane() && x * x + y * y <= spread() * spread();
    }

    /** Old clicks can finish while noticing the stop; chosen viewing spots stay local. */
    public boolean validFollowingStop(FanEntity fan, WorldPoint player) {
        return fan.followTarget != null && (fan.stopTargetChosen
                ? validSettledTarget(fan.followTarget, player)
                : fan.followTarget.distanceTo(player) <= Math.max(spread(), 12)
                        && (!config.respectPersonalSpace() || !fan.followTarget.equals(player)));
    }

    public WorldPoint chooseDeparturePoint(FanEntity fan, WorldPoint player) {
        for (int attempt = 0; attempt < 32; attempt++) {
            double angle = fan.id * 2.399963229728653 + attempt * 0.4;
            int radius = Math.max(9, spread() + 3);
            WorldPoint point = offset(player, (int) Math.round(Math.cos(angle) * radius),
                    (int) Math.round(Math.sin(angle) * radius));
            if (collision.canOccupy(point) && connected(fan.position, point)) return point;
        }
        return null;
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
