package com.celebritymode.chat;

import com.celebritymode.fan.FanPersonality;
import com.celebritymode.generation.FanUtterance;
import java.util.*;
import java.util.regex.*;

/** Occasional typing slips, kept separate from a line's repetition identity. */
final class ChatTypingStyle {
    private static final Pattern WORD = Pattern.compile("[a-zA-Z]{4,}");

    private ChatTypingStyle() {}

    static FanUtterance apply(FanUtterance line, Random random, FanPersonality personality,
            String... protectedValues) {
        int chance = personality == FanPersonality.QUIET ? 4
                : personality == FanPersonality.MEMER || personality == FanPersonality.EXCITED ? 14 : 9;
        if (random.nextInt(100) >= chance) return line;
        Matcher words = WORD.matcher(line.text);
        List<int[]> candidates = new ArrayList<>();
        while (words.find()) {
            boolean protectedWord = false;
            for (String value : protectedValues) {
                if (value == null || value.isEmpty()) continue;
                String lower = line.text.toLowerCase(Locale.ENGLISH);
                String protectedText = value.toLowerCase(Locale.ENGLISH);
                for (int at = lower.indexOf(protectedText); at >= 0;
                        at = lower.indexOf(protectedText, at + 1)) {
                    if (words.start() < at + value.length() && words.end() > at) protectedWord = true;
                }
            }
            if (!protectedWord) candidates.add(new int[] {words.start(), words.end()});
        }
        if (candidates.isEmpty()) return line;
        int[] word = candidates.get(random.nextInt(candidates.size()));
        int at = word[0] + 1 + random.nextInt(word[1] - word[0] - 2);
        String text = line.text;
        switch (random.nextInt(3)) {
            case 0: // Transposed adjacent letters: friend -> freind.
                text = text.substring(0, at) + text.charAt(at + 1) + text.charAt(at)
                        + text.substring(at + 2);
                break;
            case 1: // A missed key.
                text = text.substring(0, at) + text.substring(at + 1);
                break;
            default: // A briefly held key.
                if (text.length() < 100)
                    text = text.substring(0, at) + text.charAt(at) + text.substring(at);
                break;
        }
        return new FanUtterance(text, line.structure);
    }

    static FanUtterance keyboard(Random random, FanPersonality personality) {
        int chance = personality == FanPersonality.QUIET ? 0
                : personality == FanPersonality.MEMER || personality == FanPersonality.EXCITED
                        || personality == FanPersonality.HYPEMAN ? 5 : 2;
        if (random.nextInt(100) >= chance) return null;
        int kind = random.nextInt(5);
        if (kind == 0) return new FanUtterance("@".repeat(4 + random.nextInt(9)), "keyboard:at");
        if (kind == 1) return new FanUtterance("!".repeat(3 + random.nextInt(6)), "keyboard:bang");
        if (kind == 2) return new FanUtterance("a".repeat(4 + random.nextInt(8)), "keyboard:scream");
        if (kind == 3) return new FanUtterance("wo" + "o".repeat(2 + random.nextInt(6)), "keyboard:woo");
        String[] smashes = {"asdfgh", "ajskdks", "sdkfjsk", "asjdhaj", "akdjsk", "fjdkls"};
        return new FanUtterance(smashes[random.nextInt(smashes.length)], "keyboard:smash");
    }
}
