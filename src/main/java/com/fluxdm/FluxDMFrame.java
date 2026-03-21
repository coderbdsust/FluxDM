package com.fluxdm;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.util.prefs.Preferences;

public class FluxDMFrame extends JFrame {

    private final DownloadTableModel tableModel = new DownloadTableModel();
    private JTable table;
    private final JLabel statusLabel = new JLabel("Ready");
    private final JLabel speedLabel  = new JLabel();
    private final Preferences prefs  = Preferences.userNodeForPackage(FluxDMFrame.class);
    private String currentFilter     = "All Downloads";
    private IntegrationServer integrationServer;

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
        rebuildUI();

        // Speed update timer
        new Timer(500, e -> updateSpeedLabel()).start();

        // Listen for theme changes and rebuild
        ThemeManager.addThemeChangeListener(this::rebuildUI);

        // Start browser integration server
        integrationServer = new IntegrationServer(this::addUrlFromExternal);
        integrationServer.start();

        // Stop server on window close
        addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent e) {
                integrationServer.stop();
            }
        });
    }

    /** Called from IntegrationServer when a URL is received from the browser extension. */
    private void addUrlFromExternal(String url) {
        SwingUtilities.invokeLater(() -> {
            // Bring window to front
            if (getState() == ICONIFIED) setState(NORMAL);
            toFront();
            requestFocus();
            showAddDialog(url);
        });
    }

    private void rebuildUI() {
        getContentPane().removeAll();
        table = buildTable();
        setLayout(new BorderLayout());
        add(buildTitleBar(),  BorderLayout.NORTH);
        add(buildCenter(),    BorderLayout.CENTER);
        add(buildStatusBar(), BorderLayout.SOUTH);
        revalidate();
        repaint();
    }

    // ─── Title Bar ───────────────────────────────────────────────────────────

    private JPanel buildTitleBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBackground(ThemeManager.bgDark());
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.border()));
        bar.setPreferredSize(new Dimension(0, 46));

        // Left: logo
        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 14, 0));
        left.setBackground(ThemeManager.bgDark());
        left.setOpaque(true);
        JLabel logo = new JLabel("\u2B07  FluxDM");
        logo.setFont(new Font("Segoe UI", Font.BOLD, 14));
        logo.setForeground(ThemeManager.accentLight());
        JLabel ver = new JLabel("Download Manager v2.0");
        ver.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        ver.setForeground(ThemeManager.textDark());
        left.add(logo);
        left.add(ver);

        // Right: speed badge + theme toggle
        speedLabel.setFont(new Font("Segoe UI", Font.BOLD, 11));
        speedLabel.setForeground(ThemeManager.green());
        speedLabel.setOpaque(true);
        speedLabel.setBackground(ThemeManager.speedBg());
        speedLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ThemeManager.speedBg()),
                BorderFactory.createEmptyBorder(2, 10, 2, 10)));
        speedLabel.setVisible(false);

        JToggleButton themeBtn = new JToggleButton(ThemeManager.isDark() ? "\u2600" : "\uD83C\uDF19");
        themeBtn.setSelected(ThemeManager.isDark());
        themeBtn.setToolTipText(ThemeManager.isDark() ? "Switch to light theme" : "Switch to dark theme");
        themeBtn.setFont(new Font("Dialog", Font.PLAIN, 14));
        themeBtn.setPreferredSize(new Dimension(36, 30));
        themeBtn.setBackground(new Color(0x37, 0x41, 0x51));
        themeBtn.setForeground(ThemeManager.textPrimary());
        themeBtn.setFocusPainted(false);
        themeBtn.setBorderPainted(false);
        themeBtn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        themeBtn.addActionListener(e -> ThemeManager.toggle());

        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        right.setBackground(ThemeManager.bgDark());
        right.add(speedLabel);
        right.add(themeBtn);

        bar.add(left, BorderLayout.WEST);
        bar.add(right, BorderLayout.EAST);
        return bar;
    }

    // ─── Toolbar ─────────────────────────────────────────────────────────────

    private JPanel buildToolbar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 8));
        bar.setBackground(ThemeManager.bgDark());
        bar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.border()));

        JButton addBtn     = ThemeManager.createButton("+ Add URL",        new Color(0x25, 0x63, 0xeb));
        JButton pauseAll   = ThemeManager.createButton("\u23F8  Pause All",     new Color(0x37, 0x41, 0x51));
        JButton resumeAll  = ThemeManager.createButton("\u25B6  Resume All",    new Color(0x37, 0x41, 0x51));
        JButton clearDone  = ThemeManager.createButton("\u2715  Clear Done",    new Color(0x37, 0x41, 0x51));
        JButton openFolder = ThemeManager.createButton("\uD83D\uDCC2  Downloads",    new Color(0x37, 0x41, 0x51));

        bar.add(addBtn);
        bar.add(pauseAll);
        bar.add(resumeAll);
        bar.add(clearDone);
        bar.add(openFolder);

        // Stats label (right-aligned trick with a second panel)
        JLabel statsLabel = new JLabel("Total: 0   Active: 0   Done: 0");
        statsLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statsLabel.setForeground(ThemeManager.textMuted());
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
        tablePanel.setBackground(ThemeManager.bgDarkest());
        tablePanel.add(buildToolbar(), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBackground(ThemeManager.bgDarkest());
        scroll.getViewport().setBackground(ThemeManager.bgDarkest());
        scroll.setBorder(BorderFactory.createEmptyBorder());
        tablePanel.add(scroll, BorderLayout.CENTER);

        JSplitPane split = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, tablePanel);
        split.setDividerLocation(185);
        split.setDividerSize(1);
        split.setBorder(null);
        split.setBackground(ThemeManager.border());
        return split;
    }

    // ─── Sidebar ─────────────────────────────────────────────────────────────

    private JPanel buildSidebar() {
        JPanel sidebar = new JPanel();
        sidebar.setLayout(new BoxLayout(sidebar, BoxLayout.Y_AXIS));
        sidebar.setBackground(ThemeManager.bgDark());
        sidebar.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 1, ThemeManager.border()));
        sidebar.setPreferredSize(new Dimension(185, 0));

        String[][] items = {
                {"\uD83D\uDCE5", "All Downloads"},
                {"\u2B07",  "Downloading"},
                {"\u2713",  "Completed"}
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
                            ? ThemeManager.completedRow() : ThemeManager.bgDarkest());
                }
                return c;
            }
        };

        t.setBackground(ThemeManager.bgDarkest());
        t.setForeground(ThemeManager.textPrimary());
        t.setGridColor(ThemeManager.gridColor());
        t.setRowHeight(52);
        t.setSelectionBackground(ThemeManager.bgMedium());
        t.setSelectionForeground(ThemeManager.textPrimary());
        t.setShowHorizontalLines(true);
        t.setShowVerticalLines(false);
        t.setFocusable(false);
        t.setFillsViewportHeight(true);
        t.getTableHeader().setBackground(ThemeManager.bgDark());
        t.getTableHeader().setForeground(ThemeManager.textMuted());
        t.getTableHeader().setFont(new Font("Segoe UI", Font.BOLD, 10));
        t.getTableHeader().setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, ThemeManager.border()));
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
                setBackground(sel ? ThemeManager.bgMedium() : ThemeManager.bgDarkest());
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                DownloadTask task = tableModel.getTask(row);
                if (col == DownloadTableModel.COL_STATUS && task != null) {
                    setForeground(switch (task.getStatus()) {
                        case DOWNLOADING -> ThemeManager.accentLight();
                        case COMPLETED   -> ThemeManager.green();
                        case PAUSED      -> ThemeManager.yellow();
                        case FAILED, CANCELLED -> ThemeManager.red();
                        default          -> ThemeManager.textMuted();
                    });
                    setFont(new Font("Segoe UI", Font.BOLD, 11));
                } else if (col == DownloadTableModel.COL_SPEED) {
                    setForeground(ThemeManager.accentLight());
                    setFont(new Font("Segoe UI", Font.PLAIN, 12));
                } else if (col == DownloadTableModel.COL_ICON) {
                    setHorizontalAlignment(CENTER);
                    setFont(new Font("Dialog", Font.PLAIN, 16));
                    setForeground(ThemeManager.textPrimary());
                } else if (col == DownloadTableModel.COL_NAME) {
                    setForeground(ThemeManager.textPrimary());
                    setFont(new Font("Segoe UI", Font.BOLD, 12));
                } else {
                    setForeground(ThemeManager.textSecondary());
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
        bar.setBackground(ThemeManager.bgDark());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ThemeManager.border()),
                BorderFactory.createEmptyBorder(5, 14, 5, 14)));
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        statusLabel.setForeground(ThemeManager.textMuted());
        JLabel saveDir = new JLabel("Save to: " + defaultSavePath());
        saveDir.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        saveDir.setForeground(ThemeManager.textDark());
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
            speedLabel.setText("\u2193 " + Formatter.speed(total));
            speedLabel.setVisible(true);
        } else {
            speedLabel.setVisible(false);
        }
    }

    private void setStatus(String msg) {
        SwingUtilities.invokeLater(() -> statusLabel.setText(msg));
    }

    private String defaultSavePath() {
        String home = System.getProperty("user.home");
        String saved = prefs.get("savePath", home + java.io.File.separator + "Downloads");
        if (saved.startsWith("~")) saved = home + saved.substring(1);
        java.io.File dir = new java.io.File(saved);
        if (!dir.exists()) dir.mkdirs();
        return dir.getAbsolutePath();
    }

    private JToggleButton sidebarButton(String text) {
        JToggleButton btn = new JToggleButton(text) {
            @Override protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(isSelected() ? ThemeManager.bgMedium() : ThemeManager.bgDark());
                g2.fillRect(0, 0, getWidth(), getHeight());
                if (isSelected()) {
                    g2.setColor(ThemeManager.accentBlue());
                    g2.fillRect(0, 0, 3, getHeight());
                }
                g2.dispose();
                super.paintComponent(g);
            }
        };
        btn.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        btn.setForeground(ThemeManager.textMuted());
        btn.setBackground(ThemeManager.bgDark());
        btn.setBorder(BorderFactory.createEmptyBorder(10, 14, 10, 14));
        btn.setHorizontalAlignment(SwingConstants.LEFT);
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        btn.setAlignmentX(LEFT_ALIGNMENT);
        btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        btn.addChangeListener(e ->
                btn.setForeground(btn.isSelected() ? ThemeManager.textPrimary() : ThemeManager.textMuted()));
        return btn;
    }
}
