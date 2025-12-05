package top.yzljc.utiltools;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.*;
import java.util.concurrent.*;

public class AutoRepeat {
    // 用于记录每个群最近若干条消息：group_id -> List<msg>
    private static final int MEMORY_SIZE = 10; // 每个群只记最近10条消息
    private static final int REPEAT_THRESHOLD = 2; // 连续2条一致触发复读
    private static final Map<Long, LinkedList<String>> groupMsgHistory = new ConcurrentHashMap<>();
    // 用于避免对同一句话多次复读（如持续刷屏），key: groupId+"|"+msg
    private static final Set<String> recentlyRepeated = Collections.newSetFromMap(new ConcurrentHashMap<>());

    // 复读冷却时间（毫秒），避免同一句话刷屏就无限复读
    private static final long REPEAT_COOLDOWN_MS = 120000; // 120秒

    public static void processGroupMessage(JsonNode json) {
        long groupId = json.path("group_id").asLong();
        String rawMsg = json.path("raw_message").asText();

        if (rawMsg == null || rawMsg.isEmpty()) return;

        // 历史消息队列
        LinkedList<String> queue = groupMsgHistory.computeIfAbsent(groupId, k -> new LinkedList<>());
        queue.addLast(rawMsg);
        if (queue.size() > MEMORY_SIZE) queue.removeFirst();

        // 检查是否触发复读：最后N条是否至少2条都是这一条
        int count = 0;
        for (int i = queue.size() - 1; i >= 0; i--) {
            if (!queue.get(i).equals(rawMsg)) break;
            count++;
        }

        if (count >= REPEAT_THRESHOLD) {
            String repeatKey = groupId + "|" + rawMsg;
            if (!recentlyRepeated.contains(repeatKey)) {
                // 发送复读消息
                SendLike.sendGroupMessage(groupId, rawMsg);

                recentlyRepeated.add(repeatKey);
                // 定时移除冷却
                ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
                service.schedule(() -> {
                    recentlyRepeated.remove(repeatKey);
                    service.shutdown();
                }, REPEAT_COOLDOWN_MS, TimeUnit.MILLISECONDS);
            }
        }
    }
}