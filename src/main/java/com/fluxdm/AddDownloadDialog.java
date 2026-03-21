package com.fluxdm;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.datatransfer.*;
import java.awt.event.*;
import java.net.*;
import java.util.concurrent.Executors;

public class AddDownloadDialog extends JDialog {

    private DownloadTask result = null;

    private final JTextField urlField    = ThemeManager.createTextField("https://youtube.com/watch?v=... or any file URL");
    private final JTextField saveField;
    private final JLabel analyzeLabel   = new JLabel(" ");
    private final JLabel fileNameLabel  = new JLabel();
    private final JLabel fileSizeLabel  = new JLabel();
    private final JPanel infoPanel;
    private final JPanel ytPanel;
    private final ButtonGroup qualityGroup = new ButtonGroup();
    private String selectedQuality = "1080p";

    private static final String[] QUALITIES = {
            "4K (2160p)", "1080p", "720p", "480p", "360p", "Audio Only (MP3)"
    };

    private static boolean checkFfmpeg() {
        return DependencyManager.findFfmpeg() != null;
    }

    /** Returns clipboard text if it looks like a URL, else null */
    private static String clipboardUrl() {
        try {
            Clipboard cb = Toolkit.getDefaultToolkit().getSystemClipboard();
            String text = (String) cb.getData(DataFlavor.stringFlavor);
            if (text != null) {
                text = text.trim();
                if (text.startsWith("http://") || text.startsWith("https://") || text.startsWith("ftp://")) {
                    new URL(text); // validate
                    return text;
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    public AddDownloadDialog(Frame owner, String prefillUrl, String defaultSavePath) {
        super(owner, "Add New Download", true);
        saveField = ThemeManager.createTextField("");
        saveField.setText(defaultSavePath);

        // Auto-detect clipboard URL if no prefill given
        String effectiveUrl = prefillUrl;
        boolean fromClipboard = false;
        if (effectiveUrl.isBlank()) {
            String clip = clipboardUrl();
            if (clip != null) {
                effectiveUrl = clip;
                fromClipboard = true;
            }
        }

        boolean hasFfmpeg = checkFfmpeg();

        setUndecorated(false);
        setBackground(ThemeManager.bgDark());
        setResizable(false);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(ThemeManager.bgDark());
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));

        // Title row — with optional clipboard badge
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setBackground(ThemeManager.bgDark());
        titleRow.setAlignmentX(LEFT_ALIGNMENT);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel title = new JLabel("Add New Download");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(ThemeManager.textPrimary());
        titleRow.add(title);
        if (fromClipboard) {
            JLabel badge = new JLabel("\uD83D\uDCCB URL detected from clipboard");
            badge.setFont(new Font("Segoe UI", Font.BOLD, 10));
            badge.setForeground(ThemeManager.green());
            badge.setOpaque(true);
            badge.setBackground(ThemeManager.isDark() ? new Color(0x06, 0x3a, 0x1a) : new Color(0xd1, 0xfa, 0xe5));
            badge.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ThemeManager.isDark() ? new Color(0x10, 0x59, 0x30) : new Color(0x6e, 0xe7, 0xb7)),
                    BorderFactory.createEmptyBorder(2, 8, 2, 8)));
            titleRow.add(badge);
        }
        root.add(titleRow);
        root.add(Box.createVerticalStrut(12));
        root.add(separator());
        root.add(Box.createVerticalStrut(14));

        // URL row
        root.add(fieldLabel("Download URL"));
        root.add(Box.createVerticalStrut(5));
        JPanel urlRow = new JPanel(new BorderLayout(8, 0));
        urlRow.setBackground(ThemeManager.bgDark());
        urlRow.setAlignmentX(LEFT_ALIGNMENT);
        urlRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        urlField.setText(effectiveUrl);
        urlRow.add(urlField, BorderLayout.CENTER);
        JButton analyzeBtn = ThemeManager.createButton("Analyze", new Color(0x1d, 0x4e, 0xd8));
        analyzeBtn.setPreferredSize(new Dimension(90, 34));
        JButton pasteBtn = ThemeManager.createButton("\uD83D\uDCCB", new Color(0x37, 0x41, 0x51));
        pasteBtn.setPreferredSize(new Dimension(38, 34));
        pasteBtn.setToolTipText("Paste URL from clipboard");
        JPanel urlBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        urlBtns.setBackground(ThemeManager.bgDark());
        urlBtns.add(pasteBtn);
        urlBtns.add(analyzeBtn);
        urlRow.add(urlBtns, BorderLayout.EAST);
        root.add(urlRow);
        root.add(Box.createVerticalStrut(5));

        analyzeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        analyzeLabel.setForeground(ThemeManager.textMuted());
        analyzeLabel.setAlignmentX(LEFT_ALIGNMENT);
        root.add(analyzeLabel);
        root.add(Box.createVerticalStrut(8));

        // Info panel
        Color infoBg = ThemeManager.isDark() ? new Color(0x0d, 0x1f, 0x0d) : new Color(0xdc, 0xfc, 0xe7);
        Color infoBorder = ThemeManager.isDark() ? new Color(0x14, 0x53, 0x2d) : new Color(0x6e, 0xe7, 0xb7);
        infoPanel = roundPanel(infoBg, BorderFactory.createLineBorder(infoBorder, 1));
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        JLabel infoTitle = new JLabel("\u2713  File Analyzed");
        infoTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        infoTitle.setForeground(ThemeManager.green());
        infoPanel.add(infoTitle);
        infoPanel.add(Box.createVerticalStrut(4));
        fileNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        fileNameLabel.setForeground(ThemeManager.textPrimary());
        infoPanel.add(fileNameLabel);
        fileSizeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        fileSizeLabel.setForeground(ThemeManager.textMuted());
        infoPanel.add(fileSizeLabel);
        infoPanel.setVisible(false);
        root.add(infoPanel);

        // YouTube panel
        Color ytBg = ThemeManager.isDark() ? new Color(0x0c, 0x1a, 0x2e) : new Color(0xe0, 0xec, 0xff);
        Color ytBorderColor = ThemeManager.isDark() ? new Color(0x1e, 0x3a, 0x5f) : new Color(0x93, 0xb4, 0xf8);
        ytPanel = roundPanel(ytBg, BorderFactory.createLineBorder(ytBorderColor, 1));
        ytPanel.setLayout(new BoxLayout(ytPanel, BoxLayout.Y_AXIS));
        JLabel ytHeader = new JLabel("\u25B6  YouTube Video Detected \u2014 Select Quality");
        ytHeader.setFont(new Font("Segoe UI", Font.BOLD, 12));
        ytHeader.setForeground(ThemeManager.accentLight());
        ytPanel.add(ytHeader);
        ytPanel.add(Box.createVerticalStrut(6));

        // ffmpeg status hint
        JLabel ffmpegHint = new JLabel(hasFfmpeg
                ? "\u2713  ffmpeg found \u2014 best quality + audio merge + MP3 conversion enabled"
                : "\u2139  ffmpeg not found \u2014 will be auto-installed when needed");
        ffmpegHint.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        ffmpegHint.setForeground(hasFfmpeg ? ThemeManager.green() : ThemeManager.accentBlue());
        ytPanel.add(ffmpegHint);
        ytPanel.add(Box.createVerticalStrut(8));

        Color qPanelBg = ThemeManager.isDark() ? new Color(0x0c, 0x1a, 0x2e) : new Color(0xe0, 0xec, 0xff);
        JPanel qPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        qPanel.setBackground(qPanelBg);

        // MP3 info label (shown when Audio Only selected without ffmpeg)
        JLabel mp3Warning = new JLabel("\u2139 ffmpeg will be auto-installed for MP3 conversion");
        mp3Warning.setFont(new Font("Segoe UI", Font.BOLD, 10));
        mp3Warning.setForeground(ThemeManager.accentBlue());
        mp3Warning.setVisible(false);

        for (String q : QUALITIES) {
            JToggleButton btn = qualityToggle(q);
            qualityGroup.add(btn);
            if (q.equals("1080p")) btn.setSelected(true);
            btn.addActionListener(e -> {
                selectedQuality = q;
                mp3Warning.setVisible(q.contains("Audio") && !hasFfmpeg);
            });
            qPanel.add(btn);
        }
        ytPanel.add(qPanel);
        ytPanel.add(mp3Warning);
        ytPanel.setVisible(false);
        root.add(ytPanel);

        // Save path
        root.add(Box.createVerticalStrut(10));
        root.add(fieldLabel("Save to"));
        root.add(Box.createVerticalStrut(5));
        JPanel saveRow = new JPanel(new BorderLayout(8, 0));
        saveRow.setBackground(ThemeManager.bgDark());
        saveRow.setAlignmentX(LEFT_ALIGNMENT);
        saveRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        saveRow.add(saveField, BorderLayout.CENTER);
        JButton browseBtn = ThemeManager.createButton("Browse\u2026", new Color(0x37, 0x41, 0x51));
        browseBtn.setPreferredSize(new Dimension(90, 34));
        saveRow.add(browseBtn, BorderLayout.EAST);
        root.add(saveRow);
        root.add(Box.createVerticalStrut(18));

        // Buttons
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setBackground(ThemeManager.bgDark());
        btnRow.setAlignmentX(LEFT_ALIGNMENT);
        JButton cancelBtn = ThemeManager.createButton("Cancel", new Color(0x37, 0x41, 0x51));
        JButton startBtn  = ThemeManager.createButton("\u2B07  Start Download", new Color(0x25, 0x63, 0xeb));
        btnRow.add(cancelBtn);
        btnRow.add(startBtn);
        root.add(btnRow);

        // Wire up actions
        analyzeBtn.addActionListener(e -> analyzeUrl());
        urlField.addActionListener(e -> analyzeUrl());
        pasteBtn.addActionListener(e -> {
            String clip = clipboardUrl();
            if (clip != null) {
                urlField.setText(clip);
                analyzeUrl();
            } else {
                urlField.paste();
            }
        });
        browseBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser(saveField.getText());
            fc.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            if (fc.showOpenDialog(this) == JFileChooser.APPROVE_OPTION)
                saveField.setText(fc.getSelectedFile().getAbsolutePath());
        });
        cancelBtn.addActionListener(e -> dispose());
        startBtn.addActionListener(e -> {
            String url = urlField.getText().trim();
            if (!url.isBlank()) {
                result = new DownloadTask(url, saveField.getText().trim(), selectedQuality);
                dispose();
            }
        });

        setContentPane(root);
        pack();
        setSize(560, getPreferredSize().height + 20);
        setLocationRelativeTo(owner);

        if (!effectiveUrl.isBlank()) analyzeUrl();
    }

    private void analyzeUrl() {
        String url = urlField.getText().trim();
        if (url.isBlank()) return;
        analyzeLabel.setText("\uD83D\uDD0D Analyzing\u2026");
        analyzeLabel.setForeground(ThemeManager.textMuted());
        boolean isYT = url.contains("youtube.com/watch") || url.contains("youtu.be/");

        Executors.newSingleThreadExecutor().submit(() -> {
            String name, size;
            if (isYT) {
                name = "YouTube Video";
                size = "Size depends on selected quality";
            } else {
                try {
                    HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                    c.setRequestMethod("HEAD");
                    c.setRequestProperty("User-Agent", "Mozilla/5.0");
                    c.setConnectTimeout(8000);
                    c.setReadTimeout(8000);
                    c.connect();
                    long bytes = c.getContentLengthLong();
                    String path = new URL(url).getPath();
                    name = path.substring(path.lastIndexOf('/') + 1);
                    if (name.isBlank()) name = "download";
                    size = bytes > 0 ? Formatter.bytes(bytes) : "Size unknown";
                    c.disconnect();
                } catch (Exception ex) {
                    String path = url.contains("/") ? url.substring(url.lastIndexOf('/') + 1) : "download";
                    name = path.isEmpty() ? "download" : path;
                    size = "Could not fetch size";
                }
            }
            String fn = name, sz = size;
            SwingUtilities.invokeLater(() -> {
                analyzeLabel.setText("\u2713 Ready");
                analyzeLabel.setForeground(ThemeManager.green());
                fileNameLabel.setText(fn);
                fileSizeLabel.setText(sz);
                infoPanel.setVisible(true);
                ytPanel.setVisible(isYT);
                pack();
                setSize(560, getPreferredSize().height + 20);
            });
        });
    }

    public DownloadTask getResult() { return result; }

    private JLabel fieldLabel(String text) {
        JLabel l = new JLabel(text.toUpperCase());
        l.setFont(new Font("Segoe UI", Font.BOLD, 9));
        l.setForeground(ThemeManager.textMuted());
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JSeparator separator() {
        JSeparator s = new JSeparator();
        s.setForeground(ThemeManager.border());
        s.setBackground(ThemeManager.border());
        s.setAlignmentX(LEFT_ALIGNMENT);
        s.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        return s;
    }

    private JPanel roundPanel(Color bg, Border border) {
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
        p.setBorder(BorderFactory.createCompoundBorder(border,
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        p.setAlignmentX(LEFT_ALIGNMENT);
        p.setMaximumSize(new Dimension(Integer.MAX_VALUE, 200));
        return p;
    }

    private JToggleButton qualityToggle(String label) {
        Color selBg = ThemeManager.isDark() ? new Color(0x1e, 0x3a, 0x5f) : new Color(0xbf, 0xdb, 0xfe);
        Color defBg = ThemeManager.isDark() ? new Color(0x0f, 0x11, 0x17) : new Color(0xf3, 0xf4, 0xf6);
        JToggleButton btn = new JToggleButton(label) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isSelected() ? selBg : defBg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.setColor(isSelected() ? ThemeManager.accentBlue() : ThemeManager.border());
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btn.setForeground(ThemeManager.textMuted());
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addChangeListener(e ->
                btn.setForeground(btn.isSelected() ? ThemeManager.accentLight() : ThemeManager.textMuted()));
        return btn;
    }
}
