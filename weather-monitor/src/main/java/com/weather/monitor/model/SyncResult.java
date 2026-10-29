package com.weather.monitor.model;

import java.time.LocalDateTime;

/**
 * 抓取任务执行结果
 */
public record SyncResult(
        String triggerType,
        int totalCities,
        int successCities,
        int failedCities,
        String status,
        String message,
        LocalDateTime startedAt,
        LocalDateTime finishedAt) {
}
