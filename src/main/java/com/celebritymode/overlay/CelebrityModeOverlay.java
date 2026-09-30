package com.celebritymode.overlay;

import com.celebritymode.CelebrityModeConfig;
import com.celebritymode.fan.*;

import net.runelite.api.*;
import net.runelite.api.coords.*;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.*;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

import javax.inject.*;

@Singleton
public final class CelebrityModeOverlay extends Overlay {
    private final Client client;
    private final CelebrityModeConfig config;
    private final FanManager manager;

    @Inject
    public CelebrityModeOverlay(Client client, CelebrityModeConfig config, FanManager manager) {
        this.client = client;
        this.config = config;
        this.manager = manager;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        WorldView view = manager.getView();
        if (view == null || client.getGameState() != GameState.LOGGED_IN) return null;
        Graphics2D g = (Graphics2D) graphics.create();
        try {
            g.setFont(FontManager.getRunescapeBoldFont());
            FontMetrics metrics = g.getFontMetrics();
            List<Rectangle> placed = new ArrayList<>(30);
            Rectangle viewport =
                    new Rectangle(
                            client.getViewportXOffset(),
                            client.getViewportYOffset(),
                            client.getViewportWidth(),
                            client.getViewportHeight());
            g.clip(viewport);
            if (config.debugMode())
                for (WorldPoint point : manager.getBreadcrumbs())
                    tile(g, view, point, new Color(0, 200, 255, 90));
            for (FanEntity fan : manager.getFans()) {
                LocalPoint location = fan.getRenderLocation();
                if (!fan.isVisible() || location.getWorldView() != view.getId()) continue;
                net.runelite.api.Point p =
                        Perspective.localToCanvas(client, location, view.getPlane(), 210);
                if (p == null || !viewport.contains(p.getX(), p.getY())) continue;
                if (config.debugMode()) {
                    tile(g, view, fan.target, new Color(255, 100, 0, 120));
                    draw(
                            g,
                            metrics,
                            p.getX(),
                            p.getY() + 25,
                            fan.id
                                    + " / "
                                    + fan.formationSlot
                                    + " "
                                    + fan.state
                                    + (fan.target == null
                                            ? ""
                                            : " d=" + fan.position.distanceTo(fan.target)),
                            Color.CYAN);
                }
                int baseline = p.getY();
                if (fan.overheadText != null) {
                    String text = fan.overheadText;
                    Rectangle box =
                            new Rectangle(
                                    p.getX() - metrics.stringWidth(text) / 2,
                                    baseline - metrics.getAscent(),
                                    metrics.stringWidth(text),
                                    metrics.getHeight());
                    // At most one pass per previously placed label, with a restart when pushed
                    // upward.
                    for (int attempts = 0; attempts < placed.size() + 1; attempts++) {
                        boolean moved = false;
                        for (Rectangle other : placed)
                            if (box.intersects(other)) {
                                box.y = other.y - box.height - 2;
                                moved = true;
                                break;
                            }
                        if (!moved) break;
                    }
                    baseline = box.y + metrics.getAscent();
                    if (viewport.intersects(box)) {
                        draw(g, metrics, p.getX(), baseline, text, Color.YELLOW);
                        placed.add(box);
                    }
                }
                if (config.showFanNames())
                    draw(
                            g,
                            metrics,
                            p.getX(),
                            baseline - metrics.getHeight() - 2,
                            fan.displayName,
                            new Color(230, 230, 230));
            }
        } finally {
            g.dispose();
        }
        return null;
    }

    private void tile(Graphics2D g, WorldView view, WorldPoint point, Color color) {
        if (point == null) return;
        LocalPoint local = LocalPoint.fromWorld(view, point);
        if (local == null) return;
        Polygon polygon = Perspective.getCanvasTilePoly(client, local);
        if (polygon != null) {
            g.setColor(color);
            g.fill(polygon);
        }
    }

    private void draw(Graphics2D g, FontMetrics metrics, int x, int y, String text, Color color) {
        int left = x - metrics.stringWidth(text) / 2;
        g.setColor(Color.BLACK);
        g.drawString(text, left + 1, y + 1);
        g.setColor(color);
        g.drawString(text, left, y);
    }
}
