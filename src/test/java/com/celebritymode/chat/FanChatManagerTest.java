package com.celebritymode.chat;

import static org.junit.Assert.*;

import com.celebritymode.fan.FanPersonality;

import org.junit.Test;

import java.util.*;

public class FanChatManagerTest {
    @Test
    public void quoteParsing() {
        assertEquals(
                Arrays.asList("hello, there", "nice cape"),
                FanQuoteLibrary.parse(" hello, there \n\n nice cape "));
        assertEquals(Arrays.asList("hi", "gz"), FanQuoteLibrary.parse(" hi, ,gz, "));
        assertEquals(Arrays.asList("hi", "gz"), FanQuoteLibrary.parse("hi\r\ngz"));
        assertTrue(FanQuoteLibrary.parse(" , , ").isEmpty());
        assertTrue(FanQuoteLibrary.parse(null).isEmpty());
        assertTrue(
                FanQuoteLibrary.pool(
                                FanPersonality.EXCITED,
                                CustomQuoteMode.CUSTOM_ONLY,
                                FanQuoteLibrary.parse(""))
                        .isEmpty());
    }

    @Test
    public void offAndEmptyCustomModeSuppressEventChat() {
        com.celebritymode.TestScene scene = new com.celebritymode.TestScene();
        scene.populate();
        FanChatManager chat = new FanChatManager(new Random(8));
        chat.configure(ChatFrequency.OFF, CustomQuoteMode.MIXED, "", 0);
        for (com.celebritymode.fan.FanEntity fan : scene.manager.getFans()) fan.overheadText = null;
        chat.react(scene.manager.getFans(), 1, new String[] {"gz"});
        for (com.celebritymode.fan.FanEntity fan : scene.manager.getFans())
            assertNull(fan.overheadText);
        chat.configure(ChatFrequency.SPAM, CustomQuoteMode.CUSTOM_ONLY, "", 0);
        for (int i = 0; i < 100; i++) chat.tick(scene.manager.getFans(), i);
        chat.react(scene.manager.getFans(), 100, new String[] {"gz"});
        for (com.celebritymode.fan.FanEntity fan : scene.manager.getFans())
            assertNull(fan.overheadText);
    }

    @Test
    public void spamKeepsMostOfTheCrowdTalking() {
        com.celebritymode.TestScene scene = new com.celebritymode.TestScene();
        scene.config.size = 30;
        scene.populate();
        FanChatManager chat = new FanChatManager(new Random(18));
        chat.configure(ChatFrequency.SPAM, CustomQuoteMode.BUILT_IN_ONLY, "", 0);
        for (com.celebritymode.fan.FanEntity fan : scene.manager.getFans()) {
            fan.nextChatTick = 0;
            fan.overheadText = null;
        }
        int busyTicks = 0, messages = 0;
        for (int tick = 1; tick <= 100; tick++) {
            int[] old = new int[30];
            for (int i = 0; i < 30; i++) old[i] = scene.manager.getFans().get(i).overheadExpiryTick;
            chat.tick(scene.manager.getFans(), tick);
            int speaking = 0;
            for (int i = 0; i < 30; i++) {
                com.celebritymode.fan.FanEntity fan = scene.manager.getFans().get(i);
                if (fan.overheadText != null) speaking++;
                if (old[i] != fan.overheadExpiryTick) messages++;
            }
            if (speaking >= 20) busyTicks++;
        }
        assertTrue("Near-constant visible chatter", busyTicks >= 95);
        assertTrue("Many messages each second", messages > 1200);
    }

    @Test
    public void fansRememberRecentLinesAndContextChangesReachTheCrowd() {
        com.celebritymode.TestScene scene = new com.celebritymode.TestScene();
        scene.populate();
        com.celebritymode.fan.FanEntity fan = scene.manager.getFans().get(0);
        List<com.celebritymode.fan.FanEntity> fans = Collections.singletonList(fan);
        FanChatManager chat = new FanChatManager(new Random(4));
        chat.clear(fans);
        chat.configure(ChatFrequency.SPAM, CustomQuoteMode.BUILT_IN_ONLY, "", 0);
        chat.setContext(
                new FanChatContext(
                        "LocalHero",
                        "",
                        "Goblin",
                        FanChatContext.Activity.COMBAT,
                        null,
                        0,
                        0,
                        false,
                        false));
        Deque<String> recent = new ArrayDeque<>();
        String previous = null;
        int messages = 0, targetLines = 0, repeat = 0;
        for (int tick = 1; tick < 400; tick++) {
            int expiry = fan.overheadExpiryTick;
            chat.tick(fans, tick);
            if (expiry == fan.overheadExpiryTick) continue;
            String line = fan.overheadText.toLowerCase(Locale.ENGLISH).replaceAll("[^a-z0-9 ]", "");
            assertNotEquals(previous, line);
            if (recent.contains(line)) repeat++;
            recent.addLast(line);
            if (recent.size() > 12) recent.removeFirst();
            if (line.contains("goblin")) targetLines++;
            previous = line;
            messages++;
        }
        assertTrue(messages > 200);
        assertTrue(targetLines > 30);
        assertTrue("Per-fan recent duplicates: " + repeat, repeat <= 2);
        chat.setContext(FanChatContext.idle("New Name", ""));
        for (int tick = 400; tick < 440; tick++) {
            int expiry = fan.overheadExpiryTick;
            chat.tick(fans, tick);
            if (expiry != fan.overheadExpiryTick) {
                assertFalse(fan.overheadText.toLowerCase(Locale.ENGLISH).contains("goblin"));
                assertFalse(fan.overheadText.contains("LocalHero"));
            }
        }
    }

    @Test
    public void customLevelEventsUseActualLevelAndRemainVisibleBeforeNextChatter() {
        com.celebritymode.TestScene scene = new com.celebritymode.TestScene();
        scene.populate();
        FanChatManager chat = new FanChatManager(new Random(9));
        chat.configure(
                ChatFrequency.SPAM, CustomQuoteMode.CUSTOM_ONLY, "{player} {level} {skill}", 0);
        chat.setContext(FanChatContext.idle("LocalHero", ""));
        chat.level(scene.manager.getFans(), 10, net.runelite.api.Skill.MINING, 82);
        int reactions = 0;
        for (com.celebritymode.fan.FanEntity fan : scene.manager.getFans()) {
            if ("LocalHero 82 Mining".equals(fan.overheadText)) {
                assertTrue(fan.nextChatTick >= 13);
                reactions++;
            }
        }
        assertEquals(2, reactions);
    }

    @Test
    public void seededSchedulingBounds() {
        FanChatManager manager = new FanChatManager(new Random(10));
        for (ChatFrequency f : ChatFrequency.values()) {
            manager.configure(f, CustomQuoteMode.MIXED, "", 100);
            if (f == ChatFrequency.OFF) {
                assertEquals(Integer.MAX_VALUE, manager.getNextTick());
                manager.tick(Collections.emptyList(), 10000);
                assertEquals(Integer.MAX_VALUE, manager.getNextTick());
            } else
                for (int tick = 100; tick < 1000; tick += 30) {
                    manager.configure(f, CustomQuoteMode.MIXED, "", tick);
                    int delay = manager.getNextTick() - tick;
                    assertTrue(delay >= f.minimumTicks && delay <= f.maximumTicks);
                }
        }
    }

    @Test
    public void avoidsImmediateDuplicateWheneverPossible() {
        FanChatManager manager = new FanChatManager(new Random(8));
        String previous = null;
        for (int i = 0; i < 100; i++) {
            String q = manager.choose(Arrays.asList("gz", "gz", "hi"));
            assertNotEquals(previous, q);
            previous = q;
        }
        assertNull(manager.choose(Collections.emptyList()));
        assertEquals("only", manager.choose(Collections.singletonList("only")));
    }
}
