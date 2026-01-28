package top.yzljc.qqbot.feature;

import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.text.StringEscapeUtils;
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
    private static final Pattern AT_PATTERN = Pattern.compile("\\[CQ:at,qq=(\\d+).*?\\]");

    public static void processGroupMessage(JsonNode json) {
        long groupId = json.path("group_id").asLong();
        String rawMsg = json.path("raw_message").asText();

        if (rawMsg == null || rawMsg.isEmpty()) return;

        String realMsg = unescape(rawMsg);

        LinkedList<String> queue = groupMsgHistory.computeIfAbsent(groupId, k -> new LinkedList<>());
        queue.addLast(realMsg);
        if (queue.size() > MEMORY_SIZE) queue.removeFirst();

        int count = 0;
        for (int i = queue.size() - 1; i >= 0; i--) {
            if (!queue.get(i).equals(realMsg)) break;
            count++;
        }

        if (count >= REPEAT_THRESHOLD) {
            String repeatKey = groupId + "|" + realMsg;

            if (isIgnored(realMsg)) return;

            if (!recentlyRepeated.contains(repeatKey)) {
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

    private static String formatMsgToSend(String msg, JsonNode rootJson) {
        if (msg == null) return "";
        Matcher matcher = AT_PATTERN.matcher(msg);
        StringBuffer sb = new StringBuffer();

        while (matcher.find()) {
            String qq = matcher.group(1);

            String displayName = extractNameFromRaw(rootJson, qq);

            if (displayName != null && !displayName.isEmpty()) {
                matcher.appendReplacement(sb, Matcher.quoteReplacement(displayName));
            } else {
                matcher.appendReplacement(sb, "@" + qq);
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private static String extractNameFromRaw(JsonNode root, String targetQQ) {
        try {
            JsonNode elements = root.path("raw").path("elements");
            if (elements.isArray()) {
                for (JsonNode elem : elements) {
                    JsonNode textElem = elem.path("textElement");
                    String atUid = textElem.path("atUid").asText();
                    if (atUid.equals(targetQQ)) {
                        return textElem.path("content").asText();
                    }
                }
            }
        } catch (Exception e) {
            log.error("提取用户名时发生错误: {}", e.getMessage());
        }
        return null;
    }

    public static String unescape(String text) {
        if (text == null) return null;
        return StringEscapeUtils.unescapeHtml4(text);
    }

    private static boolean isIgnored(String msg) {
        return msg.equalsIgnoreCase("赞我") ||
                msg.equalsIgnoreCase("likeme") ||
                msg.equalsIgnoreCase("zanwo") ||
                msg.equalsIgnoreCase("电表") ||
                msg.equalsIgnoreCase("/debug") ||
                msg.equalsIgnoreCase("一言") ||
                msg.equalsIgnoreCase("db");
    }
}