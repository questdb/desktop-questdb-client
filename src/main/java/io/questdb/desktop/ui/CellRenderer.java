package io.questdb.desktop.ui;

import io.questdb.desktop.GTk;

import java.awt.Component;

import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.table.DefaultTableCellRenderer;


public class CellRenderer extends DefaultTableCellRenderer {
    private static final Border EMPTY_BORDER = BorderFactory.createEmptyBorder();

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
        setFont(GTk.TABLE_CELL_FONT);
        setBorder(EMPTY_BORDER);
        if (rowIdx > -1 && rowIdx < table.getModel().getRowCount()) {
            if (isSelected) {
                setBackground(GTk.Editor.MATCH_FOREGROUND_COLOR);
                setForeground(GTk.APP_BACKGROUND_COLOR);
            } else {
                setBackground(GTk.APP_BACKGROUND_COLOR);
                setForeground(rowIdx % 2 == 0 ?
                        GTk.Editor.MENU_FOREGROUND_COLOR
                        :
                        GTk.Editor.KEYWORD_FOREGROUND_COLOR
                );
            }
            return this;
        }
        throw new IndexOutOfBoundsException(String.format(
                "row %d does not exist, there are [0..%d] rows",
                rowIdx, table.getModel().getRowCount() - 1
        ));
    }
}
