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

package io.quest.metadata;

import com.sun.nio.file.SensitivityWatchEventModifier;
import io.quest.GTk;
import io.quest.EventConsumer;
import io.quest.EventProducer;
import io.questdb.log.Log;
import io.questdb.log.LogFactory;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

public class FolderChangeObserver implements EventProducer<FolderChangeObserver.EventType>, Closeable {
    private static final Log LOG = LogFactory.getLog(FolderChangeObserver.class);

    private static final WatchEvent.Kind<?>[] EOI = new WatchEvent.Kind<?>[]{
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_CREATE,
            StandardWatchEventKinds.ENTRY_DELETE,
            StandardWatchEventKinds.ENTRY_MODIFY
    };
    private static final WatchEvent.Modifier SENSITIVITY = SensitivityWatchEventModifier.HIGH;
    private static final long POLL_TIMEOUT_MILLIS = 2500L;

    private final WatchService watchService;
    private final Object lock;
    private final Future<?> schedulerThreadHandle;
    private final ExecutorService executor;
    private WatchKey watchedFolderKey;
    private File watchedFolder;


    public FolderChangeObserver(final EventConsumer<FolderChangeObserver, Void> onChangesDetected) {
        try {
            watchService = FileSystems.getDefault().newWatchService();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread t = Executors.defaultThreadFactory().newThread(runnable);
            t.setDaemon(true);
            t.setName(FolderChangeObserver.class.getSimpleName());
            return t;
        });
        lock = new Object();
        schedulerThreadHandle = executor.submit(() -> {
            LOG.info().$("started").$();
            while (!Thread.currentThread().isInterrupted() && isRunning()) {
                try {
                    long startTs = System.currentTimeMillis();
                    WatchKey key;
                    synchronized (lock) {
                        key = watchService.poll(POLL_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
                    }
                    if (key != null) {
                        try {
                            for (WatchEvent<?> event : key.pollEvents()) {
                                String fileEventKind = event.kind().name();
                                switch (WatchEventKind.kindOf(fileEventKind)) {
                                    case ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY -> {
                                        LOG.info().$("change detected").$();
                                        GTk.invokeLater(() -> onChangesDetected.onSourceEvent(
                                                this, EventType.CHANGES_DETECTED, null
                                        ));
                                    }
                                }
                            }
                        } finally {
                            key.reset();
                        }
                    }
                    long elapsed = System.currentTimeMillis() - startTs;
                    if (elapsed < POLL_TIMEOUT_MILLIS) {
                        long wait = POLL_TIMEOUT_MILLIS - elapsed;
                        TimeUnit.MILLISECONDS.sleep(wait);
                    }
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    LOG.info().$("interrupted").$();
                    break;
                } catch (ClosedWatchServiceException e) {
                    LOG.error().$("e: ").$(e.getMessage()).$();
                    break;
                }
            }
            close();
        });
    }

    public void registerReplacing(File folder) {
        synchronized (lock) {
            if (watchedFolder != null) {
                watchedFolderKey.cancel();
                LOG.info().$("cancel observing: ").$(watchedFolder.getAbsolutePath()).$();
            }
            watchedFolder = folder;
            try {
                watchedFolderKey = folder.toPath().register(watchService, EOI, SENSITIVITY);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        LOG.info().$("now observing: ").$(folder.getAbsolutePath()).$();
    }

    @Override
    public void close() {
        if (isRunning()) {
            schedulerThreadHandle.cancel(true);
            GTk.shutdownExecutor(executor);
        }
        synchronized (lock) {
            if (watchedFolder != null) {
                watchedFolderKey.cancel();
                watchedFolder = null;
                watchedFolderKey = null;
            }
            try {
                watchService.close(); // dispose of watch service
            } catch (IOException ignore) {
                // no-op
            }
        }
    }

    private boolean isRunning() {
        return !executor.isShutdown() && !executor.isTerminated();
    }

    public enum EventType {
        CHANGES_DETECTED
    }

    private enum WatchEventKind {
        ENTRY_CREATE, // file is created
        ENTRY_DELETE, // file is deleted
        ENTRY_MODIFY, // file is modified
        UNEXPECTED;   // unexpected event kind

        static WatchEventKind kindOf(String str) {
            try {
                return valueOf(WatchEventKind.class, str);
            } catch (IllegalArgumentException | NullPointerException e) {
                return UNEXPECTED;
            }
        }
    }
}
