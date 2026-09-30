package com.celebritymode;

import com.celebritymode.appearance.*;

import net.runelite.api.AnimationController;
import net.runelite.api.Client;
import net.runelite.api.Model;
import net.runelite.api.gameval.AnimationID;
import net.runelite.client.RuneLite;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.externalplugins.ExternalPluginManager;

import org.slf4j.LoggerFactory;

import java.util.Random;

/** Developer launcher, as used by the official Plugin Hub template. */
public class CelebrityModePluginTest {
    @SuppressWarnings("unchecked")
    public static void main(String[] args) throws Exception {
        ExternalPluginManager.loadBuiltin(CelebrityModePlugin.class);
        RuneLite.main(args);
        if (Boolean.getBoolean("celebritymode.verifyModels")) verifyCacheModels();
    }

    /**
     * Opt-in developer check against the actual injected client/cache, even at the login screen.
     */
    private static void verifyCacheModels() {
        Client client = RuneLite.getInjector().getInstance(Client.class);
        ClientThread thread = RuneLite.getInjector().getInstance(ClientThread.class);
        FanModelFactory models = new FanModelFactory(client);
        FanAppearanceFactory appearances = new FanAppearanceFactory(new Random(17), models);
        int[] animationIds = {
            AnimationID.HUMAN_READY,
            AnimationID.HUMAN_WALK_F,
            AnimationID.HUMAN_RUNNING,
            AnimationID.EMOTE_WAVE,
            AnimationID.EMOTE_CHEER,
            AnimationID.SLAYER_ABYSSAL_WHIP_WALK,
            AnimationID.SLAYER_ABYSSAL_WHIP_RUN,
            AnimationID.HUMAN_STAFFREADY
        };
        int[] next = {0};
        int[] attempts = {0};
        FanAppearance[] pending = {null};
        thread.invokeLater(
                () -> {
                    if (client.getGameState() != net.runelite.api.GameState.LOGIN_SCREEN
                            && client.getGameState() != net.runelite.api.GameState.LOGGED_IN)
                        return false;
                    if (++attempts[0] > 1000) {
                        LoggerFactory.getLogger(CelebrityModePluginTest.class)
                                .error("Cache model verification timed out");
                        return true;
                    }
                    try {
                        appearances.advanceCatalog();
                        if (!appearances.getCatalog().complete()) return false;
                        FanGearTier tier = FanGearTier.values()[next[0]];
                        if (pending[0] == null)
                            pending[0] = appearances.createAppearance(next[0], tier);
                        FanAppearance appearance = pending[0];
                        if (tier != FanGearTier.DEFAULT_BOB && appearance.equipment.length == 0)
                            throw new IllegalStateException(
                                    "Empty equipment catalogue for " + tier);
                        Model model = models.createModel(appearance);
                        if (model == null || model.getVerticesCount() == 0) return false;
                        for (int id : animationIds) {
                            Model posed = new AnimationController(client, id).animate(model);
                            if (posed == null || posed.getVerticesCount() == 0) return false;
                        }
                        LoggerFactory.getLogger(CelebrityModePluginTest.class)
                                .info(
                                        "Cache model verification passed: {} ({} vertices, {}"
                                            + " animation sequences; {} wardrobe items; equipment"
                                            + " {})",
                                        tier,
                                        model.getVerticesCount(),
                                        animationIds.length,
                                        appearances.getCatalog().size(),
                                        java.util.Arrays.toString(appearance.equipment));
                        pending[0] = null;
                        return ++next[0] == FanGearTier.values().length;
                    } catch (RuntimeException ex) {
                        if (attempts[0] % 30 == 0)
                            LoggerFactory.getLogger(CelebrityModePluginTest.class)
                                    .debug("Waiting for cache model verification resources", ex);
                        return false;
                    }
                });
    }
}
