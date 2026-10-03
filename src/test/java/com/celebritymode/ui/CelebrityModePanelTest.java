package com.celebritymode.ui;

import static org.junit.Assert.*;
import com.celebritymode.chat.ChatFrequency;
import org.junit.Test;
import javax.swing.*;

public class CelebrityModePanelTest {
    @Test
    public void crowdControlsPersistAndRefreshWithoutFeedbackWrites() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PanelFixture config = new PanelFixture();
            CelebrityModePanel panel = new CelebrityModePanel(config, config::write);
            assertEquals(0, config.writes);
            JSlider slider = (JSlider) PanelFixture.find(panel, "crowdSizeSlider");
            slider.setValueIsAdjusting(true);
            slider.setValue(72);
            assertEquals("Dragging doesn't resize repeatedly", 0, config.writes);
            slider.setValueIsAdjusting(false);
            assertEquals(72, config.crowdSize());
            assertEquals(1, config.writes);
            JSpinner count = (JSpinner) PanelFixture.find(panel, "crowdSize");
            count.setValue(100);
            assertEquals(100, config.crowdSize());
            config.values.put("crowdSize", 42);
            int writes = config.writes;
            panel.refresh("crowdSize");
            assertEquals(42, count.getValue());
            assertEquals(writes, config.writes);
            JCheckBox show = (JCheckBox) PanelFixture.find(panel, "showCrowd");
            show.doClick();
            assertFalse(config.showCrowd());
            PanelFixture.button(panel, "Small audience").doClick();
            assertEquals(8, config.crowdSize());
            assertEquals(ChatFrequency.LOW, config.chatFrequency());
            assertFalse("Presets don't unhide a paused scene", config.showCrowd());
        });
    }

    @Test
    public void inboxControlsPersistIndependentlyAndKeepQuoteDrafts() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PanelFixture config = new PanelFixture();
            config.values.put("showCrowd", false);
            config.values.put("chatFrequency", ChatFrequency.OFF);
            CelebrityModePanel panel = new CelebrityModePanel(config, config::write);
            PanelFixture.button(panel, "Dialogue").doClick();
            JTextArea text = (JTextArea) PanelFixture.find(panel, "customQuotes");
            text.setText("draft for the next take");
            JCheckBox enabled = (JCheckBox) PanelFixture.find(panel, "enableFanPms");
            assertFalse(enabled.isSelected());
            enabled.doClick();
            assertTrue(config.enableFanPms());
            JComboBox<?> intensity = (JComboBox<?>) PanelFixture.find(panel, "pmTrafficIntensity");
            intensity.setSelectedItem(com.celebritymode.pm.TrafficIntensity.PEAK_WORLD_RECORD);
            assertEquals(com.celebritymode.pm.TrafficIntensity.PEAK_WORLD_RECORD, config.pmTrafficIntensity());
            ((JCheckBox) PanelFixture.find(panel, "pmNotificationSound")).doClick();
            assertTrue(config.pmNotificationSound());
            assertEquals("draft for the next take", text.getText());
            assertFalse(config.showCrowd());
            assertEquals(ChatFrequency.OFF, config.chatFrequency());
            config.values.put("enableFanPms", false);
            panel.refresh("enableFanPms");
            assertFalse(enabled.isSelected());
        });
    }

    @Test
    public void quoteDraftsSurviveOtherEditsAndExternalChanges() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            PanelFixture config = new PanelFixture();
            config.values.put("customQuotes", "original");
            CelebrityModePanel panel = new CelebrityModePanel(config, config::write);
            PanelFixture.button(panel, "Dialogue").doClick();
            JTextArea text = (JTextArea) PanelFixture.find(panel, "customQuotes");
            text.setText("my next video bit");
            assertEquals("original", config.customQuotes());
            panel.refresh("chatFrequency");
            assertEquals("my next video bit", text.getText());
            config.values.put("customQuotes", "external edit");
            panel.refresh("customQuotes");
            assertEquals("my next video bit", text.getText());
            PanelFixture.button(panel, "Revert").doClick();
            assertEquals("external edit", text.getText());
            config.values.put("customQuotes", "another external edit");
            panel.refresh("customQuotes");
            assertEquals("Clean editors follow config changes", "another external edit", text.getText());
            text.setText("{player} nice {gear}\nhello youtube");
            PanelFixture.button(panel, "Save lines").doClick();
            assertEquals(text.getText(), config.customQuotes());
            assertFalse(PanelFixture.button(panel, "Save lines").isEnabled());
            text.setCaretPosition(text.getText().length());
            PanelFixture.button(panel, "{target}").doClick();
            assertTrue(text.getText().endsWith("{target}"));
            assertTrue(PanelFixture.button(panel, "Save lines").isEnabled());
        });
    }
}
