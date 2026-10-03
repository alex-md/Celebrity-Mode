package com.celebritymode.ui;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import javax.imageio.ImageIO;

/** The supplied crown artwork, shared by the toolbar and panel header. */
public final class CelebrityModeIcon {
    private static final BufferedImage SOURCE = load();

    private CelebrityModeIcon() {}

    private static BufferedImage load() {
        try (InputStream stream = CelebrityModeIcon.class.getResourceAsStream("icon.png")) {
            if (stream == null) throw new IllegalStateException("Missing Fame Simulator icon resource");
            BufferedImage image = ImageIO.read(stream);
            if (image == null) throw new IllegalStateException("Invalid Fame Simulator icon resource");
            return image;
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load Fame Simulator icon", ex);
        }
    }

    public static BufferedImage create(int size) {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(SOURCE, 0, 0, size, size, null);
        } finally {
            g.dispose();
        }
        return image;
    }
}
