package top.yzljc.atribot.chat.official.moderation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Properties;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.AtomicMoveNotSupportedException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
* @Author AndyOctopus
* @ClassName GroupModerationStore
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.chat.official.moderation
*/
@Slf4j
public final class GroupModerationStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, GroupModerationSettings> CACHE = new LinkedHashMap<>();
    private static boolean loaded = false;

    public static synchronized GroupModerationSettings get(String groupOpenId) {
        ensureLoaded();
        GroupModerationSettings settings = CACHE.get(groupOpenId);
        return settings == null ? new GroupModerationSettings() : settings;
    }

    public static synchronized void save(String groupOpenId, GroupModerationSettings settings) {
        update(groupOpenId, ignored -> settings);
    }

    public static synchronized GroupModerationSettings snapshot(String groupOpenId) {
        ensureLoaded();
        return copy(CACHE.get(groupOpenId));
    }

    public static synchronized GroupModerationSettings update(String groupOpenId, UnaryOperator<GroupModerationSettings> edit) {
        ensureLoaded();
        GroupModerationSettings next = copy(java.util.Objects.requireNonNull(edit.apply(copy(CACHE.get(groupOpenId)))));
        Map<String, GroupModerationSettings> candidate = new LinkedHashMap<>(CACHE);
        candidate.put(groupOpenId, next);
        persist(candidate);
        CACHE.put(groupOpenId, next);
        return copy(next);
    }

    private static GroupModerationSettings copy(GroupModerationSettings settings) {
        return settings == null ? null : MAPPER.convertValue(settings, GroupModerationSettings.class);
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        File file = new File(Properties.GROUP_MODERATION_CONFIG);
        if (!file.exists()) {
            loaded = true;
            return;
        }
        try {
            Map<String, GroupModerationSettings> data = MAPPER.readValue(file,
                    MAPPER.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, GroupModerationSettings.class));
            if (data == null) throw new IOException("Expected a moderation settings object");
            CACHE.putAll(data);
            loaded = true;
            log.info("已加载群管系统配置，共 {} 个群", CACHE.size());
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to load moderation settings", e);
        }
    }

    private static void persist(Map<String, GroupModerationSettings> settings) {
        persist(Path.of(Properties.GROUP_MODERATION_CONFIG).toAbsolutePath(), settings);
    }

    static void persist(Path file, Map<String, GroupModerationSettings> settings) {
        Path temporary = null;
        try {
            Files.createDirectories(file.getParent());
            temporary = Files.createTempFile(file.getParent(), ".moderation-", ".tmp");
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), settings);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Unable to save moderation settings", e);
        } finally {
            if (temporary != null) try { Files.deleteIfExists(temporary); } catch (IOException ignored) { }
        }
    }
}
