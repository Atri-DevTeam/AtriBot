package top.yzljc.qqbot.feature;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botkits.message.MessageSender;

import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoRepeat {

    private static final Logger log = LoggerFactory.getLogger(AutoRepeat.class);
    
    private static final int MEMORY_SIZE = 10;
    private static final int REPEAT_THRESHOLD = 3;
    private static final Map<Long, LinkedList<String>> groupMsgHistory = new ConcurrentHashMap<>();
    private static final Set<String> recentlyRepeated = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final long REPEAT_COOLDOWN_MS = 120000;

    // 匹配 [CQ:at,qq=123456]
    private static final Pattern AT_PATTERN = Pattern.compile("\\[CQ:at,qq=(\\d+).*?\\]");

    public static void processGroupMessage(JsonNode json) {
        long groupId = json.path("group_id").asLong();
        String rawMsg = json.path("raw_message").asText();

        if (rawMsg == null || rawMsg.isEmpty()) return;

        // 1. 反转义，获取用于对比的真实字符串
        String realMsg = unescape(rawMsg);

        // 2. 存入历史
        LinkedList<String> queue = groupMsgHistory.computeIfAbsent(groupId, k -> new LinkedList<>());
        queue.addLast(realMsg);
        if (queue.size() > MEMORY_SIZE) queue.removeFirst();

        // 3. 检查复读
        int count = 0;
        for (int i = queue.size() - 1; i >= 0; i--) {
            if (!queue.get(i).equals(realMsg)) break;
            count++;
        }

        if (count >= REPEAT_THRESHOLD) {
            String repeatKey = groupId + "|" + realMsg;

            if (isIgnored(realMsg)) return;

            if (!recentlyRepeated.contains(repeatKey)) {
                // 【修改点】：把整个 json 传进去，方便去掏里面的名字
                String msgToSend = formatMsgToSend(realMsg, json);

                MessageSender.sendGroupMessage(groupId, msgToSend);

                recentlyRepeated.add(repeatKey);
                ScheduledExecutorService service = Executors.newSingleThreadScheduledExecutor();
                service.schedule(() -> {
                    recentlyRepeated.remove(repeatKey);
                    service.shutdown();
                }, REPEAT_COOLDOWN_MS, TimeUnit.MILLISECONDS);
            }
        }
    }

    /**
     * 将 CQ:at 替换为 JSON 中记录的真实群名片
     */
    private static String formatMsgToSend(String msg, JsonNode rootJson) {
        if (msg == null) return "";
        Matcher matcher = AT_PATTERN.matcher(msg);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String qq = matcher.group(1);

            // 尝试从 json 的 raw 字段里提取这个 QQ 对应的群名片
            String displayName = extractNameFromRaw(rootJson, qq);

            if (displayName != null && !displayName.isEmpty()) {
                // 如果提取到了名字（raw里通常自带@前缀，如 "@张三"）
                // 这里的 replace 是为了防止特殊字符破坏正则
                matcher.appendReplacement(sb, Matcher.quoteReplacement(displayName));
            } else {
                // 如果实在没找到名字，只能兜底显示 QQ
                matcher.appendReplacement(sb, "@" + qq);
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String extractNameFromRaw(JsonNode root, String targetQQ) {
        try {
            // 你的 JSON 结构是 raw -> elements 数组
            JsonNode elements = root.path("raw").path("elements");
            if (elements.isArray()) {
                for (JsonNode elem : elements) {
                    JsonNode textElem = elem.path("textElement");
                    // 检查 atUid 是否匹配目标 QQ
                    String atUid = textElem.path("atUid").asText();
                    if (atUid.equals(targetQQ)) {
                        // 找到了！直接返回 content，比如 "@LavI LavI To-ya-ya"
                        return textElem.path("content").asText();
                    }
                }
            }
        } catch (Exception e) {
            // 解析出错忽略，返回null
        }
        return null;
    }

    private static String unescape(String text) {
        if (text == null) return null;
        return text.replace("&amp;", "&")
                .replace("&#91;", "[")
                .replace("&#93;", "]");
    }

    private static boolean isIgnored(String msg) {
        return msg.equalsIgnoreCase("赞我") ||
                msg.equalsIgnoreCase("likeme") ||
                msg.equalsIgnoreCase("zanwo") ||
                msg.equalsIgnoreCase("电表") ||
                msg.equalsIgnoreCase("/debug") ||
                msg.equalsIgnoreCase("db");
    }
}