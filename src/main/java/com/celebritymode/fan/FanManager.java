package com.celebritymode.fan;

import com.celebritymode.CelebrityModeConfig;
import com.celebritymode.appearance.*;
import com.celebritymode.chat.*;
import com.celebritymode.movement.*;
import com.celebritymode.generation.UsernameGenerator;
import com.celebritymode.util.OrientationUtil;

import net.runelite.api.*;
import net.runelite.api.coords.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalTime;
import java.util.*;

import javax.inject.*;

@Singleton
public final class FanManager {
    private static final Logger log = LoggerFactory.getLogger(FanManager.class);
    private final Client client;
    private final CelebrityModeConfig config;
    private final Random random;
    private final FanAppearanceFactory appearances;
    private final FanModelFactory models;
    private final CollisionService collision;
    private final FormationPlanner formations;
    private final FanMovementController movement;
    private final FanAnimations animations;
    private final FanChatManager chat;
    private final FanActivityTracker activity = new FanActivityTracker();
    private final List<FanEntity> fans = new ArrayList<>();
    private final List<FanEntity> readOnlyFans = Collections.unmodifiableList(fans);
    private final MovementTrail trail = new MovementTrail();
    private final CrowdPopulation population = new CrowdPopulation();
    private WorldView view;
    private WorldPoint previous;
    private int worldViewId,
            baseX,
            baseY,
            plane,
            dx = 0,
            dy = 1,
            tick,
            stationaryTicks,
            nextId,
            retryAfter,
            lastRecognition = -1000;
    private int lastEventReaction = -1000;
    private int nextCrowdEmoteTick, nextReplacementTick;
    private boolean recognitionPending, sceneSuspended, instanced, playerRunning;
    private int instanceLayout;

    @Inject
    public FanManager(
            Client client,
            CelebrityModeConfig config,
            Random random,
            FanAppearanceFactory appearances,
            FanModelFactory models,
            CollisionService collision,
            FormationPlanner formations,
            FanMovementController movement,
            FanAnimations animations) {
        this.client = client;
        this.config = config;
        this.random = random;
        this.appearances = appearances;
        this.models = models;
        this.collision = collision;
        this.formations = formations;
        this.movement = movement;
        this.animations = animations;
        chat = new FanChatManager(random);
    }

    public List<FanEntity> getFans() {
        return readOnlyFans;
    }

    public List<WorldPoint> getBreadcrumbs() {
        return trail.snapshot();
    }

    public WorldView getView() {
        return view;
    }

    public void initialize(Player player) {
        onGameTick(player);
    }

    public void onGameTick(Player player) {
        tick++;
        if (!config.showCrowd()) {
            if (view != null) resetScene();
            return;
        }
        if (client.getGameState() == GameState.LOADING) {
            suspendScene();
            return;
        }
        if (client.getGameState() != GameState.LOGGED_IN) {
            resetScene();
            return;
        }
        if (player == null) {
            suspendScene();
            return;
        }
        LocalPoint local = player.getLocalLocation();
        WorldView current = local == null ? null : client.getWorldView(local.getWorldView());
        if (current == null || current.getCollisionMaps() == null) {
            suspendScene();
            return;
        }
        WorldPoint point =
                WorldPoint.fromLocal(current, local.getX(), local.getY(), current.getPlane());
        boolean baseMoved = current.getBaseX() != baseX || current.getBaseY() != baseY;
        int layout =
                current.isInstance() ? Arrays.deepHashCode(current.getInstanceTemplateChunks()) : 0;
        boolean changed =
                view == null
                        || current.getId() != worldViewId
                        || current.getPlane() != plane
                        || current.isInstance() != instanced
                        || (previous != null && previous.distanceTo(point) > 8)
                        || (instanced && !baseMoved && layout != instanceLayout);
        if (!changed && (sceneSuspended || baseMoved || current != view)) rebaseScene(current);
        if (changed) {
            resetScene();
            view = current;
            worldViewId = view.getId();
            baseX = view.getBaseX();
            baseY = view.getBaseY();
            plane = view.getPlane();
            instanced = view.isInstance();
            instanceLayout = layout;
            collision.bind(view);
            configureChat();
            nextCrowdEmoteTick = tick + 100 + random.nextInt(140);
            recognitionPending = true;
        }
        instanceLayout = layout;
        boolean running = false;
        if (previous != null && !previous.equals(point)) {
            if (stationaryTicks > 0) {
                trail.beginTravelLeg();
                for (FanEntity fan : fans) {
                    fan.nextFollowTick = tick + random.nextInt(5);
                    fan.nextMovementCycle = client.getGameCycle() + random.nextInt(25);
                    // Wait in place while noticing the player leaving, never backtrack
                    // toward a stale destination from the preceding travel leg.
                    fan.followTarget = fan.position;
                }
            }
            dx = Integer.signum(point.getX() - previous.getX());
            dy = Integer.signum(point.getY() - previous.getY());
            running = previous.distanceTo(point) >= 2;
            // Fill a two-tile running breadcrumb only when the intermediate route passes collision
            // checks.
            WorldPoint middle = FormationPlanner.offset(previous, dx, dy);
            if (running && collision.canStep(previous, middle) && collision.canStep(middle, point))
                trail.update(middle);
            stationaryTicks = 0;
            for (FanEntity fan : fans) {
                fan.idleTarget = null;
                fan.stopTargetChosen = false;
            }
        } else {
            stationaryTicks++;
            if (stationaryTicks == 1)
                for (FanEntity fan : fans)
                    fan.stopDecisionTick = tick + 2 + random.nextInt(9)
                            + (fan.personality == FanPersonality.QUIET ? 3 : 0);
        }
        playerRunning = running;
        trail.update(point);
        previous = point;
        String equipped = "";
        PlayerComposition composition = player.getPlayerComposition();
        if (composition != null) {
            int weapon = composition.getEquipmentId(net.runelite.api.kit.KitType.WEAPON);
            if (weapon >= 0) equipped = client.getItemDefinition(weapon).getName();
        }
        chat.setContext(
                activity.observe(client, player, equipped, stationaryTicks == 0, running, tick));
        appearances.advanceCatalog();
        adjustAttendance(
                population.update(
                        tick, config.crowdSize(), nearbyPlayers(current),
                        LocalTime.now().getHour(), random), point);
        if (changed)
            for (int i = 0; i < fans.size(); i++) {
                FanEntity fan = fans.get(i);
                fan.arrived = false;
                fan.arrivalTick = config.arrivalPace().dueTick(tick, i);
            }
        updateVisits(point);
        boolean stopped = stationaryTicks >= 25;
        Set<WorldPoint> reserved = new HashSet<>();
        for (FanEntity fan : fans) if (fan.position != null) reserved.add(fan.position);
        for (FanEntity fan : fans) {
            if (!fan.arrived && tick < fan.arrivalTick) continue;
            if (fan.position != null) reserved.remove(fan.position);
            if (fan.departing) {
                if (fan.isVisible() && fan.departureTarget != null) {
                    movement.plan(fan, fan.departureTarget, trail, false, view, client.getGameCycle(), true);
                }
                continue;
            }
            boolean gathering = formations.shouldGather(fan, stationaryTicks);
            WorldPoint target;
            if (fan.state == FanState.REACTION && stationaryTicks > 0) {
                target = fan.position;
            } else if (gathering && fan.position != null) {
                target = formations.chooseIdleTarget(fan, point, reserved, random, tick);
            } else if (fan.followTarget == null && fan.position != null && tick < fan.nextFollowTick) {
                target = fan.position;
            } else if (fan.followTarget != null
                    && collision.canOccupy(fan.followTarget)
                    && ((tick < fan.nextFollowTick
                            && !(stationaryTicks == 0 && fan.position != null
                                    && fan.position.distanceTo(fan.followTarget) <= 1))
                        || (stationaryTicks > 0
                            && (fan.stopTargetChosen || tick < fan.stopDecisionTick)))
                    && (stationaryTicks == 0 || formations.validFollowingStop(fan, point))) {
                target = fan.followTarget;
            } else {
                target = formations.chooseTarget(
                        fan, point, dx, dy, trail, reserved, stationaryTicks);
                fan.nextFollowTick = tick + 1 + random.nextInt(4);
                if (stationaryTicks > 0) fan.stopTargetChosen = true;
            }
            if (!gathering && fan.state != FanState.REACTION) fan.followTarget = target;
            if (target != null) reserved.add(target);
            if (fan.object == null
                    || !fan.object.isActive()
                    || fan.position == null
                    || LocalPoint.fromWorld(view, fan.position) == null
                    || fan.position.distanceTo(point) > 45) {
                if (tick < retryAfter) continue;
                try {
                    WorldPoint spawnPoint =
                            fan.arrived
                                    ? target
                                    : formations.chooseArrivalPoint(fan, point, target, reserved);
                    spawn(fan, spawnPoint);
                    if (fan.object != null) fan.arrived = true;
                } catch (RuntimeException ex) {
                    fan.deactivate();
                    retryAfter = tick + 5;
                    log.debug("Fan {} awaiting available cache/scene resources", fan.id, ex);
                }
                continue;
            }
            if (fan.movementPath == null && target != null && !target.equals(fan.target)
                    && fan.position.equals(fan.target))
                fan.nextMovementCycle = client.getGameCycle() + random.nextInt(25);
            movement.plan(fan, target, trail, running, view, client.getGameCycle(),
                    gathering);
        }
        if (stopped && config.reactToEvents() && tick >= nextCrowdEmoteTick) {
            List<FanEntity> idle = new ArrayList<>();
            for (FanEntity fan : fans)
                if (fan.isVisible() && !fan.departing
                        && fan.state == FanState.IDLE
                        && fan.movementPath == null
                        && fan.personality != FanPersonality.QUIET) idle.add(fan);
            if (!idle.isEmpty()) {
                FanEntity fan = idle.get(random.nextInt(idle.size()));
                animations.react(fan, random.nextBoolean());
                nextCrowdEmoteTick = tick + 100 + random.nextInt(140);
            }
        }
        chat.tick(fans, tick);
        if (recognitionPending && fans.stream().anyMatch(FanEntity::isVisible)) {
            if (config.reactToEvents() && tick - lastRecognition > 100) {
                chat.reactEvent(fans, tick, "recognition");
                lastRecognition = tick;
            }
            recognitionPending = false;
        }
    }

    private int nearbyPlayers(WorldView current) {
        if (current.players() == null) return 0;
        int count = 0;
        for (Player ignored : current.players()) if (++count >= 40) break;
        return count;
    }

    private void spawn(FanEntity fan, WorldPoint point) {
        fan.deactivate();
        if (point == null || !collision.canOccupy(point)) return;
        LocalPoint local = LocalPoint.fromWorld(view, point);
        if (local == null) return;
        if (fan.appearance == null)
            fan.appearance = appearances.createAppearance(fan.id, config.crowdGearTier());
        if (fan.model == null) fan.model = models.createModel(fan.appearance);
        Model model = fan.model;
        if (model == null) throw new IllegalStateException("Missing fan model");
        fan.object = client.createRuneLiteObject();
        fan.object.setModel(model);
        fan.renderLocation = local.plus(fan.tileJitterX, fan.tileJitterY);
        fan.position = point;
        fan.target = point;
        if (fan.joinedTick < 0)
            fan.orientation = OrientationUtil.facing(
                    previous == null ? 0 : Integer.signum(previous.getX() - point.getX()),
                    previous == null ? 1 : Integer.signum(previous.getY() - point.getY()));
        fan.targetOrientation = fan.orientation;
        fan.object.setLocation(fan.renderLocation, plane);
        fan.object.setOrientation(fan.orientation);
        animations.setState(fan, FanState.IDLE);
        fan.nextWanderTick = tick + 15 + random.nextInt(45);
        // Small persistent pace differences prevent a crowd-wide step phase.
        if (fan.joinedTick < 0) fan.movementDurationCycles = 28 + random.nextInt(3);
        fan.object.setActive(true);
        fan.lastRenderCycle = client.getGameCycle();
        if (fan.joinedTick < 0) {
            fan.joinedTick = tick;
            double mean;
            switch (fan.personality) {
                case SCREENSHOTTER: mean = 100; break;
                case BEGGAR: mean = 140; break;
                case MEMER: mean = 200; break;
                case EXCITED: mean = 260; break;
                case HYPEMAN: mean = 400; break;
                default: mean = 350; break;
            }
            fan.leaveTick = tick + 100 + (int) Math.min(800, -Math.log1p(-random.nextDouble()) * mean);
        }
    }

    public void onClientTick() {
        if (!config.showCrowd()) {
            if (view != null) resetScene();
            return;
        }
        Player player = client.getLocalPlayer();
        if (client.getGameState() == GameState.LOADING) {
            suspendScene();
            return;
        }
        if (client.getGameState() != GameState.LOGGED_IN) {
            resetScene();
            return;
        }
        if (player == null) {
            suspendScene();
            return;
        }
        LocalPoint point = player.getLocalLocation();
        if (view == null) return;
        if (point == null
                || client.getWorldView(point.getWorldView()) != view
                || point.getWorldView() != worldViewId
                || view.getBaseX() != baseX
                || view.getBaseY() != baseY
                || view.getPlane() != plane) {
            suspendScene();
            return; // GameTick classifies relocation against preserved world positions.
        }
        if (sceneSuspended) return;
        for (FanEntity fan : fans)
            if (fan.isVisible()) movement.advance(
                    fan, trail, playerRunning, view, client.getGameCycle());
    }

    private void adjustAttendance(int desired, WorldPoint player) {
        List<FanEntity> attending = new ArrayList<>();
        for (FanEntity fan : fans) if (!fan.departing) attending.add(fan);
        if (attending.size() > desired) {
            FanEntity fan = attending.get(random.nextInt(attending.size()));
            if (fan.position != null) beginDeparture(fan, player);
        }
        // Attendance reductions walk out; an explicit user resize still applies the new cap immediately.
        resizeCrowd(Math.max(fans.size(), desired));
    }

    public void resizeCrowd(int size) {
        int desired = Math.max(1, Math.min(CrowdArrival.MAX_FANS, size));
        while (fans.size() > desired) fans.remove(random.nextInt(fans.size())).deactivate();
        int arrivalOrdinal = 0;
        while (fans.size() < desired && tick >= nextReplacementTick) {
            int id = nextId++;
            Set<String> excluded = new HashSet<>();
            for (FanEntity existing : fans) excluded.add(existing.displayName);
            Player player = client.getLocalPlayer();
            if (player != null) excluded.add(player.getName());
            String name = UsernameGenerator.generate(random, excluded);
            fans.add(
                    new FanEntity(
                            id,
                            FanPersonality.values()[random.nextInt(FanPersonality.values().length)],
                            name,
                            1 + random.nextInt(10),
                            random.nextInt(3) - 1));
            fans.get(fans.size() - 1).arrivalTick = config.arrivalPace().dueTick(tick, arrivalOrdinal++);
            if (nextReplacementTick > 0) nextReplacementTick = tick + 8 + random.nextInt(33);
        }
        formations.reassignSlots(fans);
    }

    private void beginDeparture(FanEntity fan, WorldPoint player) {
        fan.departing = true;
        fan.departureTarget = formations.chooseDeparturePoint(fan, player);
        fan.departureDeadline = tick + 45;
        fan.overheadText = null;
        fan.idleTarget = null;
    }

    private void updateVisits(WorldPoint player) {
        int departing = (int) fans.stream().filter(f -> f.departing).count();
        int limit = Math.max(1, (fans.size() + 9) / 10);
        for (Iterator<FanEntity> iterator = fans.iterator(); iterator.hasNext();) {
            FanEntity fan = iterator.next();
            if (!fan.departing && fan.isVisible() && tick >= fan.leaveTick && departing < limit) {
                beginDeparture(fan, player);
                departing++;
            }
            if (fan.departing && (fan.position == null || tick >= fan.departureDeadline
                    || (fan.departureTarget != null && fan.departureTarget.equals(fan.position)
                            && fan.movementPath == null))) {
                fan.deactivate();
                iterator.remove();
                nextReplacementTick = Math.max(nextReplacementTick, tick + 8 + random.nextInt(33));
            }
        }
        formations.reassignSlots(fans);
    }

    public void rebuildAppearances() {
        for (FanEntity fan : fans) {
            fan.deactivate();
            fan.appearance = null;
            fan.model = null;
        }
        retryAfter = 0;
    }

    public void onConfigChanged(String key) {
        switch (key) {
            case "showCrowd":
                if (!config.showCrowd()) resetScene();
                break;
            case "crowdSize":
                population.clear();
                nextReplacementTick = 0;
                resizeCrowd(config.crowdSize());
                break;
            case "crowdGearTier":
                rebuildAppearances();
                break;
            case "formationStyle":
                formations.reassignSlots(fans);
                // Settled followers must apply layout changes even before the player moves.
            case "maxSpread":
            case "respectPersonalSpace":
                for (FanEntity fan : fans) {
                    fan.followTarget = null;
                    fan.idleTarget = null;
                    fan.nextFollowTick = tick + Math.floorMod(fan.id * 37, 5);
                    fan.stopTargetChosen = false;
                }
                break;
            case "chatFrequency":
            case "customQuotes":
            case "customQuoteMode":
                chat.clear(fans);
                configureChat();
                break;
            default:
                break; // Other preferences are read on the next tick/paint.
        }
    }

    private void configureChat() {
        chat.configure(
                config.chatFrequency(), config.customQuoteMode(), config.customQuotes(), tick);
    }

    public void onExperience(Skill skill, int xp) {
        activity.experience(skill, xp, tick);
    }

    public void onMenuOption(String option) {
        activity.menu(option, tick);
    }

    public void reactLevel(Skill skill, int level) {
        if (!config.showCrowd() || !config.reactToEvents() || tick - lastEventReaction < 3) return;
        lastEventReaction = tick;
        chat.level(fans, tick, skill, level);
    }

    public void react(boolean death) {
        if (!config.showCrowd() || !config.reactToEvents() || tick - lastEventReaction < 3) return;
        lastEventReaction = tick;
        chat.reactEvent(fans, tick, death ? "death" : "achievement");
    }

    public void onGameStateChanged(GameState state) {
        if (state == GameState.LOADING) suspendScene();
        else if (state != GameState.LOGGED_IN) resetScene();
    }

    /** Ordinary map loading unregisters objects but keeps route, models, and arrival progress. */
    private void suspendScene() {
        if (sceneSuspended) return;
        for (FanEntity fan : fans) {
            if (fan.object != null) fan.object.setActive(false);
            fan.renderLocation = null;
            fan.movementPath = null;
        }
        sceneSuspended = true;
    }

    private void rebaseScene(WorldView current) {
        suspendScene();
        view = current;
        worldViewId = view.getId();
        baseX = view.getBaseX();
        baseY = view.getBaseY();
        plane = view.getPlane();
        collision.bind(view);
        for (FanEntity fan : fans)
            if (fan.object != null && fan.position != null) {
                LocalPoint local = LocalPoint.fromWorld(view, fan.position);
                if (local == null) continue; // Only this follower needs a bounded local repair.
                fan.renderLocation = local.plus(fan.tileJitterX, fan.tileJitterY);
                fan.object.setLocation(fan.renderLocation, plane);
                fan.object.setActive(true);
                fan.lastRenderCycle = client.getGameCycle();
                animations.setState(fan, FanState.IDLE);
            }
        sceneSuspended = false;
    }

    public void resetScene() {
        for (FanEntity fan : fans) {
            fan.deactivate();
            fan.arrived = false;
            fan.departing = false;
            fan.departureTarget = null;
            fan.arrivalTick = Integer.MAX_VALUE;
        }
        trail.clear();
        activity.clear();
        chat.clear(fans);
        previous = null;
        view = null;
        sceneSuspended = false;
        collision.bind(null);
        stationaryTicks = 0;
        recognitionPending = false;
        retryAfter = 0;
    }

    public void cleanup() {
        resetScene();
        fans.clear();
        models.clear();
        appearances.clear();
        animations.clear();
        population.clear();
        nextId = 0;
        tick = 0;
        nextReplacementTick = 0;
        lastRecognition = -1000;
        lastEventReaction = -1000;
    }
}
