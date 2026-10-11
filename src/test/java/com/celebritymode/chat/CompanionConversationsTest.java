package com.celebritymode.chat;

import com.celebritymode.fan.*;
import com.celebritymode.generation.FanUtterance;
import net.runelite.api.Skill;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class CompanionConversationsTest {
    @Test
    public void authoredScenesHaveUniqueTopicsValidTurnsAndResolvableShortLines() throws IOException {
        Set<String> topics = new HashSet<>();
        int longer = 0;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                CompanionConversations.class.getResourceAsStream("conversations.txt"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("\\|", -1);
                assertTrue(line, parts.length >= 5 && parts.length <= 7);
                assertTrue("Unique topic: " + parts[1], topics.add(parts[1]));
                if (parts.length > 5) longer++;
                for (int turn = 2; turn < parts.length; turn++)
                    for (String option : parts[turn].split("~", -1)) {
                        assertFalse(line, option.isEmpty());
                        assertTrue("Fits overhead text", option.length() <= 70);
                    }
            }
        }
        assertTrue("A substantially larger pool", topics.size() >= 140);
        assertTrue("More than a fixed three-turn format", longer >= 12);
    }

    @Test
    public void compositionKeepsSubjectsAcrossRepliesAndDoesNotRecycleRecentTopics() {
        CompanionConversations conversations = new CompanionConversations();
        Random random = new Random(54);
        Deque<String> recent = new ArrayDeque<>();
        Set<String> lines = new HashSet<>(), subjects = new HashSet<>();
        FanEntity first = new FanEntity(0, FanPersonality.MEMER, "FirstFriend", 1, 0);
        FanEntity second = new FanEntity(1, FanPersonality.QUIET, "SecondFriend", 1, 0);
        FanChatContext context = new FanChatContext("Current Hero", "", "", FanChatContext.Activity.SKILLING,
                Skill.WOODCUTTING, 82, 0, false, false);
        for (int i = 0; i < 2000; i++) {
            String[] scene = conversations.choose(random, first.personality, context);
            assertNotNull(scene);
            assertFalse(recent.contains(scene[1]));
            recent.addLast(scene[1]);
            if (recent.size() > 48) recent.removeFirst();
            if (scene[1].equals("dynamic-long-drink")) {
                String drink = null;
                for (String candidate : new String[] {"tea", "coffee", "water", "hot chocolate", "juice"})
                    if (scene[2].contains(candidate)) drink = candidate;
                assertNotNull("A subject was composed", drink);
                for (String alternative : scene[2].split("~"))
                    assertTrue("Same drink in every opening alternative", alternative.contains(drink));
                subjects.add(drink);
            }
            for (int turn = 0; turn < scene.length - 2; turn++) {
                FanUtterance line = CompanionConversations.line(scene, turn, random, context,
                        turn % 2 == 0 ? first : second, turn % 2 == 0 ? second : first);
                assertFalse(line.text, line.text.contains("{") || line.text.contains("}"));
                assertTrue(line.text.length() <= 100);
                assertEquals("conversation:" + scene[1] + ":" + turn, line.structure);
                lines.add(line.text);
            }
        }
        assertTrue("Substantive wording variety: " + lines.size(), lines.size() > 800);
        assertTrue("Details vary across sessions", subjects.size() >= 4);
    }
}
