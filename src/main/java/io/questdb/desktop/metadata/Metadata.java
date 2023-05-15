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

package io.questdb.desktop.metadata;

import io.questdb.desktop.EventConsumer;
import io.questdb.desktop.EventProducer;
import io.questdb.desktop.GTk;
import io.questdb.desktop.store.Store;
import io.questdb.cairo.*;
import io.questdb.cairo.sql.RowCursor;
import io.questdb.std.*;
import io.questdb.std.datetime.millitime.MillisecondClockImpl;
import io.questdb.std.str.Path;

import javax.swing.*;
import javax.swing.tree.TreePath;
import java.awt.*;
import java.io.*;
import java.util.function.Consumer;

public class Metadata extends JDialog implements EventProducer<Metadata.EventType>, Closeable {

    private final CairoConfiguration configuration = new DefaultCairoConfiguration(Store.ROOT_PATH.getAbsolutePath());
    private final FilesFacade ff = configuration.getFilesFacade();
    private final TxReader txReader = new TxReader(ff);
    private final CounterFileReader counterReader = new CounterFileReader(ff);
    private final TableReaderMetadata metaReader = new TableReaderMetadata(configuration);
    private final ColumnVersionReader cvReader = new ColumnVersionReader();
    private final FileReader fileReader = new FileReader();
    private final Path selectedPath = new Path();
    private final Path auxPath = new Path();
    private final Display display = new Display();
    private final FolderView treeView;
    private int rootLen;
    private String partitionFolderName;

    public Metadata(Frame owner, String title, EventConsumer<Metadata, Object> eventConsumer) {
        super(owner, title);
        GTk.configureDialog(this, 0.78F, 0.66F, () -> eventConsumer.onSourceEvent(Metadata.this, Metadata.EventType.HIDE_REQUEST, null));
        treeView = new FolderView(this::onRootSet, this::onSelectedFile);
        treeView.setPreferredSize(new Dimension(getSize().width / 4, 0));
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, true, treeView, display);
        splitPane.setDividerLocation(0.2);
        splitPane.setDividerSize(5);
        contentPane.add(BorderLayout.CENTER, splitPane);
        setRoot(Store.ROOT_PATH);
    }

    private static void selectFileInFolder(Path p, int levelUpCount, String fileName) {
        int idx = findSeparatorIdx(p, levelUpCount);
        if (idx != -1) {
            p.trimTo(idx);
            if (fileName != null) {
                p.concat(fileName);
            }
        }
    }

    private static int findNextDotIdx(CharSequence p, int start) {
        if (p == null) {
            return -1;
        }
        int len = p.length();
        int idx = start;
        while (idx < len && p.charAt(idx) != '.') {
            idx++;
        }
        return idx < len ? idx : -1;
    }

    private static int findSeparatorIdx(CharSequence p, int levelUpCount) {
        int idx = p == null ? 0 : p.length() - 2; // may end in separatorChar
        int found = 0;
        while (idx > 0) {
            char c = p.charAt(idx);
            if (c == Files.SEPARATOR) {
                found++;
                if (found == levelUpCount) {
                    break;
                }
            }
            idx--;
        }
        return idx > 0 ? idx : -1;
    }

    @Override
    public void close() {
        Misc.free(treeView);
        Misc.free(metaReader);
        Misc.free(txReader);
        Misc.free(cvReader);
        Misc.free(counterReader);
        Misc.free(selectedPath);
        Misc.free(auxPath);
    }

    public void setRoot(File root) {
        if (!root.exists() || !root.isDirectory()) {
            display.displayMessage("Folder does not exist: " + root);
        }
        treeView.setRoot(root); // receives callback onRootSet, the method bellow
    }

    private void onRootSet(File root) { // callback method called by treeView on setRoot
        String absolutePath = root.getAbsolutePath();
        selectedPath.trimTo(0).put(absolutePath).put(Files.SEPARATOR);
        rootLen = selectedPath.length();
    }

    private void onSelectedFile(TreePath treePath) { // user clicks on an item of the treeView
        Object[] nodes = treePath.getPath();
        String fileName = FolderView.extractItemName(
                nodes[nodes.length - 1].toString()
        );
        if (fileName.endsWith(Os.isPosix() ? "/" : "\\")) {
            // do nothing for folders
            display.displayMessage("");
            return;
        }

        // build the selected path
        partitionFolderName = null;
        selectedPath.trimTo(rootLen); // first node
        for (int i = 1, n = nodes.length - 1; i < n; i++) {
            String pathElement = nodes[i].toString();
            selectedPath.put(pathElement);
            if (i == n - 1) {
                int dotIdx = findNextDotIdx(pathElement, 0);
                int end = dotIdx == -1 ? pathElement.length() - 1 : dotIdx;
                partitionFolderName = pathElement.substring(0, end);
            }
        }
        selectedPath.put(fileName).$(); // last node
        FileType fileType = FileType.of(fileName);
        setTitle(String.format("[%s] %s", fileType, selectedPath));

        // display file content
        try {
            switch (fileType) {
                case META -> displayMeta();
                case TXN -> displayTxn();
                case TAB_INDEX, UPGRADE -> displayCounter();
                case CV -> displayCV();
                case D -> displayD(fileName);
                case K -> displayKV();
                case O -> {
                    selectedPath.trimTo(selectedPath.length() - fileName.length());
                    selectedPath.concat(fileName.replace(".o", ".c")).$();
                    displayCO();
                }
                case C -> displayCO();
                case V -> {
                    selectedPath.trimTo(selectedPath.length() - fileName.length());
                    selectedPath.concat(fileName.replace(".v", ".k")).$();
                    displayKV();
                }
                case TXT, JSON -> displayTxt();
                default -> display.displayMessage("No reader available.");
            }
        } catch (Throwable t) {
            display.displayMessage("Failed to open [" + selectedPath + "]: " + t.getMessage());
        }
    }

    private void displayMeta() {
        metaReader.load(selectedPath);
        display.clear();
        display.addLn("tableId: ", metaReader.getTableId());
        display.addLn("structureVersion: ", metaReader.getStructureVersion());
        display.addLn("timestampIndex: ", metaReader.getTimestampIndex());
        display.addLn("partitionBy: ", PartitionBy.toString(metaReader.getPartitionBy()));
        display.addLn("maxUncommittedRows: ", metaReader.getMaxUncommittedRows());
        display.addMicrosLn("O3MaxLag: ", metaReader.getO3MaxLag());
        display.addLn();
        int columnCount = metaReader.getColumnCount();
        display.addLn("columnCount: ", columnCount);
        for (int i = 0; i < columnCount; i++) {
            int columnType = metaReader.getColumnType(i);
            display.addColumnLn(
                    i,
                    metaReader.getColumnName(i),
                    columnType,
                    columnType > 0 && metaReader.isColumnIndexed(i),
                    columnType > 0 ? metaReader.getIndexValueBlockCapacity(i) : 0,
                    true
            );
        }
        display.addLn();
        display.addLn();
        display.addLn();
        display.addCreateTableLn(metaReader);
        display.render();
    }

    private void displayCV() {
        cvReader.ofRO(FilesFacadeImpl.INSTANCE, selectedPath);
        cvReader.readSafe(MillisecondClockImpl.INSTANCE, Long.MAX_VALUE);
        LongList cvEntries = cvReader.getCachedList();
        int limit = cvEntries.size();
        display.clear();
        display.addLn("version: ", cvReader.getVersion());
        display.addLn("entryCount: ", limit / 4);
        for (int i = 0; i < limit; i += 4) {
            long partitionTimestamp = cvEntries.getQuick(i);
            display.addLn("  + entry ", i / 4);
            display.addTimestampLn("     - partitionTimestamp: ", partitionTimestamp);
            display.addLn("     - columnIndex: ", cvEntries.getQuick(i + 1));
            display.addLn("     - columnNameTxn: ", cvEntries.getQuick(i + 2));
            display.addLn("     - columnTop: ", cvEntries.getQuick(i + 3));
            display.addLn();
        }
        display.render();
    }

    private void displayTxn() {
        if (openMetaFile(1)) {
            // load txn
            txReader.ofRO(selectedPath, metaReader.getPartitionBy());
            txReader.unsafeLoadAll();
            display.clear();
            int symbolColumnCount = txReader.getSymbolColumnCount();
            display.addLn("txn: ", txReader.getTxn());
            display.addLn("version: ", txReader.getVersion());
            display.addLn("columnVersion: ", txReader.getColumnVersion());
            display.addLn("dataVersion: ", txReader.getDataVersion());
            display.addLn("structureVersion: ", txReader.getStructureVersion());
            display.addLn("truncateVersion: ", txReader.getTruncateVersion());
            display.addLn("partitionTableVersion: ", txReader.getPartitionTableVersion());
            display.addLn();
            display.addLn("rowCount: ", txReader.getRowCount());
            display.addLn("fixedRowCount: ", txReader.getFixedRowCount());
            display.addLn("transientRowCount: ", txReader.getTransientRowCount());
            display.addTimestampLn("minTimestamp: ", txReader.getMinTimestamp());
            display.addTimestampLn("maxTimestamp: ", txReader.getMaxTimestamp());
            display.addLn("recordSize: ", txReader.getRecordSize());
            display.addLn();
            display.addLn("symbolColumnCount: ", symbolColumnCount);
            for (int i = 0; i < symbolColumnCount; i++) {
                display.addLn(" - column " + i + " value count: ", txReader.getSymbolValueCount(i));
            }
            int partitionCount = txReader.getPartitionCount();
            display.addLn();
            display.addLn("partitionCount: ", partitionCount);
            for (int i = 0; i < partitionCount; i++) {
                display.addPartitionLn(
                        i,
                        txReader.getPartitionTimestamp(i),
                        txReader.getPartitionNameTxn(i),
                        txReader.getPartitionSize(i),
                        txReader.getPartitionColumnVersion(i));
            }
            display.render();
        }
    }

    private void displayTxt() throws IOException {
        display.clear();
        fileReader.readLines(new File(selectedPath.toString())).forEach(display::addLn);
        display.render();
    }

    private void displayCounter() {
        long count = counterReader.openGetCurrentCount(selectedPath);
        display.clear();
        display.addLn("Tab index: ", count);
        display.render();
    }

    private void displayD(String fileName) {
        boolean isOpen = false;
        int levelUpCount = 1;
        while (levelUpCount < 3 && !(isOpen = openMetaFile(levelUpCount))) {
            levelUpCount++;
        }
        if (isOpen) {
            String columnName = fileName.substring(0, fileName.indexOf("."));
            int columnIndex = metaReader.getColumnIndex(columnName);
            if (columnIndex >= 0) {
                int columnType = metaReader.getColumnType(columnIndex);
                display.clear();
                display.addLn("Column index: ", columnIndex);
                display.addLn("Column name: ", columnName);
                display.addLn("Column type: ", ColumnType.nameOf(columnType));
                display.addLn("Record size: ", ColumnType.sizeOf(columnType));
                if (metaReader.isColumnIndexed(columnIndex)) {
                    display.addLn("Indexed with capacity: ", metaReader.getIndexValueBlockCapacity(columnIndex));
                }
                display.render();
            }
        }
    }

    private void displayCO() {
        int metaLevelUp = isInsidePartitionFolder() ? 2 : 1;
        if (openMetaFile(metaLevelUp) && openTxnFile(metaLevelUp)) {
            auxPath.of(selectedPath);
            ColumnNameTxn cnTxn = ColumnNameTxn.of(auxPath);
            int colIdx = metaReader.getColumnIndex(cnTxn.columnName);
            int symbolCount = txReader.unsafeReadSymbolCount(colIdx);

            // this also opens the .o (offset) file, which contains symbolCapacity, isCached, containsNull
            // as well as the .k and .v (index key/value) files, which index the static table in this case
            selectFileInFolder(auxPath, 1, null);
            SymbolMapReaderImpl symReader = new SymbolMapReaderImpl(
                    configuration,
                    auxPath,
                    cnTxn.columnName,
                    cnTxn.columnNameTxn,
                    symbolCount
            );
            display.clear();
            display.addColumnLn(
                    colIdx,
                    metaReader.getColumnName(colIdx),
                    metaReader.getColumnType(colIdx),
                    metaReader.isColumnIndexed(colIdx),
                    metaReader.getIndexValueBlockCapacity(colIdx),
                    false
            );
            display.addLn();
            display.addLn("symbolCapacity: ", symReader.getSymbolCapacity());
            display.addLn("isCached: ", symReader.isCached());
            display.addLn("isDeleted: ", symReader.isDeleted());
            display.addLn("containsNullValue: ", symReader.containsNullValue());
            display.addLn("symbolCount: ", symbolCount);
            for (int i = 0; i < symbolCount; i++) {
                display.addIndexedSymbolLn(i, symReader.valueOf(i), true);
            }
            display.render();
        }
    }

    private void displayKV() {
        int metaLevelUp = isInsidePartitionFolder() ? 2 : 1;
        if (openMetaFile(metaLevelUp) && openCvFile(metaLevelUp) && openTxnFile(metaLevelUp)) {
            auxPath.of(selectedPath);
            ColumnNameTxn cnTxn = ColumnNameTxn.of(auxPath);
            int colIdx = metaReader.getColumnIndex(cnTxn.columnName);
            int symbolCount = txReader.unsafeReadSymbolCount(colIdx);

            // this also opens the .o (offset) file, which contains symbolCapacity, isCached, containsNull
            // as well as the .k and .v (index key/value) files, which index the static table in this case
            selectFileInFolder(auxPath, metaLevelUp, null);
            SymbolMapReaderImpl symReader = new SymbolMapReaderImpl(
                    configuration,
                    auxPath,
                    cnTxn.columnName,
                    cnTxn.columnNameTxn,
                    symbolCount
            );

            long partitionTimestamp;
            try {
                partitionTimestamp = PartitionBy.parsePartitionDirName(partitionFolderName, metaReader.getPartitionBy());
            } catch (Throwable t) {
                partitionTimestamp = -1L;
            }
            int writerIdx = metaReader.getWriterIndex(colIdx);
            int versionRecordIdx = cvReader.getRecordIndex(partitionTimestamp, writerIdx);
            long columnTop = versionRecordIdx > -1L ? cvReader.getColumnTopByIndex(versionRecordIdx) : 0L;

            auxPath.of(selectedPath);
            selectFileInFolder(auxPath, 1, null);
            try (BitmapIndexFwdReader indexReader = new BitmapIndexFwdReader(
                    configuration,
                    auxPath,
                    cnTxn.columnName,
                    cnTxn.columnNameTxn,
                    columnTop,
                    -1L
            )) {
                display.clear();
                if (symReader.containsNullValue()) {
                    RowCursor cursor = indexReader.getCursor(false, 0, 0, Long.MAX_VALUE);
                    if (cursor.hasNext()) {
                        display.addLn("*: ", "");
                        display.addLn(" - offset: ", cursor.next());
                        while (cursor.hasNext()) {
                            display.addLn(" - offset: ", cursor.next());
                        }
                    }
                }
                for (int symbolKey = 0; symbolKey < symbolCount; symbolKey++) {
                    CharSequence symbol = symReader.valueOf(symbolKey);
                    RowCursor cursor = indexReader.getCursor(false, TableUtils.toIndexKey(symbolKey), 0, Long.MAX_VALUE);
                    if (cursor.hasNext()) {
                        display.addIndexedSymbolLn(symbolKey, symbol, false);
                        display.addLn(" - offset: ", cursor.next());
                        while (cursor.hasNext()) {
                            display.addLn(" - offset: ", cursor.next());
                        }
                    }
                }
                display.render();
            }
        }
    }

    private boolean openMetaFile(int levelUpCount) {
        return openFile(levelUpCount, TableUtils.META_FILE_NAME, metaReader::load);
    }

    private boolean openTxnFile(int levelUpCount) {
        return openFile(levelUpCount, TableUtils.TXN_FILE_NAME, p -> {
            txReader.ofRO(p, metaReader.getPartitionBy());
            txReader.unsafeLoadAll();
        });
    }

    private boolean openCvFile(int levelUpCount) {
        return openFile(levelUpCount, TableUtils.COLUMN_VERSION_FILE_NAME, p -> {
            cvReader.ofRO(FilesFacadeImpl.INSTANCE, p);
            cvReader.readSafe(MillisecondClockImpl.INSTANCE, Long.MAX_VALUE);
        });
    }

    private boolean openFile(int levelUpCount, String fileName, Consumer<Path> action) {
        auxPath.of(selectedPath);
        selectFileInFolder(auxPath, levelUpCount, fileName);
        if (!ff.exists(auxPath.$())) {
            display.displayError("Could not find required file: " + auxPath);
            return false;
        }
        action.accept(auxPath);
        return true;
    }

    private boolean isInsidePartitionFolder() {
        if (partitionFolderName != null) {
            int len = partitionFolderName.length();
            int i = 0;
            for (; i < len; i++) {
                char c = partitionFolderName.charAt(i);
                if (!(Character.isDigit(c) || c == '-' || c == 'T')) {
                    break;
                }
            }
            return i == len;
        }
        return false;
    }

    public enum EventType {
        HIDE_REQUEST // Request to hide the metadata files explorer
    }

    private record ColumnNameTxn(String columnName, long columnNameTxn) {
        static ColumnNameTxn of(Path p) {
            // columnName and columnNameTxn (selected path may contain suffix columnName.c.txn)
            int len = p.length();
            int nameStart = findSeparatorIdx(p, 1) + 1;
            int dotIdx = findNextDotIdx(p, nameStart);
            int dotIdx2 = findNextDotIdx(p, dotIdx + 1);
            String tmp = p.toString();
            String columnName = tmp.substring(nameStart, dotIdx);
            long columnNameTxn = dotIdx2 == -1 ? -1L : Long.parseLong(tmp.substring(dotIdx2 + 1, len));
            return new ColumnNameTxn(columnName, columnNameTxn);
        }
    }
}
