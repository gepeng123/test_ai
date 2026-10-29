package com.weather.monitor.service;

import java.util.Map;

/**
 * WMO 天气代码 -> 中文描述
 */
public final class WmoCode {

    private static final Map<Integer, String> TEXTS = Map.ofEntries(
            Map.entry(0, "晴"),
            Map.entry(1, "基本晴"),
            Map.entry(2, "局部多云"),
            Map.entry(3, "阴"),
            Map.entry(45, "雾"),
            Map.entry(48, "雾凇"),
            Map.entry(51, "轻毛毛雨"),
            Map.entry(53, "毛毛雨"),
            Map.entry(55, "浓毛毛雨"),
            Map.entry(56, "冻毛毛雨"),
            Map.entry(57, "浓冻毛毛雨"),
            Map.entry(61, "小雨"),
            Map.entry(63, "中雨"),
            Map.entry(65, "大雨"),
            Map.entry(66, "冻雨"),
            Map.entry(67, "强冻雨"),
            Map.entry(71, "小雪"),
            Map.entry(73, "中雪"),
            Map.entry(75, "大雪"),
            Map.entry(77, "雪粒"),
            Map.entry(80, "小阵雨"),
            Map.entry(81, "阵雨"),
            Map.entry(82, "强阵雨"),
            Map.entry(85, "小阵雪"),
            Map.entry(86, "阵雪"),
            Map.entry(95, "雷暴"),
            Map.entry(96, "雷暴伴冰雹"),
            Map.entry(99, "强雷暴伴冰雹"));

    private WmoCode() {
    }

    public static String describe(Integer code) {
        if (code == null) {
            return null;
        }
        return TEXTS.getOrDefault(code, "未知(" + code + ")");
    }
}
