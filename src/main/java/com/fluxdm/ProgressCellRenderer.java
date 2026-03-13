package com.fluxdm;

import javax.swing.*;
import javax.swing.table.TableCellRenderer;
import java.awt.*;

public class ProgressCellRenderer extends JPanel implements TableCellRenderer {

    private double progress = 0;
    private String label = "0%";
    private Color barColor = DarkTheme.ACCENT_BLUE;

    public ProgressCellRenderer() {
        setOpaque(true);
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean isSelected, boolean hasFocus, int row, int column) {

        if (value instanceof DownloadTask task) {
            progress = task.getProgress();
            int pct = (int)(progress * 100);
            label = pct + "%";
            barColor = switch (task.getStatus()) {
                case COMPLETED  -> DarkTheme.GREEN;
                case PAUSED     -> DarkTheme.YELLOW;
                case FAILED,
                     CANCELLED  -> DarkTheme.RED;
                default         -> DarkTheme.ACCENT_BLUE;
            };
        }

        setBackground(isSelected ? DarkTheme.BG_MEDIUM : DarkTheme.BG_DARKEST);
        return this;
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth() - 16, h = getHeight();
        int barH = 4, barY = h / 2 + 6;
        int cx = 8;

        // Percent label
        g2.setFont(new Font("Segoe UI", Font.BOLD, 11));
        g2.setColor(DarkTheme.TEXT_PRIMARY);
        g2.drawString(label, cx, barY - 7);

        // Track
        g2.setColor(DarkTheme.BG_MEDIUM);
        g2.fillRoundRect(cx, barY, w, barH, barH, barH);

        // Fill
        int fillW = (int)(w * Math.min(1.0, Math.max(0.0, progress)));
        if (fillW > 0) {
            g2.setColor(barColor);
            g2.fillRoundRect(cx, barY, fillW, barH, barH, barH);
        }
        g2.dispose();
    }
}
