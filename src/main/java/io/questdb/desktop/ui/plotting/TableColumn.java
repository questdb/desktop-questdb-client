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

package io.questdb.desktop.ui.plotting;


import io.questdb.desktop.model.SQLPagedTableModel;
import io.questdb.desktop.model.SQLType;

import java.awt.*;

public class TableColumn implements Column {

    private final String name;
    private final SQLPagedTableModel table;
    private final int colIndex;
    private final Color color;
    private final double min, max;

    public TableColumn(String name, SQLPagedTableModel table, int colIndex, Color color) {
        this.name = name;
        this.table = table;
        this.colIndex = colIndex;
        this.color = color;
        double tMin = Double.MAX_VALUE;
        double tMax = Double.MIN_VALUE;
        for (int i = 0; i < table.getRowCount(); i++) {
            double val = get(i);
            tMin = Math.min(tMin, val);
            tMax = Math.max(tMax, val);
        }
        this.min = tMin;
        this.max = tMax;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Color color() {
        return color;
    }

    @Override
    public int size() {
        return table.getRowCount();
    }

    @Override
    public void append(double value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public double get(int i) {
        return SQLType.getNumericValue(table.getValueAt(i, colIndex), table.getColumnType(colIndex));
    }

    @Override
    public double min() {
        return min;
    }

    @Override
    public double max() {
        return max;
    }
}
