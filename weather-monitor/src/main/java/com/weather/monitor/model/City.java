package com.weather.monitor.model;

import java.math.BigDecimal;

/**
 * 监控城市
 */
public record City(Long id, String name, String province, BigDecimal latitude, BigDecimal longitude) {
}
