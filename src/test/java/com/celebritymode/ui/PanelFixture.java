package com.celebritymode.ui;

import com.celebritymode.CelebrityModeConfig;
import com.celebritymode.chat.*;
import com.celebritymode.appearance.FanGearTier;
import com.celebritymode.fan.ArrivalPace;
import com.celebritymode.movement.FormationStyle;
import com.celebritymode.pm.TrafficIntensity;
import java.awt.*;
import java.util.*;
import javax.swing.*;

final class PanelFixture implements CelebrityModeConfig {
    final Map<String, Object> values = new HashMap<>();
    int writes;
    void write(String key, Object value) { values.put(key, value); writes++; }
    public int crowdSize() { return (Integer) values.getOrDefault("crowdSize", 8); }
    public boolean showCrowd() { return (Boolean) values.getOrDefault("showCrowd", true); }
    public ArrivalPace arrivalPace() { return (ArrivalPace) values.getOrDefault("arrivalPace", ArrivalPace.GRADUAL); }
    public FormationStyle formationStyle() { return (FormationStyle) values.getOrDefault("formationStyle", FormationStyle.ENTOURAGE); }
    public int maxSpread() { return (Integer) values.getOrDefault("maxSpread", 2); }
    public boolean respectPersonalSpace() { return (Boolean) values.getOrDefault("respectPersonalSpace", true); }
    public FanGearTier crowdGearTier() { return (FanGearTier) values.getOrDefault("crowdGearTier", FanGearTier.MIXED); }
    public boolean showFanNames() { return (Boolean) values.getOrDefault("showFanNames", false); }
    public ChatFrequency chatFrequency() { return (ChatFrequency) values.getOrDefault("chatFrequency", ChatFrequency.SPAM); }
    public CustomQuoteMode customQuoteMode() { return (CustomQuoteMode) values.getOrDefault("customQuoteMode", CustomQuoteMode.MIXED); }
    public String customQuotes() { return (String) values.getOrDefault("customQuotes", ""); }
    public boolean reactToEvents() { return (Boolean) values.getOrDefault("reactToEvents", true); }
    public boolean enableFanPms() { return (Boolean) values.getOrDefault("enableFanPms", false); }
    public TrafficIntensity pmTrafficIntensity() { return (TrafficIntensity) values.getOrDefault("pmTrafficIntensity", TrafficIntensity.RELAXED); }
    public boolean pmNotificationSound() { return (Boolean) values.getOrDefault("pmNotificationSound", false); }
    public boolean debugMode() { return (Boolean) values.getOrDefault("debugMode", false); }

    static Component find(Container parent, String name) {
        for (Component child : parent.getComponents()) {
            if (name.equals(child.getName())) return child;
            if (child instanceof Container) {
                Component found = find((Container) child, name);
                if (found != null) return found;
            }
        }
        return null;
    }

    static JButton button(Container parent, String text) {
        for (Component child : parent.getComponents()) {
            if (child instanceof JButton && text.equals(((JButton) child).getText())) return (JButton) child;
            if (child instanceof Container) {
                JButton found = button((Container) child, text);
                if (found != null) return found;
            }
        }
        return null;
    }
}
