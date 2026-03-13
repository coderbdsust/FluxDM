package com.fluxdm;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.plaf.basic.BasicScrollBarUI;
import java.awt.*;

public class DarkTheme {

    // Color palette
    public static final Color BG_DARKEST   = new Color(0x0f, 0x11, 0x17);
    public static final Color BG_DARK      = new Color(0x16, 0x1b, 0x27);
    public static final Color BG_MEDIUM    = new Color(0x1e, 0x26, 0x37);
    public static final Color BG_LIGHT     = new Color(0x1f, 0x29, 0x3a);
    public static final Color ACCENT_BLUE  = new Color(0x3b, 0x82, 0xf6);
    public static final Color ACCENT_LIGHT = new Color(0x60, 0xa5, 0xfa);
    public static final Color TEXT_PRIMARY = new Color(0xe2, 0xe8, 0xf0);
    public static final Color TEXT_SECONDARY= new Color(0x9c, 0xa3, 0xaf);
    public static final Color TEXT_MUTED   = new Color(0x6b, 0x72, 0x80);
    public static final Color TEXT_DARK    = new Color(0x4b, 0x55, 0x63);
    public static final Color GREEN        = new Color(0x10, 0xb9, 0x81);
    public static final Color YELLOW       = new Color(0xf5, 0x9e, 0x0b);
    public static final Color RED          = new Color(0xef, 0x44, 0x44);
    public static final Color BORDER       = new Color(0x1e, 0x26, 0x37);

    public static void apply() {
        try {
            UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
        } catch (Exception ignored) {}

        UIDefaults ui = UIManager.getDefaults();

        // Global
        ui.put("Panel.background", BG_DARKEST);
        ui.put("Panel.foreground", TEXT_PRIMARY);
        ui.put("Label.foreground", TEXT_PRIMARY);
        ui.put("Label.background", BG_DARKEST);

        // Buttons
        ui.put("Button.background", ACCENT_BLUE);
        ui.put("Button.foreground", Color.WHITE);
        ui.put("Button.border", BorderFactory.createEmptyBorder(7, 14, 7, 14));
        ui.put("Button.focus", new Color(0, 0, 0, 0));
        ui.put("Button.select", ACCENT_BLUE.darker());

        // TextField
        ui.put("TextField.background", BG_DARKEST);
        ui.put("TextField.foreground", TEXT_PRIMARY);
        ui.put("TextField.caretForeground", TEXT_PRIMARY);
        ui.put("TextField.border", BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        ui.put("TextField.selectionBackground", ACCENT_BLUE);
        ui.put("TextField.selectionForeground", Color.WHITE);

        // ComboBox
        ui.put("ComboBox.background", BG_DARK);
        ui.put("ComboBox.foreground", TEXT_PRIMARY);
        ui.put("ComboBox.selectionBackground", ACCENT_BLUE);
        ui.put("ComboBox.selectionForeground", Color.WHITE);
        ui.put("ComboBox.border", BorderFactory.createLineBorder(BORDER));

        // Table
        ui.put("Table.background", BG_DARKEST);
        ui.put("Table.foreground", TEXT_PRIMARY);
        ui.put("Table.gridColor", new Color(0x1a, 0x1f, 0x2e));
        ui.put("Table.selectionBackground", BG_MEDIUM);
        ui.put("Table.selectionForeground", TEXT_PRIMARY);
        ui.put("Table.focusCellHighlightBorder", BorderFactory.createEmptyBorder());
        ui.put("TableHeader.background", BG_DARK);
        ui.put("TableHeader.foreground", TEXT_MUTED);
        ui.put("TableHeader.cellBorder", BorderFactory.createMatteBorder(0, 0, 1, 1, BORDER));

        // ScrollPane
        ui.put("ScrollPane.background", BG_DARKEST);
        ui.put("ScrollPane.border", BorderFactory.createEmptyBorder());
        ui.put("ScrollBar.background", BG_DARKEST);
        ui.put("ScrollBar.thumb", BG_MEDIUM);
        ui.put("ScrollBar.thumbDarkShadow", BG_MEDIUM);
        ui.put("ScrollBar.thumbShadow", BG_MEDIUM);
        ui.put("ScrollBar.thumbHighlight", BG_LIGHT);
        ui.put("ScrollBar.track", BG_DARKEST);
        ui.put("ScrollBar.width", 8);

        // ProgressBar
        ui.put("ProgressBar.background", BG_MEDIUM);
        ui.put("ProgressBar.foreground", ACCENT_BLUE);
        ui.put("ProgressBar.border", BorderFactory.createEmptyBorder());

        // Separator
        ui.put("Separator.foreground", BORDER);
        ui.put("Separator.background", BORDER);

        // OptionPane
        ui.put("OptionPane.background", BG_DARK);
        ui.put("OptionPane.messageForeground", TEXT_PRIMARY);

        // Dialog / Window
        ui.put("activeCaption", BG_DARK);
        ui.put("activeCaptionText", TEXT_PRIMARY);
        ui.put("window", BG_DARK);
        ui.put("windowText", TEXT_PRIMARY);

        // Tooltip
        ui.put("ToolTip.background", BG_MEDIUM);
        ui.put("ToolTip.foreground", TEXT_PRIMARY);
        ui.put("ToolTip.border", BorderFactory.createLineBorder(BORDER));

        // List
        ui.put("List.background", BG_DARK);
        ui.put("List.foreground", TEXT_PRIMARY);
        ui.put("List.selectionBackground", ACCENT_BLUE);
        ui.put("List.selectionForeground", Color.WHITE);

        // SplitPane
        ui.put("SplitPane.background", BG_DARKEST);
        ui.put("SplitPaneDivider.background", BORDER);

        // Set default font
        Font font = new Font("Segoe UI", Font.PLAIN, 12);
        if (!font.getFamily().equals("Segoe UI")) {
            font = new Font("SF Pro Text", Font.PLAIN, 12);
        }
        if (!font.getFamily().equals("SF Pro Text")) {
            font = new Font("Dialog", Font.PLAIN, 12);
        }
        for (Object key : ui.keySet().toArray()) {
            if (key instanceof String s && s.endsWith(".font")) {
                ui.put(key, font);
            }
        }
    }

    public static JButton createButton(String text, Color bg) {
        JButton btn = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                if (getModel().isPressed()) g2.setColor(bg.darker());
                else if (getModel().isRollover()) g2.setColor(bg.brighter());
                else g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 8, 8);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.BOLD, 12));
        btn.setForeground(Color.WHITE);
        btn.setBackground(bg);
        btn.setBorder(BorderFactory.createEmptyBorder(7, 14, 7, 14));
        btn.setFocusPainted(false);
        btn.setContentAreaFilled(false);
        btn.setOpaque(false);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return btn;
    }

    public static JTextField createTextField(String placeholder) {
        JTextField field = new JTextField() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(BG_DARKEST);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        field.setBackground(BG_DARKEST);
        field.setForeground(TEXT_PRIMARY);
        field.setCaretColor(TEXT_PRIMARY);
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1, true),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        field.setOpaque(false);
        return field;
    }

    public static JPanel card(Color bg) {
        JPanel p = new JPanel() {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 10, 10);
                g2.dispose();
            }
        };
        p.setOpaque(false);
        return p;
    }
}
