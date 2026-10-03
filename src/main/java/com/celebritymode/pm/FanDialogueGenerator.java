package com.celebritymode.pm;

import java.util.*;
import java.util.regex.*;

/** Small composable vocabularies; no client APIs or game-thread state. */
public final class FanDialogueGenerator {
    private static final Pattern TOKEN = Pattern.compile("\\{([a-z_]+)\\}");
    private static final String[] GREETINGS = {
        "omg", "yo", "hey legend", "BRO", "sir", "can i get a screenshot", "is it really you"
    };
    private static final String[] BOSSES = {"Zulrah", "Vorkath", "Jad", "Zuk", "Nex", "Scurrius", "the Whisperer", "Olm"};
    private static final String[] ITEMS = {"tbow", "shadow", "fang", "whip", "bandos chestplate", "ranger boots", "dragon claws", "pet"};
    private static final String[] TOPICS = {"{boss} kc", "that {item} drop", "inferno cape", "stream", "video", "clue scroll luck"};
    private static final String[] REQUESTS = {
        "add me please", "can u say hi to my cc", "trim my armor lol", "sign my whip", "join {clan_name}",
        "can i borrow 10k", "come to our clan event", "teach me {boss}", "rate my setup"
    };
    private static final String[] SLANG = {"@@@@@", "xD", ":D", "hype", "gf", "gzzzzz", "<3"};
    private static final String[] CLANS = {"Bank Sitters", "Rat Pack", "No XP Waste", "Goblin Gang", "Lobster Club", "One More Kill"};
    private static final String[] HYPE = {
        "{greeting} {player}!!! {emote_slang}", "{player} YOUR {praise_topic} IS INSANE!!!",
        "{greeting}!!! THE {praise_topic}??? {emote_slang}", "WAIT {player} IS ONLINE {emote_slang}",
        "{greeting} {greeting}!!! {request}!!!", "{praise_topic}. ACTUAL LEGEND. {emote_slang}",
        "{player}!!! I WATCHED THAT {praise_topic} LIKE 5 TIMES", "{greeting} {player}!! made my day {emote_slang}"
    };
    private static final String[] FRIEND = {
        "bro check discord", "{player} did you see what happened?", "yo we doing {boss} later?",
        "{greeting}, how's the {praise_topic} going", "mate you still owe me that {boss} trip",
        "{player} save me a spot next stream", "been here since the first video lol",
        "{greeting} just saw you at the bank haha", "when's the next {praise_topic} update?",
        "bro the cc is talking about your {praise_topic}", "{player} same time tomorrow?"
    };
    private static final String[] BEGGING = {
        "{greeting} {request}", "{player} please {request} {emote_slang}",
        "big fan of your {praise_topic}, {request}?", "{request} i swear i'm your biggest fan",
        "{greeting} got a spare {item}?", "{player} come say hi to {clan_name}",
        "{request} just once pls", "{greeting} can you help me with {boss}"
    };
    private static final String[] SKEPTIC = {
        "wait is this actually the real one?", "{player}? the one from that {praise_topic}?",
        "{greeting} is that actually you", "is this the real {player} or another lookalike",
        "hold up did i just see you on stream", "{player} prove it's you lol",
        "someone said you were here, no way", "is the {praise_topic} person actually online?"
    };
    private static final String[] PREFIXES = {"Rune", "Dark", "Pure", "Lil", "Sir", "Iron", "Lazy", "Big", "Its", "OSRS"};
    private static final String[] NOUNS = {"Bob", "Pker", "Noob", "Mage", "Main", "Lobster", "Goblin", "Scaper", "Banker", "Rat", "Whip", "Clue"};
    private static final String[] MEMES = {"Xp Waste", "Bank Enjoyer", "Nice Cape", "Sit Rat", "One More Kc", "Lobster Lad", "Buyin Gf", "Rune Dad"};
    private static final String[] FIRST_NAMES = {"Jake", "Sam", "Alex", "Ben", "Josh", "Luke", "Amy", "Kai", "Max", "Chris"};
    private static final String[] ENDINGS = {"Plays", "RS", "2007", "OSRS", "PvM", "Main"};
    private final Deque<String> recentNames = new ArrayDeque<>();
    private final Deque<String> recentLines = new ArrayDeque<>();

    public FanMessage generate(Random random, String playerName, long nowNanos) {
        String player = playerName == null ? "legend" : playerName.replaceAll("<[^>]*>", "")
                .replaceAll("[^A-Za-z0-9 _]", "").trim();
        if (player.isEmpty()) player = "legend";
        int roll = random.nextInt(100);
        FanMessage.Archetype archetype = roll < 45 ? FanMessage.Archetype.HYPE
                : roll < 70 ? FanMessage.Archetype.FRIEND
                : roll < 90 ? FanMessage.Archetype.REQUEST : FanMessage.Archetype.SKEPTIC;
        String[] templates = archetype == FanMessage.Archetype.HYPE ? HYPE
                : archetype == FanMessage.Archetype.FRIEND ? FRIEND
                : archetype == FanMessage.Archetype.REQUEST ? BEGGING : SKEPTIC;
        String text = "";
        for (int attempt = 0; attempt < 12; attempt++) {
            Map<String, String> slots = new HashMap<>();
            slots.put("player", player);
            slots.put("greeting", pick(GREETINGS, random));
            slots.put("boss", pick(BOSSES, random));
            slots.put("item", pick(ITEMS, random));
            slots.put("praise_topic", pick(TOPICS, random));
            slots.put("request", pick(REQUESTS, random));
            slots.put("emote_slang", pick(SLANG, random));
            slots.put("clan_name", pick(CLANS, random));
            text = expand(pick(templates, random), slots);
            if (archetype == FanMessage.Archetype.HYPE && random.nextInt(3) == 0)
                text = text.toUpperCase(Locale.ENGLISH);
            text = text.substring(0, Math.min(160, text.length()));
            if (!recentLines.contains(text)) break;
        }
        remember(recentLines, text, 64);
        String username = username(random, player);
        return new FanMessage(username, text, archetype, nowNanos);
    }

    /** Nested slots resolve in bounded passes; replacement text is never interpreted as regex. */
    static String expand(String template, Map<String, String> slots) {
        String result = template;
        for (int pass = 0; pass < 4; pass++) {
            Matcher matcher = TOKEN.matcher(result);
            if (!matcher.find()) return result;
            StringBuffer expanded = new StringBuffer();
            do {
                String replacement = slots.get(matcher.group(1));
                if (replacement == null) throw new IllegalArgumentException("Unknown fan token: " + matcher.group(1));
                matcher.appendReplacement(expanded, Matcher.quoteReplacement(replacement));
            } while (matcher.find());
            matcher.appendTail(expanded);
            result = expanded.toString();
        }
        if (TOKEN.matcher(result).find()) throw new IllegalArgumentException("Cyclic fan tokens");
        return result;
    }

    private String username(Random random, String player) {
        String candidate;
        int attempts = 0;
        do {
            String base;
            switch (random.nextInt(4)) {
                case 0: base = "xX" + pick(NOUNS, random) + "Xx"; break;
                case 1: base = pick(MEMES, random); break;
                case 2: base = pick(FIRST_NAMES, random) + pick(ENDINGS, random); break;
                default: base = pick(PREFIXES, random) + "_" + pick(NOUNS, random); break;
            }
            String suffix = random.nextBoolean() ? Integer.toString(random.nextInt(1000)) : "";
            candidate = base.substring(0, Math.min(base.length(), 12 - suffix.length())) + suffix;
            attempts++;
        } while (attempts < 24 && (candidate.replace('_', ' ').equalsIgnoreCase(player.replace('_', ' '))
                || recentNames.contains(candidate.toLowerCase(Locale.ENGLISH))));
        if (candidate.replace('_', ' ').equalsIgnoreCase(player.replace('_', ' '))) {
            candidate = player.equalsIgnoreCase("FanGuest") ? "AnotherFan" : "FanGuest";
        }
        remember(recentNames, candidate.toLowerCase(Locale.ENGLISH), 64);
        return candidate;
    }

    private static void remember(Deque<String> history, String value, int limit) {
        history.addLast(value);
        if (history.size() > limit) history.removeFirst();
    }

    private static String pick(String[] values, Random random) {
        return values[random.nextInt(values.length)];
    }

    public void clear() {
        recentNames.clear();
        recentLines.clear();
    }
}
