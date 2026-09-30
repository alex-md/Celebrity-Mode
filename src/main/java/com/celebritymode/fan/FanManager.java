package com.celebritymode.fan;

import com.celebritymode.CelebrityModeConfig;
import com.celebritymode.appearance.*;
import com.celebritymode.chat.*;
import com.celebritymode.movement.*;
import com.celebritymode.util.OrientationUtil;

import net.runelite.api.*;
import net.runelite.api.coords.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import javax.inject.*;

@Singleton
public final class FanManager {
    private static final Logger log = LoggerFactory.getLogger(FanManager.class);
    private static final String[] NAMES = {
        "xX_Bob69_Xx",
        "FreeStuffPls",
        "ZezimaFan42",
        "nice cape",
        "BankStanding",
        "Wave2Flash",
        "IronNoob",
        "BuyingGF2007",
        "SirFollowU",
        "CapeEnjoyer",
        "GzMachine",
        "NoXpWaste"
    };
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
    private int nextCrowdEmoteTick;
    private boolean recognitionPending, sceneSuspended, instanced;
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
            dx = Integer.signum(point.getX() - previous.getX());
            dy = Integer.signum(point.getY() - previous.getY());
            running = previous.distanceTo(point) >= 2;
            // Fill a two-tile running breadcrumb only when the intermediate route passes collision
            // checks.
            WorldPoint middle = FormationPlanner.offset(previous, dx, dy);
            if (running && collision.canStep(previous, middle) && collision.canStep(middle, point))
                trail.update(middle);
            stationaryTicks = 0;
            for (FanEntity fan : fans) fan.idleTarget = null;
        } else stationaryTicks++;
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
        resizeCrowd(config.crowdSize());
        if (changed)
            for (int i = 0; i < fans.size(); i++) {
                FanEntity fan = fans.get(i);
                fan.arrived = false;
                fan.arrivalTick = CrowdArrival.dueTick(tick, i);
            }
        boolean stopped = stationaryTicks >= 2;
        Set<WorldPoint> reserved = new HashSet<>();
        for (FanEntity fan : fans) if (fan.position != null) reserved.add(fan.position);
        for (FanEntity fan : fans) {
            if (!fan.arrived && tick < fan.arrivalTick) continue;
            if (fan.position != null) reserved.remove(fan.position);
            WorldPoint target =
                    stopped
                            ? formations.chooseIdleTarget(fan, point, reserved, random, tick)
                            : formations.chooseTarget(fan, point, dx, dy, trail, reserved);
            if (fan.object == null
                    || !fan.object.isActive()
                    || fan.position == null
                    || LocalPoint.fromWorld(view, fan.position) == null
                    || fan.position.distanceTo(point) > 45
                    || fan.stalledTicks > 20
                    || (target != null && fan.position.distanceTo(target) > 24)) {
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
            movement.render(fan, view, client.getGameCycle());
            movement.plan(fan, target, trail, running, view, client.getGameCycle(), stopped);
            if (fan.state == FanState.IDLE || fan.state == FanState.REACTION) {
                if (fan.id % 5 != 0)
                    fan.targetOrientation =
                            OrientationUtil.facing(
                                    point.getX() - fan.position.getX(),
                                    point.getY() - fan.position.getY());
            }
        }
        if (stopped && config.reactToEvents() && tick >= nextCrowdEmoteTick) {
            List<FanEntity> idle = new ArrayList<>();
            for (FanEntity fan : fans)
                if (fan.isVisible()
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
                chat.react(
                        fans,
                        tick,
                        new String[] {
                            "omg {player}", "{player} screenshot pls", "no way {player}"
                        });
                lastRecognition = tick;
            }
            recognitionPending = false;
        }
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
        fan.orientation =
                fan.targetOrientation =
                        OrientationUtil.facing(
                                previous == null ? 0 : previous.getX() - point.getX(),
                                previous == null ? 1 : previous.getY() - point.getY());
        fan.object.setLocation(fan.renderLocation, plane);
        fan.object.setOrientation(fan.orientation);
        animations.setState(fan, FanState.IDLE);
        fan.nextWanderTick = tick + 2 + random.nextInt(5);
        fan.object.setActive(true);
    }

    public void onClientTick() {
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
            if (fan.isVisible()) movement.render(fan, view, client.getGameCycle());
    }

    public void resizeCrowd(int size) {
        int desired = Math.max(1, Math.min(CrowdArrival.MAX_FANS, size));
        while (fans.size() > desired) fans.remove(fans.size() - 1).deactivate();
        int arrivalOrdinal = 0;
        while (fans.size() < desired) {
            int id = nextId++;
            String suffix = id >= NAMES.length ? Integer.toString(id / NAMES.length) : "";
            String base = NAMES[id % NAMES.length];
            String name = base.substring(0, Math.min(base.length(), 12 - suffix.length())) + suffix;
            Player player = client.getLocalPlayer();
            if (player != null
                    && name.replace('_', ' ')
                            .equalsIgnoreCase(
                                    player.getName() == null
                                            ? ""
                                            : player.getName()
                                                    .replace('_', ' ')
                                                    .replace('\u00a0', ' '))) name = "Fan" + id;
            fans.add(
                    new FanEntity(
                            id,
                            FanPersonality.values()[random.nextInt(FanPersonality.values().length)],
                            name,
                            1 + random.nextInt(10),
                            random.nextInt(3) - 1));
            fans.get(fans.size() - 1).arrivalTick = CrowdArrival.dueTick(tick, arrivalOrdinal++);
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
            case "crowdSize":
                resizeCrowd(config.crowdSize());
                break;
            case "crowdGearTier":
                rebuildAppearances();
                break;
            case "formationStyle":
                formations.reassignSlots(fans);
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
        if (!config.reactToEvents() || tick - lastEventReaction < 3) return;
        lastEventReaction = tick;
        chat.level(fans, tick, skill, level);
    }

    public void react(boolean death) {
        if (!config.reactToEvents() || tick - lastEventReaction < 3) return;
        lastEventReaction = tick;
        chat.react(
                fans,
                tick,
                death
                        ? new String[] {"rip {player}", "{player} noooo", "F for {player}"}
                        : new String[] {"gz {player}", "huge {player}", "{player} lets go"});
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
                animations.setState(fan, FanState.IDLE);
            }
        sceneSuspended = false;
    }

    public void resetScene() {
        for (FanEntity fan : fans) {
            fan.deactivate();
            fan.arrived = false;
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
        nextId = 0;
        tick = 0;
        lastRecognition = -1000;
        lastEventReaction = -1000;
    }
}
