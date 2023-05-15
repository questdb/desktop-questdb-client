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

package io.questdb.desktop.store;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

import io.questdb.desktop.GTk;
import io.questdb.desktop.metadata.Metadata;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import io.questdb.log.Log;
import io.questdb.log.LogFactory;

import javax.swing.*;


public abstract class Store<T extends StoreEntry> implements Closeable, Iterable<T> {

    public static final File ROOT_PATH;
    private static final Log LOG = LogFactory.getLog(Store.class);
    private static final Class<?>[] ITEM_CONSTRUCTOR_SIGNATURE = {StoreEntry.class};
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Type STORE_TYPE = new TypeToken<ArrayList<StoreEntry>>() {
        /* type */
    }.getType();

    static {
        synchronized (Metadata.class) {
            String userHome = System.getProperty("user.home");
            ROOT_PATH = new File(userHome != null ? userHome : ".", "QUESTS").getAbsoluteFile();
            if (!ROOT_PATH.exists()) {
                LOG.info().$("Creating Store [path=").$(ROOT_PATH).I$();
                if (!ROOT_PATH.mkdirs()) {
                    JOptionPane.showMessageDialog(
                            null,
                            "Could not create folder: " + ROOT_PATH,
                            "Notice",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        }
    }

    private final String fileName;
    private final Class<? extends StoreEntry> entryClass;
    private final List<T> entries;
    private final ExecutorService asyncPersist;

    public Store(String fileName, Class<? extends StoreEntry> entryClass) {
        this.fileName = fileName;
        this.entryClass = entryClass;
        entries = new ArrayList<>();
        asyncPersist = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable);
            thread.setDaemon(false);
            thread.setName("Store-" + fileName);
            return thread;
        });
    }

    private static List<StoreEntry> loadFromFile(File file) {
        List<StoreEntry> entries = null;
        try (BufferedReader in = new BufferedReader(new FileReader(file, StandardCharsets.UTF_8))) {
            entries = GSON.fromJson(in, STORE_TYPE);
            LOG.info().$("Loaded [path=").$(file.getAbsolutePath()).I$();
        } catch (Exception e) {
            LOG.error().$("Could not load store [path=").$(file.getAbsolutePath())
                    .$(", e=").$(e.getMessage())
                    .I$();
        }
        return entries;
    }

    @Override
    public void close() {
        GTk.shutdownExecutor(asyncPersist);
    }

    public abstract T[] defaultStoreEntries();


    public synchronized void addEntry(T entry) {
        if (entry != null) {
            entries.add(entry);
        }
        asyncSaveToFile();
    }

    public synchronized T getEntry(int idx, Supplier<T> constructor) {
        if (constructor != null && entries.size() == idx) {
            entries.add(idx, constructor.get());
        }
        return entries.get(idx);
    }

    public synchronized void removeEntry(T entry) {
        if (entry != null) {
            entries.remove(entry);
            asyncSaveToFile();
        }
    }

    public synchronized void removeEntry(int idx) {
        entries.remove(idx);
        asyncSaveToFile();
    }

    public synchronized List<T> entries() {
        return Collections.unmodifiableList(entries);
    }

    public synchronized String[] entryNames() {
        return entries.stream().map(StoreEntry::getName).toArray(String[]::new);
    }

    public synchronized int size() {
        return entries.size();
    }

    @Override
    public Iterator<T> iterator() {
        return new Iterator<>() {

            private final List<T> values = entries();
            private int idx = 0;

            @Override
            public boolean hasNext() {
                return idx < values.size();
            }

            @Override
            public T next() {
                if (idx >= values.size()) {
                    throw new NoSuchElementException();
                }
                return values.get(idx++);
            }
        };
    }

    public void asyncSaveToFile() {
        asyncPersist.submit(() -> saveToFile());
    }

    public void saveToFile(File file) {
        try (FileWriter out = new FileWriter(file, StandardCharsets.UTF_8, false)) {
            GSON.toJson(entries, STORE_TYPE, out);
            LOG.info().$("Saved [path=").$(file.getAbsolutePath()).I$();
        } catch (IOException e) {
            LOG.error().$("Could not store into file [path=").$(file.getAbsolutePath())
                    .$(", e=").$(e.getMessage())
                    .I$();
        }
    }

    public void loadFromFile() {
        File file = getFile();
        if (!file.exists()) {
            for (T entry : defaultStoreEntries()) {
                if (entry != null) {
                    entries.add(entry);
                }
            }
            saveToFile(() -> LOG.info().$("Created default store [path=").$(file.getAbsolutePath()).I$());
            return;
        }

        List<StoreEntry> content = loadFromFile(file);
        if (content != null) {
            try {
                // This constructor is T's decorator constructor to StoreEntry(StoreEntry
                // other).
                // We do not need to instantiate yet another attribute's map when we can recycle
                // the instance provided by GSON.
                @SuppressWarnings("unchecked")
                Constructor<T> entryFactory = (Constructor<T>) entryClass.getConstructor(ITEM_CONSTRUCTOR_SIGNATURE);
                entries.clear();
                for (StoreEntry i : content) {
                    entries.add(entryFactory.newInstance(i));
                }
            } catch (Throwable e) {
                throw new RuntimeException(e);
            }
        }
    }

    public void saveToFile() {
        saveToFile(getFile());
    }

    private void saveToFile(Runnable whenDoneTask) {
        try {
            saveToFile(getFile());
        } finally {
            if (whenDoneTask != null) {
                whenDoneTask.run();
            }
        }
    }

    private File getFile() {
        return new File(ROOT_PATH, fileName);
    }
}
