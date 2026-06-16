package top.yzljc.atribot.utils.debug;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.UserInformation;
import top.yzljc.atribot.configuration.Config;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NapcatPacket {

    private static final Logger log = LoggerFactory.getLogger(NapcatPacket.class);

    private static final AtomicBoolean isDebugEnabled = new AtomicBoolean(false);
    private static volatile String targetFilterGroupId = null;
    private static final List<String> admins = Config.getInstance().getNapcatAdminUins();
    private static final String botUid = UserInformation.getBotId();
    private static final String debugGroupId = Config.getInstance().getNapcatDebugGroupUin();

    public static void process(JsonNode json) {
        if (checkToggleCommand(json)) return;
        if (isDebugEnabled.get()) {
            if (targetFilterGroupId != null) {
                String currentGroupId = json.path("group_id").asText("");
                if (!Objects.equals(currentGroupId, targetFilterGroupId)) return;
            }
            forwardDebugPacket(json);
        }
    }

    private static boolean checkToggleCommand(JsonNode json) {
        if (!"message".equals(json.path("post_type").asText())) return false;
        String userId = json.path("user_id").asText();
        if (!admins.contains(userId)) return false;

        String rawMsg = json.path("raw_message").asText().trim();
        String[] parts = rawMsg.split("\\s+");

        if (parts.length > 0 && "!debug".equalsIgnoreCase(parts[0])) {
            String fromGroupId = json.path("group_id").asText();
            String statusMsg;
            if (parts.length == 2) {
                if (parts[1].equals("this")) {
                    isDebugEnabled.set(true);
                    targetFilterGroupId = fromGroupId;
                    statusMsg = "Debug 模式已开启 (过滤模式)！\n只监听来自群 " + fromGroupId + " 的数据包\n数据将转发至群 " + debugGroupId;
                } else {
                    String targetGid = parts[1];
                    isDebugEnabled.set(true);
                    targetFilterGroupId = targetGid;
                    statusMsg = "Debug 模式已开启 (过滤模式)！\n只监听来自群 " + targetGid + " 的数据包\n数据将转发至群 " + debugGroupId;
                }
            } else {
                boolean toggled;
                while (true) {
                    boolean prev = isDebugEnabled.get();
                    boolean next = !prev;
                    if (isDebugEnabled.compareAndSet(prev, next)) {
                        toggled = next;
                        break;
                    }
                }
                targetFilterGroupId = null;
                statusMsg = toggled ? "Debug 模式已开启（全局模式）\n所有收到的原始数据包将转发至群 " + debugGroupId : "Debug 模式已关闭";
            }
            log.info(statusMsg);

            if (!fromGroupId.isEmpty()) {
                GroupMessage.chatMessage(fromGroupId, statusMsg);
            } else {
                log.warn("无效的群号，无法发送状态提示");
            }
            return true;
        }
        return false;
    }

    private static void forwardDebugPacket(JsonNode json) {
        try {
            String jsonString = json.toPrettyString();
            String userId = json.path("user_id").asText("");
            String groupid = json.path("group_id").asText("");
            if (Objects.equals(userId, botUid) && Objects.equals(groupid, debugGroupId)) return;
            GroupMessage.chatMessage(debugGroupId, jsonString);
        } catch (Exception e) {
            log.error("转发失败：{}", e.getMessage());
        }
    }
}
