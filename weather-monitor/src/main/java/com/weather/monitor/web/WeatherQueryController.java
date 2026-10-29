package com.weather.monitor.web;

import com.weather.monitor.model.SyncResult;
import com.weather.monitor.repository.CityRepository;
import com.weather.monitor.repository.WeatherRepository;
import com.weather.monitor.service.WeatherSyncService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 天气数据查询 API
 */
@RestController
@RequestMapping("/api")
public class WeatherQueryController {

    private final JdbcTemplate jdbc;
    private final CityRepository cityRepository;
    private final WeatherRepository weatherRepository;
    private final WeatherSyncService syncService;

    public WeatherQueryController(JdbcTemplate jdbc,
                                  CityRepository cityRepository,
                                  WeatherRepository weatherRepository,
                                  WeatherSyncService syncService) {
        this.jdbc = jdbc;
        this.cityRepository = cityRepository;
        this.weatherRepository = weatherRepository;
        this.syncService = syncService;
    }

    /** 城市概况 */
    @GetMapping("/cities")
    public Map<String, Object> cities() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("cityCount", cityRepository.count());
        result.put("weatherRowCount", weatherRepository.count());
        result.put("provinces", cityRepository.provinces());
        return result;
    }

    /**
     * 查询某天的全国天气。不传 date 时取最新一天。
     * 示例: /api/weather?date=2026-10-29&province=广东省&city=深圳
     */
    @GetMapping("/weather")
    public List<Map<String, Object>> weather(
            @RequestParam(required = false) LocalDate date,
            @RequestParam(required = false) String province,
            @RequestParam(required = false) String city,
            @RequestParam(defaultValue = "400") int limit) {

        StringBuilder sql = new StringBuilder("""
                SELECT c.name AS city, c.province, w.weather_date, w.condition_text,
                       w.temp_max, w.temp_min, w.temp_mean, w.humidity_avg, w.precipitation,
                       w.wind_speed_max, w.sunrise, w.sunset, w.updated_at
                FROM weather_daily w JOIN city c ON c.id = w.city_id
                WHERE w.weather_date = IFNULL(?, (SELECT MAX(weather_date) FROM weather_daily))
                """);
        List<Object> params = new ArrayList<>();
        params.add(date);
        if (province != null && !province.isBlank()) {
            sql.append(" AND c.province = ?");
            params.add(province.trim());
        }
        if (city != null && !city.isBlank()) {
            sql.append(" AND c.name LIKE CONCAT('%', ?, '%')");
            params.add(city.trim());
        }
        sql.append(" ORDER BY w.temp_max DESC LIMIT ?");
        params.add(Math.min(Math.max(limit, 1), 1000));
        return jdbc.queryForList(sql.toString(), params.toArray());
    }

    /** 某天全国天气统计（不传 date 时取最新一天） */
    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam(required = false) LocalDate date) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.putAll(jdbc.queryForMap("""
                SELECT IFNULL(?, (SELECT MAX(weather_date) FROM weather_daily)) AS weather_date,
                       COUNT(*) AS city_count,
                       ROUND(AVG(w.temp_mean), 2) AS avg_temp,
                       ROUND(SUM(w.precipitation), 2) AS total_precipitation
                FROM weather_daily w WHERE w.weather_date = IFNULL(?, (SELECT MAX(weather_date) FROM weather_daily))
                """, date, date));
        result.put("hottest", jdbc.queryForList("""
                SELECT c.name AS city, c.province, w.temp_max
                FROM weather_daily w JOIN city c ON c.id = w.city_id
                WHERE w.weather_date = IFNULL(?, (SELECT MAX(weather_date) FROM weather_daily))
                ORDER BY w.temp_max DESC LIMIT 5
                """, date));
        result.put("coldest", jdbc.queryForList("""
                SELECT c.name AS city, c.province, w.temp_min
                FROM weather_daily w JOIN city c ON c.id = w.city_id
                WHERE w.weather_date = IFNULL(?, (SELECT MAX(weather_date) FROM weather_daily))
                ORDER BY w.temp_min ASC LIMIT 5
                """, date));
        result.put("recentLogs", jdbc.queryForList("""
                SELECT trigger_type, total_cities, success_cities, failed_cities,
                       status, started_at, finished_at
                FROM fetch_log ORDER BY id DESC LIMIT 5
                """));
        return result;
    }

    /** 手动触发一次全量抓取（全量约需几秒到几十秒） */
    @PostMapping("/sync")
    public SyncResult sync() {
        return syncService.sync("MANUAL");
    }
}
