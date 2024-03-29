package io.questdb.desktop.ui.results;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.io.Closeable;
import java.sql.Types;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.*;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.table.TableColumnModel;

import io.questdb.desktop.GTk;
import io.questdb.desktop.model.SQLExecutionResponse;
import io.questdb.desktop.model.SQLPagedTableModel;
import io.questdb.desktop.model.SQLType;
import io.questdb.desktop.model.Table;
import io.questdb.desktop.ui.editor.Editor;


public class SQLResultsTable extends JPanel implements Closeable {
    private static final Dimension STATUS_LABEL_SIZE = new Dimension(600, 35);
    private static final Dimension NAVIGATION_LABEL_SIZE = new Dimension(300, 35);
    private static final Dimension NAVIGATION_BUTTON_SIZE = new Dimension(100, 35);
    private static final int TABLE_ROW_HEIGHT = 30;
    private static final int TABLE_HEADER_HEIGHT = 50;
    private final JTable table;
    private final JScrollPane tableScrollPanel;
    private final SQLPagedTableModel tableModel;
    private final AtomicReference<Table> results;
    private final Editor questPanel;
    private final JLabel rowRangeLabel;
    private final JLabel statsLabel;
    private final JButton prevButton;
    private final JButton nextButton;
    private final InfiniteSpinner infiniteSpinner;
    private Component currentModePanel;
    private Mode mode;

    public SQLResultsTable(int width, int height) {
        Dimension size = new Dimension(width, height);
        results = new AtomicReference<>();
        tableModel = new SQLPagedTableModel(results::get);
        table = new JTable(tableModel);
        table.setAutoCreateRowSorter(false);
        table.setRowSelectionAllowed(true);
        table.setColumnSelectionAllowed(true);
        table.setCellSelectionEnabled(true);
        table.setRowHeight(TABLE_ROW_HEIGHT);
        table.setGridColor(GTk.Editor.KEYWORD_FOREGROUND_COLOR.darker().darker().darker());
        table.setFont(GTk.TABLE_CELL_FONT);
        table.setDefaultRenderer(String.class, new SQLCellRenderer(results::get));
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        GTk.Keyboard.setupTableCmdKeyActions(table);
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setFont(GTk.TABLE_HEADER_FONT);
        header.setForeground(GTk.APP_BACKGROUND_COLOR);
        statsLabel = new JLabel();
        statsLabel.setFont(GTk.MENU_FONT);
        statsLabel.setBackground(GTk.APP_BACKGROUND_COLOR);
        statsLabel.setForeground(Color.WHITE);
        statsLabel.setPreferredSize(STATUS_LABEL_SIZE);
        statsLabel.setHorizontalAlignment(JLabel.RIGHT);
        rowRangeLabel = new JLabel();
        rowRangeLabel.setFont(GTk.MENU_FONT);
        rowRangeLabel.setBackground(GTk.APP_BACKGROUND_COLOR);
        rowRangeLabel.setForeground(Color.WHITE);
        rowRangeLabel.setPreferredSize(NAVIGATION_LABEL_SIZE);
        rowRangeLabel.setHorizontalAlignment(JLabel.RIGHT);
        prevButton = GTk.button(GTk.Icon.RESULTS_PREV, "Go to previous page", this::onPrevButton);
        prevButton.setFont(GTk.MENU_FONT);
        prevButton.setBackground(GTk.APP_BACKGROUND_COLOR);
        prevButton.setForeground(Color.WHITE);
        prevButton.setPreferredSize(NAVIGATION_BUTTON_SIZE);
        nextButton = GTk.button(GTk.Icon.RESULTS_NEXT, "Go to next page", this::onNextButton);
        nextButton.setFont(GTk.MENU_FONT);
        nextButton.setBackground(GTk.APP_BACKGROUND_COLOR);
        nextButton.setForeground(Color.WHITE);
        nextButton.setPreferredSize(NAVIGATION_BUTTON_SIZE);
        nextButton.setHorizontalTextPosition(SwingConstants.LEFT);
        JPanel southPanel = GTk.flowPanel(statsLabel, rowRangeLabel, prevButton, nextButton);
        southPanel.setBackground(GTk.APP_BACKGROUND_COLOR);
        questPanel = new Editor(false, false);
        tableScrollPanel = new JScrollPane(
            table,
            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
            JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        JViewport viewport = tableScrollPanel.getViewport();
        viewport.setBackground(GTk.APP_BACKGROUND_COLOR);
        viewport.setExtentSize(size);
        infiniteSpinner = new InfiniteSpinner();
        infiniteSpinner.setSize(size);
        changeMode(Mode.TABLE);
        setLayout(new BorderLayout());
        setPreferredSize(size);
        setBackground(GTk.APP_BACKGROUND_COLOR);
        add(currentModePanel, BorderLayout.CENTER);
        add(southPanel, BorderLayout.SOUTH);
        updateRowNavigationComponents();
    }

    public SQLPagedTableModel getTable() {
        return tableModel;
    }

    public void updateStats(String eventType, SQLExecutionResponse res) {
        if (res != null) {
            statsLabel.setText(String.format(
                "[%s]  Exec: %5d,  Fetch: %5d,  Total: %6d (ms)",
                eventType,
                res.getExecMillis(),
                res.getFetchMillis(),
                res.getTotalMillis()));
        } else {
            statsLabel.setText("");
        }
    }

    public void onResultsStarted() {
        infiniteSpinner.start();
        changeMode(Mode.INFINITE);
    }

    public void onMetadataAvailable(SQLExecutionResponse res) {
        if (results.compareAndSet(null, res.getTable())) {
            resetTableHeader();
        }
    }

    public void onRowsAvailable(SQLExecutionResponse res) {
        if (res.getTable().size() > 0) {
            tableModel.fireTableDataChanged();
            infiniteSpinner.close();
            changeMode(Mode.TABLE);
            updateRowNavigationComponents();
        }
    }

    public void onRowsCompleted(SQLExecutionResponse res) {
        tableModel.fireTableDataChanged(true);
        infiniteSpinner.close();
        Table table = res.getTable();
        int size = table.size();
        if (table.isSingleRowSingleVarcharColumn() || size == 0) {
            questPanel.displayMessage(size == 0 ?
                "OK.\n\nNo results for query:\n" + res.getSqlCommand()
                :
                (String) table.getValueAt(0, 0));
            changeMode(Mode.MESSAGE);
        } else {
            changeMode(Mode.TABLE);
            updateRowNavigationComponents();
        }
    }

    @Override
    public void close() {
        Table table = results.getAndSet(null);
        if (table != null) {
            table.close();
        }
        tableModel.fireTableStructureChanged();
        tableModel.fireTableDataChanged();
        infiniteSpinner.close();
        updateStats(null, null);
        updateRowNavigationComponents();
        changeMode(Mode.TABLE);
    }

    public void displayMessage(String message) {
        questPanel.displayMessage(message);
        changeMode(Mode.MESSAGE);
    }

    public void displayError(Throwable error) {
        questPanel.displayError(error);
        changeMode(Mode.MESSAGE);
    }

    public void displayError(String error) {
        questPanel.displayError(error);
        changeMode(Mode.MESSAGE);
    }

    public void onPrevButton(ActionEvent event) {
        if (prevButton.isEnabled() && tableModel.canDecPage()) {
            tableModel.decPage();
            updateRowNavigationComponents();
        }
    }

    public void onNextButton(ActionEvent event) {
        if (nextButton.isEnabled() && tableModel.canIncPage()) {
            tableModel.incPage();
            updateRowNavigationComponents();
        }
    }

    private void updateRowNavigationComponents() {
        prevButton.setEnabled(tableModel.canDecPage());
        nextButton.setEnabled(tableModel.canIncPage());
        int start = tableModel.getPageStartOffset();
        int end = tableModel.getPageEndOffset();
        int tableSize = tableModel.getTableSize();
        if (tableSize > 0) {
            start++;
        }
        rowRangeLabel.setText(String.format("Rows %d to %d of %-10d", start, end, tableSize));
    }

    private void resetTableHeader() {
        tableModel.fireTableStructureChanged();
        JTableHeader header = table.getTableHeader();
        header.setForeground(Color.WHITE);
        header.setBackground(GTk.APP_BACKGROUND_COLOR);
        header.setPreferredSize(new Dimension(0, TABLE_HEADER_HEIGHT));
        
        TableColumnModel colModel = table.getColumnModel();
        Table resultsTable = results.get();
        int tWidth = 0;
        for (int i = 0, n = colModel.getColumnCount(); i < n; i++) {
            int width = resolveColWidth(resultsTable, i);
            tWidth += width;
            TableColumn col = colModel.getColumn(i);
            col.setMinWidth(width);
            col.setPreferredWidth(width);
        }
        table.setAutoResizeMode(tWidth < getWidth() ? JTable.AUTO_RESIZE_ALL_COLUMNS : JTable.AUTO_RESIZE_OFF);
    }

    private void changeMode(Mode newMode) {
        if (mode != newMode) {
            mode = newMode;
            Component toRemove = currentModePanel;
            switch (newMode) {
                case TABLE -> currentModePanel = tableScrollPanel;
                case INFINITE -> currentModePanel = infiniteSpinner;
                case MESSAGE -> currentModePanel = questPanel;
            }
            if (toRemove != null) {
                remove(toRemove);
            }
            add(currentModePanel, BorderLayout.CENTER);
            validate();
            repaint();
        }
    }

    private enum Mode {INFINITE, TABLE, MESSAGE}

    private static int resolveColWidth(Table table, int colIdx) {
        int sqlType = table.getColumnTypes()[colIdx];
        final int width;
        switch (sqlType) {
            case Types.BIT, Types.BOOLEAN, Types.CHAR, Types.ROWID, Types.SMALLINT -> width = 100;
            case Types.INTEGER -> width = 120;
            case Types.DATE, Types.TIME, Types.BIGINT -> width = 200;
            case Types.TIMESTAMP, Types.DOUBLE, Types.REAL -> width = 250;
            case Types.BINARY -> width = 400;
            case Types.VARCHAR -> {
                int w = 0;
                for (int rowIdx = 0; rowIdx < Math.min(table.size(), 20); rowIdx++) {
                    Object value = table.getValueAt(rowIdx, colIdx);
                    if (value != null) {
                        w = Math.max(w, 15 * value.toString().length());
                    }
                }
                width = Math.min(w, 620);
            }
            default -> width = 150;
        }
        String colName = table.getColumnNames()[colIdx];
        String typeName = SQLType.resolveName(sqlType);
        return Math.max(width, 20 * (colName.length() + typeName.length()));
    }
}
