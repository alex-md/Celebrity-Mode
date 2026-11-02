package com.celebritymode.fan;

import com.celebritymode.util.OrientationUtil;

import net.runelite.api.*;
import net.runelite.api.gameval.AnimationID;

import java.util.*;

import javax.inject.*;

/** Named IDs from the current cache's generated gameval definitions. */
@Singleton
public final class FanAnimations {
    private final Client client;
    private final Map<Integer, Animation> resources = new HashMap<>();

    @Inject
    public FanAnimations(Client client) {
        this.client = client;
    }

    public void setState(FanEntity fan, FanState state) {
        if (fan.object == null) return;
        fan.state = state;
        updatePose(fan);
    }

    /** A pose describes the visible movement and turning, rather than its eventual destination. */
    public void updatePose(FanEntity fan) {
        if (fan.object == null || fan.state == FanState.REACTION) return;
        Profile profile = profile(fan.appearance == null ? "" : fan.appearance.weaponName);
        int delta = OrientationUtil.delta(fan.orientation, fan.targetOrientation);
        int id;
        if (fan.state == FanState.WALKING || fan.state == FanState.RUNNING) {
            if (Math.abs(delta) >= 768) id = profile.back;
            else if (delta > 256) id = profile.right;
            else if (delta < -256) id = profile.left;
            else id = fan.state == FanState.RUNNING ? profile.run : profile.walk;
        } else id = delta == 0 ? profile.ready : profile.turn;
        play(fan, id, false);
    }

    private enum Profile {
        DEFAULT(AnimationID.HUMAN_READY, AnimationID.HUMAN_WALK_F, AnimationID.HUMAN_RUNNING),
        WHIP(AnimationID.HUMAN_READY, AnimationID.SLAYER_ABYSSAL_WHIP_WALK, AnimationID.SLAYER_ABYSSAL_WHIP_RUN),
        STAFF(AnimationID.HUMAN_STAFFREADY, AnimationID.HUMAN_WALK_F, AnimationID.HUMAN_RUNNING),
        SPEAR(AnimationID.HUMAN_ZAMORAKSPEAR_READY, AnimationID.HUMAN_ZAMORAKSPEAR_WALK_F,
                AnimationID.HUMAN_ZAMORAKSPEAR_RUN, AnimationID.HUMAN_ZAMORAKSPEAR_WALK_B,
                AnimationID.HUMAN_ZAMORAKSPEAR_WALKLEFT, AnimationID.HUMAN_ZAMORAKSPEAR_WALKRIGHT,
                AnimationID.HUMAN_ZAMORAKSPEAR_TURNONSPOT),
        CROSSBOW(AnimationID.XBOWS_HUMAN_READY, AnimationID.XBOWS_HUMAN_WALK_F,
                AnimationID.XBOWS_HUMAN_RUN, AnimationID.XBOWS_HUMAN_WALK_B,
                AnimationID.HUMAN_WALK_L, AnimationID.HUMAN_WALK_R, AnimationID.HUMAN_TURNONSPOT),
        TWO_HANDED_SWORD(AnimationID.DH_SWORD_UPDATE_READY, AnimationID.DH_SWORD_UPDATE_WALK,
                AnimationID.DH_SWORD_UPDATE_RUN, AnimationID.HUMAN_WALK_B,
                AnimationID.DH_SWORD_UPDATE_WALK_LEFT, AnimationID.DH_SWORD_UPDATE_WALK_RIGHT,
                AnimationID.DH_SWORD_UPDATE_TURNONSPOT),
        SCYTHE(AnimationID.SCYTHE_OF_VITUR_READY, AnimationID.HUMAN_WALK_F, AnimationID.HUMAN_RUNNING),
        MAUL(AnimationID.HUMAN_ELDER_MAUL_READY, AnimationID.HUMAN_ELDER_MAUL_WALK, AnimationID.HUMAN_ELDER_MAUL_RUN),
        SOULREAPER(AnimationID.HUMAN_2H_AXE_READY, AnimationID.HUMAN_2H_AXE_WALK, AnimationID.HUMAN_2H_AXE_RUN),
        FELLING_AXE(AnimationID.FORESTRY_2H_AXE_READY, AnimationID.FORESTRY_2H_AXE_WALK, AnimationID.FORESTRY_2H_AXE_RUN);

        final int ready, walk, run, back, left, right, turn;
        Profile(int ready, int walk, int run) {
            this(ready, walk, run, AnimationID.HUMAN_WALK_B, AnimationID.HUMAN_WALK_L,
                    AnimationID.HUMAN_WALK_R, AnimationID.HUMAN_TURNONSPOT);
        }
        Profile(int ready, int walk, int run, int back, int left, int right, int turn) {
            this.ready = ready; this.walk = walk; this.run = run; this.back = back;
            this.left = left; this.right = right; this.turn = turn;
        }
    }

    private static Profile profile(String weapon) {
        if (weapon.contains("whip")) return Profile.WHIP;
        if (weapon.contains("scythe of vitur")) return Profile.SCYTHE;
        if (weapon.contains("elder maul")) return Profile.MAUL;
        if (weapon.contains("soulreaper")) return Profile.SOULREAPER;
        if (weapon.contains("felling axe")) return Profile.FELLING_AXE;
        if (weapon.contains("2h sword") || weapon.contains("godsword")) return Profile.TWO_HANDED_SWORD;
        if (weapon.contains("spear") || weapon.contains("lance") || weapon.contains("halberd")) return Profile.SPEAR;
        if (weapon.contains("crossbow")) return Profile.CROSSBOW;
        if (weapon.contains("staff") || weapon.contains("wand") || weapon.contains("sceptre")
                || weapon.contains("trident") || weapon.contains("sanguinesti") || weapon.contains("tumeken"))
            return Profile.STAFF;
        return Profile.DEFAULT;
    }

    public void react(FanEntity fan, boolean cheer) {
        if (fan.object == null || fan.state != FanState.IDLE || fan.movementPath != null
                || fan.orientation != fan.targetOrientation) return;
        fan.state = FanState.REACTION;
        play(fan, cheer ? AnimationID.EMOTE_CHEER : AnimationID.EMOTE_WAVE, true);
    }

    private void play(FanEntity fan, int id, boolean oneShot) {
        if (!oneShot && fan.animation != null && fan.animationId == id) return;
        Animation resource = resources.computeIfAbsent(id, client::loadAnimation);
        if (resource == null) {
            fan.state = FanState.IDLE;
            return;
        }
        if (fan.animation == null) {
            fan.animation = new AnimationController(client, resource);
            fan.object.setAnimationController(fan.animation);
        } else fan.animation.setAnimation(resource);
        fan.animationId = id;
        fan.animation.setOnFinished(
                oneShot
                        ? ac -> {
                            if (fan.state == FanState.REACTION && fan.object != null)
                                setState(fan, FanState.IDLE);
                        }
                        : AnimationController::loop);
    }

    public void clear() {
        resources.clear();
    }
}
