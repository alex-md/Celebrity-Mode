package com.celebritymode.chat;

import com.celebritymode.fan.FanPersonality;

import java.util.*;

public final class FanQuoteLibrary {
    private FanQuoteLibrary() {}

    public static List<String> parse(String input) {
        if (input == null || input.trim().isEmpty()) return Collections.emptyList();
        String[] parts =
                input.split(input.contains("\n") || input.contains("\r") ? "\r\n|[\r\n]" : ",");
        List<String> result = new ArrayList<>();
        for (String s : parts) {
            String clean = s.replaceAll("<[^>]*>", "").trim();
            if (!clean.isEmpty()) result.add(clean.substring(0, Math.min(100, clean.length())));
            if (result.size() == 100) break;
        }
        return Collections.unmodifiableList(result);
    }

    public static List<String> quotes(FanPersonality personality) {
        switch (personality) {
            case BEGGAR:
                return Arrays.asList(
                        "can i have free stuff", "how rich are u", "drop party???", "buying gf");
            case MEMER:
                return Arrays.asList(
                        "reported", "LOOOOOOL", "wave2:flash:@@@@@@", "{player} is actually here");
            case SCREENSHOTTER:
                return Arrays.asList("OMG SCREENSHOT", "say hi youtube", "its actually him");
            case HYPEMAN:
                return Arrays.asList("OMG IT'S {player}", "huge fan", "no way");
            default:
                return Arrays.asList(
                        "omg", "nice cape", "follow me", "bro add me", "nice", "no way");
        }
    }

    public static List<String> pool(FanPersonality p, CustomQuoteMode mode, List<String> custom) {
        List<String> pool = new ArrayList<>();
        if (mode != CustomQuoteMode.CUSTOM_ONLY) pool.addAll(quotes(p));
        if (mode != CustomQuoteMode.BUILT_IN_ONLY) pool.addAll(custom);
        return pool;
    }
}
