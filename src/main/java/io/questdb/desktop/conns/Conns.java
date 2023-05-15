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

package io.questdb.desktop.conns;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.Closeable;
import java.util.List;
import java.util.Set;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.TableModelEvent;

import io.questdb.desktop.GTk;
import io.questdb.desktop.EventConsumer;
import io.questdb.desktop.EventProducer;
import io.questdb.desktop.store.Store;

import io.questdb.log.Log;
import io.questdb.log.LogFactory;


public class Conns extends JDialog implements EventProducer<Conns.EventType>, Closeable {

    public static final String STORE_FILE_NAME = "connections.json";
    private static final Log LOG = LogFactory.getLog(Conns.class);
    private final EventConsumer<Conns, Object> eventConsumer;
    private final Store<Conn> store;
    private final JButton assignButton;
    private final JButton testButton;
    private final JButton connectButton;
    private final JButton cloneButton;
    private final JButton removeButton;
    private final JButton reloadButton;
    private final JTable table;
    private final ConnsTableModel tableModel;
    private final ConnsChecker connsValidityChecker;

    public Conns(Frame owner, EventConsumer<Conns, Object> eventConsumer) {
        super(owner, "Connections", false); // does not block use of the main app
        GTk.configureDialog(this, 0.8F, 0.35F, () -> eventConsumer.onSourceEvent(Conns.this, EventType.HIDE_REQUEST, null));
        setAlwaysOnTop(true);
        setModalityType(ModalityType.APPLICATION_MODAL);
        this.eventConsumer = eventConsumer;
        store = new Store<>(STORE_FILE_NAME, Conn.class) {
            @Override
            public Conn[] defaultStoreEntries() {
                return new Conn[]{
                        new Conn("QuestDB"),
                        new Conn("Postgres",
                                "localhost",
                                "5432",
                                "postgres",
                                "postgres",
                                "password")
                };
            }
        };
        table = ConnsTableModel.createTable(this::onTableModelUpdate, this::onListSelection);
        tableModel = (ConnsTableModel) table.getModel();
        JScrollPane tableScrollPanel = new JScrollPane(
                table,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        tableScrollPanel.getViewport().setBackground(GTk.APP_BACKGROUND_COLOR);
        connsValidityChecker = new ConnsChecker(tableModel::getConns, this::onLostConnsEvent);
        reloadButton = GTk.button(GTk.Icon.COMMAND_RELOAD, "Reload last saved connections", this::onReload);
        cloneButton = GTk.button(GTk.Icon.CONN_CLONE, "Clone selected connection", this::onCloneConn);
        JButton addButton = GTk.button(GTk.Icon.CONN_ADD, "Add connection", this::onAddConn);
        removeButton = GTk.button(GTk.Icon.CONN_REMOVE, "Remove selected connection", this::onRemove);
        testButton = GTk.button(GTk.Icon.CONN_TEST, "Test selected connection", this::onTest);
        connectButton = GTk.button(GTk.Icon.CONN_CONNECT, "Connect selected connection", this::onConnect);
        assignButton = GTk.button(GTk.Icon.CONN_ASSIGN, "Assign selected connection", this::onAssign);
        JPanel buttons = GTk.flowPanel(
                BorderFactory.createLineBorder(Color.WHITE, 1, true),
                GTk.APP_BACKGROUND_COLOR,
                50,
                0,
                GTk.flowPanel(reloadButton, cloneButton, addButton, removeButton),
                GTk.flowPanel(testButton, connectButton, assignButton));
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        contentPane.add(tableScrollPanel, BorderLayout.CENTER);
        contentPane.add(buttons, BorderLayout.SOUTH);
    }

    public Conn getSelectedConn() {
        int rowIdx = table.getSelectedRow();
        return rowIdx != -1 ? tableModel.getValueAt(rowIdx) : null;
    }

    public void setSelectedConn(Conn conn) {
        if (conn == null) {
            return;
        }
        int rowIdx = tableModel.getRowIdx(conn.getUniqueId());
        if (rowIdx >= 0) {
            table.getSelectionModel().setSelectionInterval(rowIdx, rowIdx);
            eventConsumer.onSourceEvent(this, EventType.CONNECTION_SELECTED, conn);
        }
    }

    public void start() {
        if (!connsValidityChecker.isRunning()) {
            onReload(null);
            connsValidityChecker.start();
        }
    }

    @Override
    public void close() {
        connsValidityChecker.close();
        tableModel.close();
        store.close();
    }

    public void onConnectEvent(Conn conn) {
        if (conn == null) {
            JOptionPane.showMessageDialog(
                    this,
                    "Connection not set",
                    "Connection Failed",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (!tableModel.containsConn(conn)) {
            return;
        }
        if (!conn.isOpen()) {
            try {
                conn.open();
                eventConsumer.onSourceEvent(this, EventType.CONNECTION_ESTABLISHED, conn);
            } catch (Exception e) {
                LOG.error().$("Connect [e=").$(e.getMessage()).I$();
                eventConsumer.onSourceEvent(this, EventType.CONNECTION_FAILED, conn);
            }
        } else {
            try {
                conn.close();
            } catch (RuntimeException e) {
                LOG.error().$("Disconnect [e=").$(e.getMessage()).I$();
            } finally {
                eventConsumer.onSourceEvent(this, EventType.CONNECTION_CLOSED, conn);
            }
        }
        toggleComponents();
    }

    private void onLostConnsEvent(Set<Conn> lostConns) {
        StringBuilder sb = new StringBuilder();
        for (Conn conn : lostConns) {
            sb.append(conn.getUri()).append(" as '").append(conn.getUsername()).append("', ");
        }
        if (sb.length() > 2) {
            sb.setLength(sb.length() - 2);
        }
        LOG.error().$("lost connection [conns=").$(sb.toString()).I$();
        GTk.invokeLater(() -> {
            toggleComponents();
            eventConsumer.onSourceEvent(this, EventType.CONNECTIONS_LOST, lostConns);
        });
    }

    private void onConnect(ActionEvent event) {
        onConnectEvent(getSelectedConn());
    }

    private void onListSelection(ListSelectionEvent event) {
        toggleComponents();
    }

    private void onTableModelUpdate(TableModelEvent event) {
        if (event.getType() == TableModelEvent.UPDATE) {
            toggleComponents();
            if (event.getColumn() >= 0 && event.getLastRow() != Integer.MAX_VALUE) {
                store.asyncSaveToFile();
            }
        }
    }

    private void onReload(ActionEvent event) {
        int selectedIdx = table.getSelectedRow();
        Conn selected = getSelectedConn();
        store.loadFromFile();
        List<Conn> conns = store.entries();
        tableModel.setConns(conns);
        if (conns.size() > 0) {
            if (selectedIdx >= 0 && selectedIdx < conns.size()) {
                Conn conn = tableModel.getValueAt(selectedIdx);
                if (conn.equals(selected)) {
                    table.getSelectionModel().addSelectionInterval(selectedIdx, selectedIdx);
                }
            } else {
                selectedIdx = 0;
                for (int i = 0; i < conns.size(); i++) {
                    if (conns.get(i).isDefault()) {
                        if (selectedIdx == 0) {
                            selectedIdx = i;
                        } else {
                            throw new IllegalStateException("too many default connections");
                        }
                    }
                }
                setSelectedConn(tableModel.getValueAt(selectedIdx));
            }
        }
    }

    private void onCloneConn(ActionEvent event) {
        Conn conn = getSelectedConn();
        if (conn != null) {
            onAddConnFromTemplate(conn);
        }
    }

    private void onAddConn(ActionEvent event) {
        onAddConnFromTemplate(null);
    }

    private void onAddConnFromTemplate(Conn template) {
        String name = JOptionPane.showInputDialog(
                this,
                "Name",
                "New Connection",
                JOptionPane.INFORMATION_MESSAGE);
        if (name == null || name.isEmpty()) {
            return;
        }
        if (name.contains(" ") || name.contains("\t")) {
            JOptionPane.showMessageDialog(
                    this,
                    "Name cannot contain whites",
                    "Add Fail",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        if (tableModel.containsName(name)) {
            JOptionPane.showMessageDialog(
                    this,
                    "Name already exists",
                    "Add Fail",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        Conn added = new Conn(name, template);
        int offset = tableModel.addConn(added);
        table.getSelectionModel().addSelectionInterval(offset, offset);
        store.addEntry(added);
        toggleComponents();
    }

    private void onRemove(ActionEvent event) {
        int rowIdx = table.getSelectedRow();
        if (-1 != rowIdx) {
            Conn removed = tableModel.removeConn(rowIdx);
            if (removed.isOpen()) {
                removed.close();
            }
            toggleComponents();
            if (rowIdx == 0) {
                table.getSelectionModel().setSelectionInterval(0, 0);
            } else {
                table.getSelectionModel().setSelectionInterval(rowIdx - 1, rowIdx - 1);
            }
            store.removeEntry(removed);
        }
    }

    private void onAssign(ActionEvent event) {
        setSelectedConn(getSelectedConn());
    }

    private void onTest(ActionEvent event) {
        Conn conn = getSelectedConn();
        try {
            if (conn != null) {
                conn.testConnectivity();
                JOptionPane.showMessageDialog(
                        this,
                        "Connection Successful");
            } else {
                JOptionPane.showMessageDialog(
                        this,
                        "Connection not set",
                        "Connection Failed",
                        JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(
                    this,
                    e.getMessage(),
                    "Connection Failed",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void toggleComponents() {
        if (0 == tableModel.getRowCount()) {
            testButton.setEnabled(false);
            assignButton.setEnabled(false);
            connectButton.setText("Connect");
            connectButton.setIcon(GTk.Icon.CONN_CONNECT.icon());
            cloneButton.setEnabled(false);
            removeButton.setEnabled(false);
        } else {
            Conn conn = getSelectedConn();
            boolean isSetButNotOpen = conn != null && !conn.isOpen();
            assignButton.setEnabled(conn != null);
            cloneButton.setEnabled(conn != null);
            testButton.setEnabled(isSetButNotOpen);
            removeButton.setEnabled(isSetButNotOpen);
            connectButton.setText(conn != null && conn.isOpen() ? "Disconnect" : "Connect");
            connectButton.setIcon((conn != null && conn.isOpen() ? GTk.Icon.CONN_DISCONNECT : GTk.Icon.CONN_CONNECT).icon());
            reloadButton.setEnabled(tableModel.getConns().stream().noneMatch(Conn::isOpen));
        }
        table.repaint();
        validate();
        repaint();
    }

    public enum EventType {
        CONNECTION_SELECTED,
        CONNECTION_ESTABLISHED,
        CONNECTION_CLOSED,
        CONNECTION_FAILED,
        CONNECTIONS_LOST,
        HIDE_REQUEST
    }
}
