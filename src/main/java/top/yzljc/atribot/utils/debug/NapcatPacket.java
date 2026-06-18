package top.yzljc.atribot.utils.debug;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.UserInformation;
import top.yzljc.atribot.configuration.Config;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NapcatPacket {

    private static final Logger log = LoggerFactory.getLogger(NapcatPacket.class);

    private static final AtomicBoolean isDebugEnabled = new AtomicBoolean(false);
    private static volatile String targetFilterGroupId = null;
    private static final String botUid = UserInformation.getBotId();
    private static final String debugGroupId = Config.getInstance().getNapcatDebugGroupUin();

    public static void process(JsonNode json) {
        if (!isDebugEnabled.get()) return;
        if (targetFilterGroupId != null) {
            String currentGroupId = json.path("group_id").asText("");
            if (!Objects.equals(currentGroupId, targetFilterGroupId)) return;
        }
        forwardDebugPacket(json);
    }

    public static boolean isEnabled() {
        return isDebugEnabled.get();
    }

    public static String getFilterGroupId() {
        return targetFilterGroupId;
    }

    public static void enableDebug(String filterGroupId) {
        isDebugEnabled.set(true);
        targetFilterGroupId = filterGroupId;
    }

    public static void disableDebug() {
        isDebugEnabled.set(false);
        targetFilterGroupId = null;
    }

    public static boolean toggleDebug() {
        boolean prev, next;
        do {
            prev = isDebugEnabled.get();
            next = !prev;
        } while (!isDebugEnabled.compareAndSet(prev, next));
        if (!next) {
            targetFilterGroupId = null;
        }
        return next;
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
