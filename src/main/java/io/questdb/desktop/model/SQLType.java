package io.questdb.desktop.model;

import java.sql.Timestamp;
import java.sql.Types;

public final class SQLType {

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
}
