package io.questdb.desktop.ui.results;

import java.awt.*;
import java.sql.Types;
import java.util.function.Supplier;

import javax.swing.JTable;

import io.questdb.desktop.GTk;
import io.questdb.desktop.model.Table;
import io.questdb.desktop.ui.CellRenderer;


public class SQLCellRenderer extends CellRenderer {

    private static final Color BLUE_GREENISH_COLOR = new Color(0, 112, 112); // blue-greenish
    private static final Color OLIVE_COLOR = new Color(140, 140, 0); // olive
    private static final Color CYAN_DULL_COLOR = new Color(0, 168, 188); // cyan dull

    private final Supplier<Table> tableSupplier;

    public SQLCellRenderer(Supplier<Table> tableSupplier) {
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
                    setForeground(resolveColor(columnTypes[colIdx]));
                }
            }
        }
        return this;
    }

    private static Color resolveColor(int sqlType) {
        return switch (sqlType) {
            case Types.OTHER -> Color.ORANGE;
            case Types.BOOLEAN -> BLUE_GREENISH_COLOR;
            case Types.TINYINT, Types.SMALLINT, Types.INTEGER, Types.BIGINT -> OLIVE_COLOR;
            case Types.REAL, Types.DOUBLE -> Color.GREEN;
            case Types.TIMESTAMP, Types.TIMESTAMP_WITH_TIMEZONE -> CYAN_DULL_COLOR;
            case Types.VARCHAR -> GTk.Editor.KEYWORD_FOREGROUND_COLOR;
            default -> Color.MAGENTA;
        };
    }
}
