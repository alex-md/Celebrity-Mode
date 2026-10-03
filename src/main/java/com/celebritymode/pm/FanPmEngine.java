package com.celebritymode.pm;

import com.celebritymode.CelebrityModeConfig;
import net.runelite.api.*;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.chat.QueuedMessage;
import net.runelite.client.util.Text;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import javax.inject.*;

/** Client-thread bridge. Leaves chat rendering, filtering, colours, and timestamps to RuneLite. */
@Singleton
public final class FanPmEngine {
    private static final long SOUND_INTERVAL = TimeUnit.SECONDS.toNanos(2);
    private final Client client;
    private final CelebrityModeConfig config;
    private final FanMessageScheduler scheduler;
    private final Consumer<QueuedMessage> queue;
    private final Runnable flush;
    private final IntConsumer playSound;
    private final LongSupplier clock;
    private long lastSound;
    private boolean sounded;
    private boolean enabled;

    @Inject
    public FanPmEngine(Client client, CelebrityModeConfig config, FanMessageScheduler scheduler, ChatMessageManager chat) {
        this(client, config, scheduler, chat::queue, chat::process, client::playSoundEffect, System::nanoTime);
    }

    FanPmEngine(Client client, CelebrityModeConfig config, FanMessageScheduler scheduler,
            Consumer<QueuedMessage> queue, Runnable flush, IntConsumer playSound, LongSupplier clock) {
        this.client = client;
        this.config = config;
        this.scheduler = scheduler;
        this.queue = queue;
        this.flush = flush;
        this.playSound = playSound;
        this.clock = clock;
    }

    public synchronized void startUp() { enabled = true; }

    public synchronized void onClientTick() {
        Player player = client.getLocalPlayer();
        boolean active = enabled && config.enableFanPms()
                && client.getGameState() == GameState.LOGGED_IN && player != null;
        scheduler.configure(active, config.pmTrafficIntensity(), player == null ? "" : player.getName());
        if (!active) {
            sounded = false;
            return;
        }
        List<FanMessage> batch = scheduler.drain();
        if (batch.isEmpty()) return;
        for (FanMessage message : batch) {
            queue.accept(queuedMessage(message));
        }
        // Process on the client thread in the same dispatch. No simulated entries linger in
        // RuneLite's shared queue after shutdown; never clear another plugin's queued messages.
        flush.run();
        long now = clock.getAsLong();
        if (config.pmNotificationSound() && (!sounded || now - lastSound >= SOUND_INTERVAL)) {
            // Uses native sound-effect volume and mute settings, rather than a separate audio player.
            playSound.accept(SoundEffectID.UI_BOOP);
            lastSound = now;
            sounded = true;
        }
    }

    static QueuedMessage queuedMessage(FanMessage message) {
        return QueuedMessage.builder()
                .type(ChatMessageType.PRIVATECHAT)
                .name(message.getUsername())
                .value(Text.escapeJagex(message.getText()))
                .build();
    }

    public synchronized void onGameStateChanged(GameState state) {
        if (state != GameState.LOGGED_IN) {
            scheduler.shutdown();
            sounded = false;
        }
    }

    public synchronized void shutDown() {
        enabled = false;
        scheduler.shutdown();
        sounded = false;
    }
}
