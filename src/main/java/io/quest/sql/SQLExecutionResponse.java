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

package io.quest.sql;

/**
 * The class embodying the responses emitted by the {@link SQLExecutor} as it progresses
 * through its query execution life cycle.
 * <p>
 * Each request carries a SQL statement. When it is executed, the progress is progressively
 * notified to the listener by means of instances of this class. Responses contain a
 * reference to a unique instance of {@link Table} which is updated by the executor.
 */
public class SQLExecutionResponse extends SQLExecutionRequest {
    private final Table table;
    private final long totalMillis;
    private final long execMillis;
    private final long fetchMillis;
    private final Throwable error;

    SQLExecutionResponse(SQLExecutionRequest request, Table table, long totalMillis, long execMillis, long fetchMillis) {
        super(request);
        this.table = table;
        this.totalMillis = totalMillis;
        this.execMillis = execMillis;
        this.fetchMillis = fetchMillis;
        this.error = null;
    }

    SQLExecutionResponse(SQLExecutionRequest request, Table table, long totalMillis, Throwable error) {
        super(request);
        this.totalMillis = totalMillis;
        this.error = error;
        this.table = table;
        this.execMillis = -1L;
        this.fetchMillis = -1L;
    }

    public Table getTable() {
        return table;
    }

    /**
     * @return the error, null if none
     */
    public Throwable getError() {
        return error;
    }

    public long getTotalMillis() {
        return totalMillis;
    }

    public long getExecMillis() {
        return execMillis;
    }

    public long getFetchMillis() {
        return fetchMillis;
    }
}
