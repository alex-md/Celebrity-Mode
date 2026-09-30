package com.celebritymode.fan;

import com.celebritymode.appearance.FanAppearance;

import net.runelite.api.*;
import net.runelite.api.coords.*;

/** Mutable only on the client thread; painting only reads the getters. */
public final class FanEntity {
    public final int id;
    public final FanPersonality personality;
    public final String displayName;
    public final int trailOffset, lateralBias, tileJitterX, tileJitterY;
    public int formationSlot, overheadExpiryTick, movementStartCycle, stalledTicks;
    public int orientation, targetOrientation;
    public int arrivalTick = Integer.MAX_VALUE, nextWanderTick, nextChatTick;
    public boolean arrived;
    public WorldPoint idleTarget;
    public FanState state = FanState.DESPAWNED;
    public FanAppearance appearance;
    public RuneLiteObject object;
    public Model model;
    public AnimationController animation;
    public WorldPoint position, target;
    public LocalPoint renderLocation;
    public LocalPoint[] movementPath;
    public String overheadText;

    public FanEntity(int id, FanPersonality personality, String name, int delay, int bias) {
        this.id = id;
        this.personality = personality;
        displayName = name;
        trailOffset = delay;
        lateralBias = bias;
        tileJitterX = Math.floorMod(id * 37 + 11, 55) - 27;
        tileJitterY = Math.floorMod(id * 19 + 31, 55) - 27;
        formationSlot = id;
    }

    public void deactivate() {
        if (object != null) object.setActive(false);
        object = null;
        animation = null;
        renderLocation = null;
        movementPath = null;
        position = null;
        target = null;
        state = FanState.DESPAWNED;
        overheadText = null;
        stalledTicks = 0;
        idleTarget = null;
        nextChatTick = 0;
    }

    public LocalPoint getRenderLocation() {
        return renderLocation;
    }

    public boolean isVisible() {
        return object != null && object.isActive() && renderLocation != null;
    }
}
