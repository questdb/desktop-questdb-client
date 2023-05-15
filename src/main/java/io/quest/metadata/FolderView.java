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

package io.quest.metadata;

import io.quest.GTk;
import io.questdb.std.Files;
import io.questdb.std.Misc;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.io.Closeable;
import java.io.File;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

class FolderView extends JPanel implements Closeable {
    private static final Set<String> IGNORE_FOLDERS = new HashSet<>(Arrays.asList("questdb", "conf", "public"));

    private final JTree treeView;
    private final JFileChooser chooser;
    private final Consumer<File> onRootChange;
    private final Set<FileType> visibleFileTypes = new HashSet<>();
    private final FolderChangeObserver folderChangeObserver;
    private File root; // setRoot changes it

    FolderView(Consumer<File> onRootChange, Consumer<TreePath> onSelection) {
        super(new BorderLayout());
        this.onRootChange = onRootChange;
        treeView = new JTree(new DefaultMutableTreeNode(null));
        treeView.setBackground(GTk.QUEST_APP_BACKGROUND_COLOR);
        treeView.setBorder(BorderFactory.createEmptyBorder());
        treeView.getSelectionModel().setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        final ImageIcon fileIcon = GTk.Icon.META_FILE.icon();
        final ImageIcon folderIcon = GTk.Icon.META_FOLDER.icon();
        final ImageIcon unknownIcon = GTk.Icon.META_UNKNOWN.icon();
        treeView.setCellRenderer(new DefaultTreeCellRenderer() {
            @Override
            public Component getTreeCellRendererComponent(JTree tree, Object value, boolean selected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
                super.getTreeCellRendererComponent(tree, value, selected, expanded, leaf, row, hasFocus);
                setOpaque(true);
                setFont(GTk.MENU_FONT);
                if (leaf && selected) {
                    setBackground(GTk.EDITOR_MATCH_FOREGROUND_COLOR);
                    setForeground(GTk.QUEST_APP_BACKGROUND_COLOR);
                } else {
                    setBackground(GTk.QUEST_APP_BACKGROUND_COLOR);
                    setForeground(GTk.EDITOR_NORMAL_FOREGROUND_COLOR);
                }
                String text = value.toString();
                if (leaf && !text.endsWith("" + Files.SEPARATOR)) {
                    setIcon(FileType.of(extractItemName(text)) != FileType.UNKNOWN ? fileIcon : unknownIcon);
                } else {
                    setIcon(folderIcon);
                }
                setText(text);
                return this;
            }
        });
        treeView.setExpandsSelectedPaths(true);
        treeView.addTreeSelectionListener(e -> onSelection.accept(e.getPath()));
        JScrollPane scrollPane = new JScrollPane();
        scrollPane.getViewport().add(treeView);
        scrollPane.getViewport().setBackground(GTk.QUEST_APP_BACKGROUND_COLOR);
        chooser = new JFileChooser();
        chooser.setCurrentDirectory(new File("."));
        chooser.setDialogTitle("Select a folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setMultiSelectionEnabled(false);
        folderChangeObserver = new FolderChangeObserver((source, type, data) -> reloadModel());
        JPanel checkBoxPane = new JPanel(new GridLayout(4, 4, 0, 0));
        checkBoxPane.setBackground(GTk.QUEST_APP_BACKGROUND_COLOR);
        for (FileType type : FileType.values()) {
            checkBoxPane.add(createVisibleFileTypeCheckBox(type));
        }
        JPanel buttonPanel = new JPanel(new GridLayout(1, 3, 0, 0));
        buttonPanel.setBackground(GTk.QUEST_APP_BACKGROUND_COLOR);
        buttonPanel.setBorder(BorderFactory.createLineBorder(Color.WHITE, 1, true));
        buttonPanel.add(GTk.button("Set", () -> {
            String root = JOptionPane.showInputDialog(FolderView.this, "Root:", null, JOptionPane.QUESTION_MESSAGE);
            if (root != null) {
                File folder = new File(root);
                if (folder.exists() && folder.isDirectory()) {
                    setRoot(folder.getAbsoluteFile());
                }
            }
        }));
        buttonPanel.add(GTk.button("Select", () -> {
            if (FolderView.this.chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
                setRoot(chooser.getSelectedFile());
            }
        }));
        add(checkBoxPane, BorderLayout.NORTH);
        add(scrollPane, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);
    }

    static String formatItemName(String itemName, long size) {
        int z = (63 - Long.numberOfLeadingZeros(size)) / 10;
        return String.format("%s (%.1f %sB)", itemName, (double) size / (1L << (z * 10)), " KMGTPE".charAt(z));
    }

    static String extractItemName(String withSize) {
        if (withSize.endsWith("B)")) {
            return withSize.substring(0, withSize.indexOf(" ("));
        }
        return withSize;
    }

    void setRoot(File root) {
        this.root = root;
        folderChangeObserver.registerReplacing(root);
        reloadModel();
        onRootChange.accept(root);
    }

    void reloadModel() {
        if (root != null) {
            TreePath selected = treeView.getSelectionPath();
            TreeModel model = createModel(root);
            treeView.setModel(model);
            if (selected != null) {
                treeView.setSelectionPath(expandTree(model, selected));
            }
        }
    }

    private TreePath expandTree(TreeModel model, TreePath selected) {
        DefaultMutableTreeNode top = (DefaultMutableTreeNode) model.getRoot();
        TreePath expand = new TreePath(top);
        Object[] nodes = selected.getPath();
        for (int i = 1; i < nodes.length; i++) {
            treeView.expandPath(expand);
            String nodeName = nodes[i].toString();
            DefaultMutableTreeNode child = null;
            for (int j = 0; j < top.getChildCount(); j++) {
                DefaultMutableTreeNode tmp = (DefaultMutableTreeNode) top.getChildAt(j);
                String childName = tmp.toString();
                if (childName.equals(nodeName)) {
                    top = tmp;
                    child = top;
                    break;
                }
            }
            if (child == null) {
                break;
            }
            expand = expand.pathByAddingChild(child);
        }
        return expand;
    }

    private DefaultTreeModel createModel(File folder) {
        return new DefaultTreeModel(addNodes(null, folder));
    }

    private DefaultMutableTreeNode addNodes(DefaultMutableTreeNode currentRoot, File folder) {
        String[] folderContent = folder.list();
        if (folderContent == null || folderContent.length == 0) {
            return null;
        }

        DefaultMutableTreeNode folderNode = new DefaultMutableTreeNode(folder.getName() + Files.SEPARATOR);
        if (currentRoot != null) { // should only be null at root
            currentRoot.add(folderNode);
        }
        Arrays.sort(folderContent);
        String folderPath = folder.getPath();
        for (String itemName : folderContent) {
            if (IGNORE_FOLDERS.contains(itemName)) {
                continue;
            }
            File newPath = new File(folderPath, itemName).getAbsoluteFile();
            if (newPath.exists()) {
                if (newPath.isDirectory()) {
                    addNodes(folderNode, newPath);
                } else {
                    if (visibleFileTypes.contains(FileType.of(itemName))) {
                        folderNode.add(new DefaultMutableTreeNode(formatItemName(itemName, newPath.length())));
                    }
                }
            }
        }
        return folderNode;
    }

    private JCheckBox createVisibleFileTypeCheckBox(FileType type) {
        JCheckBox checkBox = new JCheckBox(type.name(), type.isDefaultChecked());
        checkBox.setFont(GTk.MENU_FONT);
        checkBox.setBackground(GTk.QUEST_APP_BACKGROUND_COLOR);
        checkBox.setForeground(GTk.EDITOR_KEYWORD_FOREGROUND_COLOR);
        checkBox.addActionListener(e -> {
            if (checkBox.isSelected()) {
                visibleFileTypes.add(type);
            } else {
                visibleFileTypes.remove(type);
            }
            reloadModel();
        });
        if (type.isDefaultChecked()) {
            visibleFileTypes.add(type);
        }
        return checkBox;
    }

    @Override
    public void close() {
        Misc.free(folderChangeObserver);
    }
}
