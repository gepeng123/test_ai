package com.weather.monitor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 中国城市每日天气数据监控服务
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class WeatherMonitorApplication {

    public static void main(String[] args) {
        SpringApplication.run(WeatherMonitorApplication.class, args);
    }
}
