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

package io.quest.editor;

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

import io.quest.EventConsumer;
import io.quest.EventProducer;
import io.quest.conns.Conn;
import io.quest.sql.SQLExecutionRequest;
import io.quest.GTk;
import io.quest.conns.Conns;
import io.quest.store.Store;
import io.quest.store.StoreEntry;

import static io.quest.GTk.Icon;
import static io.quest.GTk.menu;
import static io.quest.GTk.menuItem;
import static io.quest.GTk.flowPanel;
import static io.quest.GTk.gap;
import static io.quest.GTk.label;

public class QuestsEditor extends Editor implements EventProducer<QuestsEditor.EventType>, Closeable {

    private static final int COMPONENT_HEIGHT = 33;
    private static final String STORE_FILE_NAME = "default-notebook.json";
    private final EventConsumer<QuestsEditor, SQLExecutionRequest> eventConsumer;
    private final JComboBox<String> questEntryNames;
    private final List<UndoManager> undoManagers;
    private final JLabel questLabel;
    private final JLabel connLabel;
    private final JLabel fontSizeLabel;
    private final JSlider fontSizeSlider;
    private final FindReplace findPanel;
    private final JMenu questsMenu;
    private Store<Content> store;
    private Conn conn; // uses it when set
    private SQLExecutionRequest lastRequest;
    private Content content;

    public QuestsEditor(EventConsumer<QuestsEditor, SQLExecutionRequest> eventConsumer) {
        super();
        this.eventConsumer = eventConsumer;
        undoManagers = new ArrayList<>(5);
        questEntryNames = new JComboBox<>();
        questEntryNames.setFont(GTk.TABLE_CELL_FONT);
        questEntryNames.setBackground(GTk.QUEST_APP_BACKGROUND_COLOR);
        questEntryNames.setForeground(GTk.EDITOR_MENU_FOREGROUND_COLOR);
        questEntryNames.setEditable(false);
        questEntryNames.setPreferredSize(new Dimension(490, COMPONENT_HEIGHT));
        questEntryNames.addActionListener(this::onChangeQuest);
        questEntryNames.setBorder(BorderFactory.createEmptyBorder());
        questEntryNames.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
                super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if (isSelected) {
                    setBackground(GTk.EDITOR_MATCH_FOREGROUND_COLOR);
                    setForeground(GTk.QUEST_APP_BACKGROUND_COLOR);
                } else {
                    setBackground(GTk.QUEST_APP_BACKGROUND_COLOR);
                    setForeground(GTk.EDITOR_MENU_FOREGROUND_COLOR);
                }
                list.setSelectionBackground(GTk.QUEST_APP_BACKGROUND_COLOR);
                list.setSelectionForeground(GTk.EDITOR_MENU_FOREGROUND_COLOR);
                return this;
            }
        });
        JPanel questsPanel = flowPanel(
                gap(24),
                questLabel = label(Icon.QUEST, "uest", null),
                gap(6),
                questEntryNames,
                gap(12),
                connLabel = label(Icon.NO_ICON, null, e -> eventConsumer.onSourceEvent(
                        QuestsEditor.this,
                        EventType.CONNECTION_STATUS_CLICKED,
                        null)));
        questLabel.setForeground(Color.WHITE);
        fontSizeLabel = new JLabel("");
        fontSizeLabel.setFont(GTk.MENU_FONT);
        fontSizeSlider = new JSlider(JSlider.HORIZONTAL, GTk.EDITOR_MIN_FONT_SIZE, GTk.EDITOR_MAX_FONT_SIZE, GTk.EDITOR_DEFAULT_FONT_SIZE);
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
        setFontSize(GTk.EDITOR_DEFAULT_FONT_SIZE);
        JPanel topPanel = new JPanel(new BorderLayout(0, 0));
        topPanel.setPreferredSize(new Dimension(0, COMPONENT_HEIGHT + 2));
        topPanel.setBackground(GTk.QUEST_APP_BACKGROUND_COLOR);
        topPanel.add(questsPanel, BorderLayout.WEST);
        topPanel.add(findPanel, BorderLayout.SOUTH);
        add(topPanel, BorderLayout.NORTH);
        loadStoreEntries(STORE_FILE_NAME);
        refreshConnLabel();
    }

    public void setFontSize(int size) {
        super.setFontSize(size);
        fontSizeLabel.setText(String.format("Font size [%d,%d]: %d",
                GTk.EDITOR_MIN_FONT_SIZE, GTk.EDITOR_MAX_FONT_SIZE, textPane.getFont().getSize()));
        fontSizeSlider.setValue(size);
    }

    public Conn getConnection() {
        return conn;
    }

    public void setConnection(Conn conn) {
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
        String entryName = JOptionPane.showInputDialog(this, "Name", "New quest", JOptionPane.INFORMATION_MESSAGE);
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
            String newName = JOptionPane.showInputDialog(this, "New name", "Renaming quest", JOptionPane.QUESTION_MESSAGE);
            if (newName != null && !newName.isBlank() && !newName.equals(currentName)) {
                store.getEntry(idx, null).setName(newName);
                refreshQuestEntryNames(idx);
            }
        }
    }

    private void onBackupQuests(ActionEvent event) {
        JFileChooser choose = new JFileChooser(Store.ROOT_PATH);
        choose.setDialogTitle("Backing up quests");
        choose.setDialogType(JFileChooser.SAVE_DIALOG);
        choose.setFileSelectionMode(JFileChooser.FILES_ONLY);
        choose.setMultiSelectionEnabled(false);
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
                            JOptionPane.YES_NO_OPTION)
                    ) {
                        store.saveToFile(selectedFile);
                    }
                }
            } catch (Throwable t) {
                JOptionPane.showMessageDialog(
                        this,
                        String.format("Could not save file '%s': %s", selectedFile.getAbsolutePath(), t.getMessage()),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void onLoadQuestsFromBackup(ActionEvent event) {
        JFileChooser choose = new JFileChooser(Store.ROOT_PATH);
        choose.setDialogTitle("Loading quests from backup");
        choose.setDialogType(JFileChooser.OPEN_DIALOG);
        choose.setFileSelectionMode(JFileChooser.FILES_ONLY);
        choose.setMultiSelectionEnabled(false);
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
                JOptionPane.showMessageDialog(this, String.format("Could not load file '%s': %s", selectedFile.getAbsolutePath(), t.getMessage()), "Error", JOptionPane.ERROR_MESSAGE);
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
        connLabel.setForeground(isConnected ? GTk.EDITOR_MENU_FOREGROUND_COLOR : GTk.EDITOR_ERROR_FOREGROUND_COLOR);
    }

    private JMenu createQuestsMenu() {
        final JMenu questsMenu = menu(Icon.QUEST, "uest");
        questsMenu.add(menuItem(Icon.COMMAND_ADD, "New", GTk.NO_KEY_EVENT, this::onCreateQuest));
        questsMenu.add(menuItem(Icon.COMMAND_EDIT, "Rename", GTk.NO_KEY_EVENT, this::onRenameQuest));
        questsMenu.add(menuItem(Icon.COMMAND_REMOVE, "Delete", GTk.NO_KEY_EVENT, this::onDeleteQuest));
        questsMenu.addSeparator();
        questsMenu.add(menuItem(Icon.COMMAND_CLEAR, "Clear", GTk.NO_KEY_EVENT, this::onClearQuest));
        questsMenu.add(menuItem(Icon.COMMAND_RELOAD, "Reload", "Recovers quest from last save", GTk.NO_KEY_EVENT, this::onReloadQuest));
        questsMenu.add(menuItem(Icon.COMMAND_SAVE, "Save", GTk.NO_KEY_EVENT, this::onSaveQuest));
        questsMenu.addSeparator();
        questsMenu.add(menuItem(Icon.COMMAND_STORE_LOAD, "Read from notebook", GTk.NO_KEY_EVENT, this::onLoadQuestsFromBackup));
        questsMenu.add(menuItem(Icon.COMMAND_STORE_BACKUP, "Write to new notebook", GTk.NO_KEY_EVENT, this::onBackupQuests));
        questsMenu.addSeparator();
        questsMenu.add(fontSizeLabel);
        questsMenu.add(fontSizeSlider);
        return questsMenu;
    }

    public enum EventType {
        COMMAND_AVAILABLE, COMMAND_CANCEL, CONNECTION_STATUS_CLICKED
    }

    public static class Content extends StoreEntry {
        private static final String ATTR_NAME = "content";

        public Content() {
            this("default");
        }

        public Content(String name) {
            super(name);
            setAttr(ATTR_NAME, GTk.BANNER);
        }

        @SuppressWarnings("unused")
        public Content(StoreEntry other) {
            super(other);
        }

        @Override
        public final String getUniqueId() {
            return getName();
        }

        public String getContent() {
            return getAttr(ATTR_NAME);
        }

        public void setContent(String content) {
            setAttr(ATTR_NAME, content);
        }
    }
}
