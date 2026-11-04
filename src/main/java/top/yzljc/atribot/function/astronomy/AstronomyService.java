package top.yzljc.atribot.function.astronomy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumMap;

/**
 * @Author YZ_Ljc_
 * @ClassName AstronomyService
 * @Created_at 2026/10/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.astronomy
 */
public final class AstronomyService {
    private static final Logger LOG = LoggerFactory.getLogger(AstronomyService.class);
    private final Path directory;
    private final SunriseSunsetClient client;
    private final Clock clock;
    private final EnumMap<AstronomyRegion, Slot> slots = new EnumMap<>(AstronomyRegion.class);

    private static final class Slot { AstronomyData data; long retryAt; }

    public AstronomyService() { this(Path.of("data", "astronomy"), new SunriseSunsetClient(), Clock.system(AstronomyData.ZONE)); }

    AstronomyService(Path directory, SunriseSunsetClient client, Clock clock) {
        this.directory = directory;
        this.client = client;
        this.clock = clock;
        for (var region : AstronomyRegion.values()) slots.put(region, new Slot());
    }

    /**
     * 同一区域的并发查询合并为一次读取；历史记录落盘，内存最多保留八条
     *
     * @param region 固定区域
     * @return 上海时区当天的数据，失败时不会返回其他日期的结果
     * @throws IOException 上游或数据解析失败，失败后一分钟内不重复请求
     * @throws InterruptedException 请求被中断
     */
    public AstronomyData today(AstronomyRegion region) throws IOException, InterruptedException {
        var slot = slots.get(region);
        synchronized (slot) {
            LocalDate date = LocalDate.now(clock.withZone(AstronomyData.ZONE));
            if (slot.data != null && slot.data.date().equals(date)) return slot.data;
            if (clock.millis() < slot.retryAt) throw new IOException("天象接口等待重试");
            Path file = directory.resolve(date.toString()).resolve(region.name() + ".json");
            if (Files.isRegularFile(file)) {
                try {
                    if (Files.size(file) > 32768) throw new IOException("天象缓存过大");
                    slot.data = SunriseSunsetClient.JSON.readValue(Files.readString(file), SunriseSunsetResponse.class).normalize(date, region);
                    return slot.data;
                } catch (IOException | RuntimeException invalid) {
                    LOG.warn("天象缓存读取失败，区域: {}，日期: {}", region.getDisplayName(), date);
                }
            }
            try {
                var response = client.query(region, date);
                slot.data = response.normalize(date, region);
                save(file, response);
                return slot.data;
            } catch (IOException | RuntimeException failure) {
                slot.retryAt = clock.millis() + 60_000;
                throw new IOException("天象数据获取失败，区域: " + region.getDisplayName(), failure);
            }
        }
    }

    private void save(Path file, SunriseSunsetResponse response) {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), "astro-", ".tmp");
            Files.writeString(temporary, SunriseSunsetClient.JSON.writeValueAsString(response));
            try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (java.nio.file.AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException failure) {
            LOG.warn("天象缓存保存失败: {}", file, failure);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
}
