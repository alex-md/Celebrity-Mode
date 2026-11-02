package com.celebritymode.fan;

import com.celebritymode.TestScene;
import com.celebritymode.appearance.*;
import net.runelite.api.AnimationController;
import net.runelite.api.gameval.AnimationID;
import org.junit.Test;
import static org.junit.Assert.*;

public class FanAnimationsTest {
    @Test
    public void weaponChangesSelectConsistentPosesWithoutRestartingUnchangedSequences() {
        TestScene scene = new TestScene();
        scene.config.arrival = ArrivalPace.INSTANT;
        scene.tick();
        FanEntity fan = scene.manager.getFans().get(0);
        fan.orientation = fan.targetOrientation = 1536;
        Object[][] examples = {
            {"Abyssal whip", AnimationID.HUMAN_READY, AnimationID.SLAYER_ABYSSAL_WHIP_WALK, AnimationID.SLAYER_ABYSSAL_WHIP_RUN},
            {"Trident of the seas", AnimationID.HUMAN_STAFFREADY, AnimationID.HUMAN_WALK_F, AnimationID.HUMAN_RUNNING},
            {"Tumeken's shadow", AnimationID.HUMAN_STAFFREADY, AnimationID.HUMAN_WALK_F, AnimationID.HUMAN_RUNNING},
            {"Zamorakian spear", AnimationID.HUMAN_ZAMORAKSPEAR_READY, AnimationID.HUMAN_ZAMORAKSPEAR_WALK_F, AnimationID.HUMAN_ZAMORAKSPEAR_RUN},
            {"Armadyl crossbow", AnimationID.XBOWS_HUMAN_READY, AnimationID.XBOWS_HUMAN_WALK_F, AnimationID.XBOWS_HUMAN_RUN},
            {"Saradomin godsword", AnimationID.DH_SWORD_UPDATE_READY, AnimationID.DH_SWORD_UPDATE_WALK, AnimationID.DH_SWORD_UPDATE_RUN},
            {"Scythe of vitur", AnimationID.SCYTHE_OF_VITUR_READY, AnimationID.HUMAN_WALK_F, AnimationID.HUMAN_RUNNING},
            {"Elder maul", AnimationID.HUMAN_ELDER_MAUL_READY, AnimationID.HUMAN_ELDER_MAUL_WALK, AnimationID.HUMAN_ELDER_MAUL_RUN},
            {"Soulreaper axe", AnimationID.HUMAN_2H_AXE_READY, AnimationID.HUMAN_2H_AXE_WALK, AnimationID.HUMAN_2H_AXE_RUN},
            {"Dragon felling axe", AnimationID.FORESTRY_2H_AXE_READY, AnimationID.FORESTRY_2H_AXE_WALK, AnimationID.FORESTRY_2H_AXE_RUN}
        };
        for (Object[] example : examples) {
            String weapon = (String) example[0];
            fan.appearance = new FanAppearance(new int[7], new int[0], new short[5], FanGearTier.MIXED, weapon);
            FanState[] states = {FanState.IDLE, FanState.WALKING, FanState.RUNNING};
            for (int i = 0; i < states.length; i++) {
                scene.animations.setState(fan, states[i]);
                assertEquals(weapon, (int) example[i + 1], fan.animationId);
                assertEquals(fan.animationId, fan.animation.getAnimation().getId());
                AnimationController controller = fan.animation;
                controller.setFrame(1);
                scene.animations.updatePose(fan);
                scene.animations.setState(fan, states[i]);
                assertSame(controller, fan.animation);
                assertEquals("Unchanged pose keeps its frame: " + weapon, 1, controller.getFrame());
            }
        }
        scene.manager.cleanup();
    }
}
