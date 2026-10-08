package com.celebritymode.chat;

import com.celebritymode.fan.*;
import com.celebritymode.generation.FanUtterance;

import java.util.*;
import java.util.regex.Pattern;

/** One crowd scheduler, rather than thirty competing timers. */
public final class FanChatManager {
    private static final Pattern PUNCTUATION = Pattern.compile("[^a-z0-9 ]");
    private static final Pattern SPACES = Pattern.compile(" +");
    private final Random random;
    private ChatFrequency frequency = ChatFrequency.OFF;
    private FanChatContext context = FanChatContext.idle("", "");
    private final Deque<String> recentCrowd = new ArrayDeque<>();
    private final Map<FanEntity, Deque<String>> recentFans = new IdentityHashMap<>();
    private final Deque<String> recentStructures = new ArrayDeque<>();
    private final Map<FanEntity, Deque<String>> fanStructures = new IdentityHashMap<>();
    private final Map<FanEntity, String> visibleStructures = new IdentityHashMap<>();
    private int nextTick = Integer.MAX_VALUE;
    private String lastQuote;
    private CustomQuoteMode mode = CustomQuoteMode.BUILT_IN_ONLY;
    private List<String> custom = Collections.emptyList();

    public FanChatManager(Random random) {
        this.random = random;
    }

    public void configure(ChatFrequency frequency, CustomQuoteMode mode, String custom, int tick) {
        this.frequency = frequency;
        this.mode = mode;
        List<String> parsed = FanQuoteLibrary.parse(custom);
        this.custom = parsed;

        nextTick =
                frequency == ChatFrequency.OFF ? Integer.MAX_VALUE : tick + frequency.delay(random);
    }

    public int getNextTick() {
        return nextTick;
    }

    public void tick(List<FanEntity> fans, int tick) {
        recentFans.keySet().retainAll(fans);
        fanStructures.keySet().retainAll(fans);
        visibleStructures.keySet().retainAll(fans);
        for (FanEntity fan : fans) if (tick >= fan.overheadExpiryTick) fan.overheadText = null;
        if (frequency == ChatFrequency.OFF || tick < nextTick) return;
        nextTick = tick + frequency.delay(random);
        List<FanEntity> eligible = new ArrayList<>(fans.size());
        int visible = 0;
        for (FanEntity fan : fans) {
            if (!fan.isVisible()) continue;
            visible++;
            if (tick >= fan.nextChatTick
                    && (mode != CustomQuoteMode.CUSTOM_ONLY || !custom.isEmpty()))
                eligible.add(fan);
        }
        Collections.shuffle(eligible, random);
        int batch =
                frequency == ChatFrequency.LOW
                        ? 1
                        : frequency == ChatFrequency.NORMAL
                                ? Math.max(1, (visible + 2) / 3)
                                : Math.max(1, (2 * visible + 2) / 3);
        for (int i = 0; i < Math.min(batch, eligible.size()); i++) {
            FanEntity fan = eligible.get(i);
            say(fan, chooseFor(fan), tick);
        }
    }

    public void setContext(String name, String equipment) {
        setContext(FanChatContext.idle(name, equipment));
    }

    public void setContext(FanChatContext context) {
        this.context = context;
    }

    private String personalize(String quote) {
        if (quote == null) return null;
        String result =
                quote.replace("{player}", context.player.isEmpty() ? "you" : context.player)
                        .replace("{gear}", context.gear.isEmpty() ? "fit" : context.gear)
                        .replace("{target}", context.target.isEmpty() ? "opponent" : context.target)
                        .replace(
                                "{skill}",
                                context.skill == null ? "skill" : context.skill.getName())
                        .replace("{level}", Integer.toString(context.level));
        return result.substring(0, Math.min(100, result.length()));
    }

    public void level(List<FanEntity> fans, int tick, net.runelite.api.Skill skill, int level) {
        FanChatContext event =
                new FanChatContext(
                        context.player,
                        context.gear,
                        context.target,
                        context.activity,
                        skill,
                        level,
                        0,
                        false,
                        false);
        FanChatContext previous = context;
        context = event;
        try {
            reactGenerated(fans, tick, () -> FanDialogueGenerator.level(event, random));
        } finally {
            context = previous;
        }
    }

    private String signature(String line) {
        String normalized = line.toLowerCase(Locale.ENGLISH);
        if (!context.player.isEmpty()) normalized = normalized.replace(context.player.toLowerCase(Locale.ENGLISH), "");
        normalized = normalized.replaceAll("\\b(ur)\\b", "your")
                .replaceAll("\\b(u)\\b", "you");
        return SPACES.matcher(PUNCTUATION.matcher(normalized).replaceAll(""))
                .replaceAll(" ").trim();
    }

    private FanUtterance chooseFor(FanEntity fan) {
        if (mode == CustomQuoteMode.CUSTOM_ONLY
                || (mode == CustomQuoteMode.MIXED && !custom.isEmpty() && random.nextInt(3) == 0))
            return new FanUtterance(choose(custom), null);
        FanUtterance best = null;
        int bestPenalty = Integer.MAX_VALUE;
        int lengthRoll = random.nextInt(100);
        // Evaluate a bounded set of candidates, preferring new wording AND new structure.
        for (int attempt = 0; attempt < 48; attempt++) {
            FanUtterance candidate = FanDialogueGenerator.generateUtterance(random, fan.personality, context, lengthRoll);
            String key = signature(candidate.text);
            int penalty = recentCrowd.contains(key) ? 8 : 0;
            if (recentFans.get(fan) != null && recentFans.get(fan).contains(key)) penalty += 12;
            if (recentStructures.contains(candidate.structure)) penalty += 4;
            if (fanStructures.get(fan) != null && fanStructures.get(fan).contains(candidate.structure)) penalty += 6;
            for (Map.Entry<FanEntity, String> entry : visibleStructures.entrySet()) {
                FanEntity other = entry.getKey();
                if (other != fan && other.overheadText != null && other.isVisible()
                        && candidate.text.length() > 40 && candidate.structure.equals(entry.getValue()))
                    penalty += 10;
            }
            if (candidate.text.equals(lastQuote)) penalty += 20;
            if (penalty < bestPenalty) {
                best = candidate;
                bestPenalty = penalty;
            }
            if (penalty == 0) break;
        }
        lastQuote = best.text;
        return best;
    }

    public String choose(List<String> pool) {
        if (pool == null || pool.isEmpty()) return null;
        List<String> candidates = new ArrayList<>();
        for (String q : pool) if (!personalize(q).equals(lastQuote)) candidates.add(personalize(q));
        String quote =
                candidates.isEmpty()
                        ? personalize(pool.get(0))
                        : candidates.get(random.nextInt(candidates.size()));
        lastQuote = quote;
        return quote;
    }

    public void reactEvent(List<FanEntity> fans, int tick, String event) {
        reactGenerated(fans, tick, () -> FanDialogueGenerator.event(event, context, random));
    }

    public void react(List<FanEntity> fans, int tick, String[] quotes) {
        reactGenerated(fans, tick, () -> choose(Arrays.asList(quotes)));
    }

    private void reactGenerated(List<FanEntity> fans, int tick, java.util.function.Supplier<String> generate) {
        if (frequency == ChatFrequency.OFF || fans.isEmpty()
                || (mode == CustomQuoteMode.CUSTOM_ONLY && custom.isEmpty())) return;
        int start = random.nextInt(fans.size());
        for (int i = 0; i < Math.min(2, fans.size()); i++) {
            FanEntity fan = fans.get((start + i) % fans.size());
            if (fan.isVisible()) {
                say(fan, mode == CustomQuoteMode.CUSTOM_ONLY ? choose(custom) : generate.get(), tick);
                fan.nextChatTick = Math.max(fan.nextChatTick, tick + 3);
            }
        }
    }

    private void say(FanEntity fan, String quote, int tick) {
        say(fan, new FanUtterance(quote, null), tick);
    }

    private void say(FanEntity fan, FanUtterance utterance, int tick) {
        String quote = utterance.text;
        if (utterance.structure != null && quote != null) {
            remember(recentStructures, utterance.structure, 16);
            remember(fanStructures.computeIfAbsent(fan, ignored -> new ArrayDeque<>()), utterance.structure, 6);
            visibleStructures.put(fan, utterance.structure);
        } else visibleStructures.remove(fan);
        if (quote != null) {
            String key = signature(quote);
            remember(recentCrowd, key, 96);
            Deque<String> personal = recentFans.computeIfAbsent(fan, ignored -> new ArrayDeque<>());
            remember(personal, key, 16);
            fan.overheadText = quote;
            fan.overheadExpiryTick = tick + 5 + quote.length() / 12 + random.nextInt(4);
            // Roughly 40–80 words/minute, plus thinking time, consistent for each sender.
            int typingTicks = (quote.length() + 1) / (2 + Math.floorMod(fan.id, 3));
            fan.nextChatTick = Math.max(fan.nextChatTick,
                    tick + Math.max(4, typingTicks) + 2 + random.nextInt(5));
        }
    }

    private static void remember(Deque<String> history, String value, int limit) {
        history.addLast(value);
        if (history.size() > limit) history.removeFirst();
    }

    String visibleStructure(FanEntity fan) { return visibleStructures.get(fan); }

    public void clear(List<FanEntity> fans) {
        for (FanEntity f : fans) {
            f.overheadText = null;
            f.nextChatTick = 0;
        }
        lastQuote = null;
        recentCrowd.clear();
        recentFans.clear();
        recentStructures.clear();
        fanStructures.clear();
        visibleStructures.clear();
        nextTick = Integer.MAX_VALUE;
    }
}
