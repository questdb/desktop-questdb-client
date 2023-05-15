/*******************************************************************************
 *     ___                  _   ____  ____
 *    / _ \ _   _  ___  ___| |_|  _ \| __ )
 *   | | | | | | |/ _ \/ __| __| | | |  _ \
 *   | |_| | |_| |  __/\__ \ |_| |_| | |_) |
 *    \__\_\\__,_|\___||___/\__|____/|____/
 *
 *  Copyright (c) 2014-2019 Appsicle
 *  Copyright (c) 2019-2023 QuestDB
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

package io.quest.conns;

import java.awt.*;
import java.io.Closeable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionListener;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumnModel;

import io.quest.GTk;
import io.quest.CellRenderer;


class ConnsTableModel extends AbstractTableModel implements Closeable {
    private static final int NAME_COL_IDX = 0;
    private static final int HOST_COL_IDX = 1;
    private static final int PORT_COL_IDX = 2;
    private static final int DATABASE_COL_IDX = 3;
    private static final int USERNAME_COL_IDX = 4;
    private static final int PASSWORD_COL_IDX = 5;
    private static final int CONNECTED_COL_IDX = 6;
    private static final String NAME_COL = "name";
    private static final String CONNECTED_COL = "connected";
    private static final String[] COL_NAMES = {
            NAME_COL,
            ConnAttrs.AttrName.host.name(),
            ConnAttrs.AttrName.port.name(),
            ConnAttrs.AttrName.database.name(),
            ConnAttrs.AttrName.username.name(),
            ConnAttrs.AttrName.password.name(),
            CONNECTED_COL
    };
    private static final int ROW_HEIGHT = 22;
    private static final int[] COL_WIDTHS = {
            200, 400, 100, 200, 200, 200, 200
    };
    private final List<Conn> conns;
    private final Set<String> existingNames;

    private ConnsTableModel() {
        conns = new ArrayList<>();
        existingNames = new TreeSet<>();
    }

    static JTable createTable(TableModelListener onTableModelEvent, ListSelectionListener selectionListener) {
        ConnsTableModel tableModel = new ConnsTableModel();
        tableModel.addTableModelListener(tableModel::onTableModelEvent);
        tableModel.addTableModelListener(onTableModelEvent);
        JTable table = new JTable(tableModel);
        table.setAutoCreateRowSorter(false);
        table.setRowHeight(ROW_HEIGHT);
        table.setGridColor(GTk.EDITOR_KEYWORD_FOREGROUND_COLOR.darker().darker().darker());
        table.setFont(GTk.TABLE_CELL_FONT);
        table.setDefaultRenderer(String.class, new CellRenderer());
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(selectionListener);
        JTableHeader header = table.getTableHeader();
        header.setReorderingAllowed(false);
        header.setFont(GTk.TABLE_HEADER_FONT);
        header.setBackground(GTk.QUEST_APP_BACKGROUND_COLOR);
        header.setForeground(Color.WHITE);
        TableColumnModel colModel = table.getTableHeader().getColumnModel();
        colModel.setColumnSelectionAllowed(false);
        colModel.getColumn(NAME_COL_IDX).setPreferredWidth(COL_WIDTHS[NAME_COL_IDX]);
        colModel.getColumn(HOST_COL_IDX).setPreferredWidth(COL_WIDTHS[HOST_COL_IDX]);
        colModel.getColumn(PORT_COL_IDX).setPreferredWidth(COL_WIDTHS[PORT_COL_IDX]);
        colModel.getColumn(DATABASE_COL_IDX).setPreferredWidth(COL_WIDTHS[DATABASE_COL_IDX]);
        colModel.getColumn(USERNAME_COL_IDX).setPreferredWidth(COL_WIDTHS[USERNAME_COL_IDX]);
        colModel.getColumn(PASSWORD_COL_IDX).setPreferredWidth(COL_WIDTHS[PASSWORD_COL_IDX]);
        colModel.getColumn(CONNECTED_COL_IDX).setPreferredWidth(COL_WIDTHS[CONNECTED_COL_IDX]);
        colModel.getColumn(PASSWORD_COL_IDX).setCellRenderer(new PasswordCellRenderer());
        return table;
    }

    List<Conn> getConns() {
        return conns;
    }

    void setConns(List<Conn> newConns) {
        conns.clear();
        existingNames.clear();
        if (newConns != null) {
            for (Conn conn : newConns) {
                conns.add(conn);
                existingNames.add(conn.getName());
            }
            fireTableDataChanged();
        }
    }

    boolean containsName(String name) {
        return name != null && existingNames.contains(name);
    }

    boolean containsConn(Conn conn) {
        return conn != null && -1 != getRowIdx(conn.getUniqueId());
    }

    int addConn(Conn conn) {
        if (conn == null) {
            return -1;
        }
        conns.add(conn);
        int idx = conns.size() - 1;
        existingNames.add(conn.getName());
        fireTableRowsInserted(idx, idx);
        return idx;
    }

    Conn removeConn(int rowIdx) {
        Conn conn = conns.remove(rowIdx);
        existingNames.remove(conn.getName());
        fireTableRowsDeleted(rowIdx, rowIdx);
        return conn;
    }

    int getRowIdx(String connKey) {
        if (connKey == null) {
            return -1;
        }
        for (int i = 0; i < conns.size(); i++) {
            Conn conn = conns.get(i);
            if (conn.getUniqueId().equals(connKey)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getRowCount() {
        return conns.size();
    }

    @Override
    public int getColumnCount() {
        return COL_NAMES.length;
    }

    @Override
    public String getColumnName(int colIdx) {
        return COL_NAMES[colIdx];
    }

    @Override
    public void setValueAt(Object value, int rowIdx, int colIdx) {
        String attrName = COL_NAMES[colIdx];
        if (!NAME_COL.equals(attrName)) {
            Conn conn = conns.get(rowIdx);
            conn.setAttr(attrName, (String) value, "");
            fireTableCellUpdated(rowIdx, colIdx);
        }
    }

    Conn getValueAt(int rowIndex) {
        return (Conn) getValueAt(rowIndex, -1);
    }

    @Override
    public Object getValueAt(int rowIdx, int colIdx) {
        if (colIdx == -1) {
            return conns.get(rowIdx);
        }
        Conn conn = conns.get(rowIdx);
        String attrName = COL_NAMES[colIdx];
        return switch (attrName) {
            case NAME_COL -> conn.getName();
            case CONNECTED_COL -> conn.isOpen() ? "Yes" : "No";
            default -> conn.getAttr(attrName);
        };
    }

    @Override
    public boolean isCellEditable(int rowIdx, int colIdx) {
        return NAME_COL_IDX != colIdx && CONNECTED_COL_IDX != colIdx;
    }

    @Override
    public Class<?> getColumnClass(int colIdx) {
        return String.class;
    }

    @Override
    public void close() {
        conns.clear();
        existingNames.clear();
    }

    private void onTableModelEvent(TableModelEvent event) {
        if (event.getType() == TableModelEvent.UPDATE) {
            int ri = event.getFirstRow();
            int ci = event.getColumn();
            if (ri > -1 && ri < conns.size() && ci > 0 && ci < COL_NAMES.length) {
                Conn updated = conns.get(ri);
                if (updated.isOpen()) {
                    updated.close();
                }
            }
        }
    }

    private static class PasswordCellRenderer extends CellRenderer {
        private static final String PASSWORD = "*********";

        @Override
        public Component getTableCellRendererComponent(
                JTable table,
                Object value,
                boolean isSelected,
                boolean hasFocus,
                int rowIdx,
                int colIdx
        ) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, rowIdx, colIdx);
            setValue(PASSWORD);
            return this;
        }
    }
}
