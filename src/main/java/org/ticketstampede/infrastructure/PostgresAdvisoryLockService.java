package org.ticketstampede.infrastructure;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class PostgresAdvisoryLockService {

    private static final long RESET_LOCK_KEY = 9_017_001L;

    private final JdbcTemplate jdbcTemplate;

    public PostgresAdvisoryLockService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void acquireResetLock() {
        jdbcTemplate.execute(
                "SELECT pg_advisory_xact_lock(" + RESET_LOCK_KEY + ")"
        );
    }
}
