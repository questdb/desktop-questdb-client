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
import io.quest.editor.Editor;
import io.quest.editor.EditorHighlighter;
import io.questdb.cairo.ColumnType;
import io.questdb.cairo.PartitionBy;
import io.questdb.cairo.TableReaderMetadata;
import io.questdb.std.datetime.DateFormat;
import io.questdb.std.datetime.microtime.TimestampFormatCompiler;
import io.questdb.std.str.StringSink;

import javax.swing.text.AbstractDocument;
import javax.swing.text.DefaultEditorKit;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Display extends Editor {

    private static final DateFormat TS_FORMATTER = new TimestampFormatCompiler().compile("yyyy-MM-ddTHH:mm:ss.SSSSSSZ");
    private static final Set<String> KEYWORDS = new HashSet<>();
    private final StringSink sink = new StringSink();
    private ScheduledExecutorService discard;

    public Display() {
        super(false, true, editor -> {
            EditorHighlighter highlighter = new EditorHighlighter(editor.getStyledDocument()) {
                @Override
                public void handleTextChanged(String txt) {
                    if (!KEYWORDS.isEmpty()) {
                        Matcher matcher = Pattern.compile(preCompileKeywords(KEYWORDS), PATTERN_FLAGS).matcher(txt);
                        applyStyle(matcher, HIGHLIGHT_KEYWORD);
                    }
                    super.handleTextChanged(txt);
                }
            };
            AbstractDocument doc = (AbstractDocument) editor.getDocument();
            doc.putProperty(DefaultEditorKit.EndOfLineStringProperty, "\n");
            doc.setDocumentFilter(highlighter);
            return highlighter;
        });
        setFontSize(GTk.METADATA_EXPLORER_FONT_SIZE);
        discard = Executors.newScheduledThreadPool(1);
        discard.schedule(() -> {
            // preload, which compiles the pattern and is costly, penalising startup time
            sink.clear();
            TS_FORMATTER.format(0, null, "Z", sink);
        }, 0L, TimeUnit.MILLISECONDS);
    }

    public void clear() {
        sink.clear();
        if (discard != null) {
            GTk.shutdownExecutor(discard);
            discard = null;
        }
    }

    public void render() {
        displayMessage(sink.toString());
    }

    public void addLn() {
        sink.put(System.lineSeparator());
    }

    public void addLn(String name) {
        sink.put(name).put(System.lineSeparator());
    }

    public void addLn(String name, int value) {
        KEYWORDS.add(name);
        sink.put(name).put(value).put(System.lineSeparator());
    }

    public void addLn(String name, long value) {
        KEYWORDS.add(name);
        sink.put(name).put(value).put(System.lineSeparator());
    }

    public void addLn(String name, boolean value) {
        KEYWORDS.add(name);
        sink.put(name).put(value).put(System.lineSeparator());
    }

    public void addLn(String name, String value) {
        KEYWORDS.add(name);
        sink.put(name).put(value).put(System.lineSeparator());
    }

    public void addIndexedSymbolLn(int index, CharSequence value, boolean indented) {
        if (indented) {
            sink.put(" - ");
        }
        KEYWORDS.add(value.toString());
        sink.put(index).put(": ").put(value).put(System.lineSeparator());
    }

    public void addMicrosLn(String name, long value) {
        KEYWORDS.add(name);
        sink.put(name).put(value).put(" micros (").put(TimeUnit.MICROSECONDS.toSeconds(value)).put(" sec, or ").put(TimeUnit.MICROSECONDS.toMinutes(value)).put(" min)").put(System.lineSeparator());
    }

    public void addTimestampLn(String name, long timestamp) {
        KEYWORDS.add(name);
        if (Long.MAX_VALUE == timestamp) {
            sink.put(name).put("MAX_VALUE").put(System.lineSeparator());
        } else if (Long.MIN_VALUE == timestamp) {
            sink.put(name).put("MIN_VALUE").put(System.lineSeparator());
        } else {
            sink.put(name).put(timestamp).put(" (");
            TS_FORMATTER.format(timestamp, null, "Z", sink);
            sink.put(')').put(System.lineSeparator());
        }
    }

    public void addPartitionLn(int partitionIndex, long partitionTimestamp, long partitionNameTxn, long partitionSize, long partitionColumnVersion) {
        sink.put(" - ").put(partitionIndex).put("/").put(partitionTimestamp).put(" (");
        TS_FORMATTER.format(partitionTimestamp, null, "Z", sink);
        sink.put(')').put(" size: ").put(partitionSize).put(", txn: ").put(partitionNameTxn).put(", cv: ").put(partitionColumnVersion).put(System.lineSeparator());
    }


    public void addColumnLn(int columnIndex, CharSequence columnName, int columnType, boolean columnIsIndexed, int columnIndexBlockCapacity, boolean indented) {
        if (indented) {
            sink.put(" - ");
        }
        String colIdx = "Column " + columnIndex;
        KEYWORDS.add(colIdx);
        KEYWORDS.add(ColumnType.nameOf(columnType));
        sink.put(colIdx).put(": ").put(columnName).put(" ").put(ColumnType.nameOf(columnType));
        if (columnIsIndexed) {
            String indexBlockCapacity = " indexed (block capacity=";
            KEYWORDS.add(indexBlockCapacity);
            sink.put(indexBlockCapacity).put(columnIndexBlockCapacity).put(')');
        }
        sink.put(System.lineSeparator());
    }

    public void addCreateTableLn(TableReaderMetadata metadata) {
        String createTable = "CREATE TABLE IF NOT EXISTS";
        KEYWORDS.add(createTable);
        sink.put(createTable).put(" change_me (").put(System.lineSeparator());
        for (int i = 0, n = metadata.getColumnCount(); i < n; i++) {
            String colName = metadata.getColumnName(i);
            int colType = metadata.getColumnType(i);
            sink.put("    ").put(colName).put(' ').put(ColumnType.nameOf(colType));
            if (colType == ColumnType.SYMBOL && metadata.isColumnIndexed(i)) {
                sink.put(" INDEX CAPACITY ").put(metadata.getIndexValueBlockCapacity(i));
            }
            if (i < n - 1) {
                sink.put(',');
            }
            sink.put(System.lineSeparator());
        }
        sink.put(')');
        int timestampIdx = metadata.getTimestampIndex();
        if (timestampIdx != -1) {
            sink.put(" TIMESTAMP(").put(metadata.getColumnName(timestampIdx)).put(')');
            int partitionBy = metadata.getPartitionBy();
            if (partitionBy != PartitionBy.NONE) {
                sink.put(" PARTITION BY ").put(PartitionBy.toString(partitionBy));
            }
        }
        sink.put(';').put(System.lineSeparator());
    }
}
