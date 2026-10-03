package com.celebritymode.pm;

import static org.junit.Assert.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class FanMessageSchedulerTest {
    @Test
    public void poissonDelaysMatchEveryRateAndIncludeClumpsAndLulls() {
        for (TrafficIntensity intensity : TrafficIntensity.values()) {
            Random random = new Random(83);
            double total = 0;
            long min = Long.MAX_VALUE, max = 0;
            for (int i = 0; i < 100000; i++) {
                long delay = FanMessageScheduler.nextDelayNanos(random, intensity);
                assertTrue(delay > 0);
                total += delay;
                min = Math.min(min, delay);
                max = Math.max(max, delay);
            }
            double mean = total / 100000;
            double expected = (double) TimeUnit.MINUTES.toNanos(1) / intensity.getMessagesPerMinute();
            assertEquals(expected, mean, expected * 0.02);
            assertTrue(min < expected / 20);
            assertTrue(max > expected * 6);
        }
    }

    @Test
    public void producerBufferDispatchAndStaleMessageWorkAreBounded() {
        ManualTimer timer = new ManualTimer();
        FanMessageScheduler scheduler = new FanMessageScheduler(new Random(3), () -> timer.now, () -> timer);
        scheduler.configure(true, TrafficIntensity.PEAK_WORLD_RECORD, "Hero");
        for (int i = 0; i < 300; i++) timer.runNext();
        assertEquals(32, scheduler.getPendingCount());
        assertEquals(268, scheduler.getDroppedCount());
        // The client missed all these messages, so don't replay the old inbox.
        assertTrue(scheduler.drain().isEmpty());
        assertEquals(0, scheduler.getPendingCount());
        for (int i = 0; i < 20; i++) timer.runNext();
        int sent = 0;
        for (int i = 0; i < 20; i++) {
            List<FanMessage> batch = scheduler.drain();
            assertTrue(batch.size() <= 2);
            sent += batch.size();
        }
        assertEquals(12, sent);
        timer.now += TimeUnit.SECONDS.toNanos(1);
        assertFalse(scheduler.drain().isEmpty());
        scheduler.shutdown();
        assertEquals(0, scheduler.getPendingCount());
        assertTrue(timer.isShutdown());
        assertTrue(scheduler.drain().isEmpty());
    }

    @Test
    public void cancelledOldCallbacksCannotProduceAfterDisableOrRestart() {
        List<ManualTimer> timers = new ArrayList<>();
        FanMessageScheduler scheduler = new FanMessageScheduler(new Random(2),
                () -> timers.get(timers.size() - 1).now, () -> {
                    ManualTimer timer = new ManualTimer();
                    timers.add(timer);
                    return timer;
                });
        scheduler.configure(true, TrafficIntensity.RELAXED, "Hero");
        Runnable stale = timers.get(0).nextCallback();
        timers.get(0).runNext();
        scheduler.configure(false, TrafficIntensity.RELAXED, "Hero");
        stale.run(); // Simulate a callback that was already entering its synchronized block.
        assertEquals(0, scheduler.getPendingCount());
        scheduler.configure(true, TrafficIntensity.STREAMER, "Hero");
        stale.run();
        assertEquals(0, scheduler.getPendingCount());
        ManualTimer timer = timers.get(1);
        for (int i = 0; i < 40; i++) scheduler.configure(true, TrafficIntensity.STREAMER, "New Name");
        assertEquals("Name refresh doesn't restart worker", 2, timers.size());
        assertEquals("Only one timer is outstanding", 1, timer.tasks.size());
        timer.runNext();
        assertEquals(1, scheduler.getPendingCount());
        scheduler.shutdown();
    }
}
