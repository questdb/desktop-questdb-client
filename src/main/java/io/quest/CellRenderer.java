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

package io.quest;

import java.awt.Component;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;


public class CellRenderer extends DefaultTableCellRenderer {
    private static final Border EMPTY_BORDER = BorderFactory.createEmptyBorder();

    @Override
    public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int rowIdx, int colIdx) {
        super.getTableCellRendererComponent(table, value, isSelected, hasFocus, rowIdx, colIdx);
        setFont(GTk.TABLE_CELL_FONT);
        setBorder(EMPTY_BORDER);
        if (rowIdx > -1 && rowIdx < table.getModel().getRowCount()) {
            if (isSelected) {
                setBackground(GTk.EDITOR_MATCH_FOREGROUND_COLOR);
                setForeground(GTk.QUEST_APP_BACKGROUND_COLOR);
            } else {
                setBackground(GTk.QUEST_APP_BACKGROUND_COLOR);
                setForeground(rowIdx % 2 == 0 ? GTk.EDITOR_MENU_FOREGROUND_COLOR : GTk.EDITOR_KEYWORD_FOREGROUND_COLOR);
            }
            return this;
        }
        throw new IndexOutOfBoundsException(String.format("row %d does not exist, there are [0..%d] rows", rowIdx, table.getModel().getRowCount() - 1));
    }
}
