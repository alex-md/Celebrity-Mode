package com.celebritymode.chat;

import static org.junit.Assert.*;

import com.celebritymode.fan.FanPersonality;

import org.junit.Test;

import java.util.*;

public class FanChatManagerTest {
    @Test
    public void nearbyCompanionsHaveDelayedCoherentExchangesWithoutRecentTopicRepeats() {
        com.celebritymode.TestScene scene = new com.celebritymode.TestScene();
        scene.populate();
        List<com.celebritymode.fan.FanEntity> fans = scene.manager.getFans().subList(0, 2);
        fans.get(1).position = fans.get(0).position;
        FanChatManager chat = new FanChatManager(new Random(74));
        chat.clear(fans);
        chat.configure(ChatFrequency.NORMAL, CustomQuoteMode.BUILT_IN_ONLY, "", 0);
        Deque<String> topics = new ArrayDeque<>();
        String topic = null;
        int lastTick = 0, lastSpeaker = -1, completed = 0, openings = 0;
        for (int tick = 1; tick <= 5000; tick++) {
            int[] expiry = {fans.get(0).overheadExpiryTick, fans.get(1).overheadExpiryTick};
            chat.tick(fans, tick);
            for (int i = 0; i < fans.size(); i++) {
                if (expiry[i] == fans.get(i).overheadExpiryTick) continue;
                String structure = chat.visibleStructure(fans.get(i));
                if (structure == null || !structure.startsWith("conversation:")) continue;
                assertSame(fans.get(1 - i), chat.conversationPartner(fans.get(i), tick));
                assertNull(chat.conversationPartner(fans.get(i), fans.get(i).overheadExpiryTick));
                String[] parts = structure.split(":");
                int turn = Integer.parseInt(parts[2]);
                if (turn == 0) {
                    assertFalse("Recent topics aren't recycled", topics.contains(parts[1]));
                    topics.addLast(parts[1]);
                    if (topics.size() > 48) topics.removeFirst();
                    topic = parts[1];
                    openings++;
                } else {
                    assertEquals("Replies stay on topic", topic, parts[1]);
                    assertNotEquals("Companions take turns", lastSpeaker, i);
                    assertTrue("Leave time before replying", tick - lastTick >= 5);
                    if (turn == 2) completed++;
                }
                lastSpeaker = i;
                lastTick = tick;
            }
        }
        assertTrue("Conversations recur during a long session: " + openings, openings > 30);
        assertTrue("Three-turn exchanges actually complete: " + completed, completed > 25);
        scene.manager.cleanup();
    }

    @Test
    public void nearbyThirdFansJoinAndLongerConversationsRetainTurnTaking() {
        com.celebritymode.TestScene scene = new com.celebritymode.TestScene();
        scene.populate();
        List<com.celebritymode.fan.FanEntity> fans = scene.manager.getFans().subList(0, 3);
        for (com.celebritymode.fan.FanEntity fan : fans) fan.position = fans.get(0).position;
        FanChatManager chat = new FanChatManager(new Random(96));
        chat.clear(fans);
        chat.configure(ChatFrequency.NORMAL, CustomQuoteMode.BUILT_IN_ONLY, "", 0);
        Set<Integer> speakers = new HashSet<>();
        String topic = null;
        int thirdJoined = 0, longTurns = 0, previousTurn = -1, previousTick = 0;
        for (int tick = 1; tick <= 8000; tick++) {
            int[] expiry = new int[fans.size()];
            for (int i = 0; i < fans.size(); i++) expiry[i] = fans.get(i).overheadExpiryTick;
            chat.tick(fans, tick);
            for (int i = 0; i < fans.size(); i++) {
                if (expiry[i] == fans.get(i).overheadExpiryTick) continue;
                String structure = chat.visibleStructure(fans.get(i));
                if (structure == null || !structure.startsWith("conversation:")) continue;
                String[] parts = structure.split(":");
                int turn = Integer.parseInt(parts[2]);
                if (turn == 0) {
                    speakers.clear();
                    topic = parts[1];
                } else {
                    assertEquals(topic, parts[1]);
                    assertEquals(previousTurn + 1, turn);
                    assertTrue(tick - previousTick >= 5);
                }
                speakers.add(i);
                if (speakers.size() == 3) thirdJoined++;
                if (turn >= 3) longTurns++;
                assertNotNull(chat.conversationPartner(fans.get(i), tick));
                previousTurn = turn;
                previousTick = tick;
            }
        }
        assertTrue("Third companions can chime in: " + thirdJoined, thirdJoined >= 5);
        assertTrue("Exchanges extend past three lines: " + longTurns, longTurns >= 2);
        scene.manager.cleanup();
    }

    @Test
    public void activityChangesAndCustomOnlyCancelPendingConversations() {
        com.celebritymode.TestScene scene = new com.celebritymode.TestScene();
        scene.populate();
        List<com.celebritymode.fan.FanEntity> fans = scene.manager.getFans().subList(0, 2);
        fans.get(1).position = fans.get(0).position;
        FanChatManager chat = new FanChatManager(new Random(74));
        chat.clear(fans);
        chat.configure(ChatFrequency.SPAM, CustomQuoteMode.BUILT_IN_ONLY, "", 0);
        int tick = 1;
        boolean opened = false;
        String oldTopic = null;
        for (; tick < 300 && !opened; tick++) {
            chat.tick(fans, tick);
            for (com.celebritymode.fan.FanEntity fan : fans) {
                String structure = chat.visibleStructure(fan);
                if (structure != null && structure.startsWith("conversation:")) {
                    opened = true;
                    oldTopic = structure.split(":")[1];
                    assertNotNull(chat.conversationPartner(fan, tick));
                }
            }
        }
        assertTrue(opened);
        chat.setContext(new FanChatContext("hero", "", "Goblin", FanChatContext.Activity.COMBAT,
                null, 0, 0, false, false));
        for (int end = tick + 60; tick < end; tick++) {
            int[] expiry = {fans.get(0).overheadExpiryTick, fans.get(1).overheadExpiryTick};
            chat.tick(fans, tick);
            for (int i = 0; i < 2; i++)
                if (expiry[i] != fans.get(i).overheadExpiryTick) {
                    String structure = chat.visibleStructure(fans.get(i));
                    assertFalse("Old scene cannot reply in a new activity", structure != null
                            && structure.startsWith("conversation:" + oldTopic + ":") && !structure.endsWith(":0"));
                }
        }
        chat.configure(ChatFrequency.SPAM, CustomQuoteMode.CUSTOM_ONLY, "my own line", tick);
        for (int end = tick + 200; tick < end; tick++) {
            chat.tick(fans, tick);
            for (com.celebritymode.fan.FanEntity fan : fans)
                if (fan.overheadText != null && chat.visibleStructure(fan) == null)
                    assertEquals("my own line", fan.overheadText);
        }
        chat.clear(fans);
        chat.configure(ChatFrequency.OFF, CustomQuoteMode.BUILT_IN_ONLY, "", tick);
        for (int end = tick + 200; tick < end; tick++) chat.tick(fans, tick);
        for (com.celebritymode.fan.FanEntity fan : fans) assertNull(fan.overheadText);
        scene.manager.cleanup();
    }

    @Test
    public void independentSpeakersAvoidSynchronizedWavesFromStartupOnward() {
        for (ChatFrequency frequency : new ChatFrequency[] {ChatFrequency.NORMAL, ChatFrequency.SPAM}) {
            for (int seed : new int[] {17, 91, 213}) {
                com.celebritymode.TestScene scene = new com.celebritymode.TestScene();
                scene.config.size = 100;
                scene.config.arrival = com.celebritymode.fan.ArrivalPace.INSTANT;
                scene.tick();
                List<com.celebritymode.fan.FanEntity> fans = scene.manager.getFans();
                FanChatManager chat = new FanChatManager(new Random(seed));
                chat.clear(fans);
                chat.configure(frequency, CustomQuoteMode.CUSTOM_ONLY, "hi", 0);
                Map<Integer, Integer> previous = new HashMap<>();
                Set<Integer> firstTicks = new HashSet<>(), intervals = new HashSet<>();
                int busiest = 0, activeTicks = 0, total = 0;
                for (int tick = 1; tick <= 300; tick++) {
                    int[] expiry = new int[fans.size()];
                    for (int i = 0; i < fans.size(); i++) expiry[i] = fans.get(i).overheadExpiryTick;
                    chat.tick(fans, tick);
                    int sent = 0;
                    for (int i = 0; i < fans.size(); i++) {
                        com.celebritymode.fan.FanEntity fan = fans.get(i);
                        if (expiry[i] == fan.overheadExpiryTick) continue;
                        sent++;
                        Integer prior = previous.put(fan.id, tick);
                        if (prior == null) firstTicks.add(tick);
                        else if (fan.id == 0) intervals.add(tick - prior);
                        if (prior != null) assertTrue("Displayed lines aren't overwritten", tick - prior >= 5);
                    }
                    busiest = Math.max(busiest, sent);
                    total += sent;
                    if (tick > 20 && sent > 0) activeTicks++;
                }
                assertEquals("Every fan gets opportunities", 100, previous.size());
                assertTrue("Spread first messages across the crowd: " + firstTicks, firstTicks.size() >= 10);
                assertTrue("No mass posting ticks: " + busiest, busiest < 30);
                assertTrue("Chatter flows between peaks: " + activeTicks, activeTicks > 250);
                assertTrue("Busy settings remain lively: " + total, total > 1500);
                assertTrue("A sender's intervals vary: " + intervals, intervals.size() >= 5);
                scene.manager.cleanup();
            }
        }
    }

    @Test
    public void lowFrequencyRemainsQuieterAndNewArrivalsDoNotTriggerABatch() {
        int[] counts = new int[3];
        ChatFrequency[] frequencies = {ChatFrequency.LOW, ChatFrequency.NORMAL, ChatFrequency.SPAM};
        for (int mode = 0; mode < frequencies.length; mode++) {
            com.celebritymode.TestScene scene = new com.celebritymode.TestScene();
            scene.populate();
            FanChatManager chat = new FanChatManager(new Random(13));
            chat.clear(scene.manager.getFans());
            chat.configure(frequencies[mode], CustomQuoteMode.CUSTOM_ONLY, "hi", 0);
            for (int tick = 1; tick <= 1000; tick++) {
                if (tick == 500) {
                    scene.config.arrival = com.celebritymode.fan.ArrivalPace.INSTANT;
                    scene.manager.resizeCrowd(30);
                    scene.tick();
                }
                List<com.celebritymode.fan.FanEntity> fans = scene.manager.getFans();
                int[] before = new int[fans.size()];
                for (int i = 0; i < fans.size(); i++) before[i] = fans.get(i).overheadExpiryTick;
                chat.tick(fans, tick);
                int sent = 0;
                for (int i = 0; i < fans.size(); i++)
                    if (before[i] != fans.get(i).overheadExpiryTick) sent++;
                if (tick == 500) assertTrue("Arrivals wait for their own deadlines", sent < 8);
                counts[mode] += sent;
            }
            scene.manager.cleanup();
        }
        assertTrue(Arrays.toString(counts), counts[0] < counts[1] / 3);
        assertTrue(Arrays.toString(counts), counts[1] < counts[2]);
    }

    @Test
    public void quoteParsing() {
        assertEquals(
                Arrays.asList("hello, there", "nice cape"),
                FanQuoteLibrary.parse(" hello, there \n\n nice cape "));
        assertEquals(Arrays.asList("hi", "gz"), FanQuoteLibrary.parse(" hi, ,gz, "));
        assertEquals(Arrays.asList("hi", "gz"), FanQuoteLibrary.parse("hi\r\ngz"));
        assertTrue(FanQuoteLibrary.parse(" , , ").isEmpty());
        assertTrue(FanQuoteLibrary.parse(null).isEmpty());

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
    public void busyCrowdUsesHumanTypingCadence() {
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
        Map<Integer, Integer> lastMessage = new HashMap<>();
        for (int tick = 1; tick <= 100; tick++) {
            int[] old = new int[30];
            for (int i = 0; i < 30; i++) old[i] = scene.manager.getFans().get(i).overheadExpiryTick;
            chat.tick(scene.manager.getFans(), tick);
            int speaking = 0;
            for (int i = 0; i < 30; i++) {
                com.celebritymode.fan.FanEntity fan = scene.manager.getFans().get(i);
                if (fan.overheadText != null) speaking++;
                if (old[i] != fan.overheadExpiryTick) {
                    messages++;
                    if (lastMessage.containsKey(fan.id))
                        assertTrue("Each sender has time to type", tick - lastMessage.get(fan.id) >= 6);
                    assertTrue(fan.nextChatTick - tick >= fan.overheadText.length() / 4 + 2);
                    lastMessage.put(fan.id, tick);
                }
            }
            if (speaking >= 5) busyTicks++;
        }
        assertTrue("The crowd still feels busy", busyTicks >= 85);
        assertTrue("Varied chatter without rapid-fire individual senders: " + messages, messages > 80 && messages < 400);
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
        assertTrue(messages > 10 && messages < 80);
        assertTrue(targetLines > 2);
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
    public void aLargeCrowdKeepsMessagesBriefAndAvoidsRepeatingSentenceShapesOnScreen() {
        com.celebritymode.TestScene scene = new com.celebritymode.TestScene();
        scene.config.size = 100;
        scene.config.arrival = com.celebritymode.fan.ArrivalPace.INSTANT;
        scene.tick();
        FanChatManager chat = new FanChatManager(new Random(213));
        chat.clear(scene.manager.getFans());
        chat.configure(ChatFrequency.SPAM, CustomQuoteMode.BUILT_IN_ONLY, "", 0);
        chat.setContext(FanChatContext.idle("Zszima", ""));
        int messages = 0, brief = 0, totalLength = 0, repeatedStructures = 0;
        Deque<String> recent = new ArrayDeque<>();
        Set<String> structures = new HashSet<>();
        for (int tick = 1; tick <= 200; tick++) {
            Map<Integer, Integer> expiry = new HashMap<>();
            for (com.celebritymode.fan.FanEntity fan : scene.manager.getFans())
                expiry.put(fan.id, fan.overheadExpiryTick);
            chat.tick(scene.manager.getFans(), tick);
            Map<String, Integer> visibleLong = new HashMap<>();
            for (com.celebritymode.fan.FanEntity fan : scene.manager.getFans()) {
                String structure = chat.visibleStructure(fan);
                if (fan.overheadText != null && fan.overheadText.length() > 40)
                    visibleLong.merge(structure, 1, Integer::sum);
                if (expiry.get(fan.id) == fan.overheadExpiryTick) continue;
                messages++;
                totalLength += fan.overheadText.length();
                if (fan.overheadText.length() <= 40) brief++;
                structures.add(structure);
                if (recent.contains(structure)) repeatedStructures++;
                recent.addLast(structure);
                if (recent.size() > 16) recent.removeFirst();
            }
            for (int count : visibleLong.values())
                assertTrue("Avoid the screenshot's simultaneous repeated long templates", count <= 1);
        }
        assertTrue(messages > 1000);
        assertTrue("Shortness survives repetition filtering: " + brief + "/" + messages, brief > messages * 0.8);
        assertTrue("Average displayed length: " + totalLength / (double) messages, totalLength < messages * 30);
        assertTrue("Structures vary across different speakers", structures.size() > 90);
        assertTrue("Few recent template repeats: " + repeatedStructures + "/" + messages,
                repeatedStructures < messages / 20);
        chat.clear(scene.manager.getFans());
        for (com.celebritymode.fan.FanEntity fan : scene.manager.getFans()) assertNull(chat.visibleStructure(fan));
        scene.manager.cleanup();
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
        assertEquals("One immediate reaction, others need time to respond", 1, reactions);
        for (int tick = 11; tick <= 15; tick++) chat.tick(scene.manager.getFans(), tick);
        reactions = 0;
        for (com.celebritymode.fan.FanEntity fan : scene.manager.getFans())
            if ("LocalHero 82 Mining".equals(fan.overheadText)) reactions++;
        assertEquals("Delayed reaction keeps the observed level", 2, reactions);
    }

    @Test
    public void pendingReactionsAreDiscardedOnClearOrReconfigure() {
        com.celebritymode.TestScene scene = new com.celebritymode.TestScene();
        scene.populate();
        FanChatManager chat = new FanChatManager(new Random(13));
        chat.clear(scene.manager.getFans());
        chat.configure(ChatFrequency.NORMAL, CustomQuoteMode.CUSTOM_ONLY, "event", 0);
        chat.react(scene.manager.getFans(), 10, new String[] {"event"});
        chat.clear(scene.manager.getFans());
        chat.configure(ChatFrequency.OFF, CustomQuoteMode.CUSTOM_ONLY, "event", 10);
        for (int tick = 11; tick <= 100; tick++) chat.tick(scene.manager.getFans(), tick);
        for (com.celebritymode.fan.FanEntity fan : scene.manager.getFans()) assertNull(fan.overheadText);
        scene.manager.cleanup();
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
