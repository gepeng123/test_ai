package com.weather.monitor.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 天气监控配置项（application.yml: weather.monitor.*）
 */
@ConfigurationProperties(prefix = "weather.monitor")
public record WeatherMonitorProperties(
        String apiUrl,
        String cron,
        int forecastDays,
        int batchSize,
        boolean runOnStartup,
        int retryTimes,
        String proxyHost,
        int proxyPort) {

    public boolean useProxy() {
        return proxyHost != null && !proxyHost.isBlank() && proxyPort > 0;
    }
}
