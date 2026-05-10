package top.yzljc.qqbot.functions;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.chat.MessageSegment;
import top.yzljc.qqbot.event.impl.GroupMessageEvent;

import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author YZ_Ljc_
 * @ClassName Repeater
 * @Created_at 2026/04/04
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions
 */
@Slf4j
public class Repeater implements Listener {

    private static final int MEMORY_SIZE = 10;
    private static final int REPEAT_THRESHOLD = 3;
    private static final Map<Long, LinkedList<LinkedList<MessageSegment>>> groupData = new ConcurrentHashMap<>();
    private static final Map<Long, LinkedList<MessageSegment>> lastRepeated = new ConcurrentHashMap<>();

    @EventHandler
    public void onGroupMessage(GroupMessageEvent event) {
        if (event.getUserId() == event.getSelfId()) return;
        if (!GroupConfigManager.isFeatureEnabled(event.getGroupId(), "repeat_msg")) return;

        Long groupId = event.getGroupId();
        LinkedList<MessageSegment> currentMessage = event.getMessage();
        LinkedList<LinkedList<MessageSegment>> queue = groupData.computeIfAbsent(groupId, k -> new LinkedList<>());

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

        LinkedList<MessageSegment> lastMsg = lastRepeated.get(groupId);
        if (count >= REPEAT_THRESHOLD) {
            if (lastMsg == null || !lastMsg.equals(currentMessage)) {
                event.getGroup().sendUnionMessage(currentMessage);
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