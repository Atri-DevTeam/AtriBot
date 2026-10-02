package top.yzljc.atribot.chat.napcat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 只使用 JSON 保存群聊记录和经历摘要。损坏的文件不会被空记录覆盖。 */
final class GroupChatMemory {
    static final ObjectMapper JSON = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    private final Path directory;
    private final Map<String, Conversation> conversations = new ConcurrentHashMap<>();

    GroupChatMemory(Path directory) {
        this.directory = directory;
    }

    Conversation get(String groupId) {
        return conversations.computeIfAbsent(normalizeGroupId(groupId), this::load);
    }

    void save(Conversation conversation) throws IOException {
        writeJson(fileFor(conversation.groupId), new Snapshot(1, conversation.groupId,
                conversation.summary, List.copyOf(conversation.messages)));
    }

    void clear(String groupId) throws IOException {
        Conversation conversation = get(groupId);
        synchronized (conversation) {
            // 先写盘再清理内存；清理失败时不报告成功，也不丢弃原记忆。
            writeJson(fileFor(conversation.groupId), new Snapshot(1, conversation.groupId, "", List.of()));
            conversation.summary = "";
            conversation.messages.clear();
        }
    }

    void clearAll() throws IOException {
        if (Files.isDirectory(directory)) {
            List<String> groups = new ArrayList<>();
            try (var files = Files.list(directory)) {
                for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json")
                        && !path.getFileName().toString().startsWith(".")).toList()) {
                    groups.add(readSnapshot(file).groupId());
                }
            }
            for (String group : groups) clear(group);
        }
        for (String group : List.copyOf(conversations.keySet())) clear(group);
    }

    Path fileFor(String groupId) {
        String id = normalizeGroupId(groupId);
        String name = id.matches("[0-9]{1,32}") ? id : hash(id);
        return directory.resolve(name + ".json");
    }

    private Conversation load(String groupId) {
        Path file = fileFor(groupId);
        Conversation conversation = new Conversation(groupId);
        if (Files.notExists(file)) return conversation;
        try {
            Snapshot snapshot = readSnapshot(file);
            if (!groupId.equals(snapshot.groupId())) throw new IOException("群聊记忆的群 ID 不匹配: " + file);
            conversation.summary = snapshot.summary();
            conversation.messages.addAll(snapshot.messages());
            return conversation;
        } catch (IOException e) {
            throw new UncheckedIOException("读取群聊记忆失败: " + file, e);
        }
    }

    private Snapshot readSnapshot(Path file) throws IOException {
        Snapshot snapshot = JSON.readValue(file.toFile(), Snapshot.class);
        if (snapshot == null || snapshot.version() != 1 || snapshot.groupId() == null
                || snapshot.groupId().isBlank() || snapshot.summary() == null || snapshot.messages() == null) {
            throw new IOException("群聊记忆 JSON 格式不正确: " + file);
        }
        for (Entry entry : snapshot.messages()) {
            if (entry == null || !("user".equals(entry.role()) || "assistant".equals(entry.role()))
                    || entry.content() == null || entry.userId() == null || entry.userName() == null
                    || entry.messageId() == null || entry.replyToUserId() == null
                    || entry.replyToMessageId() == null || entry.timestamp() == null) {
                throw new IOException("群聊记忆包含无效消息: " + file);
            }
        }
        return snapshot;
    }

    static void writeJson(Path file, Object value) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());
        Path temporary = Files.createTempFile(file.toAbsolutePath().getParent(), ".atri-chat-", ".json");
        try {
            JSON.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), value);
            try {
                Files.move(temporary, file.toAbsolutePath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, file.toAbsolutePath(), StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    static String normalizeGroupId(String groupId) {
        if (groupId == null || groupId.isBlank()) throw new IllegalArgumentException("群 ID 不能为空");
        return groupId.trim();
    }

    static String text(String value, int limit) {
        String result = value == null ? "" : value.trim();
        if (result.length() <= limit) return result;
        int end = Character.isHighSurrogate(result.charAt(limit - 1)) ? limit - 1 : limit;
        return result.substring(0, end);
    }

    private static String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    record Snapshot(int version, String groupId, String summary, List<Entry> messages) {}

    record Entry(String role, String userId, String userName, String messageId,
                 String replyToUserId, String replyToMessageId, String content, String timestamp) {
        Entry withMessageId(String id) {
            return new Entry(role, userId, userName, id, replyToUserId, replyToMessageId, content, timestamp);
        }
    }

    static final class Conversation {
        final String groupId;
        String summary = "";
        final List<Entry> messages = new ArrayList<>();
        volatile long lastChatNanos;

        Conversation(String groupId) {
            this.groupId = groupId;
        }

        boolean addUser(String userId, String userName, String messageId, String replyToMessageId, String content) {
            String id = text(userId, 128);
            String mid = text(messageId, 128);
            if (!mid.isEmpty() && messages.stream().anyMatch(entry -> "user".equals(entry.role())
                    && mid.equals(entry.messageId()) && id.equals(entry.userId()))) return false;
            String name = text(userName, 100);
            messages.add(new Entry("user", id, name.isEmpty() ? id : name, mid,
                    "", text(replyToMessageId, 128), content, Instant.now().toString()));
            return true;
        }

        void addReply(String userId, String messageId, String reply) {
            messages.add(new Entry("assistant", "", "亚托莉", "", text(userId, 128),
                    text(messageId, 128), reply.trim(), Instant.now().toString()));
        }

        String existingReply(String userId, String messageId) {
            String mid = text(messageId, 128);
            if (mid.isEmpty()) return null;
            for (Entry entry : messages) {
                if ("assistant".equals(entry.role()) && mid.equals(entry.replyToMessageId())
                        && text(userId, 128).equals(entry.replyToUserId())) return entry.content();
            }
            return null;
        }
    }
}
