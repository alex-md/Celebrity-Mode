package com.celebritymode.generation;

import java.util.*;

/** Short chat grammar. Structure identities exclude slot values, names and typing decoration. */
public final class QuickChatComposer {
    private static final String[] SHOCK = {
        "omg", "no way", "wait", "nah", "hold up", "what", "wtf", "wow", "oh wow", "wait what",
        "oh damn", "wait a sec", "no shot", "yooo", "oh", "damn", "wait fr", "huh", "nahhh",
        "omfg"
    };
    private static final String[] HELLO = {
        "hi", "hey", "yo", "hello", "sup", "heyy", "hiya", "hey hey", "hii", "yoo", "hi hi",
        "heya", "what's up", "hello hello", "yo yo", "hey there"
    };
    private static final String[] ADDRESS = {
        "bro", "dude", "mate", "man", "yo", "hey", "my guy", "boss", "bruh", "homie"
    };
    private static final String[] REAL = {
        "real", "actually here", "really here", "online", "here for real", "the real one"
    };
    private static final String[] PRAISE = {
        "clean", "insane", "wild", "goated", "unreal", "huge", "sick", "so good", "nice", "solid"
    };
    private static final String[] REQUEST = {
        "add me", "say hi", "wave", "join the cc", "duo", "raid with us", "take a pic",
        "rate my gear", "carry me", "say hi to my clan", "cc invite", "show the bank",
        "show the setup", "1v1 me", "come raid", "sign my whip", "say my name", "do an emote",
        "teach me toa", "send a raid", "clan invite", "come bossing", "duo nex", "follow me",
        "drop party", "bank tour"
    };
    private static final String[] ITEM = {
        "stams", "food", "runes", "gp", "prayer pots", "a bond", "a teleport", "restores"
    };
    private static final String[] GOAL = {
        "99", "a pet", "the cape", "the next upgrade", "a purple", "the diary", "a pb",
        "the log slot", "the tbow", "the shadow", "a max cape", "the next level", "the quest cape",
        "the inferno", "the fang", "the rebuild", "the clue loot", "the task"
    };
    private static final String[] PLACE = {
        "the cc", "discord", "my clan", "the group", "the boys", "my friends", "the gc", "my duo",
        "my team", "the raid group", "the clan chat"
    };
    private static final String[] EXCUSE = {
        "my farm run", "my slayer task", "the quest", "my bank tabs", "my xp", "my clue",
        "my supplies", "my run energy", "my raid", "my birdhouses", "my herb run", "my daily",
        "my gear", "my cash stack", "my food", "my restores", "my grind", "my fishing spot"
    };
    private static final String[] ACTION = {
        "bankstanding", "following", "lurking", "taking notes", "watching", "tagging along",
        "vibing", "waiting", "chilling", "hanging out", "spectating", "wandering", "catching up",
        "saying hi", "passing through", "getting a pic"
    };
    private static final String[] CERTAINTY = {
        "actually", "really", "legit", "seriously"
    };
    private static final String[] SHORT = {
        "{shock}", "{hello}", "{address} {hello}", "{shock} {hello}", "{hello} {player}",
        "{player}??", "{address}??", "{shock} it's {player}", "it's {player}", "is that {player}?",
        "you're {real}?", "{address} you're {real}", "is this {certainty} you?", "{shock} you're here",
        "{player} is {real}", "{certainty}??", "{request}?", "{request} pls", "can you {request}?",
        "{address} {request}", "{player} {request}", "{praise} gear", "that setup is {praise}",
        "looking {praise}", "{praise} account", "gl on {goal}", "{goal} next?", "here for {goal}",
        "hi from {place}", "{place} needs proof", "telling {place}", "{place} sent me",
        "i'm just {action}", "back to {excuse}", "there goes {excuse}", "forgot about {excuse}",
        "{keepsake} pls", "{request} then i'll go", "{hello} again", "{praise} timing",
        "{address} look", "{player} spotted", "{shock} same world", "{player} hi hi",
        "{address} say something", "{shock} a whole crowd", "worth the xp loss", "one sec {place}",
        "{certainty} made my day", "{shock} not a lookalike", "{hello} from work",
        "{content} later?", "{content} next?", "{content} tips?", "{content} anyone?",
        "{praise} {topic}", "{topic} check", "{address} your {topic}", "{topic} goal?",
        "{shock} that {topic}", "need a {role}?", "i'll bring {item}", "who's on {content}?",
        "{content} with {place}?", "{request} for {place}", "{address} that {topic}",
        "{place} says {hello}", "one more {keepsake}", "{certainty} {action}", "{address} gl on {goal}"
    };
    private static final String[] MEDIUM = {
        "{place} won't believe i found you", "i was doing {excuse} before this",
        "{shock} i wasn't expecting to see {player}", "can you {request} before you leave?",
        "{hello}, what's the next grind?", "gl on {goal}, i'll stay out of the way",
        "{place} is going to ask for proof", "{certainty} thought you were a lookalike",
        "i'm meant to be doing {excuse}", "one {keepsake} and i'll get back to {excuse}",
        "{address} i logged in at the right time", "{shock}, {place} said you were here",
        "{player}, got room for one more?", "still saving up for {goal}",
        "{place} says {hello}", "{certainty} forgot what i logged in for"
    };

    private QuickChatComposer() {}

    public static FanUtterance generate(Random random, String tone, String player,
            String category, String gear, String target, String skill, boolean compact) {
        List<String> situational = new ArrayList<>();
        switch (category) {
            case "low_health":
                Collections.addAll(situational, "food check", "{address} eat", "watch your hp",
                        "hp??", "got food?", "{shock} your hp", "eat pls", "don't risk it",
                        "heal first", "{address} food check"); break;
            case "gear":
            case "gear_change":
                if (!gear.isEmpty()) Collections.addAll(situational, "{praise} {gear}", "{gear}??",
                        "{shock} the {gear}", "{gear} worth it?", "nice upgrade", "that weapon is {praise}",
                        "{address} that {gear}", "let me see the setup"); break;
            case "combat_target":
                if (!target.isEmpty()) Collections.addAll(situational, "gl at {target}", "{target} kc?",
                        "{target} loot?", "{address} get that {target}", "{target} pet soon?",
                        "{target} again?", "how's {target}?", "{target} tips?"); break;
            case "combat":
                Collections.addAll(situational, "gl on the kill", "got supplies?", "drop soon",
                        "{praise} hit", "one more kc", "send it", "pet this kill", "loot check"); break;
            case "banking":
                Collections.addAll(situational, "bank tour?", "{praise} bank", "supplies check",
                        "gearing for what?", "show the tab", "bankstanding gang", "buying what?",
                        "{address} how much gp", "bank value?", "back for supplies?"); break;
            case "walking":
            case "running":
                Collections.addAll(situational, "where we going?", "{address} wait", "coming with",
                        "wait for me", "run energy??", "{place} incoming", "slow down pls", "following you"); break;
            default:
                if (!skill.isEmpty() && !category.equals("social") && !category.equals(tone.toLowerCase(Locale.ENGLISH)))
                    Collections.addAll(situational, "{skill} goal?", "gl on {skill}", "{skill} xp?",
                            "{skill} level?", "{skill} grind", "{address} {skill} tips?", "{skill} cape soon?");
        }
        List<String> personality = new ArrayList<>();
        switch (tone) {
            case "REQUEST":
            case "BEGGAR":
                Collections.addAll(personality, "spare {item}?", "{item} pls", "{address} i'm broke",
                        "{request} pls", "budget setup?", "need {item}", "{player} {item}?",
                        "{certainty} need a carry", "one {item}?", "{item} check"); break;
            case "SKEPTIC":
                Collections.addAll(personality, "is this {certainty} you?", "you're {real}?", "real {player}?",
                        "not a lookalike?", "{player} or a fake?", "{address} prove it", "wait the real one?",
                        "{certainty} your account?", "{shock} is it you"); break;
            case "SCREENSHOTTER":
                Collections.addAll(personality, "pic pls", "{keepsake}?", "{keepsake} for {place}?",
                        "hold still pls", "{address} one pic", "{place} needs a pic", "{player} wave",
                        "wait let me screenshot", "{certainty} need proof", "say hi to {place}"); break;
            case "MEMER":
                Collections.addAll(personality, "rip {excuse}", "{action} simulator", "{place} takeover",
                        "{address} the xp waste", "{excuse} can wait", "{shock} paparazzi",
                        "{place} side quest", "unplanned {action}", "{certainty} no xp today",
                        "efficiency left the chat"); break;
            case "QUIET":
                Collections.addAll(personality, "gl", "{hello}", "just {action}", "{praise} setup",
                        "gl on {goal}", "nice seeing you", "don't mind me", "{hello} from {place}"); break;
            default:
                Collections.addAll(personality, "{shock} {player}", "{address} no way", "{praise} account",
                        "{certainty} a fan", "huge fan", "{player} let's go", "{address} you're {real}",
                        "{shock} i'm here", "{praise} session", "{place} is hyped");
        }
        int roll = random.nextInt(100);
        List<String> pool = !compact ? Arrays.asList(MEDIUM)
                : roll < 65 && !situational.isEmpty() ? situational
                : roll < 85 ? personality : Arrays.asList(SHORT);
        // A substantial shared pool keeps personalities from becoming a small repeated script.
        if (compact && random.nextInt(3) == 0) pool = Arrays.asList(SHORT);
        String pattern = pool.get(random.nextInt(pool.size()));
        Map<String, String> slots = new LinkedHashMap<>();
        slots.put("player", player.isEmpty() ? "you" : player);
        slots.put("shock", pick(SHOCK, random));
        slots.put("hello", pick(HELLO, random));
        slots.put("address", pick(ADDRESS, random));
        slots.put("real", pick(REAL, random));
        slots.put("praise", pick(PRAISE, random));
        slots.put("request", pick(REQUEST, random));
        slots.put("item", pick(ITEM, random));
        slots.put("goal", pick(GOAL, random));
        slots.put("place", pick(PLACE, random));
        slots.put("excuse", pick(EXCUSE, random));
        slots.put("action", pick(ACTION, random));
        slots.put("certainty", pick(CERTAINTY, random));
        slots.put("content", pick(new String[] {"toa", "tob", "cox", "nex", "zulrah", "vorkath", "slayer",
                "clues", "questing", "pvm", "barrows", "inferno", "gauntlet", "duo raids", "wildy bosses",
                "gwd", "pest control", "skilling", "dt2 bosses", "a farm run", "a clan event"}, random));
        slots.put("topic", pick(new String[] {"cape", "pet", "gear", "bank", "account", "setup", "weapon",
                "collection log", "skill total", "outfit", "stats", "loot", "kc", "combat level", "clue luck"}, random));
        slots.put("role", pick(new String[] {"duo", "tank", "third", "learner", "healer", "carry", "teammate"}, random));
        slots.put("keepsake", pick(new String[] {"pic", "screenshot", "group pic", "photo"}, random));
        slots.put("gear", gear);
        slots.put("target", target);
        slots.put("skill", skill);
        String body = pattern;
        for (Map.Entry<String, String> slot : slots.entrySet())
            body = body.replace("{" + slot.getKey() + "}", slot.getValue());
        // Optional brief address, never an automatic follow-up sentence.
        if (compact && body.length() < 24 && !pattern.contains("{address}") && random.nextInt(4) == 0)
            body = pick(ADDRESS, random) + " " + body;
        // No automatic clause or greeting is tacked on to every short message.
        if (!pattern.contains("{player}") && !player.isEmpty() && random.nextInt(5) == 0)
            body = random.nextBoolean() ? player + " " + body : body + " " + player;
        int style = random.nextInt(20);
        if (style == 0) body = body.toUpperCase(Locale.ENGLISH);
        else if (style < 4) body = Character.toUpperCase(body.charAt(0)) + body.substring(1);
        else if (style < 7) body = body.replace("you're", "your").replace("you ", "u ").replace("your ", "ur ");
        if (random.nextInt(12) == 0 && !body.endsWith("?")) body += random.nextBoolean() ? "!" : " lol";
        // Rare unusually long actor/item names still fit the overhead limit at a word boundary.
        if (body.length() > 100) {
            int boundary = body.lastIndexOf(' ', 100);
            body = body.substring(0, boundary > 0 ? boundary : 100);
        }
        return new FanUtterance(body, pattern);
    }

    private static String pick(String[] words, Random random) { return words[random.nextInt(words.length)]; }
}
