package io.quest.metadata;

import io.questdb.cairo.TableUtils;

public enum FileType {
    META(true),
    TXN(true),
    CV(true),
    SB(true),
    TAB_INDEX(true),
    UPGRADE(true),
    D(true),
    C,
    O,
    K,
    V,
    TXT,
    JSON,
    UNKNOWN;

    private final boolean defaultChecked;

    FileType() {
        this(false);
    }

    FileType(boolean checked) {
        defaultChecked = checked;
    }

    public static FileType of(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return FileType.UNKNOWN;
        }

        // trim potential transaction version
        int p = fileName.length() - 1;
        while (p > 0) {
            char c = fileName.charAt(p);
            if (c >= '0' && c <= '9') {
                p--;
            } else {
                break;
            }
        }
        if (p <= 0) {
            return FileType.UNKNOWN;
        }
        if (fileName.charAt(p) == '.') {
            fileName = fileName.substring(0, p);
        }

        if (fileName.startsWith(TableUtils.META_FILE_NAME)) {
            return FileType.META;
        }

        if (fileName.startsWith(TableUtils.TXN_SCOREBOARD_FILE_NAME)) {
            return FileType.SB;
        }

        if (fileName.startsWith(TableUtils.TXN_FILE_NAME)) {
            return FileType.TXN;
        }

        if (fileName.startsWith(TableUtils.COLUMN_VERSION_FILE_NAME)) {
            return FileType.CV;
        }

        if (fileName.startsWith("_tab_index.d")) {
            return FileType.TAB_INDEX;
        }

        if (fileName.startsWith("_upgrade.d")) {
            return FileType.UPGRADE;
        }

        if (fileName.endsWith(".k")) {
            return FileType.K;
        }

        if (fileName.endsWith(".o")) {
            return FileType.O;
        }

        if (fileName.endsWith(".c")) {
            return FileType.C;
        }

        if (fileName.endsWith(".v")) {
            return FileType.V;
        }

        if (fileName.endsWith(".d")) {
            return FileType.D;
        }

        if (fileName.endsWith(".txt")) {
            return FileType.TXT;
        }

        if (fileName.endsWith(".json")) {
            return FileType.JSON;
        }

        return FileType.UNKNOWN;
    }

    public boolean isDefaultChecked() {
        return defaultChecked;
    }
}
