package org.ticketstampede.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
/**
 * Provides a PostgreSQL transaction-scoped advisory lock used to serialize
 * concurrent sale resets.
 * All reset transactions acquire the same lock key before modifying the
 * active sale. PostgreSQL allows only one transaction to hold this advisory
 * lock at a time, so concurrent resets execute one after another.
 * The lock is automatically released when the surrounding transaction
 * commits or rolls back.
 */
@Component
public class PostgresAdvisoryLockService {

    private static final long RESET_LOCK_KEY = 9_017_001L;

    private final JdbcTemplate jdbcTemplate;

    public PostgresAdvisoryLockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // Blocks until this transaction acquires the global reset lock.
    public void acquireResetLock() {
        jdbcTemplate.execute(
                "SELECT pg_advisory_xact_lock(" + RESET_LOCK_KEY + ")"
        );
    }
}
