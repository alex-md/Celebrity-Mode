package com.celebritymode;

import com.celebritymode.appearance.FanGearTier;
import com.celebritymode.chat.*;
import com.celebritymode.movement.FormationStyle;

import net.runelite.client.config.*;

@ConfigGroup(CelebrityModeConfig.GROUP)
public interface CelebrityModeConfig extends Config {
    String GROUP = "celebritymode";

    @ConfigSection(name = "Crowd", description = "Size and formation", position = 0)
    String crowd = "crowd";

    @ConfigSection(
            name = "Appearance",
            description = "Persistent cosmetic appearances",
            position = 1)
    String appearance = "appearance";

    @ConfigSection(name = "Chatter", description = "Cosmetic overhead text", position = 2)
    String chatter = "chatter";

    @ConfigSection(name = "Behavior", description = "Reactions and diagnostics", position = 3)
    String behavior = "behavior";

    @Range(min = 1, max = 30)
    @ConfigItem(
            keyName = "crowdSize",
            name = "Crowd size",
            description = "Number of simulated fans",
            section = crowd,
            position = 0)
    default int crowdSize() {
        return 8;
    }

    @ConfigItem(
            keyName = "formationStyle",
            name = "Formation",
            description = "How your entourage follows",
            section = crowd,
            position = 1)
    default FormationStyle formationStyle() {
        return FormationStyle.ENTOURAGE;
    }

    @ConfigItem(
            keyName = "respectPersonalSpace",
            name = "Personal space",
            description = "Prefer distinct tiles; avoid your tile while gathering close",
            section = crowd,
            position = 2)
    default boolean respectPersonalSpace() {
        return true;
    }

    @Range(min = 1, max = 4)
    @ConfigItem(
            keyName = "maxSpread",
            name = "Maximum spread",
            description = "Lateral spread in tiles",
            section = crowd,
            position = 3)
    default int maxSpread() {
        return 2;
    }

    @ConfigItem(
            keyName = "crowdGearTier",
            name = "Gear theme",
            description = "Randomized player outfits",
            section = appearance,
            position = 0)
    default FanGearTier crowdGearTier() {
        return FanGearTier.MIXED;
    }

    @ConfigItem(
            keyName = "showFanNames",
            name = "Show fan names",
            description = "Draw simulated usernames above fans",
            section = appearance,
            position = 1)
    default boolean showFanNames() {
        return false;
    }

    @ConfigItem(
            keyName = "chatFrequency",
            name = "Chat frequency",
            description = "Normal is busy; Spam keeps the crowd constantly shouting",
            section = chatter,
            position = 0)
    default ChatFrequency chatFrequency() {
        return ChatFrequency.SPAM;
    }

    @ConfigItem(
            keyName = "customQuotes",
            name = "Custom quotes",
            description = "One quote per line, or comma separated",
            section = chatter,
            position = 1)
    default String customQuotes() {
        return "";
    }

    @ConfigItem(
            keyName = "customQuoteMode",
            name = "Quote source",
            description = "Built-in, custom, or both; empty custom-only is silent",
            section = chatter,
            position = 2)
    default CustomQuoteMode customQuoteMode() {
        return CustomQuoteMode.MIXED;
    }

    @ConfigItem(
            keyName = "reactToEvents",
            name = "React to events",
            description = "Idle emotes, recognition, level and death reactions",
            section = behavior,
            position = 0)
    default boolean reactToEvents() {
        return true;
    }

    @ConfigItem(
            keyName = "debugMode",
            name = "Debug overlay",
            description = "Show trail, target tiles and follower state",
            section = behavior,
            position = 1)
    default boolean debugMode() {
        return false;
    }
}
