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

    private final JTextField urlField    = DarkTheme.createTextField("https://youtube.com/watch?v=... or any file URL");
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
        String[] candidates = {"ffmpeg", "/usr/local/bin/ffmpeg", "/opt/homebrew/bin/ffmpeg", "/usr/bin/ffmpeg"};
        for (String c : candidates) {
            try {
                Process p = new ProcessBuilder(c, "-version").redirectErrorStream(true).start();
                if (p.waitFor(4, java.util.concurrent.TimeUnit.SECONDS) && p.exitValue() == 0) return true;
            } catch (Exception ignored) {}
        }
        return false;
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
        saveField = DarkTheme.createTextField("");
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

        setUndecorated(false);
        setBackground(DarkTheme.BG_DARK);
        setResizable(false);

        JPanel root = new JPanel();
        root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
        root.setBackground(DarkTheme.BG_DARK);
        root.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));

        // Title row — with optional clipboard badge
        JPanel titleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        titleRow.setBackground(DarkTheme.BG_DARK);
        titleRow.setAlignmentX(LEFT_ALIGNMENT);
        titleRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 30));
        JLabel title = new JLabel("Add New Download");
        title.setFont(new Font("Segoe UI", Font.BOLD, 16));
        title.setForeground(DarkTheme.TEXT_PRIMARY);
        titleRow.add(title);
        if (fromClipboard) {
            JLabel badge = new JLabel("📋 URL detected from clipboard");
            badge.setFont(new Font("Segoe UI", Font.BOLD, 10));
            badge.setForeground(DarkTheme.GREEN);
            badge.setOpaque(true);
            badge.setBackground(new Color(0x06, 0x3a, 0x1a));
            badge.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(0x10, 0x59, 0x30)),
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
        urlRow.setBackground(DarkTheme.BG_DARK);
        urlRow.setAlignmentX(LEFT_ALIGNMENT);
        urlRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        urlField.setText(effectiveUrl);
        urlRow.add(urlField, BorderLayout.CENTER);
        JButton analyzeBtn = DarkTheme.createButton("Analyze", new Color(0x1d, 0x4e, 0xd8));
        analyzeBtn.setPreferredSize(new Dimension(90, 34));
        JButton pasteBtn = DarkTheme.createButton("📋", new Color(0x37, 0x41, 0x51));
        pasteBtn.setPreferredSize(new Dimension(38, 34));
        pasteBtn.setToolTipText("Paste URL from clipboard");
        JPanel urlBtns = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
        urlBtns.setBackground(DarkTheme.BG_DARK);
        urlBtns.add(pasteBtn);
        urlBtns.add(analyzeBtn);
        urlRow.add(urlBtns, BorderLayout.EAST);
        root.add(urlRow);
        root.add(Box.createVerticalStrut(5));

        analyzeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        analyzeLabel.setForeground(DarkTheme.TEXT_MUTED);
        analyzeLabel.setAlignmentX(LEFT_ALIGNMENT);
        root.add(analyzeLabel);
        root.add(Box.createVerticalStrut(8));

        // Info panel
        infoPanel = roundPanel(new Color(0x0d, 0x1f, 0x0d),
                BorderFactory.createLineBorder(new Color(0x14, 0x53, 0x2d), 1));
        infoPanel.setLayout(new BoxLayout(infoPanel, BoxLayout.Y_AXIS));
        JLabel infoTitle = new JLabel("✓  File Analyzed");
        infoTitle.setFont(new Font("Segoe UI", Font.BOLD, 11));
        infoTitle.setForeground(DarkTheme.GREEN);
        infoPanel.add(infoTitle);
        infoPanel.add(Box.createVerticalStrut(4));
        fileNameLabel.setFont(new Font("Segoe UI", Font.BOLD, 13));
        fileNameLabel.setForeground(DarkTheme.TEXT_PRIMARY);
        infoPanel.add(fileNameLabel);
        fileSizeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        fileSizeLabel.setForeground(DarkTheme.TEXT_MUTED);
        infoPanel.add(fileSizeLabel);
        infoPanel.setVisible(false);
        root.add(infoPanel);

        // YouTube panel
        ytPanel = roundPanel(new Color(0x0c, 0x1a, 0x2e),
                BorderFactory.createLineBorder(new Color(0x1e, 0x3a, 0x5f), 1));
        ytPanel.setLayout(new BoxLayout(ytPanel, BoxLayout.Y_AXIS));
        JLabel ytHeader = new JLabel("▶  YouTube Video Detected — Select Quality");
        ytHeader.setFont(new Font("Segoe UI", Font.BOLD, 12));
        ytHeader.setForeground(DarkTheme.ACCENT_LIGHT);
        ytPanel.add(ytHeader);
        ytPanel.add(Box.createVerticalStrut(6));

        // ffmpeg status hint
        boolean hasFfmpeg = checkFfmpeg();
        JLabel ffmpegHint = new JLabel(hasFfmpeg
                ? "✓  ffmpeg found — best quality + audio merge enabled"
                : "⚠  ffmpeg not found — using pre-muxed streams (max ~720p). Install: brew install ffmpeg");
        ffmpegHint.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        ffmpegHint.setForeground(hasFfmpeg ? DarkTheme.GREEN : DarkTheme.YELLOW);
        ytPanel.add(ffmpegHint);
        ytPanel.add(Box.createVerticalStrut(8));

        JPanel qPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        qPanel.setBackground(new Color(0x0c, 0x1a, 0x2e));
        for (String q : QUALITIES) {
            JToggleButton btn = qualityToggle(q);
            qualityGroup.add(btn);
            if (q.equals("1080p")) btn.setSelected(true);
            btn.addActionListener(e -> selectedQuality = q);
            qPanel.add(btn);
        }
        ytPanel.add(qPanel);
        ytPanel.setVisible(false);
        root.add(ytPanel);

        // Save path
        root.add(Box.createVerticalStrut(10));
        root.add(fieldLabel("Save to"));
        root.add(Box.createVerticalStrut(5));
        JPanel saveRow = new JPanel(new BorderLayout(8, 0));
        saveRow.setBackground(DarkTheme.BG_DARK);
        saveRow.setAlignmentX(LEFT_ALIGNMENT);
        saveRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
        saveRow.add(saveField, BorderLayout.CENTER);
        JButton browseBtn = DarkTheme.createButton("Browse…", new Color(0x37, 0x41, 0x51));
        browseBtn.setPreferredSize(new Dimension(90, 34));
        saveRow.add(browseBtn, BorderLayout.EAST);
        root.add(saveRow);
        root.add(Box.createVerticalStrut(18));

        // Buttons
        JPanel btnRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        btnRow.setBackground(DarkTheme.BG_DARK);
        btnRow.setAlignmentX(LEFT_ALIGNMENT);
        JButton cancelBtn = DarkTheme.createButton("Cancel", new Color(0x37, 0x41, 0x51));
        JButton startBtn  = DarkTheme.createButton("⬇  Start Download", new Color(0x25, 0x63, 0xeb));
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
                // Fallback: just paste raw clipboard text
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
        analyzeLabel.setText("🔍 Analyzing…");
        analyzeLabel.setForeground(DarkTheme.TEXT_MUTED);
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
                analyzeLabel.setText("✓ Ready");
                analyzeLabel.setForeground(DarkTheme.GREEN);
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
        l.setForeground(DarkTheme.TEXT_MUTED);
        l.setAlignmentX(LEFT_ALIGNMENT);
        return l;
    }

    private JSeparator separator() {
        JSeparator s = new JSeparator();
        s.setForeground(DarkTheme.BORDER);
        s.setBackground(DarkTheme.BORDER);
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
        Color selBg = new Color(0x1e, 0x3a, 0x5f);
        Color defBg = new Color(0x0f, 0x11, 0x17);
        JToggleButton btn = new JToggleButton(label) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(isSelected() ? selBg : defBg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.setColor(isSelected() ? DarkTheme.ACCENT_BLUE : DarkTheme.BORDER);
                g2.drawRoundRect(0, 0, getWidth()-1, getHeight()-1, 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        btn.setForeground(DarkTheme.TEXT_MUTED);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addChangeListener(e ->
                btn.setForeground(btn.isSelected() ? DarkTheme.ACCENT_LIGHT : DarkTheme.TEXT_MUTED));
        return btn;
    }
}
