package com.celebritymode.pm;

import static org.junit.Assert.*;
import java.util.*;
import org.junit.Test;

public class FanDialogueGeneratorTest {
    @Test
    public void corpusHasVariedLegalNamesResolvedTokensAndWeightedTones() {
        FanDialogueGenerator generator = new FanDialogueGenerator();
        Random random = new Random(709);
        Set<String> names = new HashSet<>(), lines = new HashSet<>();
        EnumMap<FanMessage.Archetype, Integer> counts = new EnumMap<>(FanMessage.Archetype.class);
        int nostalgic = 0, brief = 0, totalLength = 0;
        for (int i = 0; i < 10000; i++) {
            FanMessage message = generator.generate(random, "Local Hero", i);
            assertTrue(message.getUsername(), message.getUsername().matches("[A-Za-z0-9 _]{1,12}"));
            assertFalse(message.getUsername().replace('_', ' ').equalsIgnoreCase("Local Hero"));
            assertFalse(message.getText().contains("{"));
            if (message.getText().contains("xD") || message.getText().contains("@@@@@")) nostalgic++;
            assertFalse(message.getText().contains("}"));
            assertTrue(message.getText().length() <= 160);
            if (message.getText().length() <= 40) brief++;
            totalLength += message.getText().length();
            assertEquals(i, message.getCreatedNanos());
            counts.merge(message.getArchetype(), 1, Integer::sum);
            names.add(message.getUsername());
            lines.add(message.getText());
        }
        assertTrue("Inbox spam is mostly brief: " + brief, brief > 8000);
        assertTrue("Average inbox length: " + totalLength / 10000.0, totalLength < 10000 * 32);
        assertTrue("Older chat flourishes remain occasional", nostalgic > 0 && nostalgic < 500);
        assertTrue("Distinct handles: " + names.size(), names.size() > 6500);
        assertTrue("Distinct messages: " + lines.size(), lines.size() > 5000);
        assertTrue(counts.get(FanMessage.Archetype.HYPE) > 4200 && counts.get(FanMessage.Archetype.HYPE) < 4800);
        assertTrue(counts.get(FanMessage.Archetype.FRIEND) > 2200 && counts.get(FanMessage.Archetype.FRIEND) < 2800);
        assertTrue(counts.get(FanMessage.Archetype.REQUEST) > 1700 && counts.get(FanMessage.Archetype.REQUEST) < 2300);
        assertTrue(counts.get(FanMessage.Archetype.SKEPTIC) > 700 && counts.get(FanMessage.Archetype.SKEPTIC) < 1300);
    }

    @Test
    public void hostileNameMarkupDoesNotEnterMessages() {
        FanDialogueGenerator generator = new FanDialogueGenerator();
        Random random = new Random(1);
        for (int i = 0; i < 100; i++) {
            String text = generator.generate(random, "<img=1>Hero<col=ff0000>{boss}", i).getText();
            assertFalse(text.contains("<img"));
            assertFalse(text.contains("<col"));
            assertFalse(text.contains("{"));
        }
    }
}
