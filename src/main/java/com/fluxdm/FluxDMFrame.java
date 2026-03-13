package com.fluxdm;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.prefs.Preferences;

public class FluxDMFrame extends JFrame {

    private final DownloadTableModel tableModel = new DownloadTableModel();
    private final JTable table;
    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel speedLabel  = new JLabel();
    private final Preferences prefs  = Preferences.userNodeForPackage(FluxDMFrame.class);
    private String currentFilter     = "All Downloads";

    public FluxDMFrame() {
        super("FluxDM — Download Manager");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setSize(1100, 680);
        setMinimumSize(new Dimension(900, 500));
        setLocationRelativeTo(null);

        // macOS-specific: use system menu bar
        System.setProperty("apple.laf.useScreenMenuBar", "true");
        System.setProperty("com.apple.mrj.application.apple.menu.about.name", "FluxDM");

        // Build table
        table = buildTable();

        // Layout
        setLayout(new BorderLayout());
        add(buildTitleBar(),  BorderLayout.NORTH);
        add(buildCenter(),    BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);

        // Speed update timer
        new Timer(500, e -> updateSpeedLabel()).start();
    }

    // ─── Title Bar ───────────────────────────────────────────────────────────

    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(DarkTheme.BG_DARK);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER));
        bar.setPreferredSize(new Dimension(0, 46));

        // Left: logo
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        left.setBackground(DarkTheme.BG_DARK);
        left.setOpaque(true);
        JLabel logo = new JLabel("⬇  FluxDM");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 14));
        logo.setForeground(DarkTheme.ACCENT_LIGHT);
        JLabel ver = new JLabel("Download Manager v2.0");
        ver.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        ver.setForeground(DarkTheme.TEXT_DARK);
        left.add(logo);
        left.add(ver);

        // Right: speed badge
        speedLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        speedLabel.setForeground(DarkTheme.GREEN);
        speedLabel.setOpaque(true);
        speedLabel.setBackground(new Color(0x06, 0x4e, 0x3b));
        speedLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(0x06, 0x4e, 0x3b)),
                BorderFactory.createEmptyBorder(2, 10, 2, 10)));
        speedLabel.setVisible(false);
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        right.setBackground(DarkTheme.BG_DARK);
        right.add(speedLabel);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ─── Toolbar ─────────────────────────────────────────────────────────────

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        bar.setBackground(DarkTheme.BG_DARK);
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER));

        JButton addBtn     = DarkTheme.createButton("+ Add URL",        new Color(0x25, 0x63, 0xeb));
        JButton pauseAll   = DarkTheme.createButton("⏸  Pause All",     new Color(0x37, 0x41, 0x51));
        JButton resumeAll  = DarkTheme.createButton("▶  Resume All",    new Color(0x37, 0x41, 0x51));
        JButton clearDone  = DarkTheme.createButton("✕  Clear Done",    new Color(0x37, 0x41, 0x51));
        JButton openFolder = DarkTheme.createButton("📂  Downloads",    new Color(0x37, 0x41, 0x51));

        bar.add(addBtn);
        bar.add(pauseAll);
        bar.add(resumeAll);
        bar.add(clearDone);
        bar.add(openFolder);

        // Stats label (right-aligned trick with a second panel)
        JLabel statsLabel = new JLabel("Total: 0   Active: 0   Done: 0");
        statsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statsLabel.setForeground(DarkTheme.TEXT_MUTED);
        new Timer(1000, e -> {
            var tasks = tableModel.getAllTasks();
            long active = tasks.stream().filter(t -> t.getStatus() == DownloadTask.Status.DOWNLOADING).count();
            long done   = tasks.stream().filter(t -> t.getStatus() == DownloadTask.Status.COMPLETED).count();
            statsLabel.setText(String.format("Total: %d   Active: %d   Done: %d", tasks.size(), active, done));
        }).start();
        bar.add(Box.createHorizontalGlue());
        bar.add(statsLabel);

        // Actions
        addBtn.addActionListener(e -> showAddDialog(""));
        pauseAll.addActionListener(e ->
                tableModel.getAllTasks().stream()
                        .filter(t -> t.getStatus() == DownloadTask.Status.DOWNLOADING)
                        .forEach(DownloadTask::pause));
        resumeAll.addActionListener(e ->
                tableModel.getAllTasks().stream()
                        .filter(t -> t.getStatus() == DownloadTask.Status.PAUSED)
                        .forEach(DownloadTask::resume));
        clearDone.addActionListener(e -> {
            tableModel.getAllTasks().stream()
                    .filter(t -> t.getStatus() == DownloadTask.Status.COMPLETED)
                    .toList()
                    .forEach(tableModel::removeTask);
        });
        openFolder.addActionListener(e -> openDownloadsFolder());

        return bar;
    }

    // ─── Center (sidebar + table) ─────────────────────────────────────────────

    private JSplitPane buildCenter() {
        JPanel sidebar = buildSidebar();
        JPanel tablePanel = new JPanel(new BorderLayout());
        tablePanel.setBackground(DarkTheme.BG_DARKEST);
        tablePanel.add(buildToolbar(), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBackground(DarkTheme.BG_DARKEST);
        scroll.getViewport().setBackground(DarkTheme.BG_DARKEST);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        tablePanel.add(scroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, tablePanel);
        split.setDividerLocation(185);
        split.setDividerSize(1);
        split.setBorder(null);
        split.setBackground(DarkTheme.BORDER);
        return split;
    }

    // ─── Sidebar ─────────────────────────────────────────────────────────────

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(DarkTheme.BG_DARK);
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, DarkTheme.BORDER));
        sidebar.setPreferredSize(new Dimension(185, 0));

        String[][] items = {
                {"📥", "All Downloads"},
                {"⬇",  "Downloading"},
                {"✓",  "Completed"},
                {"▶",  "YouTube"},
                {"🌐", "HTTP Files"},
        };

        ButtonGroup bg = new ButtonGroup();
        for (String[] item : items) {
            JToggleButton btn = sidebarButton(item[0] + "  " + item[1]);
            bg.add(btn);
            btn.addActionListener(e -> { currentFilter = item[1]; applyFilter(); });
            sidebar.add(btn);
            if (item[1].equals("All Downloads")) btn.setSelected(true);
        }

        sidebar.add(Box.createVerticalStrut(8));

        JSeparator sep = new JSeparator();
        sep.setForeground(DarkTheme.BORDER);
        sep.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
        sidebar.add(sep);

        JLabel sampleLbl = new JLabel("  QUICK ADD");
        sampleLbl.setFont(new Font("Segoe UI", Font.BOLD, 9));
        sampleLbl.setForeground(DarkTheme.TEXT_DARK);
        sampleLbl.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
        sidebar.add(sampleLbl);

        String[] samples = {
                "https://youtube.com/watch?v=dQw4w9WgXcQ",
                "https://releases.ubuntu.com/ubuntu-22.04.iso",
                "https://example.com/sample-video.mp4",
        };
        for (String s : samples) {
            String prefix = s.contains("youtube") ? "▶ " : "🌐 ";
            String display = prefix + s.replace("https://", "").substring(0, Math.min(22, s.replace("https://","").length())) + "…";
            JButton lnk = new JButton(display);
            lnk.setFont(new Font("Segoe UI", Font.PLAIN, 10));
            lnk.setForeground(DarkTheme.TEXT_DARK);
            lnk.setBackground(DarkTheme.BG_DARK);
            lnk.setBorder(BorderFactory.createEmptyBorder(3, 14, 3, 8));
            lnk.setHorizontalAlignment(SwingConstants.LEFT);
            lnk.setContentAreaFilled(false);
            lnk.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            lnk.setMaximumSize(new Dimension(Integer.MAX_VALUE, 24));
            lnk.setAlignmentX(LEFT_ALIGNMENT);
            final String url = s;
            lnk.addActionListener(e -> showAddDialog(url));
            lnk.addMouseListener(new MouseAdapter() {
                public void mouseEntered(MouseEvent e) { lnk.setForeground(DarkTheme.TEXT_SECONDARY); }
                public void mouseExited(MouseEvent e)  { lnk.setForeground(DarkTheme.TEXT_DARK); }
            });
            sidebar.add(lnk);
        }

        sidebar.add(Box.createVerticalGlue());
        return sidebar;
    }

    // ─── Table ───────────────────────────────────────────────────────────────

    private JTable buildTable() {
        JTable t = new JTable(tableModel) {
            @Override public Component prepareRenderer(TableCellRenderer r, int row, int col) {
                Component c = super.prepareRenderer(r, row, col);
                if (!isRowSelected(row)) {
                    DownloadTask task = tableModel.getTask(row);
                    c.setBackground(task != null && task.getStatus() == DownloadTask.Status.COMPLETED
                            ? new Color(0x06, 0x1a, 0x10) : DarkTheme.BG_DARKEST);
                }
                return c;
            }
        };

        t.setBackground(DarkTheme.BG_DARKEST);
        t.setForeground(DarkTheme.TEXT_PRIMARY);
        t.setGridColor(new Color(0x1a, 0x1f, 0x2e));
        t.setRowHeight(52);
        t.setSelectionBackground(DarkTheme.BG_MEDIUM);
        t.setSelectionForeground(DarkTheme.TEXT_PRIMARY);
        t.setShowHorizontalLines(true);
        t.setShowVerticalLines(false);
        t.setFocusable(false);
        t.setFillsViewportHeight(true);
        t.getTableHeader().setBackground(DarkTheme.BG_DARK);
        t.getTableHeader().setForeground(DarkTheme.TEXT_MUTED);
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 10));
        t.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, DarkTheme.BORDER));
        t.getTableHeader().setReorderingAllowed(false);

        // Column widths
        int[] widths = {36, 300, 130, 150, 90, 70, 90, 100};
        for (int i = 0; i < widths.length; i++) {
            t.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
            if (i == 0) { t.getColumnModel().getColumn(0).setMaxWidth(36); t.getColumnModel().getColumn(0).setMinWidth(36); }
        }

        // Custom renderers
        t.setDefaultRenderer(String.class, new DefaultTableCellRenderer() {
            { setOpaque(true); }
            @Override public Component getTableCellRendererComponent(JTable table, Object value,
                    boolean sel, boolean focus, int row, int col) {
                super.getTableCellRendererComponent(table, value, sel, focus, row, col);
                setBackground(sel ? DarkTheme.BG_MEDIUM : DarkTheme.BG_DARKEST);
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                DownloadTask task = tableModel.getTask(row);
                if (col == DownloadTableModel.COL_STATUS && task != null) {
                    setForeground(switch (task.getStatus()) {
                        case DOWNLOADING -> DarkTheme.ACCENT_LIGHT;
                        case COMPLETED   -> DarkTheme.GREEN;
                        case PAUSED      -> DarkTheme.YELLOW;
                        case FAILED, CANCELLED -> DarkTheme.RED;
                        default          -> DarkTheme.TEXT_MUTED;
                    });
                    setFont(new Font("Segoe UI", Font.BOLD, 11));
                } else if (col == DownloadTableModel.COL_SPEED) {
                    setForeground(DarkTheme.ACCENT_LIGHT);
                    setFont(new Font("Segoe UI", Font.PLAIN, 12));
                } else if (col == DownloadTableModel.COL_ICON) {
                    setHorizontalAlignment(CENTER);
                    setFont(new Font("Dialog", Font.PLAIN, 16));
                    setForeground(DarkTheme.TEXT_PRIMARY);
                } else if (col == DownloadTableModel.COL_NAME) {
                    setForeground(DarkTheme.TEXT_PRIMARY);
                    setFont(new Font("Segoe UI", Font.BOLD, 12));
                } else {
                    setForeground(DarkTheme.TEXT_SECONDARY);
                    setFont(new Font("Segoe UI", Font.PLAIN, 12));
                }
                return this;
            }
        });

        ProgressCellRenderer progressRenderer = new ProgressCellRenderer();
        t.getColumnModel().getColumn(DownloadTableModel.COL_PROGRESS).setCellRenderer(progressRenderer);

        ActionCellHandler actionHandler = new ActionCellHandler(tableModel);
        t.getColumnModel().getColumn(DownloadTableModel.COL_ACTIONS).setCellRenderer(actionHandler);
        t.getColumnModel().getColumn(DownloadTableModel.COL_ACTIONS).setCellEditor(actionHandler);

        return t;
    }

    // ─── Status Bar ──────────────────────────────────────────────────────────

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(DarkTheme.BG_DARK);
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, DarkTheme.BORDER),
                BorderFactory.createEmptyBorder(5, 14, 5, 14)));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabel.setForeground(DarkTheme.TEXT_MUTED);
        JLabel saveDir = new JLabel("Save to: " + defaultSavePath());
        saveDir.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        saveDir.setForeground(DarkTheme.TEXT_DARK);
        bar.add(statusLabel, BorderLayout.WEST);
        bar.add(saveDir, BorderLayout.EAST);
        return bar;
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────

    private void showAddDialog(String prefill) {
        AddDownloadDialog dlg = new AddDownloadDialog(this, prefill, defaultSavePath());
        dlg.setVisible(true);
        DownloadTask task = dlg.getResult();
        if (task != null) {
            tableModel.addTask(task);
            task.start();
            setStatus("Download started: " + task.getFileName());
        }
    }

    private void applyFilter() {
        // Swap model to a filtered one using a row sorter
        RowFilter<DownloadTableModel, Integer> rf = RowFilter.regexFilter(""); // show all by default
        TableRowSorter<DownloadTableModel> sorter = new TableRowSorter<>(tableModel);
        sorter.setRowFilter(switch (currentFilter) {
            case "Downloading" -> RowFilter.orFilter(java.util.List.of(
                    statusFilter(DownloadTask.Status.DOWNLOADING),
                    statusFilter(DownloadTask.Status.PAUSED)));
            case "Completed"   -> statusFilter(DownloadTask.Status.COMPLETED);
            case "YouTube"     -> new RowFilter<>() {
                public boolean include(Entry<? extends DownloadTableModel, ? extends Integer> e) {
                    DownloadTask t = tableModel.getTask(e.getIdentifier());
                    return t != null && t.getFileType() == DownloadTask.FileType.YOUTUBE;
                }
            };
            case "HTTP Files"  -> new RowFilter<>() {
                public boolean include(Entry<? extends DownloadTableModel, ? extends Integer> e) {
                    DownloadTask t = tableModel.getTask(e.getIdentifier());
                    return t != null && t.getFileType() != DownloadTask.FileType.YOUTUBE;
                }
            };
            default -> null; // show all
        });
        table.setRowSorter(sorter);
    }

    private RowFilter<DownloadTableModel, Integer> statusFilter(DownloadTask.Status status) {
        return new RowFilter<>() {
            public boolean include(Entry<? extends DownloadTableModel, ? extends Integer> e) {
                DownloadTask t = tableModel.getTask(e.getIdentifier());
                return t != null && t.getStatus() == status;
            }
        };
    }

    private void openDownloadsFolder() {
        java.io.File dir = new java.io.File(defaultSavePath());
        if (!dir.exists()) dir.mkdirs();
        try {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("mac")) {
                // Use 'open' — always works on macOS, reveals in Finder
                Runtime.getRuntime().exec(new String[]{"open", dir.getAbsolutePath()});
            } else if (os.contains("win")) {
                Runtime.getRuntime().exec(new String[]{"explorer", dir.getAbsolutePath()});
            } else {
                Runtime.getRuntime().exec(new String[]{"xdg-open", dir.getAbsolutePath()});
            }
            setStatus("Opened: " + dir.getAbsolutePath());
        } catch (Exception e) {
            setStatus("Could not open folder: " + e.getMessage());
        }
    }

    private void updateSpeedLabel() {
        double total = tableModel.getAllTasks().stream()
                .filter(t -> t.getStatus() == DownloadTask.Status.DOWNLOADING)
                .mapToDouble(DownloadTask::getSpeed).sum();
        if (total > 0) {
            speedLabel.setText("↓ " + Formatter.speed(total));
            speedLabel.setVisible(true);
        } else {
            speedLabel.setVisible(false);
        }
    }

    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
    }

    private String defaultSavePath() {
        String home = System.getProperty("user.home"); // /Users/yourname on macOS
        String saved = prefs.get("savePath", home + java.io.File.separator + "Downloads");
        // Expand ~ if present (safety)
        if (saved.startsWith("~")) saved = home + saved.substring(1);
        java.io.File dir = new java.io.File(saved);
        if (!dir.exists()) dir.mkdirs();
        return dir.getAbsolutePath();
    }

    private JToggleButton sidebarButton(String text) {
        Color selBg = DarkTheme.BG_MEDIUM;
        Color defBg = DarkTheme.BG_DARK;
        JToggleButton btn = new JToggleButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(isSelected() ? selBg : defBg);
                g2.fillRect(0, 0, getWidth(), getHeight());
                if (isSelected()) {
                    g2.setColor(DarkTheme.ACCENT_BLUE);
                    g2.fillRect(0, 0, 3, getHeight());
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setForeground(DarkTheme.TEXT_MUTED);
        btn.setBackground(defBg);
        btn.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btn.setAlignmentX(LEFT_ALIGNMENT);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addChangeListener(e ->
                btn.setForeground(btn.isSelected() ? DarkTheme.TEXT_PRIMARY : DarkTheme.TEXT_MUTED));
        return btn;
    }
}
