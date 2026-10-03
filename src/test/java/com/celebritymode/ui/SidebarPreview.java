package com.celebritymode.ui;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.*;
import net.runelite.client.ui.laf.RuneLiteLAF;

/** Renders the actual Swing panel at RuneLite's sidebar width without logging into the game. */
public final class SidebarPreview {
    private SidebarPreview() {}

    public static void main(String[] args) throws Exception {
        UIManager.setLookAndFeel(new RuneLiteLAF());
        SwingUtilities.invokeAndWait(() -> {
            try {
                PanelFixture config = new PanelFixture();
                config.values.put("customQuotes", "{player} can we get a bank tour\nall this gear just to forget a teleport\n{target} can we get an autograph after this");
                CelebrityModePanel panel = new CelebrityModePanel(config, config::write);
                String[] pages = {"Crowd", "Dialogue", "Extras"};
                BufferedImage[] images = new BufferedImage[pages.length];
                int height = 0;
                for (int i = 0; i < pages.length; i++) {
                    PanelFixture.button(panel, pages[i]).doClick();
                    panel.setSize(225, panel.getPreferredSize().height);
                    layout(panel);
                    images[i] = new BufferedImage(450, panel.getHeight() * 2, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = images[i].createGraphics();
                    g.scale(2, 2);
                    panel.printAll(g);
                    g.dispose();
                    height = Math.max(height, images[i].getHeight());
                }
                BufferedImage preview = new BufferedImage(1410, height + 40, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = preview.createGraphics();
                g.setColor(new Color(23, 26, 30));
                g.fillRect(0, 0, preview.getWidth(), preview.getHeight());
                for (int i = 0; i < images.length; i++) g.drawImage(images[i], 10 + i * 470, 20, null);
                g.dispose();
                File output = new File("build/sidebar-preview.png");
                ImageIO.write(preview, "png", output);
                ImageIO.write(CelebrityModeIcon.create(64), "png", new File("build/sidebar-icon.png"));
                System.out.println(output.getAbsolutePath());
            } catch (Exception ex) {
                throw new IllegalStateException(ex);
            }
        });
    }

    private static void layout(Container container) {
        container.doLayout();
        for (Component child : container.getComponents()) if (child instanceof Container) layout((Container) child);
    }
}
