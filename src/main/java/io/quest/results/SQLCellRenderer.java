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

package io.quest.results;

import java.awt.Component;
import java.util.function.Supplier;

import javax.swing.JTable;

import io.quest.sql.SQLType;
import io.quest.sql.Table;
import io.quest.CellRenderer;


class SQLCellRenderer extends CellRenderer {
    private final Supplier<Table> tableSupplier;

    SQLCellRenderer(Supplier<Table> tableSupplier) {
        this.tableSupplier = tableSupplier;
    }

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
        Table sqlTable = tableSupplier.get();
        if (sqlTable != null && !isSelected && rowIdx > -1 && rowIdx < table.getModel().getRowCount()) {
            if (colIdx > -1) {
                int[] columnTypes = sqlTable.getColumnTypes();
                if (columnTypes != null) {
                    setForeground(SQLType.resolveColor(columnTypes[colIdx]));
                }
            }
        }
        return this;
    }
}
