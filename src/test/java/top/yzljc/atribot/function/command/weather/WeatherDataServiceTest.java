package top.yzljc.atribot.function.command.weather;

import org.junit.jupiter.api.Test;
import top.yzljc.atribot.function.command.weather.WeatherDataService.MessageSample;
import top.yzljc.atribot.function.command.weather.WeatherReport.Phenomenon;
import top.yzljc.atribot.function.command.weather.WeatherReport.WeatherType;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WeatherDataServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-25T22:00:00Z");

    @Test
    void selectsWindlessNightWhenActivityDropsBelowHistory() {
        List<MessageSample> samples = new ArrayList<>();
        for (int day = 1; day <= 3; day++) {
            Instant start = NOW.minus(Duration.ofDays(day + 1L));
            for (int i = 0; i < 20; i++) {
                samples.add(sample(start.plus(Duration.ofMinutes(i * 40L)), "baseline-" + (i % 8), 0, false));
            }
        }
        samples.add(sample(NOW.minus(Duration.ofHours(10)), "a", 0, false));
        samples.add(sample(NOW.minus(Duration.ofHours(5)), "b", 0, false));

        WeatherReport report = WeatherDataService.analyze(samples, NOW, NOW.minus(Duration.ofDays(5)));

        assertEquals(WeatherType.WINDLESS_NIGHT, report.weather());
        assertTrue(report.baselineDays() >= 3);
    }

    @Test
    void selectsThunderstormForShortMessageBurst() {
        List<MessageSample> samples = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            samples.add(sample(NOW.minus(Duration.ofMinutes(30)).plusSeconds(i * 30L), "u" + (i % 3), 0, false));
        }

        WeatherReport report = WeatherDataService.analyze(samples, NOW, NOW.minus(Duration.ofHours(2)));

        assertEquals(WeatherType.THUNDERSTORM, report.weather());
        assertEquals(120, report.windowMinutes());
    }

    @Test
    void selectsCloudyForManyStableParticipants() {
        List<MessageSample> samples = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            samples.add(sample(NOW.minus(Duration.ofHours(20 - i * 2L)), "u" + i, 0, false));
        }

        WeatherReport report = WeatherDataService.analyze(samples, NOW, NOW.minus(Duration.ofHours(24)));

        assertEquals(WeatherType.CLOUDY, report.weather());
        assertEquals(10, report.activeUsers());
    }

    @Test
    void limitsSpecialPhenomenaToTwoStrongestSignals() {
        List<MessageSample> samples = new ArrayList<>();
        Instant start = Instant.parse("2026-08-25T16:10:00Z");
        for (int i = 0; i < 12; i++) {
            samples.add(sample(start.plusSeconds(i * 60L), "u" + (i % 4), i < 6 ? 1 : 0, i < 3));
        }

        WeatherReport report = WeatherDataService.analyze(samples, NOW, NOW.minus(Duration.ofHours(24)));

        assertEquals(2, report.phenomena().size());
        assertTrue(report.phenomena().contains(Phenomenon.RAINBOW_CLOUD));
        assertTrue(report.phenomena().contains(Phenomenon.AURORA));
    }

    private static MessageSample sample(Instant time, String userId, int images, boolean gameCommand) {
        return new MessageSample(time, userId, images, gameCommand);
    }
}
