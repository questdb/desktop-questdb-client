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

package io.questdb.desktop;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.io.File;

import io.questdb.desktop.conns.ConnAttrs;
import io.questdb.desktop.store.Store;
import io.questdb.desktop.store.StoreEntry;
import io.questdb.desktop.editor.QuestsEditor;
import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;

public class StoreTest {

    private static String deleteIfExists(String fileName) {
        if (fileName != null) {
            File file = new File(Store.ROOT_PATH, fileName);
            if (file.exists()) {
                assertThat("delete", file.delete());
            }
            assertThat(file.exists(), is(false));
        }
        return fileName;
    }

    @Test
    public void test_persist_load_DBConnection() {
        String fileName = deleteIfExists("test-db-connection-persistence.json");
        try {
            ConnAttrs conn = new ConnAttrs("master-node-0");
            conn.setAttr("host", "prometheus");
            conn.setAttr("port", "5433");
            conn.setAttr("username", "root");
            conn.setAttr("password", "secret password");
            try (Store<ConnAttrs> store = new TStore<>(fileName, ConnAttrs.class)) {
                store.addEntry(conn);
            }

            ConnAttrs pConn;
            try (Store<ConnAttrs> store = new TStore<>(fileName, ConnAttrs.class)) {
                store.loadFromFile();
                assertThat(store.size(), is(1));
                pConn = store.entries().get(0);
            }
            assertThat(pConn.getName(), is("master-node-0"));
            assertThat(pConn.getHost(), is("prometheus"));
            assertThat(pConn.getPort(), is("5433"));
            assertThat(pConn.getUsername(), is("root"));
            assertThat(pConn.getPassword(), is("secret password"));
            assertThat(pConn.getUri(), is("jdbc:postgresql://prometheus:5433/main"));
            assertThat(pConn.getUniqueId(), is("master-node-0 root@prometheus:5433/main"));
            assertThat(conn, Matchers.is(pConn));
        } finally {
            deleteIfExists(fileName);
        }
    }

    @Test
    public void test_persist_load_Content() {
        String fileName = deleteIfExists("test-command-board-content-persistence.json");
        try {
            QuestsEditor.Content content = new QuestsEditor.Content();
            content.setContent("Audentes fortuna  iuvat");
            try (Store<QuestsEditor.Content> store = new TStore<>(fileName, QuestsEditor.Content.class)) {
                store.addEntry(content);
            }

            QuestsEditor.Content rcontent;
            try (Store<QuestsEditor.Content> store = new TStore<>(fileName, QuestsEditor.Content.class)) {
                store.loadFromFile();
                assertThat(store.size(), is(1));
                rcontent = store.entries().get(0);
            }
            MatcherAssert.assertThat(rcontent.getName(), is("default"));
            assertThat(rcontent.getContent(), is("Audentes fortuna  iuvat"));
            MatcherAssert.assertThat(rcontent.getUniqueId(), Matchers.is(rcontent.getName()));
            assertThat(rcontent, is(content));
        } finally {
            deleteIfExists(fileName);
        }
    }

    @Test
    public void test_iterator() {
        String fileName = deleteIfExists("test-store-iterator.json");
        try {
            try (Store<StoreEntry> store = new TStore<>(fileName, StoreEntry.class)) {
                for (int i = 0; i < 10; i++) {
                    StoreEntry entry = new StoreEntry("entry_" + i);
                    entry.setAttr("id", String.valueOf(i));
                    entry.setAttr("age", "14_000");
                    store.addEntry(entry);
                }
            }
            try (Store<StoreEntry> store = new TStore<>(fileName, StoreEntry.class)) {
                store.loadFromFile();
                assertThat(store.size(), is(10));
                int i = 0;
                for (StoreEntry entry : store) {
                    assertThat(entry.getName(), is("entry_" + i));
                    assertThat(entry.getAttr("id"), is(String.valueOf(i)));
                    assertThat(entry.getAttr("age"), is("14_000"));
                    i++;
                }
            }
        } finally {
            deleteIfExists(fileName);
        }
    }

    private static class TStore<T extends StoreEntry> extends Store<T> {
        public TStore(String fileName, Class<? extends StoreEntry> clazz) {
            super(fileName, clazz);
        }

        @Override
        public T[] defaultStoreEntries() {
            return null;
        }
    }
}
