package com.celebritymode;

import com.celebritymode.fan.FanManager;
import com.celebritymode.overlay.CelebrityModeOverlay;
import com.google.inject.Provides;

import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.*;
import net.runelite.client.ui.overlay.OverlayManager;

import java.util.*;

import javax.inject.*;

@PluginDescriptor(
        name = "Celebrity Mode",
        description = "A purely cosmetic entourage of simulated fans",
        tags = {"fun", "cosmetic", "followers", "fans", "celebrity"})
public class CelebrityModePlugin extends Plugin {
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private OverlayManager overlayManager;
    @Inject private FanManager fanManager;
    @Inject private CelebrityModeOverlay overlay;
    private final EnumMap<Skill, Integer> levels = new EnumMap<>(Skill.class);
    private volatile boolean enabled;

    @Provides
    CelebrityModeConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(CelebrityModeConfig.class);
    }

    @Provides
    @Singleton
    Random provideRandom() {
        return new Random();
    }

    @Override
    protected void startUp() {
        enabled = true;
        overlayManager.add(overlay);
        clientThread.invoke(
                () -> {
                    if (enabled) {
                        fanManager.cleanup();
                        levels.clear();
                    }
                });
    }

    @Override
    protected void shutDown() {
        enabled = false;
        overlayManager.remove(overlay);
        clientThread.invoke(
                () -> {
                    if (!enabled) {
                        fanManager.cleanup();
                        levels.clear();
                    }
                });
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (enabled) fanManager.onGameTick(client.getLocalPlayer());
    }

    @Subscribe
    public void onClientTick(ClientTick event) {
        if (enabled) fanManager.onClientTick();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        fanManager.onGameStateChanged(event.getGameState());
        if (event.getGameState() != GameState.LOGGED_IN
                && event.getGameState() != GameState.LOADING) levels.clear();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (enabled && CelebrityModeConfig.GROUP.equals(event.getGroup()))
            clientThread.invoke(
                    () -> {
                        if (enabled) fanManager.onConfigChanged(event.getKey());
                    });
    }

    @Subscribe
    public void onStatChanged(StatChanged event) {
        Integer old = levels.put(event.getSkill(), event.getLevel());
        if (enabled && client.getGameState() == GameState.LOGGED_IN) {
            fanManager.onExperience(event.getSkill(), event.getXp());
            if (old != null && event.getLevel() > old)
                fanManager.reactLevel(event.getSkill(), event.getLevel());
        }
    }

    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event) {
        if (enabled && client.getGameState() == GameState.LOGGED_IN)
            fanManager.onMenuOption(event.getMenuOption());
    }

    @Subscribe
    public void onActorDeath(ActorDeath event) {
        if (enabled && event.getActor() == client.getLocalPlayer()) fanManager.react(true);
    }
}
