package com.fluxdm;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;

public class DownloadTableModel extends AbstractTableModel {

    public static final int COL_ICON     = 0;
    public static final int COL_NAME     = 1;
    public static final int COL_SIZE     = 2;
    public static final int COL_PROGRESS = 3;
    public static final int COL_SPEED    = 4;
    public static final int COL_ETA      = 5;
    public static final int COL_STATUS   = 6;
    public static final int COL_ACTIONS  = 7;

    private static final String[] COLUMNS = {"", "Name / URL", "Size", "Progress", "Speed", "ETA", "Status", "Actions"};

    private final List<DownloadTask> tasks = new ArrayList<>();

    public void addTask(DownloadTask t) {
        tasks.add(0, t);
        fireTableRowsInserted(0, 0);
        t.setOnUpdate(task -> {
            int idx = tasks.indexOf(task);
            if (idx >= 0) fireTableRowsUpdated(idx, idx);
        });
    }

    public void removeTask(DownloadTask t) {
        int idx = tasks.indexOf(t);
        if (idx >= 0) { tasks.remove(idx); fireTableRowsDeleted(idx, idx); }
    }

    public DownloadTask getTask(int row) {
        return (row >= 0 && row < tasks.size()) ? tasks.get(row) : null;
    }

    public List<DownloadTask> getAllTasks() { return tasks; }

    @Override public int getRowCount() { return tasks.size(); }
    @Override public int getColumnCount() { return COLUMNS.length; }
    @Override public String getColumnName(int col) { return COLUMNS[col]; }
    @Override public boolean isCellEditable(int r, int c) { return c == COL_ACTIONS; }

    @Override public Class<?> getColumnClass(int col) {
        return switch (col) {
            case COL_PROGRESS, COL_ACTIONS -> DownloadTask.class;
            default -> String.class;
        };
    }

    @Override public Object getValueAt(int row, int col) {
        DownloadTask t = tasks.get(row);
        return switch (col) {
            case COL_ICON     -> Formatter.icon(t.getFileType());
            case COL_NAME     -> t.getFileName();
            case COL_SIZE     -> t.getTotalSize() > 0
                                    ? Formatter.bytes(t.getDownloaded()) + " / " + Formatter.bytes(t.getTotalSize())
                                    : "—";
            case COL_PROGRESS -> t;
            case COL_SPEED    -> t.getStatus() == DownloadTask.Status.DOWNLOADING
                                    ? Formatter.speed(t.getSpeed()) : "—";
            case COL_ETA      -> t.getStatus() == DownloadTask.Status.DOWNLOADING
                                    ? Formatter.eta(t.getSpeed(), t.getTotalSize() - t.getDownloaded()) : "—";
            case COL_STATUS   -> t.getStatus().toString().charAt(0)
                                    + t.getStatus().toString().substring(1).toLowerCase();
            case COL_ACTIONS  -> t;
            default -> "";
        };
    }
}
