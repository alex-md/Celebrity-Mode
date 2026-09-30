package com.celebritymode.chat;

import static org.junit.Assert.*;

import com.celebritymode.TestScene;

import net.runelite.api.*;

import org.junit.Test;

public class FanActivityTrackerTest {
    @Test
    public void loginXpIsNotAnActivityButRealGainsAreAndExpire() {
        TestScene scene = new TestScene();
        scene.skillXp.put(Skill.WOODCUTTING, 40000);
        FanActivityTracker tracker = new FanActivityTracker();
        tracker.experience(Skill.WOODCUTTING, 40000, 1);
        assertEquals(FanChatContext.Activity.IDLE, observe(tracker, scene, 1).activity);
        tracker.experience(Skill.WOODCUTTING, 40075, 2);
        FanChatContext context = observe(tracker, scene, 3);
        assertEquals(FanChatContext.Activity.SKILLING, context.activity);
        assertEquals(Skill.WOODCUTTING, context.skill);
        assertEquals(75, context.gainedXp);
        assertEquals(70, context.level);
        assertEquals(
                FanChatContext.Activity.RUNNING,
                tracker.observe(scene.client, scene.player, "", true, true, 4).activity);
        assertEquals(FanChatContext.Activity.IDLE, observe(tracker, scene, 13).activity);
        assertNull(observe(tracker, scene, 13).skill);
        tracker.clear();
        assertEquals(FanChatContext.Activity.IDLE, observe(tracker, scene, 1).activity);
    }

    @Test
    public void bankMovementGearAndNoncombatMagicUseObservedState() {
        TestScene scene = new TestScene();
        FanActivityTracker tracker = new FanActivityTracker();
        tracker.observe(scene.client, scene.player, "bow", true, true, 1);
        assertEquals(
                FanChatContext.Activity.WALKING,
                tracker.observe(scene.client, scene.player, "bow", true, false, 2).activity);
        FanChatContext changed =
                tracker.observe(scene.client, scene.player, "staff", true, true, 3);
        assertEquals(FanChatContext.Activity.RUNNING, changed.activity);
        assertTrue(changed.gearChanged);
        assertFalse(
                tracker.observe(scene.client, scene.player, "staff", false, false, 12).gearChanged);
        tracker.experience(Skill.MAGIC, 65, 12);
        assertEquals(FanChatContext.Activity.SKILLING, observe(tracker, scene, 13).activity);
        scene.bankOpen = true;
        assertEquals(FanChatContext.Activity.BANKING, observe(tracker, scene, 13).activity);
    }

    @Test
    public void TalkingToNpcIsNotFightingAndLowHealthNeedsCombat() {
        TestScene scene = new TestScene();
        scene.interacting =
                TestScene.proxy(
                        NPC.class,
                        (method, args) -> {
                            if (method.equals("getName")) return "<col=ffff00>Goblin</col>";
                            if (method.equals("getCombatLevel")) return 2;
                            if (method.equals("getHealthRatio")) return -1;
                            return null;
                        });
        scene.hp = 10;
        FanActivityTracker tracker = new FanActivityTracker();
        assertEquals(FanChatContext.Activity.IDLE, observe(tracker, scene, 1).activity);
        assertFalse(observe(tracker, scene, 1).lowHealth);
        tracker.menu("Attack", 2);
        FanChatContext fighting = observe(tracker, scene, 2);
        assertEquals(FanChatContext.Activity.COMBAT, fighting.activity);
        assertEquals("Goblin", fighting.target);
        assertTrue(fighting.lowHealth);
        scene.interacting = null;
        assertFalse(observe(tracker, scene, 3).lowHealth);
        assertEquals("", observe(tracker, scene, 3).target);
    }

    private static FanChatContext observe(FanActivityTracker tracker, TestScene scene, int tick) {
        return tracker.observe(scene.client, scene.player, "", false, false, tick);
    }
}
