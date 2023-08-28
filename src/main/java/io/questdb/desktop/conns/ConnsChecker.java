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

package io.questdb.desktop.conns;

import java.io.Closeable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import io.questdb.desktop.GTk;
import io.questdb.log.Log;
import io.questdb.log.LogFactory;


/**
 * A connection is not valid when it was previously open and then it became
 * unresponsive perhaps due to a server side failure, or network latency.
 * <p>
 * Connections are provided by a supplier. A predefined number of threads are
 * in charge of periodically checking the validity of the supplied database
 * connections. <b>Only</b> connections that are <b>open</b> participate in
 * the validity check.
 * Checks are done concurrently, as any may block for up to 10 secs.
 * When connections are detected to be invalid they are closed and collected
 * into a set which is given back as a callback to a consumer.
 * Supplier and consumer references are provided to the constructor of this
 * class.
 *
 * @see Conn#isValid()
 */
public class ConnsChecker implements Closeable {
    private static final int PERIOD_SECS = 300; // validity period
    private static final int NUM_THREADS = 2;
    private static final Log LOG = LogFactory.getLog(ConnsChecker.class);

    private final Supplier<List<Conn>> connsSupplier;
    private final Consumer<Set<Conn>> lostConnsConsumer;
    private final AtomicBoolean isChecking;
    private ScheduledExecutorService scheduler;


    public ConnsChecker(Supplier<List<Conn>> connsSupplier, Consumer<Set<Conn>> lostConnsConsumer) {
        this.connsSupplier = connsSupplier;
        this.lostConnsConsumer = lostConnsConsumer;
        this.isChecking = new AtomicBoolean();
    }

    public synchronized boolean isRunning() {
        return scheduler != null && !scheduler.isTerminated();
    }

    public synchronized void start() {
        if (scheduler == null) {
            scheduler = Executors.newScheduledThreadPool(NUM_THREADS);
            scheduler.scheduleAtFixedRate(this::dbConnsValidityCheck, PERIOD_SECS, PERIOD_SECS, TimeUnit.SECONDS);
            LOG.info().$("Check every [period=").$(PERIOD_SECS).$(", unit=sec").I$();
        }
    }

    private void dbConnsValidityCheck() {
        if (isChecking.compareAndSet(false, true)) {
            try {
                List<ScheduledFuture<Conn>> notValid = connsSupplier.get()
                        .stream()
                        .filter(Conn::isOpen).map(conn -> scheduler.schedule(
                                () -> !conn.isValid() ? conn : null, 0, TimeUnit.SECONDS
                        )).toList();
                while (notValid.size() > 0) {
                    Set<Conn> notValidSet = new HashSet<>();
                    for (Iterator<ScheduledFuture<Conn>> it = notValid.iterator(); it.hasNext(); ) {
                        ScheduledFuture<Conn> invalidConnFuture = it.next();
                        if (invalidConnFuture.isDone()) {
                            Conn conn = null;
                            try {
                                conn = invalidConnFuture.get();
                            } catch (Exception unexpected) {
                                LOG.error().$("Unexpected error [e=").$(unexpected.getMessage()).I$();
                            } finally {
                                if (conn != null) {
                                    notValidSet.add(conn);
                                }
                                it.remove();
                            }
                        } else if (invalidConnFuture.isCancelled()) {
                            it.remove();
                        }
                    }
                    if (!notValidSet.isEmpty()) {
                        lostConnsConsumer.accept(notValidSet);
                    }
                }
            } finally {
                isChecking.set(false);
            }
        }
    }

    @Override
    public synchronized void close() {
        if (scheduler != null) {
            try {
                GTk.shutdownExecutor(scheduler);
            } finally {
                scheduler = null;
                isChecking.set(false);
                LOG.info().$("Connectivity check stopped").$();
            }
        }
    }
}
