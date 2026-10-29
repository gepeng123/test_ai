package com.weather.monitor.repository;

import com.weather.monitor.model.City;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class CityRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO city (name, province, latitude, longitude)
            VALUES (?, ?, ?, ?) AS n
            ON DUPLICATE KEY UPDATE latitude = n.latitude, longitude = n.longitude
            """;

    private static final RowMapper<City> CITY_MAPPER = (rs, rowNum) -> new City(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getString("province"),
            rs.getBigDecimal("latitude"),
            rs.getBigDecimal("longitude"));

    private final JdbcTemplate jdbc;

    public CityRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 同步内置城市清单到数据库（存在则更新经纬度） */
    public void syncAll(List<City> cities) {
        List<Object[]> params = cities.stream()
                .map(c -> new Object[]{c.name(), c.province(), c.latitude(), c.longitude()})
                .toList();
        jdbc.batchUpdate(UPSERT_SQL, params);
    }

    public List<City> findEnabled() {
        return jdbc.query(
                "SELECT id, name, province, latitude, longitude FROM city WHERE enabled = 1 ORDER BY id",
                CITY_MAPPER);
    }

    public int count() {
        Integer cnt = jdbc.queryForObject("SELECT COUNT(*) FROM city", Integer.class);
        return cnt == null ? 0 : cnt;
    }

    public List<String> provinces() {
        return jdbc.queryForList(
                "SELECT DISTINCT province FROM city ORDER BY province", String.class);
    }
}
