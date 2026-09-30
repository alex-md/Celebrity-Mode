package com.celebritymode.chat;

import com.celebritymode.fan.FanPersonality;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Context-selected patterns composed with conversational fragments, rather than fixed quotes. */
public final class FanDialogueGenerator {
    private static final Map<String, List<String>> PATTERNS = load();
    private static final String[] GREETINGS = {
        "yo",
        "hey",
        "oh hey",
        "wait",
        "omg",
        "no way",
        "hold up",
        "ay",
        "look who it is",
        "well hello"
    };
    private static final String[] PRAISE = {
        "clean",
        "goated",
        "huge",
        "insane",
        "unreal",
        "actually sick",
        "looking good",
        "solid",
        "proper nice",
        "impressive",
        "a vibe",
        "next level"
    };
    private static final String[] REQUESTS = {
        "add me",
        "teach me your ways",
        "say hi to the cc",
        "raid with us",
        "join the clan",
        "share the secret",
        "give us a tip",
        "save me a spot",
        "show me your setup",
        "come on a boss trip"
    };
    private static final String[] ACTIVITIES = {
        "toa",
        "tob",
        "cox",
        "a boss trip",
        "a duo",
        "a quest",
        "a chill slayer trip",
        "a minigame",
        "a clan event",
        "some pvm",
        "a wildy trip"
    };
    private static final String[] GOALS = {
        "99",
        "pet",
        "gear upgrade",
        "diary",
        "collection log slot",
        "combat achievement",
        "quest",
        "skill milestone",
        "personal best",
        "cape"
    };
    private static final String[] SUPPLIES = {
        "staminas", "food", "teleports", "restores", "prayer pots", "runes", "bonds", "advice"
    };
    private static final String[] ENDINGS = {" lol", " haha", " pls", " :)", " fr", "", "", "", ""};

    private FanDialogueGenerator() {}

    public static String generate(
            Random random, FanPersonality personality, String name, String gear) {
        return generate(random, personality, FanChatContext.idle(name, gear));
    }

    public static String generate(
            Random random, FanPersonality personality, FanChatContext context) {
        String category;
        int roll = random.nextInt(100);
        if (context.lowHealth && roll < 20) category = "low_health";
        else if (context.gearChanged && roll < 30) category = "gear_change";
        else if (!context.gear.isEmpty() && roll >= 90) category = "gear";
        else if (roll < 65) {
            category = context.activity.name().toLowerCase(Locale.ENGLISH);
            if (context.activity == FanChatContext.Activity.SKILLING
                    && context.skill != null
                    && random.nextInt(3) != 0) {
                String specific = context.skill.name().toLowerCase(Locale.ENGLISH);
                if (PATTERNS.containsKey(specific)) category = specific;
            }
            if (context.activity == FanChatContext.Activity.COMBAT
                    && !context.target.isEmpty()
                    && random.nextBoolean()) category = "combat_target";
        } else
            category =
                    random.nextBoolean()
                            ? "social"
                            : personality.name().toLowerCase(Locale.ENGLISH);
        return line(category, context, random, true);
    }

    public static String level(FanChatContext context, Random random) {
        return line("level", context, random, false);
    }

    private static String line(
            String category, FanChatContext context, Random random, boolean decorate) {
        List<String> valid = new ArrayList<>();
        for (String pattern : PATTERNS.getOrDefault(category, PATTERNS.get("social"))) {
            if (pattern.contains("{level}") && context.level <= 0
                    || pattern.contains("{xp}") && context.gainedXp <= 0
                    || pattern.contains("{skill}") && context.skill == null) continue;
            valid.add(pattern);
        }
        if (valid.isEmpty()) valid = PATTERNS.get("social");
        String result =
                valid.get(random.nextInt(valid.size()))
                        .replace("{player}", context.player.isEmpty() ? "friend" : context.player)
                        .replace(
                                "{gear}",
                                context.gear.isEmpty()
                                        ? "fit"
                                        : context.gear.toLowerCase(Locale.ENGLISH))
                        .replace("{target}", context.target)
                        .replace(
                                "{skill}",
                                context.skill == null
                                        ? "skill"
                                        : context.skill.getName().toLowerCase(Locale.ENGLISH))
                        .replace("{level}", Integer.toString(context.level))
                        .replace("{xp}", Integer.toString(context.gainedXp))
                        .replace("{greeting}", pick(GREETINGS, random))
                        .replace("{praise}", pick(PRAISE, random))
                        .replace("{request}", pick(REQUESTS, random))
                        .replace("{activity}", pick(ACTIVITIES, random))
                        .replace("{goal}", pick(GOALS, random))
                        .replace("{supply}", pick(SUPPLIES, random));
        if (decorate && random.nextInt(7) == 0) result = pick(GREETINGS, random) + ", " + result;
        if (decorate && random.nextInt(5) == 0 && !result.endsWith("?"))
            result += pick(ENDINGS, random);
        if (decorate && random.nextInt(24) == 0) result = result.toUpperCase(Locale.ENGLISH);
        return result.substring(0, Math.min(100, result.length()));
    }

    private static String pick(String[] options, Random random) {
        return options[random.nextInt(options.length)];
    }

    private static Map<String, List<String>> load() {
        Map<String, List<String>> result = new HashMap<>();
        try (BufferedReader reader =
                new BufferedReader(
                        new InputStreamReader(
                                Objects.requireNonNull(
                                        FanDialogueGenerator.class.getResourceAsStream(
                                                "dialogue.txt")),
                                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] entry = line.split("\\|", 2);
                if (entry.length != 2) throw new IOException("Invalid dialogue pattern");
                result.computeIfAbsent(entry[0], key -> new ArrayList<>()).add(entry[1]);
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load fan dialogue", ex);
        }
        return result;
    }
}
