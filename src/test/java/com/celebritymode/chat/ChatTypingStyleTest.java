package com.celebritymode.chat;

import com.celebritymode.fan.FanPersonality;
import com.celebritymode.generation.FanUtterance;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ChatTypingStyleTest {
    @Test
    public void occasionalSlipsPreserveNamesContextAndRepetitionIdentity() {
        FanUtterance source = new FanUtterance(
                "Current Hero brought supplies for Goblin with dragon boots, Mining 82", "supplies");
        Random random = new Random(41);
        Set<String> altered = new HashSet<>();
        int changes = 0;
        for (int i = 0; i < 2000; i++) {
            FanUtterance result = ChatTypingStyle.apply(source, random, FanPersonality.MEMER,
                    "Current Hero", "Goblin", "dragon boots", "Mining");
            assertTrue(result.text.contains("Current Hero"));
            assertTrue(result.text.contains("Goblin"));
            assertTrue(result.text.contains("dragon boots"));
            assertTrue(result.text.contains("Mining 82"));
            assertEquals(source.structure, result.structure);
            assertTrue(result.text.length() <= 100);
            if (!source.text.equals(result.text)) {
                changes++;
                altered.add(result.text);
            }
        }
        assertTrue("Slips are occasional: " + changes, changes > 150 && changes < 400);
        assertTrue("Different slips rather than one canned typo", altered.size() > 15);
    }

    @Test
    public void punctuationSpamIsBriefVariedAndMoreCommonForLoudPersonalities() {
        int loud = 0, quiet = 0;
        Set<String> structures = new HashSet<>();
        boolean at = false;
        Random random = new Random(11);
        for (int i = 0; i < 5000; i++) {
            FanUtterance line = ChatTypingStyle.keyboard(random, FanPersonality.EXCITED);
            if (line != null) {
                loud++;
                structures.add(line.structure);
                assertTrue(line.text.length() >= 3 && line.text.length() <= 12);
                at |= line.text.matches("@{4,12}");
            }
            if (ChatTypingStyle.keyboard(random, FanPersonality.QUIET) != null) quiet++;
        }
        assertTrue(at);
        assertEquals(5, structures.size());
        assertTrue("Keyboard spam is occasional: " + loud, loud > 150 && loud < 400);
        assertEquals("Quiet fans don't scream", 0, quiet);
    }

    @Test
    public void shortProtectedAndMaximumLengthLinesRemainSafe() {
        Random random = new Random(12);
        for (int i = 0; i < 200; i++) {
            assertEquals("hi", ChatTypingStyle.apply(new FanUtterance("hi", "short"), random,
                    FanPersonality.MEMER).text);
            assertEquals("Mining", ChatTypingStyle.apply(new FanUtterance("Mining", "skill"), random,
                    FanPersonality.MEMER, "Mining").text);
            FanUtterance longLine = ChatTypingStyle.apply(new FanUtterance("a".repeat(100), "long"),
                    random, FanPersonality.MEMER);
            assertTrue(longLine.text.length() <= 100);
        }
    }
}
