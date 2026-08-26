package top.yzljc.atribot.function.command.weather;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record WeatherReport(
        Instant generatedAt,
        int windowMinutes,
        WeatherType weather,
        List<Phenomenon> phenomena,
        int messageCount,
        int activeUsers,
        int imageCount,
        int gameParticipants,
        int activityIndex,
        int imageIndex,
        int nightIndex,
        int interactionIndex,
        int baselineDays
) {

    public WeatherReport {
        phenomena = phenomena == null ? List.of() : List.copyOf(phenomena);
    }

    public Map<String, Object> toPayload() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("generatedAt", generatedAt.toString());
        payload.put("windowMinutes", windowMinutes);
        payload.put("weather", weather.name());
        payload.put("phenomena", phenomena.stream().map(Enum::name).toList());
        payload.put("messageCount", messageCount);
        payload.put("activeUsers", activeUsers);
        payload.put("imageCount", imageCount);
        payload.put("gameParticipants", gameParticipants);
        payload.put("activityIndex", activityIndex);
        payload.put("imageIndex", imageIndex);
        payload.put("nightIndex", nightIndex);
        payload.put("interactionIndex", interactionIndex);
        payload.put("baselineDays", baselineDays);
        return payload;
    }

    public enum WeatherType {
        WINDLESS_NIGHT,
        SUNNY,
        CLOUDY,
        THUNDERSTORM
    }

    public enum Phenomenon {
        RAINBOW_CLOUD,
        METEOR_SHOWER,
        AURORA,
        PRESSURE_WAVE
    }
}
