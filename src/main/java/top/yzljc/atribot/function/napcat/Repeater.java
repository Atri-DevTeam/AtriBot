package top.yzljc.atribot.function.napcat;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatGroupMessageEvent;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author YZ_Ljc_
 * @ClassName Repeater
 * @Created_at 2026/04/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.napcat
 */
@Slf4j
public class Repeater implements Listener {

    private static final int MEMORY_SIZE = 10;
    private static final int REPEAT_THRESHOLD = 3;
    private static final Map<String, LinkedList<String>> groupData = new ConcurrentHashMap<>();
    private static final Map<String, String> lastRepeated = new ConcurrentHashMap<>();

    @EventHandler
    public void onGroupMessage(NapcatGroupMessageEvent event) {
        if (event.getUser().isBot()) return;
        if (!GroupConfigManager.isFeatureEnabled(event.getGroupId(), "repeat_msg")) return;

        String groupId = event.getGroupId();
        String currentMessage = event.getMessage().getContent();
        LinkedList<String> queue = groupData.computeIfAbsent(groupId, k -> new LinkedList<>());

        int count = 0;
        synchronized (queue) {
            queue.addLast(currentMessage);
            if (queue.size() > MEMORY_SIZE) {
                queue.removeFirst();
            }

            for (int i = queue.size() - 1; i >= 0; i--) {
                if (!queue.get(i).equals(currentMessage)) break;
                count++;
            }
        }

        String lastMsg = lastRepeated.get(groupId);
        if (count >= REPEAT_THRESHOLD) {
            if (lastMsg == null || !lastMsg.equals(currentMessage)) {
                GroupMessage.chatMessage(groupId, currentMessage);
                log.info("群 {} 的消息被复读了！", groupId);

                lastRepeated.put(groupId, currentMessage);
            }
        } else {
            if (lastMsg != null && !lastMsg.equals(currentMessage)) {
                lastRepeated.remove(groupId);
            }
        }
    }
}
