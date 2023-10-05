package io.questdb.desktop;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.*;

import io.questdb.desktop.model.DbConn;
import io.questdb.desktop.model.SQLExecutor;
import io.questdb.desktop.model.SQLExecutionRequest;
import io.questdb.desktop.model.SQLExecutionResponse;
import io.questdb.desktop.ui.metadata.Metadata;
import io.questdb.desktop.ui.plotting.Plot;
import io.questdb.desktop.ui.plotting.TableColumn;
import io.questdb.desktop.model.SQLPagedTableModel;
import io.questdb.desktop.model.SQLType;
import io.questdb.desktop.model.Store;
import io.questdb.ServerMain;
import io.questdb.desktop.ui.connectivity.Conns;
import io.questdb.desktop.ui.editor.MainEditor;
import io.questdb.desktop.ui.EventProducer;
import io.questdb.desktop.ui.results.SQLResultsTable;
import io.questdb.log.Log;
import io.questdb.log.LogFactory;
import io.questdb.std.Misc;


public final class Main {
    private static final Log LOG = LogFactory.getLog(Main.class);

    private final JFrame frame;
    private final MainEditor commands;
    private final Conns conns;
    private final SQLResultsTable results;
    private final SQLExecutor executor;
    private final Metadata meta;
    private final Plot plot;
    private final JMenuItem toggleConns;
    private final JMenuItem togglePlot;
    private final JMenuItem toggleQuestDB;
    private final JMenuItem toggleMeta;
    private final JMenuItem toggleAssignedConn;
    private ServerMain questDb;

    private Main() {
        frame = GTk.frame(String.format("%s [store: %s]", GTk.APP_NAME, Store.ROOT_PATH));
        frame.setIconImage(GTk.Icon.QUESTDB.icon().getImage());
        int dividerHeight = (int) (frame.getHeight() * 0.6);
        executor = new SQLExecutor();
        meta = new Metadata(frame, "Metadata Files", this::dispatchEvent);
        plot = new Plot(frame, "Plot", this::dispatchEvent);
        conns = new Conns(frame, this::dispatchEvent);
        commands = new MainEditor(this::dispatchEvent);
        commands.setPreferredSize(new Dimension(0, dividerHeight));
        results = new SQLResultsTable(frame.getWidth(), dividerHeight);
        JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, true, commands, results);
        splitPane.setDividerLocation(dividerHeight);
        splitPane.setDividerSize(5);
        frame.add(splitPane, BorderLayout.CENTER);
        toggleConns = new JMenuItem();
        toggleAssignedConn = new JMenuItem();
        toggleQuestDB = new JMenuItem();
        toggleMeta = new JMenuItem();
        togglePlot = new JMenuItem();
        frame.setJMenuBar(createMenuBar());
        Runtime.getRuntime().addShutdownHook(new Thread(this::close, "shutdown-hook"));
        LOG.info().$('\n').$(GTk.BANNER).$('\n').$();
        executor.start();
        conns.start();
        frame.setVisible(true);
    }

    public static void main(String[] args) {
        GTk.invokeLater(Main::new);
    }

    private static void onToggleDialog(JDialog widget, Consumer<Boolean> consumer) {
        boolean wasVisible = widget.isVisible();
        if (!wasVisible) {
            Dimension location = GTk.frameLocation(widget.getSize());
            widget.setLocation(location.width, location.height);
        }
        widget.setVisible(!wasVisible);
        consumer.accept(wasVisible);
    }

    private JMenuBar createMenuBar() {
        JMenu connsMenu = GTk.menu(GTk.Icon.CONNS, "Connections");
        connsMenu.add(GTk.menuItem(toggleConns, GTk.Icon.CONN_SHOW, "Connections", KeyEvent.VK_T, this::onToggleConns));
        connsMenu.add(GTk.menuItem(toggleAssignedConn, GTk.Icon.CONN_CONNECT, "Connect", KeyEvent.VK_O, this::onToggleAssignedConn));

        JMenu commandsMenu = GTk.menu(GTk.Icon.COMMANDS, "Commands");
        commandsMenu.add(GTk.menuItem(GTk.Icon.COMMAND_EXEC_LINE, "L.Exec", KeyEvent.VK_L, commands::onExecLine));
        commandsMenu.add(GTk.menuItem(GTk.Icon.COMMAND_EXEC, "Exec", KeyEvent.VK_ENTER, commands::onExec));
        commandsMenu.add(GTk.menuItem(GTk.Icon.COMMAND_EXEC_ABORT, "Abort", KeyEvent.VK_W, commands::fireCancelEvent));
        commandsMenu.addSeparator();
        commandsMenu.add(GTk.menuItem(GTk.Icon.COMMAND_FIND, "Find", KeyEvent.VK_F, e -> commands.onFind()));
        commandsMenu.add(GTk.menuItem(GTk.Icon.COMMAND_REPLACE, "Replace", KeyEvent.VK_R, e -> commands.onReplace()));

        JMenu resultsMenu = GTk.menu(GTk.Icon.RESULTS, "Results");
        resultsMenu.add(GTk.menuItem(GTk.Icon.RESULTS_PREV, "PREV", KeyEvent.VK_B, results::onPrevButton));
        resultsMenu.add(GTk.menuItem(GTk.Icon.RESULTS_NEXT, "NEXT", KeyEvent.VK_N, results::onNextButton));

        JMenu menu = GTk.menu(GTk.Icon.MENU);
        menu.add(commands.getQuestsMenu());
        menu.addSeparator();
        menu.add(GTk.menuItem(toggleQuestDB, GTk.Icon.ROCKET, "Run QuestDB", KeyEvent.VK_PERIOD, this::onToggleQuestDB));
        menu.addSeparator();
        menu.add(GTk.menuItem(toggleMeta, GTk.Icon.META, "Meta Explorer", KeyEvent.VK_M, this::onToggleMeta));
        menu.addSeparator();
        menu.add(GTk.menuItem(togglePlot, GTk.Icon.PLOT, "Plot", KeyEvent.VK_J, this::onTogglePlot));
        menu.addSeparator();
        menu.add(connsMenu);
        menu.add(commandsMenu);
        menu.add(resultsMenu);
        menu.addSeparator();
        menu.add(GTk.menuItem(GTk.Icon.HELP, "QuestDB Docs", KeyEvent.VK_H, GTk::openQuestDBDocs));

        JMenuBar menuBar = new JMenuBar();
        menuBar.setBorder(BorderFactory.createLoweredSoftBevelBorder());
        menuBar.add(menu);
        return menuBar;
    }

    private void onToggleAssignedConn(ActionEvent event) {
        DbConn conn = commands.getConnection();
        conns.onConnectEvent(conn);
        toggleAssignedConn.setText(conn != null && conn.isOpen() ? "Connect" : "Disconnect");
    }

    private void onToggleConns(ActionEvent event) {
        onToggleDialog(conns, wasVisible -> {
            toggleConns.setText(wasVisible ? "Connections" : "Hide Connections");
            toggleConns.setIcon((wasVisible ? GTk.Icon.CONN_SHOW : GTk.Icon.CONN_HIDE).icon());
        });
    }

    private void onToggleMeta(ActionEvent event) {
        onToggleDialog(meta, wasVisible -> toggleMeta.setText(wasVisible ? "Meta Explorer" : "Close Meta Explorer"));
    }

    private void onTogglePlot(ActionEvent event) {
        if (plot.isVisible()) {
            plot.setVisible(false);
            togglePlot.setText("Plot");
        } else {
            SQLPagedTableModel table = results.getTable();
            if (table == null) {
                GTk.showErrorDialog(frame, "No results to plot");
                return;
            }
            if (table.getColumnCount() != 3) { // #, x, y
                GTk.showErrorDialog(frame, "Select only two columns");
                return;
            }
            if (SQLType.isNotNumeric(table.getColumnType(1))) {
                GTk.showErrorDialog(frame, "Column X is not numeric");
                return;
            }
            if (!SQLType.isNotNumeric(table.getColumnType(2))) {
                GTk.showErrorDialog(frame, "Column Y is not numeric");
                return;
            }
            plot.setDataSet(
                    new TableColumn("x", table, 1, Color.WHITE),
                    new TableColumn("y", table, 2, GTk.Editor.MATCH_FOREGROUND_COLOR)
            );
            plot.setVisible(true);
            togglePlot.setText("Close Plot");
        }
    }

    private void onToggleQuestDB(ActionEvent event) {
        if (questDb == null) {
            try {
                questDb = new ServerMain("-d", Store.ROOT_PATH.getAbsolutePath());
                questDb.start(false);
                toggleQuestDB.setText("Shutdown QuestDB");
                DbConn conn = commands.getConnection();
                if (conn == null || !conn.isValid()) {
                    onToggleAssignedConn(null);
                }
            } finally {
                results.displayMessage("QuestDB is UP");
            }
        } else {
            if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(
                    frame,
                    "Shutdown QuestDB?",
                    "Choice",
                    JOptionPane.YES_NO_OPTION)
            ) {
                questDb.close();
                questDb = null;
                toggleQuestDB.setText("Run QuestDB");
                results.displayMessage("QuestDB is DOWN");
                onToggleAssignedConn(null);
            }
        }
    }

    private void dispatchEvent(EventProducer<?> source, Enum<?> event, Object data) {
        GTk.invokeLater(() -> {
            if (source instanceof MainEditor) {
                onCommandEvent(EventProducer.eventType(event), (SQLExecutionRequest) data);
            } else if (source instanceof SQLExecutor) {
                onSQLExecutorEvent(EventProducer.eventType(event), (SQLExecutionResponse) data);
            } else if (source instanceof Conns) {
                onConnsEvent(EventProducer.eventType(event), data);
            } else if (source instanceof Metadata) {
                onMetaEvent(EventProducer.eventType(event));
            } else if (source instanceof Plot) {
                onPlotEvent(EventProducer.eventType(event));
            }
        });
    }

    private void onCommandEvent(MainEditor.EventType event, SQLExecutionRequest req) {
        switch (event) {
            case COMMAND_AVAILABLE -> {
                DbConn conn = commands.getConnection();
                if (conn == null || !conn.isValid()) {
                    onToggleAssignedConn(null);
                }
                results.close();
                executor.submit(req, this::dispatchEvent);
            }
            case COMMAND_CANCEL -> {
                executor.cancelExistingRequest(req);
                onToggleAssignedConn(null);
            }
            case CONNECTION_STATUS_CLICKED -> onToggleConns(null);
        }
    }

    private void onSQLExecutorEvent(SQLExecutor.EventType event, SQLExecutionResponse res) {
        results.updateStats(event.name(), res);
        switch (event) {
            case STARTED -> results.onResultsStarted();
            case FIRST_ROW_AVAILABLE -> results.onMetadataAvailable(res);
            case ROWS_AVAILABLE -> results.onRowsAvailable(res);
            case COMPLETED -> results.onRowsCompleted(res);
            case CANCELLED -> results.close();
            case FAILURE -> {
                results.close();
                results.displayError(res.getError());
            }
        }
    }

    private void onMetaEvent(Metadata.EventType event) {
        if (event == Metadata.EventType.HIDE_REQUEST) {
            onToggleMeta(null);
        }
    }

    private void onPlotEvent(Plot.EventType event) {
        if (event == Plot.EventType.HIDE_REQUEST) {
            onTogglePlot(null);
        }
    }

    private void onConnsEvent(Conns.EventType event, Object data) {
        switch (event) {
            case CONNECTION_SELECTED -> {
                commands.setConnection((DbConn) data);
                if (conns.isVisible()) {
                    onToggleConns(null);
                }
            }
            case CONNECTION_ESTABLISHED, CONNECTION_CLOSED -> {
                DbConn conn = (DbConn) data;
                DbConn current = commands.getConnection();
                if (current != null && current.equals(conn)) {
                    commands.setConnection(conn);
                    toggleAssignedConn.setText(conn.isOpen() ? "Disconnect" : "Connect");
                }
            }
            case CONNECTIONS_LOST -> {
                @SuppressWarnings("unchecked") Set<DbConn> droppedConns = (Set<DbConn>) data;
                DbConn current = commands.getConnection();
                if (current != null) {
                    for (DbConn dc : droppedConns) {
                        if (current.equals(dc)) {
                            commands.setConnection(dc);
                        }
                    }
                }
            }
            case CONNECTION_FAILED -> results.displayError(String.format("Server is down: %s%n", data));
            case HIDE_REQUEST -> onToggleConns(null);
        }
    }

    private void close() {
        Misc.free(executor);
        Misc.free(conns);
        Misc.free(commands);
        Misc.free(results);
        Misc.free(meta);
        Misc.free(questDb);
    }
}
