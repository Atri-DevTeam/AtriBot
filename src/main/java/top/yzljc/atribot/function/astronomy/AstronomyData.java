package top.yzljc.atribot.function.astronomy;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName AstronomyData
 * @Created_at 2026/10/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.astronomy
 */
public record AstronomyData(LocalDate date, OffsetDateTime sunrise, OffsetDateTime sunset,
                            OffsetDateTime solarNoon, Long dayLength, OffsetDateTime moonrise,
                            OffsetDateTime moonset, String moonPhase, Double moonIllumination,
                            TimeRange morningGoldenHour, TimeRange eveningGoldenHour,
                            TimeRange morningBlueHour, TimeRange eveningBlueHour) {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final Map<String, String> PHASES = Map.of(
            "New Moon", "新月", "Waxing Crescent", "娥眉月", "First Quarter", "上弦月",
            "Waxing Gibbous", "盈凸月", "Full Moon", "满月", "Waning Gibbous", "亏凸月",
            "Last Quarter", "下弦月", "Waning Crescent", "残月");

    public record TimeRange(OffsetDateTime begin, OffsetDateTime end) {
        public String display() { return begin == null || end == null ? "暂无" : time(begin) + "–" + time(end); }
        Map<String, Object> payload() {
            var values = new LinkedHashMap<String, Object>();
            values.put("begin", iso(begin));
            values.put("end", iso(end));
            return values;
        }
    }

    public String phaseName() { return moonPhase == null ? "暂无" : PHASES.getOrDefault(moonPhase, moonPhase); }
    public static String time(OffsetDateTime value) { return value == null ? "暂无" : TIME.format(value.atZoneSameInstant(ZONE)); }
    private static String iso(OffsetDateTime value) { return value == null ? null : value.toString(); }
    public String daylight() { return dayLength == null ? "暂无" : dayLength / 3600 + "小时" + dayLength % 3600 / 60 + "分"; }

    /**
     * @param region 查询使用的固定区域
     * @return 交给 UGC 渲染的标准化数据，时间使用带偏移的 ISO 8601 格式
     */
    public Map<String, Object> renderRequest(AstronomyRegion region) {
        var values = new LinkedHashMap<String, Object>();
        values.put("region", region.name());
        values.put("date", date.toString());
        values.put("timezone", ZONE.getId());
        values.put("sunrise", iso(sunrise));
        values.put("sunset", iso(sunset));
        values.put("solarNoon", iso(solarNoon));
        values.put("dayLength", dayLength);
        values.put("moonrise", iso(moonrise));
        values.put("moonset", iso(moonset));
        values.put("moonPhase", moonPhase);
        values.put("moonIllumination", moonIllumination);
        values.put("morningGoldenHour", morningGoldenHour.payload());
        values.put("eveningGoldenHour", eveningGoldenHour.payload());
        values.put("morningBlueHour", morningBlueHour.payload());
        values.put("eveningBlueHour", eveningBlueHour.payload());
        return values;
    }
}
