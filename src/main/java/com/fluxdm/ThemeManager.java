package com.fluxdm;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

public class ThemeManager {

    private static boolean dark = true;
    private static final Preferences PREFS = Preferences.userNodeForPackage(ThemeManager.class);
    private static final String PREF_KEY = "theme";
    private static final List<Runnable> listeners = new ArrayList<>();

    // ─── Dark palette (original DarkTheme colors) ────────────────────────────

    private static final Color D_BG_DARKEST    = new Color(0x0f, 0x11, 0x17);
    private static final Color D_BG_DARK       = new Color(0x16, 0x1b, 0x27);
    private static final Color D_BG_MEDIUM     = new Color(0x1e, 0x26, 0x37);
    private static final Color D_BG_LIGHT      = new Color(0x1f, 0x29, 0x3a);
    private static final Color D_ACCENT_BLUE   = new Color(0x3b, 0x82, 0xf6);
    private static final Color D_ACCENT_LIGHT  = new Color(0x60, 0xa5, 0xfa);
    private static final Color D_TEXT_PRIMARY   = new Color(0xe2, 0xe8, 0xf0);
    private static final Color D_TEXT_SECONDARY = new Color(0x9c, 0xa3, 0xaf);
    private static final Color D_TEXT_MUTED     = new Color(0x6b, 0x72, 0x80);
    private static final Color D_TEXT_DARK      = new Color(0x4b, 0x55, 0x63);
    private static final Color D_GREEN          = new Color(0x10, 0xb9, 0x81);
    private static final Color D_YELLOW         = new Color(0xf5, 0x9e, 0x0b);
    private static final Color D_RED            = new Color(0xef, 0x44, 0x44);
    private static final Color D_BORDER         = new Color(0x1e, 0x26, 0x37);
    private static final Color D_GRID           = new Color(0x1a, 0x1f, 0x2e);
    private static final Color D_SPEED_BG       = new Color(0x06, 0x4e, 0x3b);
    private static final Color D_COMPLETED_ROW  = new Color(0x06, 0x1a, 0x10);

    // ─── Light palette ───────────────────────────────────────────────────────

    private static final Color L_BG_DARKEST    = new Color(0xf8, 0xf9, 0xfb);
    private static final Color L_BG_DARK       = new Color(0xf0, 0xf1, 0xf4);
    private static final Color L_BG_MEDIUM     = new Color(0xe4, 0xe7, 0xec);
    private static final Color L_BG_LIGHT      = new Color(0xdb, 0xdf, 0xe6);
    private static final Color L_ACCENT_BLUE   = new Color(0x25, 0x63, 0xeb);
    private static final Color L_ACCENT_LIGHT  = new Color(0x3b, 0x82, 0xf6);
    private static final Color L_TEXT_PRIMARY   = new Color(0x1e, 0x29, 0x3b);
    private static final Color L_TEXT_SECONDARY = new Color(0x4b, 0x55, 0x63);
    private static final Color L_TEXT_MUTED     = new Color(0x6b, 0x72, 0x80);
    private static final Color L_TEXT_DARK      = new Color(0x9c, 0xa3, 0xaf);
    private static final Color L_GREEN          = new Color(0x05, 0x96, 0x69);
    private static final Color L_YELLOW         = new Color(0xd9, 0x77, 0x06);
    private static final Color L_RED            = new Color(0xdc, 0x26, 0x26);
    private static final Color L_BORDER         = new Color(0xd1, 0xd5, 0xdb);
    private static final Color L_GRID           = new Color(0xe5, 0xe7, 0xeb);
    private static final Color L_SPEED_BG       = new Color(0xd1, 0xfa, 0xe5);
    private static final Color L_COMPLETED_ROW  = new Color(0xdc, 0xfc, 0xe7);

    // ─── Color accessors ─────────────────────────────────────────────────────

    public static Color bgDarkest()     { return dark ? D_BG_DARKEST    : L_BG_DARKEST; }
    public static Color bgDark()        { return dark ? D_BG_DARK       : L_BG_DARK; }
    public static Color bgMedium()      { return dark ? D_BG_MEDIUM     : L_BG_MEDIUM; }
    public static Color bgLight()       { return dark ? D_BG_LIGHT      : L_BG_LIGHT; }
    public static Color accentBlue()    { return dark ? D_ACCENT_BLUE   : L_ACCENT_BLUE; }
    public static Color accentLight()   { return dark ? D_ACCENT_LIGHT  : L_ACCENT_LIGHT; }
    public static Color textPrimary()   { return dark ? D_TEXT_PRIMARY   : L_TEXT_PRIMARY; }
    public static Color textSecondary() { return dark ? D_TEXT_SECONDARY : L_TEXT_SECONDARY; }
    public static Color textMuted()     { return dark ? D_TEXT_MUTED     : L_TEXT_MUTED; }
    public static Color textDark()      { return dark ? D_TEXT_DARK      : L_TEXT_DARK; }
    public static Color green()         { return dark ? D_GREEN          : L_GREEN; }
    public static Color yellow()        { return dark ? D_YELLOW         : L_YELLOW; }
    public static Color red()           { return dark ? D_RED            : L_RED; }
    public static Color border()        { return dark ? D_BORDER         : L_BORDER; }
    public static Color gridColor()     { return dark ? D_GRID           : L_GRID; }
    public static Color speedBg()       { return dark ? D_SPEED_BG       : L_SPEED_BG; }
    public static Color completedRow()  { return dark ? D_COMPLETED_ROW  : L_COMPLETED_ROW; }
    public static boolean isDark()      { return dark; }

    // ─── Initialization ──────────────────────────────────────────────────────

    public static void init() {
        dark = PREFS.get(PREF_KEY, "dark").equals("dark");
        applyLaf();
    }

    private static void applyLaf() {
        try {
            if (dark) {
                FlatDarkLaf.setup();
            } else {
                FlatLightLaf.setup();
            }
        } catch (Exception e) {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {}
        }
    }

    // ─── Toggle ──────────────────────────────────────────────────────────────

    public static void toggle() {
        dark = !dark;
        PREFS.put(PREF_KEY, dark ? "dark" : "light");
        applyLaf();
        for (Window w : Window.getWindows()) {
            SwingUtilities.updateComponentTreeUI(w);
        }
        for (Runnable r : listeners) {
            r.run();
        }
    }

    // ─── Listener support ────────────────────────────────────────────────────

    public static void addThemeChangeListener(Runnable listener) {
        listeners.add(listener);
    }

    // ─── Factory methods (match DarkTheme signatures) ────────────────────────

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
                g2.setColor(bgDarkest());
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        field.setBackground(bgDarkest());
        field.setForeground(textPrimary());
        field.setCaretColor(textPrimary());
        field.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border(), 1, true),
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
