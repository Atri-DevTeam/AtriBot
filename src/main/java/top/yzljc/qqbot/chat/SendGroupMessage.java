package top.yzljc.qqbot.chat;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botservice.request.PostRequest;
import top.yzljc.qqbot.botservice.request.RequestType;
import top.yzljc.qqbot.botservice.tools.RM;
import top.yzljc.qqbot.utils.Logger;

import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName SendGroupMessage
 * @Created_at 2026/04/04
 * @Project AtriBot
 * @Package top.yzljc.qqbot.chat
 */
public class SendGroupMessage {
    @SuppressWarnings("UnusedReturnValue")
    public static long singleTextMessage(long groupId, String text) {
        return unionChatMessage(
                groupId,
                List.of(new MessageSegment(
                        "text",
                        Map.of("text", text)
                ))
        );
    }

    @SuppressWarnings("UnusedReturnValue")
    public static long unionChatMessage(long groupId, List<MessageSegment> data) {
        if (data == null || data.isEmpty()) return 0L;
        try {
            JsonNode resp = PostRequest.getPostResult(
                    RequestType.SEND_GROUP_MSG,
                    Map.of("group_id", groupId, "message", data)
            );

            long messageId = resp.path("data").path("message_id").asLong(0L);

            if (messageId != 0L) {
                RM.recordLastMsg(groupId, messageId);
                return messageId;
            }

            Logger.error("消息发送失败，返回内容: {}", resp);
        } catch (Exception e) {
            Logger.error("推送异常：{}", e.getMessage(), e);
        }

        return 0L;
    }
}