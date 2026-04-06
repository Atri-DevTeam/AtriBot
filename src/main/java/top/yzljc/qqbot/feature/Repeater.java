package top.yzljc.qqbot.feature;

import top.yzljc.qqbot.botservice.message.MessageSender;

import java.util.*;
import java.util.concurrent.*;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated(forRemoval = true)
public class Repeater {

    private static final Logger log = LoggerFactory.getLogger(Repeater.class);
    
    private static final int MEMORY_SIZE = 10;
    private static final int REPEAT_THRESHOLD = 3;
    private static final Map<Long, LinkedList<List<Map<String, Object>>>> groupData = new ConcurrentHashMap<>();
    private static final Set<String> recentlyRepeated = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static void repeatGroupData(Long groupId, LinkedList<Map<String, Object>> msgData) {
        LinkedList<List<Map<String, Object>>> queue = groupData.computeIfAbsent(groupId, _ -> new LinkedList<>());
        queue.addLast(msgData);
        if (queue.size() > MEMORY_SIZE) queue.removeFirst();

        int count = 0;
        for (int i = queue.size() - 1; i >= 0; i--) {
            if (!queue.get(i).equals(msgData)) break;
            count++;
        }

        if (count >= REPEAT_THRESHOLD) {
            String repeatKey = groupId + "|" + msgData;

            if (!recentlyRepeated.contains(repeatKey)) {
                MessageSender.sendGroupData(groupId, msgData);
                log.info("群 {} 消息复读触发，内容: {}", groupId, msgData);

                recentlyRepeated.add(repeatKey);
            }
        }
    }
}