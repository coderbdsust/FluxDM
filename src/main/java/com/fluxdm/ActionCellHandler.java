package com.fluxdm;

import javax.swing.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.EventObject;

public class ActionCellHandler extends AbstractCellEditor implements TableCellRenderer, TableCellEditor {

    private final DownloadTableModel model;
    private DownloadTask currentTask;

    private final JPanel panel = new JPanel(new FlowLayout(FlowLayout.CENTER, 3, 0));
    private final JButton pauseBtn  = iconBtn("⏸", new Color(0x92, 0x40, 0x0e));
    private final JButton resumeBtn = iconBtn("▶", new Color(0x1d, 0x4e, 0xd8));
    private final JButton openBtn   = iconBtn("📂", new Color(0x06, 0x5f, 0x46));
    private final JButton removeBtn = iconBtn("✕", new Color(0x7f, 0x1d, 0x1d));

    public ActionCellHandler(DownloadTableModel model) {
        this.model = model;
        panel.setOpaque(true);

        pauseBtn.addActionListener(e  -> { if (currentTask != null) currentTask.pause();  fireEditingStopped(); });
        resumeBtn.addActionListener(e -> { if (currentTask != null) currentTask.resume(); fireEditingStopped(); });
        removeBtn.addActionListener(e -> {
            if (currentTask != null) { currentTask.cancel(); model.removeTask(currentTask); }
            fireEditingStopped();
        });
        openBtn.addActionListener(e -> {
            if (currentTask != null) {
                String filePath = currentTask.getSavedFilePath();
                String dirPath  = currentTask.getSavePath();
                try {
                    String os = System.getProperty("os.name").toLowerCase();
                    if (os.contains("mac")) {
                        if (filePath != null && new java.io.File(filePath).exists()) {
                            // Reveal and select the file in Finder
                            Runtime.getRuntime().exec(new String[]{"open", "-R", filePath});
                        } else {
                            // Fallback: open the downloads folder
                            Runtime.getRuntime().exec(new String[]{"open", dirPath});
                        }
                    } else if (os.contains("win")) {
                        if (filePath != null)
                            Runtime.getRuntime().exec(new String[]{"explorer", "/select,", filePath});
                        else
                            Runtime.getRuntime().exec(new String[]{"explorer", dirPath});
                    } else {
                        Runtime.getRuntime().exec(new String[]{"xdg-open", dirPath});
                    }
                } catch (Exception ex) { ex.printStackTrace(); }
            }
            fireEditingStopped();
        });
    }

    private void configure(DownloadTask task, boolean selected) {
        currentTask = task;
        panel.setBackground(selected ? DarkTheme.BG_MEDIUM : DarkTheme.BG_DARKEST);
        panel.removeAll();

        DownloadTask.Status s = task.getStatus();
        if (s == DownloadTask.Status.DOWNLOADING) panel.add(pauseBtn);
        if (s == DownloadTask.Status.PAUSED)      panel.add(resumeBtn);
        if (s == DownloadTask.Status.COMPLETED)   panel.add(openBtn);
        panel.add(removeBtn);
        panel.revalidate();
    }

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value,
            boolean selected, boolean focused, int row, int col) {
        if (value instanceof DownloadTask t) configure(t, selected);
        return panel;
    }

    @Override
    public Component getTableCellEditorComponent(JTable table, Object value,
            boolean selected, int row, int col) {
        if (value instanceof DownloadTask t) configure(t, true);
        return panel;
    }

    @Override public Object getCellEditorValue() { return currentTask; }

    @Override public boolean isCellEditable(EventObject e) { return true; }

    private JButton iconBtn(String text, Color bg) {
        JButton b = new JButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(getModel().isRollover() ? bg.brighter() : bg);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 6, 6);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        b.setPreferredSize(new Dimension(26, 26));
        b.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        b.setForeground(Color.WHITE);
        b.setBackground(bg);
        b.setFocusPainted(false);
        b.setContentAreaFilled(false);
        b.setOpaque(false);
        b.setBorder(BorderFactory.createEmptyBorder());
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }
}
