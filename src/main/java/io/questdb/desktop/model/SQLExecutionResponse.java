package io.questdb.desktop.model;

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
