package com.celebritymode.pm;

import static org.junit.Assert.*;
import com.celebritymode.CelebrityModeConfig;
import com.celebritymode.TestScene;
import net.runelite.api.*;
import net.runelite.client.chat.QueuedMessage;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class FanPmEngineTest {
    private static final class Settings implements CelebrityModeConfig {
        boolean enabled = true, sound = true;
        public boolean enableFanPms() { return enabled; }
        public boolean pmNotificationSound() { return sound; }
        public TrafficIntensity pmTrafficIntensity() { return TrafficIntensity.PEAK_WORLD_RECORD; }
    }

    @Test
    public void incomingMessagesUseNativeFieldsAndEscapeLiteralEmotes() {
        QueuedMessage queued = FanPmEngine.queuedMessage(new FanMessage("RuneBob42", "omg <3 :D", FanMessage.Archetype.HYPE, 1));
        assertEquals(ChatMessageType.PRIVATECHAT, queued.getType());
        assertEquals("RuneBob42", queued.getName());
        assertEquals("omg <lt>3 :D", queued.getValue());
        assertNull("Native formatting must remain in charge", queued.getRuneLiteFormattedMessage());
        assertNull(queued.getSender());
        assertEquals("Native timestamp uses insertion time", 0, queued.getTimestamp());
    }

    @Test
    public void dispatchFlushesOnClientThreadAndThrottlesSound() {
        ManualTimer timer = new ManualTimer();
        FanMessageScheduler scheduler = new FanMessageScheduler(new Random(4), () -> timer.now, () -> timer);
        TestScene scene = new TestScene();
        Settings settings = new Settings();
        List<QueuedMessage> queued = new ArrayList<>(), delivered = new ArrayList<>();
        List<Integer> sounds = new ArrayList<>();
        FanPmEngine engine = new FanPmEngine(scene.client, settings, scheduler, queued::add,
                () -> { delivered.addAll(queued); queued.clear(); }, sounds::add, () -> timer.now);
        engine.startUp();
        engine.onClientTick();
        assertTrue(delivered.isEmpty());
        timer.runNext();
        engine.onClientTick();
        assertEquals(1, delivered.size());
        assertTrue("No PMs linger in the shared queue", queued.isEmpty());
        assertEquals(Collections.singletonList(SoundEffectID.UI_BOOP), sounds);
        for (int i = 0; i < 4; i++) timer.runNext();
        engine.onClientTick();
        assertEquals(1, sounds.size());
        timer.now += TimeUnit.SECONDS.toNanos(2);
        engine.onClientTick();
        assertEquals(2, sounds.size());
        settings.sound = false;
        for (int i = 0; i < 4; i++) timer.runNext();
        timer.now += TimeUnit.SECONDS.toNanos(2);
        engine.onClientTick();
        assertEquals(2, sounds.size());
        engine.shutDown();
        assertTrue(timer.isShutdown());
        int count = delivered.size();
        engine.onClientTick();
        assertEquals(count, delivered.size());
    }

    @Test
    public void loginLoadingAndConfigDisableCannotLeakMessages() {
        List<ManualTimer> timers = new ArrayList<>();
        FanMessageScheduler scheduler = new FanMessageScheduler(new Random(6),
                () -> timers.get(timers.size() - 1).now, () -> {
                    ManualTimer timer = new ManualTimer(); timers.add(timer); return timer;
                });
        TestScene scene = new TestScene();
        scene.state = GameState.LOGIN_SCREEN;
        Settings settings = new Settings();
        List<QueuedMessage> messages = new ArrayList<>();
        FanPmEngine engine = new FanPmEngine(scene.client, settings, scheduler, messages::add,
                () -> {}, sound -> {}, () -> timers.isEmpty() ? 0 : timers.get(timers.size() - 1).now);
        engine.startUp();
        engine.onClientTick();
        assertTrue(timers.isEmpty());
        scene.state = GameState.LOGGED_IN;
        engine.onClientTick();
        timers.get(0).runNext();
        settings.enabled = false;
        engine.onClientTick();
        assertTrue(messages.isEmpty());
        assertEquals(0, scheduler.getPendingCount());
        assertTrue(timers.get(0).isShutdown());
        settings.enabled = true;
        engine.onClientTick();
        timers.get(1).runNext();
        engine.onGameStateChanged(GameState.LOADING);
        assertEquals(0, scheduler.getPendingCount());
        assertTrue(timers.get(1).isShutdown());
        scene.state = GameState.HOPPING;
        engine.onClientTick();
        assertTrue(messages.isEmpty());
        engine.shutDown();
    }
}
