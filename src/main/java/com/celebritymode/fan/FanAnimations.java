package com.celebritymode.fan;

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
        if (fan.object == null || fan.state == state) return;
        fan.state = state;
        String weapon = fan.appearance == null ? "" : fan.appearance.weaponName;
        boolean whip = weapon.contains("whip"),
                staff =
                        weapon.contains("staff")
                                || weapon.contains("wand")
                                || weapon.contains("sceptre");
        boolean pole =
                weapon.contains("spear") || weapon.contains("lance") || weapon.contains("halberd");
        int id;
        if (state == FanState.RUNNING)
            id =
                    whip
                            ? AnimationID.SLAYER_ABYSSAL_WHIP_RUN
                            : pole ? AnimationID.HUMAN_HALBERDRUNNING : AnimationID.HUMAN_RUNNING;
        else if (state == FanState.WALKING)
            id =
                    whip
                            ? AnimationID.SLAYER_ABYSSAL_WHIP_WALK
                            : pole ? AnimationID.HUMAN_HALBERDWALK_F : AnimationID.HUMAN_WALK_F;
        else
            id =
                    staff
                            ? AnimationID.HUMAN_STAFFREADY
                            : pole ? AnimationID.HUMAN_ZAMORAKSPEAR_READY : AnimationID.HUMAN_READY;
        play(fan, id, false);
    }

    public void react(FanEntity fan, boolean cheer) {
        if (fan.object == null || fan.state != FanState.IDLE) return;
        fan.state = FanState.REACTION;
        play(fan, cheer ? AnimationID.EMOTE_CHEER : AnimationID.EMOTE_WAVE, true);
    }

    private void play(FanEntity fan, int id, boolean oneShot) {
        Animation resource = resources.computeIfAbsent(id, client::loadAnimation);
        if (resource == null) {
            fan.state = FanState.IDLE;
            return;
        }
        if (fan.animation == null) {
            fan.animation = new AnimationController(client, resource);
            fan.object.setAnimationController(fan.animation);
        } else fan.animation.setAnimation(resource);
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
