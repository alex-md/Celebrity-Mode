package com.celebritymode.generation;

import java.util.*;

/** Shared crowd/PM handles built from words, real names and pronounceable syllables. */
public final class UsernameGenerator {
    private static final String[] WORDS = {
        "Ash", "Cedar", "Moss", "River", "Slate", "Dusk", "Vale", "Fern", "Oak", "Mist",
        "Rune", "Iron", "Clue", "Raid", "Bank", "Moon", "Dawn", "Ember", "Cloud", "Frost",
        "Flux", "Nova", "Orbit", "Echo", "Drift", "North", "Quiet", "Late", "Slow", "Solo",
        "Grey", "Blue", "Gold", "Red", "Jade", "Onyx", "Opal", "Salt", "Tea", "Coffee",
        "Wolf", "Finch", "Otter", "Hawk", "Lynx", "Fox", "Wren", "Pine", "Kite", "Reed"
    };
    private static final String[] NAMES = {
        "Alex", "Sam", "Jamie", "Morgan", "Taylor", "Jordan", "Casey", "Riley", "Robin",
        "Chris", "Jules", "Theo", "Maya", "Nina", "Leo", "Eli", "Zoe", "Noah", "Milo",
        "Iris", "Owen", "Ari", "Remy", "Sasha", "Luca", "Anya", "Erin", "Drew", "Ellis"
    };
    private static final String[] START = {"al", "bel", "cor", "dar", "el", "fen", "hal", "is", "jor", "kel", "lin", "mar", "nor", "or", "per", "ren", "sol", "tal", "val", "wyn"};
    private static final String[] MIDDLE = {"a", "e", "i", "o", "u", "an", "en", "in", "or", "ar", "el", "ir"};
    private static final String[] END = {"n", "s", "th", "ra", "ro", "len", "lis", "vin", "wen", "ric", "rin", "dor", "na", "lo", "mi", "den"};

    private UsernameGenerator() {}

    public static String generate(Random random, Collection<String> excluded) {
        Set<String> taken = new HashSet<>();
        for (String name : excluded) taken.add(normalize(name));
        String candidate;
        int attempts = 0;
        do {
            String separator = new String[] {"", "", " ", "_"}[random.nextInt(4)];
            String base;
            switch (random.nextInt(5)) {
                case 0: base = pick(NAMES, random) + separator + pick(WORDS, random); break;
                case 1: base = pick(WORDS, random) + separator + pick(WORDS, random); break;
                case 2: base = pick(NAMES, random) + separator + new String[] {"RS", "PvM", "Plays", "Alt", ""}[random.nextInt(5)]; break;
                default: base = pick(START, random) + pick(MIDDLE, random) + pick(END, random);
            }
            String suffix = random.nextInt(3) == 0 || attempts > 24
                    ? Integer.toString(random.nextInt(10000)) : "";
            candidate = base.substring(0, Math.min(base.length(), 12 - suffix.length())).trim() + suffix;
            if (random.nextInt(4) == 0) candidate = candidate.toLowerCase(Locale.ENGLISH);
            attempts++;
        } while (attempts < 64 && taken.contains(normalize(candidate)));
        if (taken.contains(normalize(candidate))) {
            int guest = 0;
            do { candidate = "Guest" + guest++; } while (taken.contains(normalize(candidate)));
        }
        return candidate;
    }

    public static String normalize(String name) {
        return name == null ? "" : name.replace('_', ' ').replace('\u00a0', ' ').trim().toLowerCase(Locale.ENGLISH);
    }

    private static String pick(String[] words, Random random) { return words[random.nextInt(words.length)]; }
}
