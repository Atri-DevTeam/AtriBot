package top.yzljc.atribot.function.astronomy;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * @Author YZ_Ljc_
 * @ClassName SunriseSunsetResponse
 * @Created_at 2026/10/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.astronomy
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SunriseSunsetResponse(String date, String tzid, Double lat, Double lng,
        String sunrise, String sunset, @JsonProperty("solar_noon") String solarNoon,
        @JsonProperty("day_length") Long dayLength, String moonrise, String moonset,
        @JsonProperty("moon_phase") String moonPhase, @JsonProperty("moon_illumination") Double moonIllumination,
        @JsonProperty("golden_hour") Hours goldenHour, @JsonProperty("blue_hour") Hours blueHour) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Hours(Range morning, Range evening) {}
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Range(String begin, String end) {}

    /**
     * @param expectedDate 本次查询日期
     * @param region 本次查询区域，校验缓存是否仍对应当前代表坐标
     * @return 标准化天象数据，不存在的事件保留为空
     * @throws IllegalArgumentException 上游日期、时区、坐标或数值无效
     */
    public AstronomyData normalize(LocalDate expectedDate, AstronomyRegion region) {
        if (!expectedDate.toString().equals(date) || !AstronomyData.ZONE.getId().equals(tzid)
                || lat == null || lng == null || !Double.isFinite(lat) || !Double.isFinite(lng)
                || Math.abs(lat - region.getLat()) > 0.001 || Math.abs(lng - region.getLng()) > 0.001
                || (dayLength != null && (dayLength < 0 || dayLength > 86400))
                || (moonIllumination != null && (!Double.isFinite(moonIllumination) || moonIllumination < 0 || moonIllumination > 100))
                || (moonPhase != null && (moonPhase.length() > 48 || moonPhase.codePoints().anyMatch(Character::isISOControl))))
            throw new IllegalArgumentException("天象数据与请求不符");
        return new AstronomyData(expectedDate, event(sunrise), event(sunset), event(solarNoon), dayLength,
                event(moonrise), event(moonset), moonPhase, moonIllumination,
                range(goldenHour == null ? null : goldenHour.morning()), range(goldenHour == null ? null : goldenHour.evening()),
                range(blueHour == null ? null : blueHour.morning()), range(blueHour == null ? null : blueHour.evening()));
    }

    private OffsetDateTime event(String value) {
        if (value == null) return null;
        var parsed = OffsetDateTime.parse(value).atZoneSameInstant(AstronomyData.ZONE).toOffsetDateTime();
        if (!parsed.toLocalDate().equals(LocalDate.parse(date))) throw new IllegalArgumentException("天象事件不在查询日期内");
        return parsed;
    }

    private AstronomyData.TimeRange range(Range range) {
        var begin = range == null ? null : event(range.begin());
        var end = range == null ? null : event(range.end());
        if (begin != null && end != null && begin.isAfter(end)) throw new IllegalArgumentException("天象时段顺序无效");
        return new AstronomyData.TimeRange(begin, end);
    }
}
