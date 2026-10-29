package com.weather.monitor.job;

import com.weather.monitor.config.WeatherMonitorProperties;
import com.weather.monitor.service.WeatherSyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 每日定时抓取任务（默认每天 07:00，见 application.yml 的 weather.monitor.cron）。
 * 启动时可选立即执行一次。
 */
@Component
public class DailyWeatherJob implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DailyWeatherJob.class);

    private final WeatherSyncService syncService;
    private final WeatherMonitorProperties props;

    public DailyWeatherJob(WeatherSyncService syncService, WeatherMonitorProperties props) {
        this.syncService = syncService;
        this.props = props;
    }

    @Scheduled(cron = "${weather.monitor.cron}", zone = "Asia/Shanghai")
    public void scheduled() {
        syncService.sync("SCHEDULED");
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!props.runOnStartup()) {
            return;
        }
        // 独立线程执行，避免阻塞应用启动
        Thread starter = new Thread(() -> {
            try {
                Thread.sleep(2000); // 等待数据源等组件就绪
                syncService.sync("STARTUP");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                log.error("启动抓取失败", e);
            }
        }, "startup-weather-sync");
        starter.setDaemon(true);
        starter.start();
    }
}
