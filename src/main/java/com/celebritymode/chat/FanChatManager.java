package com.celebritymode.chat;

import com.celebritymode.fan.*;

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
            fan.nextChatTick =
                    tick
                            + (frequency == ChatFrequency.SPAM
                                    ? 1 + random.nextInt(2)
                                    : frequency == ChatFrequency.NORMAL
                                            ? 3 + random.nextInt(3)
                                            : 8 + random.nextInt(5));
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
            react(
                    fans,
                    tick,
                    new String[] {
                        FanDialogueGenerator.level(event, random),
                        FanDialogueGenerator.level(event, random)
                    });
        } finally {
            context = previous;
        }
    }

    private static String signature(String line) {
        return SPACES.matcher(PUNCTUATION.matcher(line.toLowerCase(Locale.ENGLISH)).replaceAll(""))
                .replaceAll(" ")
                .trim();
    }

    private String chooseFor(FanEntity fan) {
        if (mode == CustomQuoteMode.CUSTOM_ONLY
                || (mode == CustomQuoteMode.MIXED && !custom.isEmpty() && random.nextInt(3) == 0))
            return choose(custom);
        String line = null;
        for (int attempt = 0; attempt < 24; attempt++) {
            line = FanDialogueGenerator.generate(random, fan.personality, context);
            String key = signature(line);
            if (!recentCrowd.contains(key)
                    && (recentFans.get(fan) == null || !recentFans.get(fan).contains(key))
                    && !line.equals(lastQuote)) break;
        }
        lastQuote = line;
        return line;
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

    public void react(List<FanEntity> fans, int tick, String[] quotes) {
        if (frequency == ChatFrequency.OFF || fans.isEmpty()) return;
        List<String> source = mode == CustomQuoteMode.CUSTOM_ONLY ? custom : Arrays.asList(quotes);
        if (source.isEmpty()) return;
        int start = random.nextInt(fans.size());
        for (int i = 0; i < Math.min(2, fans.size()); i++) {
            FanEntity fan = fans.get((start + i) % fans.size());
            if (fan.isVisible()) {
                say(fan, choose(source), tick);
                fan.nextChatTick = Math.max(fan.nextChatTick, tick + 3);
            }
        }
    }

    private void say(FanEntity fan, String quote, int tick) {
        if (quote != null) {
            String key = signature(quote);
            recentCrowd.addLast(key);
            if (recentCrowd.size() > 64) recentCrowd.removeFirst();
            Deque<String> personal = recentFans.computeIfAbsent(fan, ignored -> new ArrayDeque<>());
            personal.addLast(key);
            if (personal.size() > 12) personal.removeFirst();
            fan.overheadText = quote;
            fan.overheadExpiryTick = tick + 5 + random.nextInt(4);
        }
    }

    public void clear(List<FanEntity> fans) {
        for (FanEntity f : fans) {
            f.overheadText = null;
            f.nextChatTick = 0;
        }
        lastQuote = null;
        recentCrowd.clear();
        recentFans.clear();
        nextTick = Integer.MAX_VALUE;
    }
}
