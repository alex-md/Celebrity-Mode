package com.celebritymode.chat;

import net.runelite.api.Skill;

/** Observations about the local character, never guesses about loot or achievements. */
public final class FanChatContext {
    public enum Activity {
        IDLE,
        WALKING,
        RUNNING,
        BANKING,
        COMBAT,
        SKILLING
    }

    public final String player, gear, target;
    public final Activity activity;
    public final Skill skill;
    public final int level, gainedXp;
    public final boolean lowHealth, gearChanged;

    public FanChatContext(
            String player,
            String gear,
            String target,
            Activity activity,
            Skill skill,
            int level,
            int gainedXp,
            boolean lowHealth,
            boolean gearChanged) {
        this.player = clean(player);
        this.gear = clean(gear);
        this.target = clean(target);
        this.activity = activity;
        this.skill = skill;
        this.level = level;
        this.gainedXp = gainedXp;
        this.lowHealth = lowHealth;
        this.gearChanged = gearChanged;
    }

    public static FanChatContext idle(String name, String gear) {
        return new FanChatContext(name, gear, "", Activity.IDLE, null, 0, 0, false, false);
    }

    public static String clean(String value) {
        return value == null ? "" : value.replaceAll("<[^>]*>", "").replace('\u00a0', ' ').trim();
    }
}
