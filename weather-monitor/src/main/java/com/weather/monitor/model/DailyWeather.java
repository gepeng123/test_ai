package com.weather.monitor.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 城市单日天气数据
 */
public record DailyWeather(
        long cityId,
        LocalDate weatherDate,
        Integer weatherCode,
        String conditionText,
        BigDecimal tempMax,
        BigDecimal tempMin,
        BigDecimal tempMean,
        BigDecimal humidityAvg,
        BigDecimal pressureAvg,
        BigDecimal cloudCoverAvg,
        BigDecimal precipitation,
        BigDecimal rain,
        BigDecimal snowfall,
        BigDecimal precipitationHours,
        BigDecimal windSpeedMax,
        BigDecimal windGustsMax,
        Integer windDirectionDominant,
        BigDecimal uvIndexMax,
        LocalTime sunrise,
        LocalTime sunset) {
}
