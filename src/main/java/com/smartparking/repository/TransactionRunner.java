package com.smartparking.repository;

import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Runs a block of JDBC work on one connection inside a transaction.
 * Commits if the block finishes normally, rolls back if anything throws.
 */
@Component
public class TransactionRunner {

    private final DataSource dataSource;

    public TransactionRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @FunctionalInterface
    public interface SqlWork<T> {
        T apply(Connection connection) throws SQLException;
    }

    public <T> T inTransaction(SqlWork<T> work) {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                T result = work.apply(connection);
                connection.commit();
                return result;
            } catch (SQLException ex) {
                rollbackQuietly(connection);
                throw new PersistenceException("SQL error, transaction rolled back: " + ex.getMessage(), ex);
            } catch (RuntimeException ex) {
                rollbackQuietly(connection);
                throw ex;
            }
        } catch (SQLException ex) {
            throw new PersistenceException("Could not open database connection", ex);
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException rollbackEx) {
            System.err.println("Rollback failed: " + rollbackEx.getMessage());
        }
    }
}
