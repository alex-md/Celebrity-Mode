package com.celebritymode;

import com.celebritymode.appearance.FanGearTier;
import com.celebritymode.chat.*;
import com.celebritymode.fan.ArrivalPace;
import com.celebritymode.fan.CrowdArrival;
import com.celebritymode.movement.FormationStyle;
import com.celebritymode.pm.TrafficIntensity;

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

    @ConfigSection(name = "Chatter", description = "Custom lines and activity-aware overhead dialogue", position = 2)
    String chatter = "chatter";

    @ConfigSection(name = "Behavior", description = "Reactions and diagnostics", position = 3)
    String behavior = "behavior";

    @ConfigSection(name = "Fan inbox", description = "Simulated incoming private messages", position = 4)
    String fanInbox = "fanInbox";

    @ConfigItem(
            keyName = "enableFanPms",
            name = "Enable fan PMs",
            description = "Simulate incoming fan messages in native private chat; independent of the overhead crowd",
            section = fanInbox,
            position = 0)
    default boolean enableFanPms() { return false; }

    @ConfigItem(
            keyName = "pmTrafficIntensity",
            name = "Traffic intensity",
            description = "Target averages: Relaxed 20, Streamer 80, Global Celebrity 180, Peak World Record 360 PMs/min",
            section = fanInbox,
            position = 1)
    default TrafficIntensity pmTrafficIntensity() { return TrafficIntensity.RELAXED; }

    @ConfigItem(
            keyName = "pmNotificationSound",
            name = "Play native notification sound",
            description = "Native UI sound, at most once every two seconds; respects sound-effect volume",
            section = fanInbox,
            position = 2)
    default boolean pmNotificationSound() { return false; }

    @Range(min = 1, max = CrowdArrival.MAX_FANS)
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
            keyName = "showCrowd",
            name = "Show crowd",
            description = "Hide the fans between takes while keeping your settings",
            section = crowd,
            position = 4)
    default boolean showCrowd() {
        return true;
    }

    @ConfigItem(
            keyName = "arrivalPace",
            name = "Arrival pace",
            description = "Build the audience gradually, quickly, or all at once; applies to new arrivals",
            section = crowd,
            position = 5)
    default ArrivalPace arrivalPace() {
        return ArrivalPace.GRADUAL;
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

    @Range(min = 1, max = 8)
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
            description = "Your own dialogue, one quote per line (or comma separated). Supports {player}, {gear}, {target}, {skill}, {level}",
            section = chatter,
            position = 1)
    default String customQuotes() {
        return "";
    }

    @ConfigItem(
            keyName = "customQuoteMode",
            name = "Quote source",
            description = "Built-in activity-aware chatter, your custom lines, or both; empty custom-only is silent",
            section = chatter,
            position = 2)
    default CustomQuoteMode customQuoteMode() {
        return CustomQuoteMode.MIXED;
    }

    @ConfigItem(
            keyName = "reactToEvents",
            name = "React to events",
            description = "Occasional idle emotes plus recognition, level and death reactions; activity chatter uses Quote source",
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
