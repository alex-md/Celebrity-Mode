package com.celebritymode.generation;

import java.util.*;

/** Grammar fragments shared by overhead and inbox dialogue. No service or model calls. */
public final class ConversationComposer {
    private static final String[] OPENERS = {
        "hey", "yo", "hey there", "oh hey", "omg", "ngl", "quick question",
        "hey, quick question", "nice running into you", "good to see you", "hey again",
        "wait", "actually", "by the way", "while you're here", "before you head off"
    };
    private static final String[] ACTIVITIES = {
        "a few raids", "some toa", "a cox run", "a tob run", "a nex trip", "duo slayer",
        "a boss trip", "some clues", "a farm run", "a wildy trip", "a minigame",
        "some quests", "a diary step", "a collection log grind", "a pet grind",
        "an inferno attempt", "a vorkath trip", "some zulrah", "a chill skilling session",
        "a clan event", "some combat achievements", "a gear rebuild", "a new personal best"
    };
    private static final String[] TIMES = {
        "later", "this evening", "after this trip", "when you've got time", "this weekend",
        "next time you're on", "after the bank run", "once you're done here", "tomorrow",
        "on your next session", "at some point", "after work", "on a day off", "tonight"
    };
    private static final String[] PLANS = {
        "thinking about", "planning", "trying to fit in", "getting ready for", "considering",
        "looking for a group for", "making time for", "saving supplies for", "working towards"
    };
    private static final String[] MOTIVES = {
        "trying to learn the routes", "still sorting out my setup", "back after a break",
        "only have a couple of hours", "finally have the supplies", "working on my account",
        "taking a break from slayer", "looking for something different", "learning with a friend",
        "keeping it fairly casual", "playing after work", "trying to finish the diary",
        "not in a rush", "want to get a bit more consistent", "doing a budget rebuild",
        "usually play solo", "just finished a quest", "got a free evening", "saving for an upgrade"
    };
    private static final String[] RESPONSES = {
        "no rush", "if you're around", "whenever works", "if you're up for it", "all good either way",
        "no worries if you're busy", "only if you've got a minute", "just curious", "would appreciate it"
    };
    private static final String[] APPRECIATION = {
        "nice progress on", "respect the effort on", "really like", "been following",
        "good to see", "looking forward to more of", "enjoyed seeing", "caught a bit of",
        "been meaning to ask about", "wanted to hear more about", "was interested in",
        "ngl, really like", "still thinking about", "actually hyped for"
    };
    private static final String[] TOPICS = {
        "your raids", "your setup", "your account progress", "your last stream", "your guides",
        "your boss trips", "your quest progress", "your collection log", "your rebuild",
        "your gear choices", "your skilling sessions", "your challenge runs", "your clue luck",
        "your last video", "your clan events", "your pet grind", "your combat achievements"
    };

    private ConversationComposer() {}

    public static String activity(Random random) { return pick(ACTIVITIES, random); }
    public static String time(Random random) { return pick(TIMES, random); }
    public static String motive(Random random) { return pick(MOTIVES, random); }
    public static String response(Random random) { return pick(RESPONSES, random); }

    public static String social(Random random, String tone) {
        String activity = activity(random), time = time(random), motive = motive(random);
        switch (tone) {
            case "BEGGAR":
            case "REQUEST":
                return pick(new String[] {
                    "any tips for " + activity + "? " + motive,
                    "would you be up for " + activity + " " + time + "? " + response(random),
                    "could use some advice on " + activity + ", " + motive,
                    "any budget setup you'd recommend for " + activity + "? " + motive,
                    "mind if i tag along for " + activity + " " + time + "? " + response(random)
                }, random);
            case "SKEPTIC":
                String source = pick(new String[] {"on a stream", "in a video", "in a guide",
                        "in a clan screenshot", "on reddit", "in the cc", "in a raid clip",
                        "in a progress post", "in a discord thread", "in a gear discussion"}, random);
                return pick(new String[] {
                    "think i saw " + pick(TOPICS, random) + " " + source + ", was that you?",
                    "weren't you doing " + activity + " " + source + "?",
                    "are you the person who posted about " + activity + "? " + motive,
                    "someone mentioned you " + source + ". are you doing " + activity + " " + time + "?"
                }, random);
            case "HYPE":
            case "HYPEMAN":
            case "EXCITED":
                String praise = random.nextInt(3) == 0
                        ? pick(new String[] {"ngl, ", "honestly, ", "ok but ", "still thinking about "}, random)
                            + pick(TOPICS, random) + pick(new String[] {
                                ", actually wild", ", so clean", ". respect", ". unreal"}, random)
                        : pick(APPRECIATION, random) + " " + pick(TOPICS, random);
                return praise
                        + pick(new String[] {", " + motive, ". got plans for " + activity + " " + time + "?",
                            ". i'm " + pick(PLANS, random) + " " + activity + " " + time,
                            ". any advice for " + activity + "?", ", hope the next trip goes well"}, random);
            case "MEMER":
                return pick(new String[] {
                    "logged in for " + activity + ", ended up " + pick(new String[] {
                        "in the fan club", "on crowd duty", "in a follow train", "taking screenshots",
                        "bankstanding with witnesses", "here instead"}, random) + ". " + motive,
                    "calling this " + pick(new String[] {"a clan event", "a social xp grind",
                        "a break from being productive", "the scenic route", "group bankstanding"}, random)
                        + ". " + activity + " " + time + " then",
                    "my plan was " + activity + " " + time + ". " + pick(new String[] {
                        "apparently we're doing this now", "the follow train had other plans",
                        "there goes my efficiency", "still counts as logging in", "close enough"}, random)
                }, random);
            case "SCREENSHOTTER":
                return pick(new String[] {"mind if i grab a screenshot", "got a minute for a picture",
                        "can i get a quick screenshot", "would you be up for a photo with the cc"}, random)
                        + " " + time + "? " + response(random);
            default:
                return pick(new String[] {
                    "anyone up for " + activity + " " + time + "? " + motive,
                    "i'm " + pick(PLANS, random) + " " + activity + " " + time + ", " + motive,
                    "how do you usually prep for " + activity + "? " + motive,
                    "might do " + activity + " " + time + ". " + motive,
                    "what would you recommend for " + activity + "? " + motive,
                    "any plans for " + activity + " " + time + "? " + response(random)
                }, random);
        }
    }

    public static String style(Random random, String player, String body, int limit) {
        String address = player == null || player.isEmpty() ? "" : player;
        String result;
        switch (random.nextInt(6)) {
            case 0: result = pick(OPENERS, random) + (address.isEmpty() ? ", " : " " + address + ", ") + body; break;
            case 1: result = body; break;
            default: result = address.isEmpty() ? body : address + ", " + body;
        }
        // Ordinary chat mixes lowercase and sentence case, with occasional short abbreviations.
        if (random.nextInt(5) == 0) result = result.replace("you ", "u ").replace("your ", "ur ");
        if (random.nextInt(4) == 0) result = Character.toUpperCase(result.charAt(0)) + result.substring(1);
        if (result.length() > limit) {
            int boundary = result.lastIndexOf(' ', limit - 1);
            result = result.substring(0, boundary > limit / 2 ? boundary : limit);
        }
        return result;
    }

    private static String pick(String[] values, Random random) { return values[random.nextInt(values.length)]; }
}
