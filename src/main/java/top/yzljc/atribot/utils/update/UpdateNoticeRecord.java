package top.yzljc.atribot.utils.update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class UpdateNoticeRecord {
    private static final Logger log = LoggerFactory.getLogger(UpdateNoticeRecord.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Path DATA_FILE = Path.of("data", "update.json");
    private static final Object LOCK = new Object();

    private static volatile UpdateData cache = loadFromFile();

    private UpdateNoticeRecord() {
    }

    public static boolean hasNotice() {
        return !cache.text.isBlank();
    }

    public static String getText() {
        return cache.text;
    }

    public static long getCreatedAt() {
        return cache.createdAt;
    }

    public static long getUpdatedAt() {
        return cache.updatedAt;
    }

    public static Set<String> getNotifiedGroups() {
        return cache.notifiedGroups;
    }

    public static boolean isNotified(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return false;
        }
        return cache.notifiedGroups.contains(groupId.trim());
    }

    public static boolean shouldNotify(String groupId) {
        return hasNotice() && !isNotified(groupId);
    }

    public static void setText(String text) {
        String normalizedText = normalizeText(text);
        long now = Instant.now().toEpochMilli();
        synchronized (LOCK) {
            UpdateData next = new UpdateData(normalizedText, now, now, Set.of());
            saveAndSwap(next);
        }
    }

    public static boolean markNotified(String groupId) {
        if (groupId == null || groupId.isBlank()) {
            return false;
        }

        synchronized (LOCK) {
            UpdateData current = cache;
            if (current.text.isBlank()) {
                return false;
            }

            String normalizedGroupId = groupId.trim();
            if (current.notifiedGroups.contains(normalizedGroupId)) {
                return false;
            }

            LinkedHashSet<String> groups = new LinkedHashSet<>(current.notifiedGroups);
            groups.add(normalizedGroupId);
            saveAndSwap(current.withNotifiedGroups(groups));
            return true;
        }
    }

    public static int markNotified(Collection<String> groupIds) {
        if (groupIds == null || groupIds.isEmpty()) {
            return 0;
        }

        synchronized (LOCK) {
            UpdateData current = cache;
            if (current.text.isBlank()) {
                return 0;
            }

            LinkedHashSet<String> groups = new LinkedHashSet<>(current.notifiedGroups);
            int added = 0;
            for (String groupId : groupIds) {
                if (groupId == null || groupId.isBlank()) {
                    continue;
                }
                if (groups.add(groupId.trim())) {
                    added++;
                }
            }

            if (added > 0) {
                saveAndSwap(current.withNotifiedGroups(groups));
            }
            return added;
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            saveAndSwap(UpdateData.empty());
        }
    }

    public static void reload() {
        synchronized (LOCK) {
            cache = loadFromFile();
        }
    }

    private static String normalizeText(String text) {
        return Objects.requireNonNullElse(text, "").trim();
    }

    private static UpdateData loadFromFile() {
        if (!Files.exists(DATA_FILE)) {
            return UpdateData.empty();
        }

        try {
            RawUpdateData raw = MAPPER.readValue(DATA_FILE.toFile(), new TypeReference<>() {
            });
            return UpdateData.from(raw);
        } catch (IOException e) {
            log.warn("读取更新通知记录失败，将使用空记录: {}", e.getMessage());
            return UpdateData.empty();
        }
    }

    private static void saveAndSwap(UpdateData data) {
        try {
            saveToFile(data);
            cache = data;
        } catch (IOException e) {
            log.warn("保存更新通知记录失败: {}", e.getMessage());
        }
    }

    private static void saveToFile(UpdateData data) throws IOException {
        Path parent = DATA_FILE.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }

        Path tempFile = DATA_FILE.resolveSibling(DATA_FILE.getFileName() + ".tmp");
        MAPPER.writeValue(tempFile.toFile(), RawUpdateData.from(data));
        try {
            Files.move(tempFile, DATA_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tempFile, DATA_FILE, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private record UpdateData(String text, long createdAt, long updatedAt, Set<String> notifiedGroups) {
        private static UpdateData empty() {
            return new UpdateData("", 0, 0, Set.of());
        }

        private static UpdateData from(RawUpdateData raw) {
            if (raw == null) {
                return empty();
            }

            String text = normalizeText(raw.text);
            long createdAt = Math.max(raw.createdAt, 0);
            long updatedAt = Math.max(raw.updatedAt, createdAt);
            Set<String> notifiedGroups = copyGroups(raw.notifiedGroups);

            if (text.isBlank()) {
                return empty();
            }
            if (createdAt == 0) {
                createdAt = Instant.now().toEpochMilli();
            }
            if (updatedAt == 0) {
                updatedAt = createdAt;
            }
            return new UpdateData(text, createdAt, updatedAt, notifiedGroups);
        }

        private UpdateData withNotifiedGroups(Set<String> groups) {
            return new UpdateData(text, createdAt, updatedAt, copyGroups(groups));
        }

        private static Set<String> copyGroups(Collection<String> groups) {
            if (groups == null || groups.isEmpty()) {
                return Set.of();
            }

            LinkedHashSet<String> copy = new LinkedHashSet<>();
            for (String group : groups) {
                if (group != null && !group.isBlank()) {
                    copy.add(group.trim());
                }
            }
            return Collections.unmodifiableSet(copy);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static final class RawUpdateData {
        public String text = "";
        public long createdAt;
        public long updatedAt;
        public Set<String> notifiedGroups = Set.of();

        private static RawUpdateData from(UpdateData data) {
            RawUpdateData raw = new RawUpdateData();
            raw.text = data.text;
            raw.createdAt = data.createdAt;
            raw.updatedAt = data.updatedAt;
            raw.notifiedGroups = data.notifiedGroups;
            return raw;
        }
    }
}
