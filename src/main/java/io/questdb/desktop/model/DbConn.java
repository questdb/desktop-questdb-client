package io.questdb.desktop.model;

import io.questdb.log.Log;
import io.questdb.log.LogFactory;

import java.io.Closeable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicBoolean;

public class DbConn extends DbConnProperties implements Closeable {

    private static final int IS_VALID_TIMEOUT_SECS = 10;

    private final transient Log log;
    private final transient AtomicBoolean isOpen;
    private transient Connection conn;

    public DbConn(String name) {
        super(name);
        isOpen = new AtomicBoolean();
        log = LogFactory.getLog(String.format("%s [%s]", getClass().getSimpleName(), getUniqueId()));
    }

    /**
     * Deep copy constructor to create a new copy of the connection, with a
     * different name.
     *
     * @param name  name of the connection
     * @param other source connection
     */
    public DbConn(String name, DbConn other) {
        this(name);
        if (other != null) {
            setHost(other.getHost());
            setPort(other.getPort());
            setDatabase(other.getDatabase());
            setUsername(other.getUsername());
            setPassword(other.getPassword());
            setDefault(other.isDefault());
        }
    }

    /**
     * Shallow copy constructor, used by the store, attributes are a reference to
     * the attributes of 'other'.
     *
     * @param other original store item
     */
    @SuppressWarnings("unused")
    public DbConn(StoreEntry other) {
        super(other);
        isOpen = new AtomicBoolean();
        log = LogFactory.getLog(String.format("%s [%s]", getClass().getSimpleName(), getUniqueId()));
    }

    public DbConn(String name, String host, String port, String database, String username, String password) {
        super(name, host, port, username, database, password);
        isOpen = new AtomicBoolean();
        log = LogFactory.getLog(String.format("%s [%s]", getClass().getSimpleName(), getUniqueId()));
    }

    /**
     * @return true if open() was called and thus the connection is open. No checks
     * on validity.
     */
    public boolean isOpen() {
        return isOpen.get();
    }

    /**
     * Connection getter. No checks as to whether it is set, open and/or valid are
     * applied.
     *
     * @return the connection
     */
    public Connection getConnection() {
        return conn;
    }

    /**
     * Returns true if the connection has not been closed and is still valid. The
     * driver shall submit a query on the connection or use some other mechanism
     * that positively verifies the connection is still valid when this method is
     * called.
     * <p>
     * The query submitted by the driver to validate the connection shall be
     * executed in the context of the current transaction. Waits up to 10 seconds
     * for the database operation used to validate the connection to complete. If
     * the timeout period expires before the operation completes, this method
     * returns false.
     *
     * @return true if the connection is valid, false otherwise
     */
    public boolean isValid() {
        try {
            isOpen.set(conn != null && conn.isValid(IS_VALID_TIMEOUT_SECS));
        } catch (SQLException e) {
            isOpen.set(false);
            conn = null;
        }
        return isOpen.get();
    }

    /**
     * Opens the connection, sets it to auto commit true.
     *
     * @return the connection
     * @throws SQLException when the connection cannot be established
     */
    public synchronized Connection open() throws SQLException {
        if (isOpen.get()) {
            return conn;
        }
        log.info().$("Connecting").$();
        conn = DriverManager.getConnection(getUri(), createLoginProperties());
        conn.setAutoCommit(true);
        isOpen.set(true);
        log.info().$("Connected").$();
        return conn;
    }

    @Override
    public synchronized void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                log.info().$("Closing").$();
                conn.close();
                log.info().$("Closed").$();
            }
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        } finally {
            conn = null;
            isOpen.set(false);
        }
    }

    public boolean testConnectivity() {
        try (Connection c = DriverManager.getConnection(getUri(), createLoginProperties())) {
            return c.isValid(IS_VALID_TIMEOUT_SECS);
        } catch (Throwable ignore) {
            ignore.printStackTrace();
            return false;
        }
    }
}
