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

package io.questdb.desktop.sql;

import java.awt.Color;
import java.sql.Timestamp;
import java.sql.Types;

import io.questdb.desktop.GTk;

// Resolves {@link java.sql.Types} to their text representation, column width and rendering color
public final class SQLType {
    private static final Color BLUE_GREENISH_COLOR = new Color(0, 112, 112); // blue-greenish
    private static final Color OLIVE_COLOR = new Color(140, 140, 0); // olive
    private static final Color CYAN_DULL_COLOR = new Color(0, 168, 188); // cyan dull

    private SQLType() {
        throw new IllegalStateException("not meant to me instantiated");
    }

    public static boolean isNotNumeric(int sqlType) {
        return switch (sqlType) {
            case Types.TINYINT,
                    Types.SMALLINT,
                    Types.INTEGER,
                    Types.BIGINT,
                    Types.REAL,
                    Types.DOUBLE,
                    Types.DATE,
                    Types.TIMESTAMP,
                    Types.TIMESTAMP_WITH_TIMEZONE,
                    Types.TIME -> false;
            default -> true;
        };
    }

    public static double getNumericValue(Object o, int sqlType) {
        return switch (sqlType) {
            case Types.INTEGER -> (int) o;
            case Types.BIGINT -> (long) o;
            case Types.REAL, Types.DOUBLE -> (double) o;
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> ((Timestamp) o).getTime();
            default -> Double.NaN;
        };
    }

    public static String resolveName(int sqlType) {
        return switch (sqlType) {
            case Types.OTHER -> "OBJECT";
            case Types.BOOLEAN -> "BOOLEAN";
            case Types.TINYINT -> "TINYINT";
            case Types.SMALLINT -> "SMALLINT";
            case Types.INTEGER -> "INTEGER";
            case Types.BIGINT -> "BIGINT";
            case Types.REAL -> "REAL";
            case Types.DOUBLE -> "DOUBLE";
            case Types.DATE -> "DATE";
            case Types.TIMESTAMP -> "TIMESTAMP";
            case Types.TIMESTAMP_WITH_TIMEZONE -> "TIMESTAMPTZ";
            case Types.TIME -> "TIME";
            case Types.TIME_WITH_TIMEZONE -> "TIMETZ";
            case Types.ARRAY -> "ARRAY";
            case Types.BLOB -> "BLOB";
            case Types.BINARY -> "BINARY";
            case Types.VARBINARY -> "VARBINARY";
            case Types.CHAR -> "CHAR";
            case Types.CLOB -> "CLOB";
            case Types.VARCHAR -> "VARCHAR";
            case Types.BIT -> "BIT";
            case Types.STRUCT -> "STRUCT";
            case Types.JAVA_OBJECT -> "JAVA_OBJECT";
            case Types.ROWID -> "";
            default -> String.valueOf(sqlType);
        };
    }

    public static int resolveColWidth(Table table, int colIdx) {
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
        String typeName = resolveName(sqlType);
        return Math.max(width, 20 * (colName.length() + typeName.length()));
    }

    public static Color resolveColor(int sqlType) {
        return switch (sqlType) {
            case Types.OTHER -> Color.ORANGE;
            case Types.BOOLEAN -> BLUE_GREENISH_COLOR;
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> OLIVE_COLOR;
            case Types.REAL, Types.DOUBLE -> Color.GREEN;
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> CYAN_DULL_COLOR;
            case Types.VARCHAR -> GTk.EDITOR_KEYWORD_FOREGROUND_COLOR;
            default -> Color.MAGENTA;
        };
    }
}
