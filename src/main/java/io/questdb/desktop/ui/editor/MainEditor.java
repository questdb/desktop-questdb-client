package io.questdb.desktop.ui.editor;

import java.awt.*;
import java.awt.event.*;
import java.io.Closeable;
import java.io.File;
import java.util.List;
import java.util.ArrayList;
import java.util.function.Supplier;

import javax.swing.*;
import javax.swing.event.UndoableEditEvent;
import javax.swing.filechooser.FileFilter;
import javax.swing.undo.UndoManager;

import io.questdb.desktop.GTk;
import io.questdb.desktop.model.DbConn;
import io.questdb.desktop.model.SQLExecutionRequest;
import io.questdb.desktop.model.Store;
import io.questdb.desktop.ui.EventConsumer;
import io.questdb.desktop.ui.EventProducer;
import io.questdb.desktop.ui.connectivity.Conns;

import static io.questdb.desktop.GTk.Icon;
import static io.questdb.desktop.GTk.menu;
import static io.questdb.desktop.GTk.menuItem;
import static io.questdb.desktop.GTk.flowPanel;
import static io.questdb.desktop.GTk.gap;
import static io.questdb.desktop.GTk.label;

public class MainEditor extends Editor implements EventProducer<MainEditor.EventType>, Closeable {

    private static final int COMPONENT_HEIGHT = 33;
    private static final String STORE_FILE_NAME = "default-notebook.json";
    private final EventConsumer<MainEditor, SQLExecutionRequest> eventConsumer;
    private final JComboBox<String> questEntryNames;
    private final List<UndoManager> undoManagers;
    private final JLabel questLabel;
    private final JLabel connLabel;
    private final JLabel fontSizeLabel;
    private final JSlider fontSizeSlider;
    private final FindReplace findPanel;
    private final JMenu questsMenu;
    private Store<Content> store;
    private DbConn conn; // uses it when set
    private SQLExecutionRequest lastRequest;
    private Content content;

    public MainEditor(EventConsumer<MainEditor, SQLExecutionRequest> eventConsumer) {
        super();
        this.eventConsumer = eventConsumer;
        undoManagers = new ArrayList<>(5);
        questEntryNames = new JComboBox<>();
        questEntryNames.setFont(GTk.TABLE_CELL_FONT);
        questEntryNames.setBackground(GTk.APP_BACKGROUND_COLOR);
        questEntryNames.setForeground(GTk.Editor.MENU_FOREGROUND_COLOR);
        questEntryNames.setEditable(false);
        questEntryNames.setPreferredSize(new Dimension(490, COMPONENT_HEIGHT));
        questEntryNames.addActionListener(this::onChangeQuest);
        questEntryNames.setBorder(BorderFactory.createEmptyBorder());
        questEntryNames.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (isSelected) {
                    setBackground(GTk.Editor.MATCH_FOREGROUND_COLOR);
                    setForeground(GTk.APP_BACKGROUND_COLOR);
                } else {
                    setBackground(GTk.APP_BACKGROUND_COLOR);
                    setForeground(GTk.Editor.MENU_FOREGROUND_COLOR);
                }
                list.setSelectionBackground(GTk.APP_BACKGROUND_COLOR);
                list.setSelectionForeground(GTk.Editor.MENU_FOREGROUND_COLOR);
                return this;
            }
        });
        JPanel questsPanel = flowPanel(
                gap(24),
                questLabel = label(Icon.QUESTDB, "uest", null),
                gap(6),
                questEntryNames,
                gap(12),
                connLabel = label(Icon.NO_ICON, null, e -> eventConsumer.onSourceEvent(
                        MainEditor.this,
                        EventType.CONNECTION_STATUS_CLICKED,
                        null)));
        questLabel.setForeground(Color.WHITE);
        fontSizeLabel = new JLabel("");
        fontSizeLabel.setFont(GTk.MENU_FONT);
        fontSizeSlider = new JSlider(
                JSlider.HORIZONTAL,
                GTk.Editor.MIN_FONT_SIZE,
                GTk.Editor.MAX_FONT_SIZE,
                GTk.Editor.DEFAULT_FONT_SIZE
        );
        fontSizeSlider.addChangeListener(e -> {
            JSlider x = (JSlider) e.getSource();
            setFontSize(x.getValue());
        });
        questsMenu = createQuestsMenu();
        findPanel = new FindReplace((source, event, eventData) -> {
            switch ((FindReplace.EventType) EventProducer.eventType(event)) {
                case FIND -> onFind();
                case REPLACE -> onReplace();
            }
        });
        setFontSize(GTk.Editor.DEFAULT_FONT_SIZE);
        JPanel topPanel = new JPanel(new BorderLayout(0, 0));
        topPanel.setPreferredSize(new Dimension(0, COMPONENT_HEIGHT + 2));
        topPanel.setBackground(GTk.APP_BACKGROUND_COLOR);
        topPanel.add(questsPanel, BorderLayout.WEST);
        topPanel.add(findPanel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);
        loadStoreEntries(STORE_FILE_NAME);
        refreshConnLabel();
    }

    private static JFileChooser createChooser(String title, int dialogType) {
        JFileChooser choose = new JFileChooser(Store.ROOT_PATH);
        choose.setDialogTitle(title);
        choose.setDialogType(dialogType);
        choose.setFileSelectionMode(JFileChooser.FILES_ONLY);
        choose.setMultiSelectionEnabled(false);
        return choose;
    }

    public void setFontSize(int size) {
        super.setFontSize(size);
        fontSizeLabel.setText(String.format("Font size [%d,%d]: %d",
                GTk.Editor.MIN_FONT_SIZE,
                GTk.Editor.MAX_FONT_SIZE,
                textPane.getFont().getSize()
        ));
        fontSizeSlider.setValue(size);
    }

    public DbConn getConnection() {
        return conn;
    }

    public void setConnection(DbConn conn) {
        this.conn = conn;
        refreshConnLabel();
    }

    public JMenu getQuestsMenu() {
        return questsMenu;
    }

    public void onExec(ActionEvent ignoredEvent) {
        fireCommandEvent(this::getCommand);
    }

    public void onExecLine(ActionEvent ignoredEvent) {
        fireCommandEvent(this::getCurrentLine);
    }

    public void fireCancelEvent(ActionEvent ignoredEvent) {
        if (conn == null || !conn.isOpen()) {
            return;
        }
        if (lastRequest != null) {
            eventConsumer.onSourceEvent(this, EventType.COMMAND_CANCEL, lastRequest);
            lastRequest = null;
        }
    }

    public void onFind() {
        onFindReplace(() -> highlightContent(findPanel.getFind()));
    }

    public void onReplace() {
        onFindReplace(() -> replaceContent(findPanel.getFind(), findPanel.getReplace()));
    }

    @Override
    public boolean requestFocusInWindow() {
        return super.requestFocusInWindow() && textPane.requestFocusInWindow();
    }

    @Override
    public void close() {
        undoManagers.clear();
        refreshQuest();
        store.saveToFile();
        store.close();
    }

    private String getCommand() {
        String cmd = textPane.getSelectedText();
        return cmd != null ? cmd : getText();
    }

    private void loadStoreEntries(String fileName) {
        store = new Store<>(fileName, Content.class) {
            @Override
            public Content[] defaultStoreEntries() {
                Content keyboardShortcuts = new Content("keyboard shortcuts");
                keyboardShortcuts.setContent(GTk.KEYBOARD_SHORTCUTS);
                return new Content[]{new Content(), keyboardShortcuts,};
            }
        };
        store.loadFromFile();
        questLabel.setToolTipText(String.format("notebook: %s", fileName));
        undoManagers.clear();
        for (int idx = 0; idx < store.size(); idx++) {
            undoManagers.add(new UndoManager() {
                @Override
                public void undoableEditHappened(UndoableEditEvent e) {
                    if (!EditorHighlighter.EVENT_TYPE.equals(e.getEdit().getPresentationName())) {
                        super.undoableEditHappened(e);
                    }
                }
            });
        }
        refreshQuestEntryNames(0);
    }

    private void onFindReplace(Supplier<Integer> matchesCountSupplier) {
        if (!findPanel.isVisible()) {
            findPanel.setVisible(true);
        } else {
            findPanel.updateMatches(matchesCountSupplier.get());
        }
        findPanel.requestFocusInWindow();
    }

    private void onChangeQuest(ActionEvent event) {
        int idx = questEntryNames.getSelectedIndex();
        if (idx >= 0) {
            if (refreshQuest()) {
                store.asyncSaveToFile();
            }
            content = store.getEntry(idx, Content::new);
            textPane.setText(content.getContent());
            setUndoManager(undoManagers.get(idx));
        }
    }

    private void onCreateQuest(ActionEvent event) {
        String entryName = JOptionPane.showInputDialog(
                this,
                "Name",
                "New quest",
                JOptionPane.INFORMATION_MESSAGE);
        if (entryName == null || entryName.isEmpty()) {
            return;
        }
        store.addEntry(new Content(entryName));
        questEntryNames.addItem(entryName);
        undoManagers.add(new UndoManager() {
            @Override
            public void undoableEditHappened(UndoableEditEvent e) {
                if (!EditorHighlighter.EVENT_TYPE.equals(e.getEdit().getPresentationName())) {
                    super.undoableEditHappened(e);
                }
            }
        });
        questEntryNames.setSelectedItem(entryName);
    }

    private void onDeleteQuest(ActionEvent event) {
        int idx = questEntryNames.getSelectedIndex();
        if (idx > 0) {
            if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(
                    this,
                    String.format("Delete %s?", questEntryNames.getSelectedItem()),
                    "Deleting quest",
                    JOptionPane.YES_NO_OPTION)
            ) {
                store.removeEntry(idx);
                content = null;
                questEntryNames.removeItemAt(idx);
                undoManagers.remove(idx);
                questEntryNames.setSelectedIndex(idx - 1);
            }
        }
    }

    private void onRenameQuest(ActionEvent event) {
        int idx = questEntryNames.getSelectedIndex();
        if (idx >= 0) {
            String currentName = (String) questEntryNames.getSelectedItem();
            String newName = JOptionPane.showInputDialog(
                    this,
                    "New name",
                    "Renaming quest",
                    JOptionPane.QUESTION_MESSAGE);
            if (newName != null && !newName.isBlank() && !newName.equals(currentName)) {
                store.getEntry(idx, null).setName(newName);
                refreshQuestEntryNames(idx);
            }
        }
    }

    private void onBackupQuests(ActionEvent event) {
        JFileChooser choose = createChooser("Backing up quests", JFileChooser.SAVE_DIALOG);
        if (JFileChooser.APPROVE_OPTION == choose.showSaveDialog(this)) {
            File selectedFile = choose.getSelectedFile();
            try {
                if (!selectedFile.exists()) {
                    store.saveToFile(selectedFile);
                } else {
                    if (JOptionPane.YES_OPTION == JOptionPane.showConfirmDialog(
                            this,
                            "Override file?",
                            "Dilemma",
                            JOptionPane.YES_NO_OPTION
                    )) {
                        store.saveToFile(selectedFile);
                    }
                }
            } catch (Throwable t) {
                JOptionPane.showMessageDialog(
                        this,
                        String.format("Could not save file '%s': %s", selectedFile.getAbsolutePath(), t.getMessage()),
                        "Error",
                        JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }

    private void onLoadQuestsFromBackup(ActionEvent event) {
        JFileChooser choose = createChooser("Loading quests from backup", JFileChooser.OPEN_DIALOG);
        choose.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                String name = f.getName();
                return name.endsWith(".json") && !name.equals(Conns.STORE_FILE_NAME);
            }

            @Override
            public String getDescription() {
                return "JSON files";
            }
        });

        if (JFileChooser.APPROVE_OPTION == choose.showOpenDialog(this)) {
            File selectedFile = choose.getSelectedFile();
            try {
                loadStoreEntries(selectedFile.getName());
            } catch (Throwable t) {
                JOptionPane.showMessageDialog(
                        this,
                        String.format(
                                "Could not load file '%s': %s",
                                selectedFile.getAbsolutePath(), t.getMessage()
                        ),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onClearQuest(ActionEvent event) {
        textPane.setText("");
    }

    private void onReloadQuest(ActionEvent event) {
        textPane.setText(content.getContent());
    }

    private void onSaveQuest(ActionEvent event) {
        if (refreshQuest()) {
            store.asyncSaveToFile();
        }
    }

    private boolean refreshQuest() {
        String text = getText();
        String currentContent = content != null ? content.getContent() : null;
        if (currentContent != null && !currentContent.equals(text)) {
            content.setContent(text);
            return true;
        }
        return false;
    }

    private void refreshQuestEntryNames(int idx) {
        questEntryNames.removeAllItems();
        for (String item : store.entryNames()) {
            questEntryNames.addItem(item);
        }
        if (idx >= 0 && idx < questEntryNames.getItemCount()) {
            questEntryNames.setSelectedIndex(idx);
        }
    }

    private void fireCommandEvent(Supplier<String> commandSupplier) {
        if (conn == null) {
            JOptionPane.showMessageDialog(this, "Connection not set, assign one");
            return;
        }
        String command = commandSupplier.get();
        if (command == null || command.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Command not available, type something");
            return;
        }
        if (lastRequest != null) {
            eventConsumer.onSourceEvent(this, EventType.COMMAND_CANCEL, lastRequest);
            lastRequest = null;
        }
        lastRequest = new SQLExecutionRequest(content.getUniqueId(), conn, command);
        eventConsumer.onSourceEvent(this, EventType.COMMAND_AVAILABLE, lastRequest);
    }

    private void refreshConnLabel() {
        boolean isConnected = conn != null && conn.isOpen();
        String connKey = conn != null ? conn.getUniqueId() : "None set";
        connLabel.setText(String.format("on %s", connKey));
        connLabel.setForeground(isConnected ?
                GTk.Editor.MENU_FOREGROUND_COLOR
                :
                GTk.Editor.ERROR_FOREGROUND_COLOR
        );
    }

    private JMenu createQuestsMenu() {
        final JMenu questsMenu = menu(Icon.QUESTDB, "uest");
        questsMenu.add(menuItem(Icon.COMMAND_ADD, "New", this::onCreateQuest));
        questsMenu.add(menuItem(Icon.COMMAND_EDIT, "Rename", this::onRenameQuest));
        questsMenu.add(menuItem(Icon.COMMAND_REMOVE, "Delete", this::onDeleteQuest));
        questsMenu.addSeparator();
        questsMenu.add(menuItem(Icon.COMMAND_CLEAR, "Clear", this::onClearQuest));
        questsMenu.add(menuItem(Icon.COMMAND_RELOAD, "Reload", this::onReloadQuest));
        questsMenu.add(menuItem(Icon.COMMAND_SAVE, "Save", this::onSaveQuest));
        questsMenu.addSeparator();
        questsMenu.add(menuItem(Icon.COMMAND_STORE_LOAD, "Read from notebook", this::onLoadQuestsFromBackup));
        questsMenu.add(menuItem(Icon.COMMAND_STORE_BACKUP, "Write to new notebook", this::onBackupQuests));
        questsMenu.addSeparator();
        questsMenu.add(fontSizeLabel);
        questsMenu.add(fontSizeSlider);
        return questsMenu;
    }

    public enum EventType {
        COMMAND_AVAILABLE, COMMAND_CANCEL, CONNECTION_STATUS_CLICKED
    }
}
