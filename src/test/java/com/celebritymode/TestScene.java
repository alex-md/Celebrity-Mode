package com.celebritymode;

import com.celebritymode.appearance.*;
import com.celebritymode.fan.*;
import com.celebritymode.movement.*;

import net.runelite.api.*;
import net.runelite.api.coords.*;

import java.lang.reflect.*;
import java.util.*;

/** In-memory API fixture; uses real RuneLiteObject and AnimationController implementations. */
public final class TestScene {
    public int baseX = 3200, baseY = 3200, plane, viewId = -1, cycle, modelBuilds;
    public final List<Integer> loadedAnimations = new ArrayList<>();
    public boolean bodyCacheReady = true, unsupportedEquipment;
    public int textureCloneCalls;
    public short[] faceTextures;
    public String playerName = "LocalHero";
    public Actor interacting;
    public int playerAnimation = -1, hp = 99;
    public boolean bankOpen;
    public final Map<Skill, Integer> skillXp = new EnumMap<>(Skill.class);
    public boolean instance;
    public int[][][] instanceChunks = new int[4][13][13];
    public final Map<Integer, byte[]> itemDefinitions = new LinkedHashMap<>();
    public WorldPoint playerPosition = new WorldPoint(3232, 3232, 0);
    public GameState state = GameState.LOGGED_IN;
    public final int[][] flags = new int[104][104];
    private final byte[][][] settings = new byte[4][104][104];
    private final int[][][] heights = new int[4][105][105];
    public final Set<RuneLiteObjectController> active =
            Collections.newSetFromMap(new IdentityHashMap<>());
    public final WorldView view;
    public final Player player;
    public final Client client;
    public final CollisionService collision = new CollisionService();
    public final FanAnimations animations;
    public final FanMovementController movement;
    public final FanManager manager;
    public final MutableConfig config = new MutableConfig();

    public TestScene() {
        CollisionData map =
                proxy(CollisionData.class, (m, a) -> m.equals("getFlags") ? flags : null);
        view =
                proxy(
                        WorldView.class,
                        (m, a) -> {
                            switch (m) {
                                case "isInstance":
                                    return instance;
                                case "getInstanceTemplateChunks":
                                    return instanceChunks;
                                case "getId":
                                    return viewId;
                                case "getBaseX":
                                    return baseX;
                                case "getBaseY":
                                    return baseY;
                                case "getPlane":
                                    return plane;
                                case "getSizeX":
                                case "getSizeY":
                                    return 104;
                                case "getCollisionMaps":
                                    return new CollisionData[] {map, map, map, map};
                                case "getTileSettings":
                                    return settings;
                                case "getTileHeights":
                                    return heights;
                                default:
                                    return null;
                            }
                        });
        player =
                proxy(
                        Player.class,
                        (m, a) -> {
                            if (m.equals("getLocalLocation"))
                                return LocalPoint.fromWorld(view, playerPosition);
                            if (m.equals("getName")) return playerName;
                            if (m.equals("getInteracting")) return interacting;
                            if (m.equals("getAnimation")) return playerAnimation;
                            return null;
                        });
        Model model = proxy(Model.class, (m, a) -> null);
        ModelData[] mesh = new ModelData[1];
        mesh[0] =
                proxy(
                        ModelData.class,
                        (m, a) -> {
                            if (m.equals("light")) {
                                modelBuilds++;
                                return model;
                            }
                            if (m.equals("cloneTextures")) {
                                textureCloneCalls++;
                                if (faceTextures == null)
                                    throw new NullPointerException(
                                            "No texture array on untextured mesh");
                                faceTextures = faceTextures.clone();
                                return mesh[0];
                            }
                            if (m.equals("getFaceTextures")) return faceTextures;
                            if (m.equals("getVerticesY")) return new float[3];
                            if (m.equals("getFaceColors"))
                                return new short[] {6798, 8741, 25238, 4626, 4550};
                            return mesh[0];
                        });
        IndexDataBase index =
                proxy(
                        IndexDataBase.class,
                        (m, a) -> {
                            if (m.equals("getFileIds"))
                                return (int) a[0] == 3
                                        ? new int[] {0, 1, 2, 3, 4, 5, 6}
                                        : itemDefinitions.keySet().stream()
                                                .mapToInt(Integer::intValue)
                                                .toArray();
                            if (m.equals("loadData")) {
                                int archive = (int) a[0], id = (int) a[1];
                                if (archive == 3)
                                    return bodyCacheReady
                                            ? new byte[] {
                                                1, (byte) id, 2, 1, 3, (byte) (100 + id), 0
                                            }
                                            : null;
                                if (unsupportedEquipment) return new byte[] {(byte) 199, 0};
                                if (itemDefinitions.containsKey(id)) return itemDefinitions.get(id);
                                return new byte[] {13, 3, 23, 3, (byte) 100, 0, 0};
                            }
                            return null;
                        });
        Animation animation =
                proxy(
                        Animation.class,
                        (m, a) -> {
                            if (m.equals("getFrameLengths")) return new int[] {100, 100};
                            if (m.equals("getDuration")) return 2;
                            if (m.equals("getFrameStep")) return 2;
                            return null;
                        });
        Client[] handle = new Client[1];
        handle[0] =
                proxy(
                        Client.class,
                        (m, a) -> {
                            switch (m) {
                                case "getSkillExperience":
                                    return skillXp.getOrDefault((Skill) a[0], 0);
                                case "getRealSkillLevel":
                                    return a[0] == Skill.HITPOINTS ? 99 : 70;
                                case "getBoostedSkillLevel":
                                    return a[0] == Skill.HITPOINTS ? hp : 70;
                                case "getWidget":
                                    return bankOpen
                                            ? proxy(
                                                    net.runelite.api.widgets.Widget.class,
                                                    (method, args) ->
                                                            method.equals("isHidden")
                                                                    ? false
                                                                    : null)
                                            : null;
                                case "getGameState":
                                    return state;
                                case "getGameCycle":
                                    return cycle;
                                case "getWorldView":
                                case "getTopLevelWorldView":
                                    return view;
                                case "getLocalPlayer":
                                    return player;
                                case "getIndexConfig":
                                    return index;
                                case "mergeModels":
                                case "loadModelData":
                                    return mesh[0];
                                case "loadAnimation":
                                    loadedAnimations.add((int) a[0]);
                                    return animation;
                                case "createRuneLiteObject":
                                    return new RuneLiteObject(handle[0]);
                                case "registerRuneLiteObject":
                                    active.add((RuneLiteObjectController) a[0]);
                                    return null;
                                case "removeRuneLiteObject":
                                    active.remove(a[0]);
                                    return null;
                                case "isRuneLiteObjectRegistered":
                                    return active.contains(a[0]);
                                default:
                                    return null;
                            }
                        });
        client = handle[0];
        collision.bind(view);
        animations = new FanAnimations(client);
        movement = new FanMovementController(collision, animations);
        Random random = new Random(17);
        FanModelFactory models = new FanModelFactory(client);
        FanAppearanceFactory appearances = new FanAppearanceFactory(random, models);
        manager =
                new FanManager(
                        client,
                        config,
                        random,
                        appearances,
                        models,
                        collision,
                        new FormationPlanner(config, collision),
                        movement,
                        animations);
    }

    public void tick() {
        cycle += 30;
        for (RuneLiteObjectController object : new ArrayList<>(active)) object.tick(30);
        manager.onGameTick(player);
    }

    public void populate() {
        for (int i = 0; i < 90; i++) tick();
    }

    public static final class MutableConfig implements CelebrityModeConfig {
        public int size = 8;
        public FormationStyle formation = FormationStyle.ENTOURAGE;
        public boolean space = true;
        public FanGearTier gear = FanGearTier.DEFAULT_BOB;

        @Override
        public int crowdSize() {
            return size;
        }

        @Override
        public FormationStyle formationStyle() {
            return formation;
        }

        @Override
        public boolean respectPersonalSpace() {
            return space;
        }

        @Override
        public FanGearTier crowdGearTier() {
            return gear;
        }
    }

    @FunctionalInterface
    public interface Answer {
        Object answer(String method, Object[] args);
    }

    @SuppressWarnings("unchecked")
    public static <T> T proxy(Class<T> type, Answer answer) {
        return (T)
                Proxy.newProxyInstance(
                        type.getClassLoader(),
                        new Class<?>[] {type},
                        (p, m, a) -> {
                            if (m.getName().equals("equals")) return p == a[0];
                            if (m.getName().equals("hashCode")) return System.identityHashCode(p);
                            if (m.getName().equals("toString"))
                                return "Fixture " + type.getSimpleName();
                            Object result = answer.answer(m.getName(), a);
                            if (result != null) return result;
                            Class<?> r = m.getReturnType();
                            if (r == boolean.class) return false;
                            if (r == int.class) return 0;
                            if (r == long.class) return 0L;
                            if (r == float.class) return 0F;
                            if (r == double.class) return 0D;
                            if (r == byte.class) return (byte) 0;
                            if (r == short.class) return (short) 0;
                            if (r == char.class) return (char) 0;
                            return null;
                        });
    }
}
