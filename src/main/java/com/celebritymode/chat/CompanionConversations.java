package com.celebritymode.chat;

import com.celebritymode.fan.FanPersonality;
import com.celebritymode.fan.FanEntity;
import com.celebritymode.generation.FanUtterance;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Authored three- to five-turn scenes with shared subjects and coherent replies. */
final class CompanionConversations {
    private static final List<String[]> SCENES = load();
    private static final String[][] SUBJECTS = {
            {"snack", "toast", "crisps", "noodles", "biscuits", "pizza", "popcorn", "sandwiches", "cheese"},
            {"drink", "tea", "coffee", "water", "hot chocolate", "juice"},
            {"town", "lumbridge", "varrock", "catherby", "falador", "draynor", "yanille", "ardy", "seers"},
            {"boss", "zulrah", "vorkath", "barrows", "nex", "the gauntlet", "sarachnis"},
            {"pet", "cat", "dog", "kitten", "puppy", "rabbit"},
            {"chore", "laundry", "the dishes", "tidying up", "homework", "the shopping"}
    };
    private final Deque<String> recent = new ArrayDeque<>();

    String[] choose(Random random, FanPersonality personality, FanChatContext context) {
        List<String[]> eligible = new ArrayList<>();
        for (String[] scene : SCENES) {
            if (!(scene[0].equals("social")
                    || scene[0].equals(context.activity.name().toLowerCase(Locale.ENGLISH))
                    || scene[0].equals(personality.name().toLowerCase(Locale.ENGLISH)))) continue;
            if (!recent.contains(scene[1])) eligible.add(scene);
        }
        if (eligible.isEmpty()) return null;
        String[] scene = eligible.get(random.nextInt(eligible.size()));
        recent.addLast(scene[1]);
        if (recent.size() > 48) recent.removeFirst();
        String[] composed = scene.clone();
        for (String[] subject : SUBJECTS) {
            String value = subject[1 + random.nextInt(subject.length - 1)];
            for (int turn = 2; turn < composed.length; turn++)
                composed[turn] = composed[turn].replace("{" + subject[0] + "}", value);
        }
        return composed;
    }

    static FanUtterance line(String[] scene, int turn, Random random, FanChatContext context,
            FanEntity speaker, FanEntity partner) {
        String[] options = scene[turn + 2].split("~");
        String text = options[random.nextInt(options.length)]
                .replace("{skill}", context.skill == null ? "skilling" : context.skill.getName().toLowerCase(Locale.ENGLISH))
                .replace("{friend}", partner.displayName);
        return ChatTypingStyle.apply(new FanUtterance(text, "conversation:" + scene[1] + ":" + turn),
                random, speaker.personality, context.player, context.target, context.gear, partner.displayName,
                context.skill == null ? "" : context.skill.getName());
    }

    void clear() { recent.clear(); }

    private static List<String[]> load() {
        List<String[]> scenes = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Objects.requireNonNull(CompanionConversations.class.getResourceAsStream("conversations.txt")),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] scene = line.split("\\|", -1);
                if (scene.length < 5 || scene.length > 7) throw new IOException("Invalid companion conversation");
                scenes.add(scene);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load companion conversations", ex);
        }
        return Collections.unmodifiableList(scenes);
    }
}
