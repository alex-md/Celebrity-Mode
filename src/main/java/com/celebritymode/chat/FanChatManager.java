package com.celebritymode.chat;

import com.celebritymode.fan.*;
import com.celebritymode.generation.FanUtterance;

import java.util.*;
import java.util.regex.Pattern;

/** Independent per-fan deadlines, serviced on the client thread without background timers. */
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
    private final Map<FanEntity, FanEntity> conversationPartners = new IdentityHashMap<>();
    private final CompanionConversations conversations = new CompanionConversations();
    private FanEntity firstSpeaker, secondSpeaker, thirdSpeaker;
    private String[] conversation;
    private FanChatContext conversationContext;
    private int conversationTurn, replyTick, replyDeadline, nextConversationTick;
    private final Map<FanEntity, Integer> speakingTicks = new IdentityHashMap<>();
    private final Map<FanEntity, PendingReaction> reactions = new IdentityHashMap<>();
    private int warmupTick = Integer.MAX_VALUE;

    private static final class PendingReaction {
        final String text, player;
        final int dueTick;

        PendingReaction(String text, String player, int dueTick) {
            this.text = text;
            this.player = player;
            this.dueTick = dueTick;
        }
    }

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

        cancelConversation();
        conversationPartners.clear();
        speakingTicks.clear();
        reactions.clear();
        nextConversationTick = tick + 30 + random.nextInt(50);
        warmupTick =
                frequency == ChatFrequency.OFF ? Integer.MAX_VALUE : tick + frequency.delay(random);
    }

    /** Initial warm-up boundary; actual speech deadlines are individual after this point. */
    public int getNextTick() {
        return warmupTick;
    }

    public void tick(List<FanEntity> fans, int tick) {
        recentFans.keySet().retainAll(fans);
        fanStructures.keySet().retainAll(fans);
        visibleStructures.keySet().retainAll(fans);
        conversationPartners.entrySet().removeIf(entry -> !fans.contains(entry.getKey())
                || !fans.contains(entry.getValue()) || tick >= entry.getKey().overheadExpiryTick);
        for (FanEntity fan : fans) if (tick >= fan.overheadExpiryTick) fan.overheadText = null;
        speakingTicks.keySet().removeIf(fan -> !fans.contains(fan) || !fan.isVisible());
        reactions.keySet().removeIf(fan -> !fans.contains(fan) || !fan.isVisible());
        if (frequency == ChatFrequency.OFF) return;
        advanceReactions(tick, fans.size());
        advanceConversation(fans, tick);
        if (tick < warmupTick) return;
        List<FanEntity> eligible = new ArrayList<>(fans.size());
        int visible = 0;
        for (FanEntity fan : fans) if (fan.isVisible()) visible++;
        final int crowdSize = visible;
        for (FanEntity fan : fans) {
            if (!fan.isVisible()) continue;
            int due = speakingTicks.computeIfAbsent(fan,
                    ignored -> tick + initialDelay(crowdSize));
            if (fan != firstSpeaker && fan != secondSpeaker && fan != thirdSpeaker && !reactions.containsKey(fan)
                    && tick >= due && tick >= fan.nextChatTick && fan.overheadText == null
                    && (mode != CustomQuoteMode.CUSTOM_ONLY || !custom.isEmpty()))
                eligible.add(fan);
        }
        Collections.shuffle(eligible, random);
        if (mode != CustomQuoteMode.CUSTOM_ONLY && conversation == null
                && tick >= nextConversationTick && !eligible.isEmpty()) {
            FanEntity first = eligible.get(0);
            FanEntity second = null;
            for (FanEntity candidate : fans) {
                if (candidate == first || !candidate.isVisible() || reactions.containsKey(candidate)
                        || candidate.nextChatTick > tick + 30) continue;
                if (first.position != null && candidate.position != null
                        && first.position.distanceTo(candidate.position) <= 6) {
                    second = candidate;
                    break;
                }
            }
            if (second != null) {
                nextConversationTick = tick + 45 + random.nextInt(100);
                conversation = conversations.choose(random, first.personality, context);
                if (conversation != null) {
                    firstSpeaker = first;
                    secondSpeaker = second;
                    if (random.nextInt(3) == 0) {
                        List<FanEntity> bystanders = new ArrayList<>(fans);
                        Collections.shuffle(bystanders, random);
                        for (FanEntity candidate : bystanders) {
                            if (candidate != first && candidate != second && candidate.isVisible()
                                    && !reactions.containsKey(candidate) && candidate.position != null
                                    && candidate.nextChatTick <= tick + 30
                                    && candidate.position.distanceTo(first.position) <= 6
                                    && candidate.position.distanceTo(second.position) <= 6) {
                                thirdSpeaker = candidate;
                                break;
                            }
                        }
                    }
                    conversationContext = context;
                    conversationTurn = 0;
                    say(first, CompanionConversations.line(conversation, 0, random, context, first, second), tick);
                    conversationPartners.put(first, second);
                    scheduleNext(first, tick, visible);
                    scheduleReply(tick);
                    eligible.remove(first);
                    eligible.remove(second);
                    eligible.remove(thirdSpeaker);
                }
            }
        }
        for (FanEntity fan : eligible) {
            say(fan, chooseFor(fan), tick);
            scheduleNext(fan, tick, visible);
        }
    }

    private int initialDelay(int crowdSize) {
        int window = frequency == ChatFrequency.LOW ? Math.max(10, crowdSize * 15)
                : frequency == ChatFrequency.NORMAL ? 18 : 14;
        return 1 + random.nextInt(window);
    }

    private void scheduleNext(FanEntity fan, int tick, int crowdSize) {
        double mean = frequency == ChatFrequency.LOW ? Math.max(5, crowdSize * 7.5)
                : frequency == ChatFrequency.NORMAL ? 4 : 2;
        // Memoryless thinking pauses keep each fan independent; bound exceptionally long draws.
        int pause = 1 + (int) Math.min(mean * 5, -Math.log1p(-random.nextDouble()) * mean);
        speakingTicks.put(fan, Math.max(tick + 1, fan.nextChatTick) + pause);
    }

    private void advanceReactions(int tick, int crowdSize) {
        Iterator<Map.Entry<FanEntity, PendingReaction>> iterator = reactions.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<FanEntity, PendingReaction> entry = iterator.next();
            PendingReaction reaction = entry.getValue();
            if (!reaction.player.equals(context.player) || tick > reaction.dueTick + 5) {
                iterator.remove();
            } else if (tick >= reaction.dueTick) {
                say(entry.getKey(), reaction.text, tick);
                scheduleNext(entry.getKey(), tick, crowdSize);
                iterator.remove();
            }
        }
    }

    private void scheduleReply(int tick) {
        replyTick = tick + 5 + random.nextInt(6);
        replyDeadline = tick + 60;
    }

    private void advanceConversation(List<FanEntity> fans, int tick) {
        if (conversation == null) return;
        if (!fans.contains(firstSpeaker) || !fans.contains(secondSpeaker)
                || !firstSpeaker.isVisible() || !secondSpeaker.isVisible()
                || firstSpeaker.position == null || secondSpeaker.position == null
                || firstSpeaker.position.distanceTo(secondSpeaker.position) > 6
                || (thirdSpeaker != null && (!fans.contains(thirdSpeaker) || !thirdSpeaker.isVisible()
                        || thirdSpeaker.position == null
                        || thirdSpeaker.position.distanceTo(firstSpeaker.position) > 6
                        || thirdSpeaker.position.distanceTo(secondSpeaker.position) > 6))
                || context.activity != conversationContext.activity
                || !context.player.equals(conversationContext.player)
                || context.skill != conversationContext.skill
                || !context.target.equals(conversationContext.target)
                || tick > replyDeadline) {
            cancelConversation();
            return;
        }
        FanEntity speaker = conversationSpeaker(conversationTurn + 1);
        FanEntity partner = conversationSpeaker(conversationTurn);
        if (tick < replyTick || tick < speaker.nextChatTick || speaker.overheadText != null) return;
        conversationTurn++;
        say(speaker, CompanionConversations.line(conversation, conversationTurn, random, context, speaker, partner), tick);
        conversationPartners.put(speaker, partner);
        scheduleNext(speaker, tick, fans.size());
        if (conversationTurn == conversation.length - 3) cancelConversation();
        else scheduleReply(tick);
    }

    private FanEntity conversationSpeaker(int turn) {
        if (turn == conversation.length - 3 && thirdSpeaker != null) return thirdSpeaker;
        return turn % 2 == 0 ? firstSpeaker : secondSpeaker;
    }

    private void cancelConversation() {
        conversation = null;
        conversationContext = null;
        firstSpeaker = secondSpeaker = thirdSpeaker = null;
    }

    /** Only stationary speakers turn; walking and running retain path orientation. */
    public FanEntity conversationPartner(FanEntity fan, int tick) {
        if (tick >= fan.overheadExpiryTick) return null;
        FanEntity partner = conversationPartners.get(fan);
        return partner != null && partner.isVisible() ? partner : null;
    }

    public void setContext(String name, String equipment) {
        setContext(FanChatContext.idle(name, equipment));
    }

    public void setContext(FanChatContext context) {
        if (this.context.activity != context.activity || !this.context.player.equals(context.player)
                || this.context.skill != context.skill || !this.context.target.equals(context.target)) {
            cancelConversation();
            conversationPartners.clear();
        }
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
        String key = SPACES.matcher(PUNCTUATION.matcher(normalized).replaceAll(""))
                .replaceAll(" ").trim();
        return key.isEmpty() ? normalized.trim() : key;
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
        cancelConversation();
        conversationPartners.clear();
        int start = random.nextInt(fans.size());
        for (int i = 0; i < Math.min(2, fans.size()); i++) {
            FanEntity fan = fans.get((start + i) % fans.size());
            if (fan.isVisible()) {
                String text = mode == CustomQuoteMode.CUSTOM_ONLY ? choose(custom) : generate.get();
                if (i == 0) {
                    reactions.remove(fan);
                    say(fan, text, tick);
                    scheduleNext(fan, tick, fans.size());
                } else reactions.put(fan,
                        new PendingReaction(text, context.player, tick + 1 + random.nextInt(5)));
            }
        }
    }

    private void say(FanEntity fan, String quote, int tick) {
        say(fan, new FanUtterance(quote, null), tick);
    }

    private void say(FanEntity fan, FanUtterance utterance, int tick) {
        conversationPartners.remove(fan);
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
        cancelConversation();
        conversations.clear();
        speakingTicks.clear();
        reactions.clear();
        conversationPartners.clear();
        lastQuote = null;
        recentCrowd.clear();
        recentFans.clear();
        recentStructures.clear();
        fanStructures.clear();
        visibleStructures.clear();
        warmupTick = Integer.MAX_VALUE;
    }
}
