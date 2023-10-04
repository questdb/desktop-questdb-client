package io.questdb.desktop.ui.metadata;

import io.questdb.cairo.TableUtils;
import io.questdb.std.*;
import io.questdb.std.str.Path;

import java.io.Closeable;

public class CounterFileReader implements Closeable {

    private final FilesFacade ff;

    private int uniqueIdFd = -1;
    private long uniqueIdMem = 0;

    public CounterFileReader(FilesFacade ff) {
        this.ff = ff;
    }

    public long openGetCurrentCount(Path path) {
        close();
        try {
            uniqueIdFd = TableUtils.openFileRWOrFail(ff, path, 0L);
            uniqueIdMem = TableUtils.mapRW(ff, uniqueIdFd, Files.PAGE_SIZE, MemoryTag.MMAP_DEFAULT);
            return getCurrentId();
        } catch (Throwable e) {
            close();
            throw e;
        }
    }

    @Override
    public void close() {
        if (uniqueIdMem != 0) {
            ff.munmap(uniqueIdMem, Files.PAGE_SIZE, MemoryTag.MMAP_DEFAULT);
            uniqueIdMem = 0;
        }
        if (uniqueIdFd != -1) {
            ff.close(uniqueIdFd);
            uniqueIdFd = -1;
        }
    }

    public long getCurrentId() {
        return Unsafe.getUnsafe().getLong(uniqueIdMem);
    }
}
