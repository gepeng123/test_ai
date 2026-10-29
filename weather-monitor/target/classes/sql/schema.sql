-- ============================================================
-- 中国城市天气监控数据库初始化脚本 (MySQL 8.0)
-- 执行方式: mysql -uroot < schema.sql
-- ============================================================

CREATE DATABASE IF NOT EXISTS weather_monitor
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 应用专用账号
CREATE USER IF NOT EXISTS 'weather'@'%'     IDENTIFIED BY 'Weather@2026';
CREATE USER IF NOT EXISTS 'weather'@'localhost' IDENTIFIED BY 'Weather@2026';
GRANT ALL PRIVILEGES ON weather_monitor.* TO 'weather'@'%';
GRANT ALL PRIVILEGES ON weather_monitor.* TO 'weather'@'localhost';
FLUSH PRIVILEGES;

USE weather_monitor;

-- ------------------------------------------------------------
-- 城市表：中国地级及以上行政区（含港澳台）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS city (
    id         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name       VARCHAR(64)  NOT NULL COMMENT '城市名称',
    province   VARCHAR(64)  NOT NULL COMMENT '省/自治区/直辖市',
    latitude   DECIMAL(8,4) NOT NULL COMMENT '纬度',
    longitude  DECIMAL(9,4) NOT NULL COMMENT '经度',
    enabled    TINYINT(1)   NOT NULL DEFAULT 1 COMMENT '是否启用监控',
    created_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_city_name_province (name, province)
) ENGINE = InnoDB COMMENT = '监控城市表';

-- ------------------------------------------------------------
-- 每日天气数据表（按 城市+日期 唯一，重复抓取自动覆盖更新）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS weather_daily (
    id                    BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    city_id               BIGINT        NOT NULL COMMENT '城市ID',
    weather_date          DATE          NOT NULL COMMENT '天气日期',
    weather_code          SMALLINT      NULL COMMENT 'WMO天气代码',
    condition_text        VARCHAR(64)   NULL COMMENT '天气现象(中文)',
    temp_max              DECIMAL(6,2)  NULL COMMENT '最高气温(°C)',
    temp_min              DECIMAL(6,2)  NULL COMMENT '最低气温(°C)',
    temp_mean             DECIMAL(6,2)  NULL COMMENT '平均气温(°C)',
    humidity_avg          DECIMAL(6,2)  NULL COMMENT '平均相对湿度(%)',
    pressure_avg          DECIMAL(9,2)  NULL COMMENT '平均气压(hPa)',
    cloud_cover_avg       DECIMAL(6,2)  NULL COMMENT '平均云量(%)',
    precipitation         DECIMAL(7,2)  NULL COMMENT '降水量(mm)',
    rain                  DECIMAL(7,2)  NULL COMMENT '降雨量(mm)',
    snowfall              DECIMAL(7,2)  NULL COMMENT '降雪量(cm)',
    precipitation_hours   DECIMAL(6,2)  NULL COMMENT '降水时长(h)',
    wind_speed_max        DECIMAL(7,2)  NULL COMMENT '最大风速(km/h)',
    wind_gusts_max        DECIMAL(7,2)  NULL COMMENT '最大阵风(km/h)',
    wind_direction_dominant SMALLINT    NULL COMMENT '主导风向(°)',
    uv_index_max          DECIMAL(5,2)  NULL COMMENT '最大紫外线指数',
    sunrise               TIME          NULL COMMENT '日出时间',
    sunset                TIME          NULL COMMENT '日落时间',
    fetched_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '首次抓取时间',
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_city_date (city_id, weather_date),
    KEY idx_weather_date (weather_date),
    CONSTRAINT fk_weather_city FOREIGN KEY (city_id) REFERENCES city (id)
) ENGINE = InnoDB COMMENT = '城市每日天气数据表';

-- ------------------------------------------------------------
-- 抓取任务日志表
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS fetch_log (
    id             BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
    trigger_type   VARCHAR(16) NOT NULL COMMENT '触发方式: STARTUP/SCHEDULED/MANUAL',
    total_cities   INT         NULL COMMENT '计划抓取城市数',
    success_cities INT         NULL COMMENT '成功城市数',
    failed_cities  INT         NULL COMMENT '失败城市数',
    status         VARCHAR(16) NOT NULL COMMENT 'SUCCESS/PARTIAL/FAILED',
    message        VARCHAR(512) NULL COMMENT '说明信息',
    started_at     DATETIME    NULL COMMENT '开始时间',
    finished_at    DATETIME    NULL COMMENT '结束时间',
    PRIMARY KEY (id),
    KEY idx_started_at (started_at)
) ENGINE = InnoDB COMMENT = '天气抓取任务日志表';
