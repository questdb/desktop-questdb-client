package io.questdb.desktop.model;

import java.util.UUID;


/**
 * {@link SQLExecutor}'s unit of work.
 * <p>
 * Each request comes from a source, carries a SQL statement, and is identified by a unique
 * id. On execution, the results are returned by means of one, or many, callbacks delivering
 * instances of {@link SQLExecutionResponse}. Responses must be seen as delta updates on the
 * loading state of a single instance of {@link Table} updated by the executor.
 */
public class SQLExecutionRequest implements UniqueId<String> {
    private final String sourceId;
    private final String uniqueId;
    private final DbConn conn;
    private final String sqlCommand;

    /**
     * Constructor used by {@link SQLExecutionResponse} to keep the relation between
     * a request and a response. This constructor is used to produce responses for
     * requests which execution are successful.
     *
     * @param sourceId   command source, or requester, id
     * @param conn       will send the command down this connection
     * @param sqlCommand SQL command to execute
     */
    public SQLExecutionRequest(String sourceId, DbConn conn, String sqlCommand) {
        this(sourceId, UUID.randomUUID().toString(), conn, sqlCommand);
    }

    SQLExecutionRequest(SQLExecutionRequest request) {
        this(request.sourceId, request.uniqueId, request.conn, request.sqlCommand);
    }

    private SQLExecutionRequest(String sourceId, String uniqueId, DbConn conn, String sqlCommand) {
        this.sourceId = sourceId;
        this.uniqueId = uniqueId;
        this.conn = conn;
        this.sqlCommand = sqlCommand;
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getSqlCommand() {
        return sqlCommand;
    }

    public DbConn getConnection() {
        return conn;
    }

    @Override
    public String getUniqueId() {
        return uniqueId;
    }
}
