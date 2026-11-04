package top.yzljc.atribot.function.admin.update;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.utils.AtomicFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.List;

/**
 * 更新通知持久化及群推送记录，消息与入群事件共用发送占位
 *
 * @Author YZ_Ljc_
 * @ClassName UpdateNoticeRecord
 * @Created_at 2026/06/24
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.admin.update
 */
public final class UpdateNoticeRecord {
    private static final Logger log = LoggerFactory.getLogger(UpdateNoticeRecord.class);
    private static final ObjectMapper MAPPER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final Path DATA_FILE = Path.of("data", "update.json");
    private static final Object LOCK = new Object();

    private static volatile UpdateData cache = loadFromFile();
    private static long revision;
    private static long joinSettingsRevision;
    private static final java.util.Map<String, Long> sending = new java.util.HashMap<>();

    public record JoinTrigger(long revision, long settingsRevision) {}

    /** 仅为当前尚未通知的群创建入群触发凭据 */
    public static JoinTrigger getJoinTrigger(String groupId) {
        synchronized (LOCK) {
            return cache.joinEventPush && groupId != null && shouldNotify(groupId)
                    ? new JoinTrigger(revision, joinSettingsRevision) : null;
        }
    }

    public static boolean isJoinEventPushEnabled() {
        return cache.joinEventPush;
    }

    public static boolean isEnabled() {
        return cache.enabled;
    }

    public static boolean isPassiveMessagePushEnabled() {
        return cache.passiveMessagePush;
    }

    /** 切换入群推送不重置通知内容、发布时间及已通知群记录 */
    public static void setJoinEventPush(boolean enabled) throws IOException {
        setSettings(null, enabled);
    }

    /** 开关独立保存，关闭后旧入群任务失效，已通知群记录保持不变 */
    public static void setSettings(Boolean enabled, Boolean joinEventPush) throws IOException {
        setSettings(enabled, joinEventPush, null);
    }

    public static void setSettings(Boolean enabled, Boolean joinEventPush, Boolean passiveMessagePush) throws IOException {
        synchronized (LOCK) {
            boolean nextEnabled = enabled == null ? cache.enabled : enabled;
            boolean nextJoin = joinEventPush == null ? cache.joinEventPush : joinEventPush;
            boolean nextPassive = passiveMessagePush == null ? cache.passiveMessagePush : passiveMessagePush;
            boolean joinChanged = cache.enabled != nextEnabled || cache.joinEventPush != nextJoin;
            if (!joinChanged && cache.passiveMessagePush == nextPassive) return;
            UpdateData next = cache.withJoinEventPush(nextJoin).withEnabled(nextEnabled).withPassiveMessagePush(nextPassive);
            saveToFile(next);
            cache = next;
            if (joinChanged) joinSettingsRevision++;
        }
    }

    /** 被动消息在取得发送占位时再次检查开关 */
    public static void deliverPassiveOnce(String groupId,
                                         java.util.function.BiFunction<UpdateNoticeContent, Long, String> send) {
        deliverOnce(groupId, null, true, send);
    }

    /** 消息和入群事件共用发送占位，网络请求期间不持有记录锁 */
    public static void deliverOnce(String groupId, JoinTrigger trigger,
                                   java.util.function.BiFunction<UpdateNoticeContent, Long, String> send) {
        deliverOnce(groupId, trigger, false, send);
    }

    private static void deliverOnce(String groupId, JoinTrigger trigger, boolean passiveMessage,
                                    java.util.function.BiFunction<UpdateNoticeContent, Long, String> send) {
        UpdateData notice;
        long version;
        synchronized (LOCK) {
            if (passiveMessage && !cache.passiveMessagePush) return;
            if (groupId == null || groupId.isBlank() || !shouldNotify(groupId)) return;
            if (trigger != null && (!cache.joinEventPush || trigger.revision != revision
                    || trigger.settingsRevision != joinSettingsRevision)) return;
            if (sending.containsKey(groupId)) return;
            notice = cache;
            version = revision;
            sending.put(groupId, version);
        }
        boolean delivered = false;
        try {
            String messageId = send.apply(new UpdateNoticeContent(notice.text, notice.format, notice.buttons,
                    notice.textLayout, notice.markdownLayout), notice.createdAt);
            delivered = messageId != null && !messageId.isBlank();
        } finally {
            synchronized (LOCK) {
                if (delivered && revision == version) {
                    LinkedHashSet<String> groups = new LinkedHashSet<>(cache.notifiedGroups);
                    groups.add(groupId);
                    UpdateData next = cache.withNotifiedGroups(groups);
                    // 已发送的消息不能撤销，落盘失败时仍保留内存记录，防止重复推送
                    cache = next;
                    try {
                        saveToFile(next);
                    } catch (IOException e) {
                        log.error("保存更新通知群记录失败，群ID {}", groupId, e);
                    }
                }
                sending.remove(groupId, version);
            }
        }
    }

    private UpdateNoticeRecord() {
    }

    public static boolean hasNotice() {
        return !cache.text.isBlank();
    }

    public static String getText() {
        return cache.text;
    }

    public static UpdateNoticeContent getContent() {
        UpdateData current = cache;
        return new UpdateNoticeContent(current.text, current.format, current.buttons, current.textLayout, current.markdownLayout);
    }

    /** 保存标题与落款，不改变通知版本、发布时间及已通知群记录 */
    public static void setLayouts(UpdateNoticeContent.MessageLayout textLayout,
                                  UpdateNoticeContent.MessageLayout markdownLayout) throws IOException {
        Objects.requireNonNull(textLayout);
        Objects.requireNonNull(markdownLayout);
        synchronized (LOCK) {
            UpdateData next = cache.withLayouts(textLayout, markdownLayout);
            saveToFile(next);
            cache = next;
        }
    }

    /** 与原 setText 相同，设置新通知时覆盖旧通知记录 */
    public static void setContent(UpdateNoticeContent content) throws IOException {
        if (content.text().isBlank()) throw new IllegalArgumentException("更新推送内容不能为空");
        synchronized (LOCK) {
            if (cache.text.equals(content.text()) && cache.format.equals(content.format())) return;
            long now = Instant.now().toEpochMilli();
            UpdateData next = new UpdateData(content.text(), now, now, Set.of(), content.format(), cache.buttons, cache.textLayout, cache.markdownLayout, cache.joinEventPush, cache.enabled, cache.passiveMessagePush);
            saveToFile(next);
            cache = next;
            revision++;
        }
    }

    public static long getCreatedAt() {
        return cache.createdAt;
    }

    /** 保存默认键盘，不改变通知版本及已通知群记录 */
    public static void setButtons(List<List<UpdateNoticeContent.NoticeButton>> buttons) throws IOException {
        var validated = new UpdateNoticeContent("", "MARKDOWN", buttons).buttons();
        synchronized (LOCK) {
            UpdateData next = cache.withButtons(validated);
            saveToFile(next);
            cache = next;
        }
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
        return cache.enabled && hasNotice() && !isNotified(groupId);
    }

    public static void setText(String text) {
        String normalizedText = normalizeText(text);
        long now = Instant.now().toEpochMilli();
        synchronized (LOCK) {
            UpdateData next = new UpdateData(normalizedText, now, now, Set.of(), "TEXT", cache.buttons, cache.textLayout, cache.markdownLayout, cache.joinEventPush, cache.enabled, cache.passiveMessagePush);
            saveAndSwap(next);
            if (cache == next) revision++;
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
            UpdateData next = UpdateData.empty().withJoinEventPush(cache.joinEventPush).withEnabled(cache.enabled).withPassiveMessagePush(cache.passiveMessagePush).withLayouts(cache.textLayout, cache.markdownLayout).withButtons(cache.buttons);
            saveAndSwap(next);
            if (cache == next) revision++;
        }
    }

    public static void reload() {
        synchronized (LOCK) {
            cache = loadFromFile();
            revision++;
            joinSettingsRevision++;
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
        } catch (IOException | IllegalArgumentException e) {
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
        AtomicFiles.write(DATA_FILE, MAPPER.writeValueAsBytes(RawUpdateData.from(data)));
    }

    private record UpdateData(String text, long createdAt, long updatedAt, Set<String> notifiedGroups,
                              String format, List<List<UpdateNoticeContent.NoticeButton>> buttons,
                              UpdateNoticeContent.MessageLayout textLayout, UpdateNoticeContent.MessageLayout markdownLayout,
                              boolean joinEventPush, boolean enabled, boolean passiveMessagePush) {
        private static UpdateData empty() {
            return new UpdateData("", 0, 0, Set.of(), "TEXT", List.of(), UpdateNoticeContent.MessageLayout.defaults(), UpdateNoticeContent.MessageLayout.defaults(), false, true, false);
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
                var content = new UpdateNoticeContent("", "TEXT", raw.buttons, raw.textLayout, raw.markdownLayout);
                return empty().withJoinEventPush(raw.joinEventPush).withEnabled(raw.enabled).withPassiveMessagePush(raw.passiveMessagePush).withLayouts(content.textLayout(), content.markdownLayout()).withButtons(content.buttons());
            }
            if (createdAt == 0) {
                createdAt = Instant.now().toEpochMilli();
            }
            if (updatedAt == 0) {
                updatedAt = createdAt;
            }
            var content = new UpdateNoticeContent(text, raw.format, raw.buttons, raw.textLayout, raw.markdownLayout);
            return new UpdateData(content.text(), createdAt, updatedAt, notifiedGroups, content.format(), content.buttons(), content.textLayout(), content.markdownLayout(), raw.joinEventPush, raw.enabled, raw.passiveMessagePush);
        }

        private UpdateData withNotifiedGroups(Set<String> groups) {
            return new UpdateData(text, createdAt, updatedAt, copyGroups(groups), format, buttons, textLayout, markdownLayout, joinEventPush, enabled, passiveMessagePush);
        }

        private UpdateData withJoinEventPush(boolean join) {
            return new UpdateData(text, createdAt, updatedAt, notifiedGroups, format, buttons, textLayout, markdownLayout, join, enabled, passiveMessagePush);
        }

        private UpdateData withPassiveMessagePush(boolean value) {
            return new UpdateData(text, createdAt, updatedAt, notifiedGroups, format, buttons, textLayout, markdownLayout, joinEventPush, enabled, value);
        }

        private UpdateData withEnabled(boolean value) {
            return new UpdateData(text, createdAt, updatedAt, notifiedGroups, format, buttons, textLayout, markdownLayout, joinEventPush, value, passiveMessagePush);
        }

        private UpdateData withLayouts(UpdateNoticeContent.MessageLayout plain, UpdateNoticeContent.MessageLayout markdown) {
            return new UpdateData(text, createdAt, updatedAt, notifiedGroups, format, buttons, plain, markdown, joinEventPush, enabled, passiveMessagePush);
        }

        private UpdateData withButtons(List<List<UpdateNoticeContent.NoticeButton>> value) {
            return new UpdateData(text, createdAt, updatedAt, notifiedGroups, format, value, textLayout, markdownLayout, joinEventPush, enabled, passiveMessagePush);
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
        public String format = "TEXT";
        public List<List<UpdateNoticeContent.NoticeButton>> buttons = List.of();
        public UpdateNoticeContent.MessageLayout textLayout;
        public UpdateNoticeContent.MessageLayout markdownLayout;
        public boolean joinEventPush;
        public boolean enabled = true;
        public boolean passiveMessagePush;

        private static RawUpdateData from(UpdateData data) {
            RawUpdateData raw = new RawUpdateData();
            raw.text = data.text;
            raw.createdAt = data.createdAt;
            raw.updatedAt = data.updatedAt;
            raw.notifiedGroups = data.notifiedGroups;
            raw.format = data.format;
            raw.buttons = data.buttons;
            raw.textLayout = data.textLayout;
            raw.markdownLayout = data.markdownLayout;
            raw.joinEventPush = data.joinEventPush;
            raw.enabled = data.enabled;
            raw.passiveMessagePush = data.passiveMessagePush;
            return raw;
        }
    }
}
