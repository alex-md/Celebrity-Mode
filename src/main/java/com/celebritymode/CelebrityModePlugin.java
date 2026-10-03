package com.celebritymode;

import com.celebritymode.fan.FanManager;
import com.celebritymode.pm.FanPmEngine;
import com.celebritymode.overlay.CelebrityModeOverlay;
import com.celebritymode.ui.CelebrityModeIcon;
import com.celebritymode.ui.CelebrityModePanel;
import com.google.inject.Provides;

import net.runelite.api.*;
import net.runelite.api.events.*;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.*;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;

import java.util.*;

import javax.inject.*;
import javax.swing.SwingUtilities;

@PluginDescriptor(
        name = "Celebrity Mode",
        description = "An in-game audience for OSRS videos with cosmetic fans, custom dialogue, and activity-aware reactions",
        tags = {"fun", "cosmetic", "followers", "fans", "celebrity", "content", "creator", "video"})
public class CelebrityModePlugin extends Plugin {
    @Inject private Client client;
    @Inject private ClientThread clientThread;
    @Inject private OverlayManager overlayManager;
    @Inject private FanManager fanManager;
    @Inject private FanPmEngine fanPmEngine;
    @Inject private CelebrityModeOverlay overlay;
    @Inject private ClientToolbar clientToolbar;
    @Inject private ConfigManager configManager;
    @Inject private CelebrityModeConfig config;
    // Created, refreshed, and removed only on the Swing event thread.
    private CelebrityModePanel panel;
    private NavigationButton navigation;
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
        fanPmEngine.startUp();
        overlayManager.add(overlay);
        SwingUtilities.invokeLater(() -> {
            if (!enabled || navigation != null) return;
            panel = new CelebrityModePanel(config,
                    (key, value) -> configManager.setConfiguration(CelebrityModeConfig.GROUP, key, value));
            navigation = NavigationButton.builder()
                    .tooltip("Celebrity Mode")
                    .icon(CelebrityModeIcon.create(32))
                    .priority(7)
                    .panel(panel)
                    .build();
            clientToolbar.addNavigation(navigation);
        });
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
        fanPmEngine.shutDown();
        overlayManager.remove(overlay);
        SwingUtilities.invokeLater(() -> {
            if (navigation != null) clientToolbar.removeNavigation(navigation);
            navigation = null;
            panel = null;
        });
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
        if (enabled) {
            fanManager.onClientTick();
            fanPmEngine.onClientTick();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        fanManager.onGameStateChanged(event.getGameState());
        fanPmEngine.onGameStateChanged(event.getGameState());
        if (event.getGameState() != GameState.LOGGED_IN
                && event.getGameState() != GameState.LOADING) levels.clear();
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (enabled && CelebrityModeConfig.GROUP.equals(event.getGroup())) {
            SwingUtilities.invokeLater(() -> {
                if (enabled && panel != null) panel.refresh(event.getKey());
            });
            clientThread.invoke(
                    () -> {
                        if (enabled) {
                            fanManager.onConfigChanged(event.getKey());
                            if (event.getKey().startsWith("pm") || "enableFanPms".equals(event.getKey()))
                                fanPmEngine.onClientTick();
                        }
                    });
        }
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
