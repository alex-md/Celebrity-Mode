package com.celebritymode.chat;

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

}
