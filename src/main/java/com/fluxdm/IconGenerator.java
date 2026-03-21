package com.fluxdm;

import java.awt.*;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.List;

public final class IconGenerator {

    private static final Color BG = new Color(0x25, 0x63, 0xEB);

    private IconGenerator() {}

    public static BufferedImage generate(int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);

        // Rounded-rect background
        double arc = size * 0.22;
        g.setColor(BG);
        g.fill(new RoundRectangle2D.Double(0, 0, size, size, arc, arc));

        // White download arrow
        g.setColor(Color.WHITE);
        float s = size;
        float cx = s / 2;

        // Vertical shaft
        float shaftW = s * 0.16f;
        float shaftTop = s * 0.18f;
        float shaftBot = s * 0.52f;
        g.fill(new Rectangle.Float(cx - shaftW / 2, shaftTop, shaftW, shaftBot - shaftTop));

        // Arrowhead (triangle)
        Path2D arrow = new Path2D.Float();
        float arrowHalfW = s * 0.24f;
        float arrowTip = s * 0.68f;
        arrow.moveTo(cx, arrowTip);
        arrow.lineTo(cx - arrowHalfW, shaftBot);
        arrow.lineTo(cx + arrowHalfW, shaftBot);
        arrow.closePath();
        g.fill(arrow);

        // Horizontal bar (tray)
        float barY = s * 0.76f;
        float barH = s * 0.06f;
        float barMargin = s * 0.2f;
        g.fill(new Rectangle.Float(barMargin, barY, s - 2 * barMargin, barH));

        g.dispose();
        return img;
    }

    public static List<Image> appIcons() {
        return List.of(
                generate(16), generate(32), generate(48),
                generate(64), generate(128), generate(256)
        );
    }
}
