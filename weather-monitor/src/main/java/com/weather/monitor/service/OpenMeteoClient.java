package com.weather.monitor.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weather.monitor.config.WeatherMonitorProperties;
import com.weather.monitor.model.City;
import com.weather.monitor.model.DailyWeather;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Open-Meteo 免费天气 API 客户端（无需 API Key）。
 * 支持一次请求批量查询多个城市，返回未来 N 天的逐日预报。
 */
@Component
public class OpenMeteoClient {

    private static final Logger log = LoggerFactory.getLogger(OpenMeteoClient.class);

    private static final String DAILY_VARS = String.join(",",
            "weather_code", "temperature_2m_max", "temperature_2m_min", "temperature_2m_mean",
            "precipitation_sum", "rain_sum", "snowfall_sum", "precipitation_hours",
            "wind_speed_10m_max", "wind_gusts_10m_max", "wind_direction_10m_dominant",
            "uv_index_max", "sunrise", "sunset");

    private static final String HOURLY_VARS = String.join(",",
            "relative_humidity_2m", "surface_pressure", "cloud_cover");

    private final WeatherMonitorProperties props;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OpenMeteoClient(WeatherMonitorProperties props, ObjectMapper objectMapper) {
        this.props = props;
        this.objectMapper = objectMapper;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NORMAL);
        if (props.useProxy()) {
            builder.proxy(ProxySelector.of(
                    new InetSocketAddress(props.proxyHost(), props.proxyPort())));
            log.info("天气 API 出站代理: {}:{}", props.proxyHost(), props.proxyPort());
        }
        this.httpClient = builder.build();
    }

    /**
     * 批量获取一批城市的逐日天气（含未来 N 天）。
     */
    public List<DailyWeather> fetch(List<City> cities) throws IOException, InterruptedException {
        String latitudes = String.join(",", cities.stream().map(c -> c.latitude().toPlainString()).toList());
        String longitudes = String.join(",", cities.stream().map(c -> c.longitude().toPlainString()).toList());

        URI uri = URI.create(props.apiUrl()
                + "?latitude=" + latitudes
                + "&longitude=" + longitudes
                + "&timezone=Asia/Shanghai"
                + "&forecast_days=" + props.forecastDays()
                + "&daily=" + DAILY_VARS
                + "&hourly=" + HOURLY_VARS);

        String body = getWithRetry(uri);
        JsonNode root = objectMapper.readTree(body);
        // 多城市返回数组；单城市返回对象
        List<JsonNode> locations = new ArrayList<>();
        if (root.isArray()) {
            root.forEach(locations::add);
        } else {
            locations.add(root);
        }
        if (locations.size() != cities.size()) {
            throw new IOException("API 返回城市数不匹配: 期望 " + cities.size() + " 实际 " + locations.size());
        }

        List<DailyWeather> result = new ArrayList<>();
        for (int i = 0; i < cities.size(); i++) {
            result.addAll(parseLocation(cities.get(i), locations.get(i)));
        }
        return result;
    }

    private String getWithRetry(URI uri) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
        IOException lastError = null;
        for (int attempt = 1; attempt <= Math.max(1, props.retryTimes()); attempt++) {
            try {
                HttpResponse<String> response =
                        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IOException("HTTP " + response.statusCode() + ": "
                            + truncate(response.body()));
                }
                return response.body();
            } catch (IOException e) {
                lastError = e;
                log.warn("天气 API 请求失败(第{}次): {}", attempt, e.getMessage());
                if (attempt < props.retryTimes()) {
                    Thread.sleep(1000L * attempt);
                }
            }
        }
        throw lastError;
    }

    private List<DailyWeather> parseLocation(City city, JsonNode location) {
        List<DailyWeather> rows = new ArrayList<>();
        JsonNode daily = location.path("daily");
        JsonNode dailyTime = daily.path("time");
        if (!dailyTime.isArray() || dailyTime.isEmpty()) {
            return rows;
        }

        // 小时级数据按日期分组求日均（湿度/气压/云量）
        Map<String, double[]> hourlyAvgByDate = averageHourlyByDate(location.path("hourly"));

        for (int d = 0; d < dailyTime.size(); d++) {
            LocalDate date = LocalDate.parse(dailyTime.get(d).asText());
            Integer weatherCode = intOrNull(daily, "weather_code", d);
            double[] hourly = hourlyAvgByDate.get(date.toString());
            rows.add(new DailyWeather(
                    city.id(),
                    date,
                    weatherCode,
                    WmoCode.describe(weatherCode),
                    decOrNull(daily, "temperature_2m_max", d),
                    decOrNull(daily, "temperature_2m_min", d),
                    decOrNull(daily, "temperature_2m_mean", d),
                    hourly == null ? null : round2(hourly[0]),
                    hourly == null ? null : round2(hourly[1]),
                    hourly == null ? null : round2(hourly[2]),
                    decOrNull(daily, "precipitation_sum", d),
                    decOrNull(daily, "rain_sum", d),
                    decOrNull(daily, "snowfall_sum", d),
                    decOrNull(daily, "precipitation_hours", d),
                    decOrNull(daily, "wind_speed_10m_max", d),
                    decOrNull(daily, "wind_gusts_10m_max", d),
                    intOrNull(daily, "wind_direction_10m_dominant", d),
                    decOrNull(daily, "uv_index_max", d),
                    timeOrNull(daily, "sunrise", d),
                    timeOrNull(daily, "sunset", d)));
        }
        return rows;
    }

    /** 返回 date -> {平均湿度, 平均气压, 平均云量} */
    private Map<String, double[]> averageHourlyByDate(JsonNode hourly) {
        Map<String, double[]> result = new HashMap<>();
        JsonNode time = hourly.path("time");
        if (!time.isArray() || time.isEmpty()) {
            return result;
        }
        Map<String, double[]> sums = new HashMap<>();
        Map<String, int[]> counts = new HashMap<>();
        for (int h = 0; h < time.size(); h++) {
            String date = time.get(h).asText().substring(0, 10);
            double humidity = hourly.path("relative_humidity_2m").path(h).asDouble(Double.NaN);
            double pressure = hourly.path("surface_pressure").path(h).asDouble(Double.NaN);
            double cloud = hourly.path("cloud_cover").path(h).asDouble(Double.NaN);
            if (Double.isNaN(humidity) && Double.isNaN(pressure) && Double.isNaN(cloud)) {
                continue;
            }
            double[] sum = sums.computeIfAbsent(date, k -> new double[3]);
            int[] cnt = counts.computeIfAbsent(date, k -> new int[3]);
            if (!Double.isNaN(humidity)) {
                sum[0] += humidity;
                cnt[0]++;
            }
            if (!Double.isNaN(pressure)) {
                sum[1] += pressure;
                cnt[1]++;
            }
            if (!Double.isNaN(cloud)) {
                sum[2] += cloud;
                cnt[2]++;
            }
        }
        sums.forEach((date, sum) -> {
            int[] cnt = counts.get(date);
            result.put(date, new double[]{
                    cnt[0] == 0 ? Double.NaN : sum[0] / cnt[0],
                    cnt[1] == 0 ? Double.NaN : sum[1] / cnt[1],
                    cnt[2] == 0 ? Double.NaN : sum[2] / cnt[2]});
        });
        return result;
    }

    private static BigDecimal decOrNull(JsonNode parent, String field, int idx) {
        JsonNode node = parent.path(field).path(idx);
        return node.isNumber() ? BigDecimal.valueOf(node.asDouble()) : null;
    }

    private static Integer intOrNull(JsonNode parent, String field, int idx) {
        JsonNode node = parent.path(field).path(idx);
        return node.isNumber() ? node.asInt() : null;
    }

    private static LocalTime timeOrNull(JsonNode parent, String field, int idx) {
        JsonNode node = parent.path(field).path(idx);
        if (!node.isTextual()) {
            return null;
        }
        return LocalDateTime.parse(node.asText()).toLocalTime();
    }

    private static BigDecimal round2(double v) {
        return Double.isNaN(v) ? null : BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static String truncate(String s) {
        return s == null ? "" : (s.length() > 200 ? s.substring(0, 200) : s);
    }
}
