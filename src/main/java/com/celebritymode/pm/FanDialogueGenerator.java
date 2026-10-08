package com.celebritymode.pm;

import com.celebritymode.generation.ConversationComposer;
import com.celebritymode.generation.UsernameGenerator;
import com.celebritymode.generation.QuickChatComposer;
import com.celebritymode.generation.FanUtterance;

import java.util.*;

/** Small composable vocabularies; no client APIs or game-thread state. */
public final class FanDialogueGenerator {
    private final Deque<String> recentNames = new ArrayDeque<>();
    private final Deque<String> recentLines = new ArrayDeque<>();
    private final Deque<String> recentStructures = new ArrayDeque<>();

    public FanMessage generate(Random random, String playerName, long nowNanos) {
        String player = playerName == null ? "" : playerName.replaceAll("<[^>]*>", "")
                .replaceAll("[^A-Za-z0-9 _]", "").trim();
        int roll = random.nextInt(100);
        FanMessage.Archetype archetype = roll < 45 ? FanMessage.Archetype.HYPE
                : roll < 70 ? FanMessage.Archetype.FRIEND
                : roll < 90 ? FanMessage.Archetype.REQUEST : FanMessage.Archetype.SKEPTIC;
        String text = "";
        String structure = "";
        int lengthRoll = random.nextInt(100);
        for (int attempt = 0; attempt < 48; attempt++) {
            if (lengthRoll < 75) {
                FanUtterance message = QuickChatComposer.generate(random, archetype.name(), player,
                        "social", "", "", "", true);
                text = message.text;
                structure = message.structure;
            } else if (lengthRoll < 85) {
                FanUtterance message = QuickChatComposer.generate(random, archetype.name(), player,
                        "social", "", "", "", false);
                text = message.text;
                structure = message.structure;
            } else if (lengthRoll < 95) {
                text = ClassicFanDialogue.generate(random, player, archetype);
                structure = "classic/" + archetype;
            } else {
                text = ConversationComposer.style(random, player,
                        ConversationComposer.social(random, archetype.name()), 160);
                structure = "conversation/" + archetype;
            }
            String key = normalized(text, player);
            if (!recentLines.contains(key) && !recentStructures.contains(structure)) break;
            // Old templates stay available, but do not repeatedly spend work on the same bucket.
            if (lengthRoll >= 85 && attempt >= 8) lengthRoll = random.nextInt(85);
        }
        remember(recentLines, normalized(text, player), 96);
        remember(recentStructures, structure, 12);
        String username = username(random, player);
        return new FanMessage(username, text, archetype, nowNanos);
    }

    private static String normalized(String text, String player) {
        String key = text.toLowerCase(Locale.ENGLISH);
        if (!player.isEmpty()) key = key.replace(player.toLowerCase(Locale.ENGLISH), "");
        return key.replaceAll("[^a-z0-9 ]", "").replaceAll(" +", " ").trim();
    }

    private String username(Random random, String player) {
        List<String> excluded = new ArrayList<>(recentNames);
        excluded.add(player);
        String candidate = UsernameGenerator.generate(random, excluded);
        remember(recentNames, candidate, 128);
        return candidate;
    }

    private static void remember(Deque<String> history, String value, int limit) {
        history.addLast(value);
        if (history.size() > limit) history.removeFirst();
    }

    public void clear() {
        recentNames.clear();
        recentLines.clear();
        recentStructures.clear();
    }
}
