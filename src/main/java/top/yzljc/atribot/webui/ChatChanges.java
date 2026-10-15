package top.yzljc.atribot.webui;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

/**
 * @Author YZ_Ljc_
 * @ClassName ChatChanges
 * @Created_at 2026/10/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui
 */
public final class ChatChanges {
    private static final int LIMIT = 4096;
    private static final String EPOCH = UUID.randomUUID().toString();
    private static final ArrayDeque<Change> changes = new ArrayDeque<>();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static long sequence;

    private ChatChanges() {}

    public static synchronized String cursor() {
        return EPOCH + ":" + sequence;
    }

    public static synchronized void publish(String type, String openId, long recordId, boolean reset) {
        changes.addLast(new Change(++sequence, type, openId, recordId, reset));
        while (changes.size() > LIMIT) changes.removeFirst();
        var payload = mapper.createObjectNode();
        payload.put("type", "group".equals(type) ? "refresh" : "c2c_refresh");
        payload.put("group".equals(type) ? "groupOpenId" : "userOpenId", openId);
        SseBroadcaster.broadcast(payload.toString());
    }

    public static synchronized Batch since(String cursor) {
        long position;
        try {
            if (cursor == null || !cursor.startsWith(EPOCH + ":")) return new Batch(cursor(), true, List.of());
            position = Long.parseLong(cursor.substring(EPOCH.length() + 1));
        } catch (NumberFormatException e) {
            return new Batch(cursor(), true, List.of());
        }
        if (position < 0 || position > sequence || (!changes.isEmpty() && position < changes.getFirst().sequence() - 1)) {
            return new Batch(cursor(), true, List.of());
        }
        return new Batch(cursor(), false, changes.stream().filter(c -> c.sequence() > position).toList());
    }

    public record Change(long sequence, String type, String openId, long recordId, boolean reset) {
        public String key() { return type + ":" + openId; }
    }

    public record Batch(String cursor, boolean reset, List<Change> changes) {}
}
