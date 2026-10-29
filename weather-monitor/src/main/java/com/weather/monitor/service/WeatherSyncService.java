package com.weather.monitor.service;

import com.weather.monitor.config.WeatherMonitorProperties;
import com.weather.monitor.model.City;
import com.weather.monitor.model.DailyWeather;
import com.weather.monitor.model.SyncResult;
import com.weather.monitor.repository.CityRepository;
import com.weather.monitor.repository.FetchLogRepository;
import com.weather.monitor.repository.WeatherRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 天气同步编排：内置城市清单入库 -> 分批调用天气 API -> Upsert 入库 -> 记录任务日志。
 */
@Service
public class WeatherSyncService {

    private static final Logger log = LoggerFactory.getLogger(WeatherSyncService.class);

    private final WeatherMonitorProperties props;
    private final CityRepository cityRepository;
    private final WeatherRepository weatherRepository;
    private final FetchLogRepository fetchLogRepository;
    private final OpenMeteoClient openMeteoClient;

    public WeatherSyncService(WeatherMonitorProperties props,
                              CityRepository cityRepository,
                              WeatherRepository weatherRepository,
                              FetchLogRepository fetchLogRepository,
                              OpenMeteoClient openMeteoClient) {
        this.props = props;
        this.cityRepository = cityRepository;
        this.weatherRepository = weatherRepository;
        this.fetchLogRepository = fetchLogRepository;
        this.openMeteoClient = openMeteoClient;
    }

    public synchronized SyncResult sync(String triggerType) {
        LocalDateTime startedAt = LocalDateTime.now();
        log.info("[{}] 开始同步全国城市天气...", triggerType);

        int success = 0;
        int failed = 0;
        List<String> errors = new ArrayList<>();
        try {
            // 1. 同步内置城市清单（首次运行自动导入 344 个城市）
            List<City> citiesFromCsv = loadCitiesFromCsv();
            cityRepository.syncAll(citiesFromCsv);

            // 2. 分批抓取并入库
            List<City> cities = cityRepository.findEnabled();
            int batchSize = Math.max(1, props.batchSize());
            for (int from = 0; from < cities.size(); from += batchSize) {
                List<City> batch = cities.subList(from, Math.min(from + batchSize, cities.size()));
                try {
                    List<DailyWeather> rows = openMeteoClient.fetch(batch);
                    weatherRepository.upsertAll(rows);
                    success += batch.size();
                } catch (Exception e) {
                    failed += batch.size();
                    String msg = "批次城市[" + batch.get(0).name() + "~]抓取失败: " + e.getMessage();
                    log.error(msg, e);
                    errors.add(msg);
                }
            }
        } catch (Exception e) {
            log.error("同步任务异常终止", e);
            errors.add("任务异常终止: " + e.getMessage());
        }

        int total = success + failed;
        String status = failed == 0 && success > 0 ? "SUCCESS"
                : success == 0 ? "FAILED" : "PARTIAL";
        String message = errors.isEmpty() ? null
                : String.join(" | ", errors.subList(0, Math.min(3, errors.size())));
        SyncResult result = new SyncResult(triggerType, total, success, failed,
                status, message, startedAt, LocalDateTime.now());
        try {
            fetchLogRepository.insert(result);
        } catch (Exception e) {
            log.error("写入抓取日志失败", e);
        }
        log.info("[{}] 同步完成: 总数={}, 成功={}, 失败={}, 状态={}, 耗时={}s",
                triggerType, total, success, failed, status,
                java.time.Duration.between(startedAt, result.finishedAt()).getSeconds());
        return result;
    }

    /** 读取 classpath:data/china-cities.csv（名称,省份,纬度,经度） */
    private List<City> loadCitiesFromCsv() throws IOException {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                new ClassPathResource("data/china-cities.csv").getInputStream(), StandardCharsets.UTF_8))) {
            Stream<String> lines = reader.lines();
            List<City> cities = lines
                    .skip(1) // 表头
                    .filter(line -> !line.isBlank())
                    .map(line -> line.split(",", -1))
                    .filter(parts -> parts.length >= 4)
                    .map(parts -> new City(null, parts[0].trim(), parts[1].trim(),
                            new BigDecimal(parts[2].trim()), new BigDecimal(parts[3].trim())))
                    .toList();
            if (cities.isEmpty()) {
                throw new IOException("城市清单为空");
            }
            return cities;
        }
    }
}
