package top.yzljc.qqbot.debug;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.service.userinfo.GetUserInfo;
import top.yzljc.qqbot.chat.GroupMessage;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PacketEvent {

    private static final Logger log = LoggerFactory.getLogger(PacketEvent.class);

    private static final AtomicBoolean isDebugEnabled = new AtomicBoolean(false);
    private static volatile Long targetFilterGroupId = null;

    static final Settings settings = Config.getInstance();
    private static final List<Long> admins = settings.getAdminUids();
    private static final long botUid = GetUserInfo.getBotId();
    private static final long debugGroupId = settings.getDebugGroupId();

    public static void process(JsonNode json) {
        if (checkToggleCommand(json)) return;
        if (isDebugEnabled.get()) {
            if (targetFilterGroupId != null) {
                long currentGroupId = json.path("group_id").asLong(0);
                if (currentGroupId != targetFilterGroupId) return;
            }
            forwardDebugPacket(json);
        }
    }

    private static boolean checkToggleCommand(JsonNode json) {
        if (!"message".equals(json.path("post_type").asText())) return false;
        long userId = json.path("user_id").asLong();
        if (!admins.contains(userId)) return false;

        String rawMsg = json.path("raw_message").asText().trim();
        String[] parts = rawMsg.split("\\s+");

        if (parts.length > 0 && "/debug".equalsIgnoreCase(parts[0])) {
            long fromGroupId = json.path("group_id").asLong();
            String statusMsg;
            if (parts.length == 2) {
                if (parts[1].equals("this")){
                    isDebugEnabled.set(true);
                    targetFilterGroupId = fromGroupId;
                    statusMsg = "Debug 模式已开启 (过滤模式)！\n只监听来自群 " + fromGroupId + " 的数据包\n数据将转发至群 " + debugGroupId;
                }else {
                    try {
                        long targetGid = Long.parseLong(parts[1]);
                        isDebugEnabled.set(true);
                        targetFilterGroupId = targetGid;
                        statusMsg = "Debug 模式已开启 (过滤模式)！\n只监听来自群 " + targetGid + " 的数据包\n数据将转发至群 " + debugGroupId;
                    } catch (NumberFormatException e) {
                        statusMsg = "群号格式错误，请使用纯数字";
                    }
                }
            }else {
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
                statusMsg = toggled
                        ? "Debug 模式已开启（全局模式）\n所有收到的原始数据包将转发至群 " + debugGroupId
                        : "Debug 模式已关闭";
            }
            log.info(statusMsg);

            if (fromGroupId > 0) {
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
            long userId = json.path("user_id").asLong(0);
            long groupid = json.path("group_id").asLong(0);
            if (userId == botUid && groupid == debugGroupId) return;
            GroupMessage.chatMessage(debugGroupId, jsonString);
        } catch (Exception e) {
            log.error("转发失败：{}", e.getMessage());
        }
    }
}