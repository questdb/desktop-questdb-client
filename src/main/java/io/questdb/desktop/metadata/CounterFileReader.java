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

package io.questdb.desktop.metadata;

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
