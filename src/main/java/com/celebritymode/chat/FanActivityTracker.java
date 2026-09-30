package com.celebritymode.chat;

import net.runelite.api.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;

import java.util.*;

/** Client-thread-only observations. Login snapshots establish baselines, not fake XP gains. */
// RuneLite retains the deprecated OVERALL pseudo-skill in values(); exclude it from XP snapshots.
@SuppressWarnings("deprecation")
public final class FanActivityTracker {
    private final EnumMap<Skill, Integer> experience = new EnumMap<>(Skill.class);
    private Skill recentSkill;
    private int xpTick = Integer.MIN_VALUE,
            attackTick = Integer.MIN_VALUE,
            gearTick = Integer.MIN_VALUE;
    private int gain;
    private String previousGear;

    public void experience(Skill skill, int xp, int tick) {
        if (skill == Skill.OVERALL) return;
        Integer previous = experience.put(skill, xp);
        if (previous == null || xp <= previous || skill == Skill.HITPOINTS) return;
        recentSkill = skill;
        gain = xp - previous;
        xpTick = tick;
    }

    public void menu(String option, int tick) {
        if ("Attack".equalsIgnoreCase(FanChatContext.clean(option))) attackTick = tick;
    }

    public FanChatContext observe(
            Client client, Player player, String gear, boolean moved, boolean running, int tick) {
        for (Skill skill : Skill.values())
            if (skill != Skill.OVERALL)
                experience.putIfAbsent(skill, client.getSkillExperience(skill));
        String cleanGear = FanChatContext.clean(gear);
        if (previousGear != null && !previousGear.equals(cleanGear)) gearTick = tick;
        previousGear = cleanGear;
        boolean recentXp = (long) tick - xpTick <= 10;
        Skill skill = recentXp ? recentSkill : null;
        Actor target = player.getInteracting();
        boolean fighting =
                target != null
                        && ((long) tick - attackTick <= 8
                                || recentXp && combat(skill)
                                || target.getCombatLevel() > 0
                                        && target.getHealthRatio() >= 0
                                        && player.getAnimation() >= 0);
        Widget bank = client.getWidget(InterfaceID.Bankmain.UNIVERSE);
        FanChatContext.Activity activity =
                bank != null && !bank.isHidden()
                        ? FanChatContext.Activity.BANKING
                        : fighting
                                ? FanChatContext.Activity.COMBAT
                                : recentXp
                                                && (!moved || skill == Skill.AGILITY)
                                                && (!combat(skill) || target == null)
                                        ? FanChatContext.Activity.SKILLING
                                        : moved
                                                ? running
                                                        ? FanChatContext.Activity.RUNNING
                                                        : FanChatContext.Activity.WALKING
                                                : FanChatContext.Activity.IDLE;
        int realHp = client.getRealSkillLevel(Skill.HITPOINTS);
        boolean lowHealth =
                fighting
                        && realHp > 0
                        && client.getBoostedSkillLevel(Skill.HITPOINTS) * 100 / realHp <= 30;
        return new FanChatContext(
                player.getName(),
                cleanGear,
                fighting ? target.getName() : "",
                activity,
                skill,
                skill == null ? 0 : client.getRealSkillLevel(skill),
                recentXp ? gain : 0,
                lowHealth,
                (long) tick - gearTick <= 8 && !cleanGear.isEmpty());
    }

    private static boolean combat(Skill skill) {
        return skill == Skill.ATTACK
                || skill == Skill.STRENGTH
                || skill == Skill.DEFENCE
                || skill == Skill.RANGED
                || skill == Skill.MAGIC
                || skill == Skill.HITPOINTS;
    }

    public void clear() {
        experience.clear();
        recentSkill = null;
        gain = 0;
        previousGear = null;
        xpTick = attackTick = gearTick = Integer.MIN_VALUE;
    }
}
