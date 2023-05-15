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

package io.questdb.desktop.sql;

import java.io.Closeable;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import io.questdb.desktop.EventConsumer;
import io.questdb.desktop.EventProducer;
import io.questdb.desktop.GTk;
import io.questdb.desktop.conns.Conn;
import io.questdb.log.Log;
import io.questdb.log.LogFactory;

public class SQLExecutor implements EventProducer<SQLExecutor.EventType>, Closeable {

    public static final int MAX_BATCH_SIZE = 5000;
    public static final int QUERY_EXECUTION_TIMEOUT_SECS = 30;
    private static final int START_BATCH_SIZE = 100;
    private static final Log LOG = LogFactory.getLog(SQLExecutor.class);
    private static final ThreadFactory THREAD_FACTORY = Executors.defaultThreadFactory();
    private static final int NUMBER_OF_THREADS = 1;
    private final ConcurrentMap<String, Future<?>> runningQueries = new ConcurrentHashMap<>();
    private ExecutorService executor;

    private static long elapsedMillis(long start) {
        return millis(System.nanoTime() - start);
    }

    private static long millis(long nanos) {
        return TimeUnit.MILLISECONDS.convert(nanos, TimeUnit.NANOSECONDS);
    }

    public synchronized void start() {
        if (executor == null) {
            runningQueries.clear();
            final String name = getClass().getSimpleName();
            executor = Executors.newFixedThreadPool(NUMBER_OF_THREADS, runnable -> {
                Thread t = THREAD_FACTORY.newThread(runnable);
                t.setDaemon(true);
                t.setName(name);
                return t;
            });
            LOG.info().$(name).$("is running").$();
        }
    }

    @Override
    public synchronized void close() {
        if (executor != null) {
            for (Future<?> query : runningQueries.values()) {
                if (!query.isDone() && !query.isCancelled()) {
                    query.cancel(true);
                }
            }
            try {
                GTk.shutdownExecutor(executor);
            } finally {
                executor = null;
                runningQueries.clear();
                LOG.info().$("has finished").$();
            }
        }
    }

    public synchronized void submit(SQLExecutionRequest req, EventConsumer<SQLExecutor, SQLExecutionResponse> eventConsumer) {
        if (executor == null) {
            throw new IllegalStateException("not started");
        }
        if (eventConsumer == null) {
            throw new IllegalStateException("eventConsumer cannot be null");
        }
        cancelExistingRequest(req);
        String sourceId = req.getSourceId();
        runningQueries.put(sourceId, executor.submit(() -> executeRequest(req, eventConsumer)));
        LOG.info().$("Execution submitted [reqId=").$(req.getUniqueId())
            .$(", srcId=").$(sourceId)
            .I$();
    }

    public synchronized void cancelExistingRequest(SQLExecutionRequest req) {
        if (executor == null) {
            throw new IllegalStateException("not started");
        }
        final String sourceId = req.getSourceId();
        final Future<?> exec = runningQueries.remove(sourceId);
        if (exec != null && !exec.isDone() && !exec.isCancelled()) {
            exec.cancel(true);
            LOG.info().$("Cancelling [reqId=").$(req.getUniqueId())
                .$(", srcId=").$(sourceId)
                .I$();
        }
    }

    private void executeRequest(SQLExecutionRequest req, EventConsumer<SQLExecutor, SQLExecutionResponse> eventListener) {
        final long startNanos = System.nanoTime();
        final String sourceId = req.getSourceId();
        final Conn conn = req.getConnection();
        final String query = req.getSqlCommand();
        final Table table = new Table(req.getUniqueId());

        if (!conn.isValid()) {
            runningQueries.remove(sourceId);
            LOG.info().$("Failed [reqId=").$(req.getUniqueId())
                .$(", srcId=").$(sourceId)
                .$(", conn=").$(conn)
                .I$();
            eventListener.onSourceEvent(
                SQLExecutor.this,
                EventType.FAILURE,
                new SQLExecutionResponse(
                    req,
                    table,
                    elapsedMillis(startNanos),
                    new RuntimeException(String.format("Connection [%s] is not valid", conn))
                ));
            return;
        }

        LOG.info().$("Executing [reqId=").$(req.getUniqueId())
            .$(", srcId=").$(sourceId)
            .$(", connId=").$(conn.getUniqueId())
            .$(", query=").$(query)
            .I$();
        eventListener.onSourceEvent(
            SQLExecutor.this,
            EventType.STARTED,
            new SQLExecutionResponse(req, table, elapsedMillis(startNanos), 0L, 0L));

        final long fetchStartNanos;
        final long execMillis;
        long rowIdx = 0;
        int batchSize = START_BATCH_SIZE;
        try (Statement stmt = conn.getConnection().createStatement()) {
            stmt.setQueryTimeout(QUERY_EXECUTION_TIMEOUT_SECS);
            final boolean returnsResults = stmt.execute(query);
            fetchStartNanos = System.nanoTime();
            execMillis = millis(fetchStartNanos - startNanos);
            if (returnsResults) {
                ResultSet rs = stmt.getResultSet();
                if (rs.next()) {
                    final long fetchChkNanos = System.nanoTime();
                    final long totalMs = millis(fetchChkNanos - startNanos);
                    final long fetchMs = millis(fetchChkNanos - fetchStartNanos);
                    table.setColumnMetadata(rs);
                    table.addRow(rowIdx++, rs);
                    eventListener.onSourceEvent(
                        SQLExecutor.this,
                        EventType.FIRST_ROW_AVAILABLE,
                        new SQLExecutionResponse(req, table, totalMs, execMillis, fetchMs));
                }
                while (rs.next()) {
                    final long fetchChkNanos = System.nanoTime();
                    table.addRow(rowIdx++, rs);
                    if (0 == rowIdx % batchSize) {
                        batchSize = Math.min(batchSize * 2, MAX_BATCH_SIZE);
                        final long totalMs = millis(fetchChkNanos - startNanos);
                        final long fetchMs = millis(fetchChkNanos - fetchStartNanos);
                        eventListener.onSourceEvent(
                            SQLExecutor.this,
                            EventType.ROWS_AVAILABLE,
                            new SQLExecutionResponse(req, table, totalMs, execMillis, fetchMs));
                    }
                }
            }
        } catch (SQLException fail) {
            runningQueries.remove(sourceId);
            LOG.error().$("Failed [reqId=").$(req.getUniqueId())
                .$(", srcId=").$(sourceId)
                .$(", e=").$(fail.getMessage())
                .I$();
            eventListener.onSourceEvent(
                SQLExecutor.this,
                EventType.FAILURE,
                new SQLExecutionResponse(req, table, elapsedMillis(startNanos), fail));
            return;
        }
        runningQueries.remove(sourceId);
        EventType eventType = EventType.COMPLETED;
        final long endNanos = System.nanoTime();
        final long totalMs = millis(endNanos - startNanos);
        final long fetchMs = millis(endNanos - fetchStartNanos);
        LOG.info().$("Event [name=").$(eventType.name())
            .$(", reqId=").$(req.getUniqueId())
            .$(", tableSize=").$(table.size())
            .$(", totalMs=").$(totalMs)
            .$(", execMs=").$(execMillis)
            .$(", fetchMs=").$(fetchMs)
            .I$();
        eventListener.onSourceEvent(
            SQLExecutor.this,
            eventType,
            new SQLExecutionResponse(req, table, totalMs, execMillis, fetchMs));
    }

    public enum EventType {
        STARTED,
        FIRST_ROW_AVAILABLE,
        ROWS_AVAILABLE,
        COMPLETED,
        CANCELLED,
        FAILURE
    }
}
