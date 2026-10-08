package com.celebritymode.chat;

import static org.junit.Assert.*;

import com.celebritymode.fan.FanPersonality;

import org.junit.Test;

import java.util.*;

public class FanDialogueGeneratorTest {
    @Test
    public void composableDialogueHasBroadVarietyAndUsesCurrentName() {
        Random random = new Random(5);
        Set<String> lines = new HashSet<>();
        int personalized = 0;
        for (int i = 0; i < 2000; i++) {
            String quote =
                    FanDialogueGenerator.generate(
                            random,
                            FanPersonality.values()[i % 6],
                            "Current Hero",
                            "Tumeken's shadow");
            if (quote.toLowerCase(Locale.ENGLISH).contains("current hero")) personalized++;
            assertFalse(quote.toLowerCase(Locale.ENGLISH).contains("zezima"));
            assertTrue(quote.length() <= 100);
            lines.add(quote);
        }
        assertTrue("Composed dialogue variety: " + lines.size(), lines.size() > 400);
        assertTrue(
                "Names mixed with natural observations",
                personalized > 200 && personalized < 1100);
    }

    @Test
    public void broadCorpusSelectsRelevantObservationsWithoutUnresolvedTokens() {
        Random random = new Random(84);
        Set<String> lines = new HashSet<>();
        int mining = 0, targets = 0, warning = 0;
        for (int i = 0; i < 18000; i++) {
            net.runelite.api.Skill skill =
                    new net.runelite.api.Skill[] {
                                net.runelite.api.Skill.MINING, net.runelite.api.Skill.FISHING,
                                net.runelite.api.Skill.WOODCUTTING, net.runelite.api.Skill.AGILITY,
                                net.runelite.api.Skill.HERBLORE, net.runelite.api.Skill.CRAFTING
                            }
                            [i % 6];
            FanChatContext.Activity activity = FanChatContext.Activity.values()[i / 6 % 6];
            FanChatContext context =
                    new FanChatContext(
                            "Current Hero",
                            "Tumeken's shadow",
                            activity == FanChatContext.Activity.COMBAT ? "Goblin" : "",
                            activity,
                            activity == FanChatContext.Activity.SKILLING ? skill : null,
                            80,
                            100,
                            activity == FanChatContext.Activity.COMBAT,
                            i % 7 == 0);
            String line =
                    FanDialogueGenerator.generate(random, FanPersonality.values()[i % 6], context);
            assertFalse(line, line.contains("{") || line.contains("}"));
            assertTrue(line.length() <= 100);
            if (line.toLowerCase(Locale.ENGLISH).contains("goblin")) targets++;
            if (line.toLowerCase(Locale.ENGLISH).contains("hp") || line.contains("food check"))
                warning++;
            if (line.toLowerCase(Locale.ENGLISH).contains("mining")) mining++;
            if (activity != FanChatContext.Activity.COMBAT)
                assertFalse(line, line.toLowerCase(Locale.ENGLISH).contains("goblin"));
            lines.add(line);
        }
        assertTrue("Distinct composed lines: " + lines.size(), lines.size() > 2500);
        assertTrue("Occasional combat target observations: " + targets, targets > 300);
        assertTrue("Situational health warnings", warning > 100);
        assertTrue("Actual skill observations", mining > 60);
    }

    @Test
    public void overheadMessagesAreMostlyBriefWithoutRepeatedFollowupClauses() {
        Random random = new Random(221);
        int brief = 0, tiny = 0, longLines = 0, totalLength = 0, addressed = 0;
        Set<String> structures = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            com.celebritymode.generation.FanUtterance utterance = FanDialogueGenerator.generateUtterance(
                    random, FanPersonality.values()[i % 6], FanChatContext.idle("Zszima", ""));
            String text = utterance.text;
            structures.add(utterance.structure);
            totalLength += text.length();
            if (text.length() <= 40) brief++;
            if (text.split(" ").length <= 5) tiny++;
            if (text.length() > 60) longLines++;
            if (text.toLowerCase(Locale.ENGLISH).contains("zszima")) addressed++;
            assertFalse(text, text.contains("got plans for") || text.contains("looking forward to more"));
        }
        assertTrue("Most lines are short: " + brief, brief > 4000);
        assertTrue("Mostly a few words: " + tiny, tiny > 3000);
        assertTrue("Long comments stay occasional: " + longLines, longLines < 300);
        assertTrue("Average length: " + totalLength / 5000.0, totalLength < 5000 * 30);
        assertTrue("Substantive structure variety: " + structures.size(), structures.size() > 90);
        assertTrue("Don't address the player in every line", addressed < 2500);
    }

    @Test
    public void levelReactionsNameTheActualSkillAndLevel() {
        FanChatContext context =
                new FanChatContext(
                        "LocalHero",
                        "",
                        "",
                        FanChatContext.Activity.SKILLING,
                        net.runelite.api.Skill.MINING,
                        82,
                        0,
                        false,
                        false);
        Random random = new Random(4);
        Set<String> lines = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            String quote = FanDialogueGenerator.level(context, random);
            assertTrue(quote.contains("LocalHero"));
            assertTrue(quote.contains("mining") || quote.contains("82"));
            assertFalse(quote.contains("{"));
            lines.add(quote);
        }
        assertTrue(lines.size() >= 8);
    }

    @Test
    public void customQuotesAndEventsUseLiveContextWithoutChangingTheirSource() {
        FanChatManager chat = new FanChatManager(new Random(1));
        chat.configure(ChatFrequency.SPAM, CustomQuoteMode.CUSTOM_ONLY, "{player} nice {gear}", 0);
        chat.setContext("<col=ff0000>Old Name</col>", "bow");
        assertEquals(
                "Old Name nice bow", chat.choose(FanQuoteLibrary.parse("{player} nice {gear}")));
        chat.setContext("New Name", "staff");
        assertEquals(
                "New Name nice staff", chat.choose(FanQuoteLibrary.parse("{player} nice {gear}")));
        chat.setContext(null, null);
        assertEquals("you nice fit", chat.choose(FanQuoteLibrary.parse("{player} nice {gear}")));
    }
}
