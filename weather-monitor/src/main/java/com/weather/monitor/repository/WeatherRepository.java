package com.weather.monitor.repository;

import com.weather.monitor.model.DailyWeather;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Time;
import java.util.ArrayList;
import java.util.List;

@Repository
public class WeatherRepository {

    /** 按 (city_id, weather_date) 唯一键 Upsert，重复抓取自动覆盖为最新预报 */
    private static final String UPSERT_SQL = """
            INSERT INTO weather_daily (
                city_id, weather_date, weather_code, condition_text,
                temp_max, temp_min, temp_mean, humidity_avg, pressure_avg, cloud_cover_avg,
                precipitation, rain, snowfall, precipitation_hours,
                wind_speed_max, wind_gusts_max, wind_direction_dominant, uv_index_max,
                sunrise, sunset)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) AS n
            ON DUPLICATE KEY UPDATE
                weather_code = n.weather_code, condition_text = n.condition_text,
                temp_max = n.temp_max, temp_min = n.temp_min, temp_mean = n.temp_mean,
                humidity_avg = n.humidity_avg, pressure_avg = n.pressure_avg,
                cloud_cover_avg = n.cloud_cover_avg,
                precipitation = n.precipitation, rain = n.rain, snowfall = n.snowfall,
                precipitation_hours = n.precipitation_hours,
                wind_speed_max = n.wind_speed_max, wind_gusts_max = n.wind_gusts_max,
                wind_direction_dominant = n.wind_direction_dominant, uv_index_max = n.uv_index_max,
                sunrise = n.sunrise, sunset = n.sunset
            """;

    private final JdbcTemplate jdbc;

    public WeatherRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void upsertAll(List<DailyWeather> rows) {
        List<Object[]> params = new ArrayList<>(rows.size());
        for (DailyWeather w : rows) {
            params.add(new Object[]{
                    w.cityId(), w.weatherDate(), w.weatherCode(), w.conditionText(),
                    w.tempMax(), w.tempMin(), w.tempMean(), w.humidityAvg(), w.pressureAvg(),
                    w.cloudCoverAvg(), w.precipitation(), w.rain(), w.snowfall(),
                    w.precipitationHours(), w.windSpeedMax(), w.windGustsMax(),
                    w.windDirectionDominant(), w.uvIndexMax(),
                    w.sunrise() == null ? null : Time.valueOf(w.sunrise()),
                    w.sunset() == null ? null : Time.valueOf(w.sunset())});
        }
        jdbc.batchUpdate(UPSERT_SQL, params);
    }

    public int count() {
        Integer cnt = jdbc.queryForObject("SELECT COUNT(*) FROM weather_daily", Integer.class);
        return cnt == null ? 0 : cnt;
    }
}
