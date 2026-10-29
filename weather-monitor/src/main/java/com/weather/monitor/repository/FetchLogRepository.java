package com.weather.monitor.repository;

import com.weather.monitor.model.SyncResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class FetchLogRepository {

    private static final String INSERT_SQL = """
            INSERT INTO fetch_log (trigger_type, total_cities, success_cities, failed_cities,
                                   status, message, started_at, finished_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbc;

    public FetchLogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(SyncResult r) {
        jdbc.update(INSERT_SQL, r.triggerType(), r.totalCities(), r.successCities(),
                r.failedCities(), r.status(), r.message(), r.startedAt(), r.finishedAt());
    }
}
