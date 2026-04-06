package top.yzljc.qqbot.chat;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botservice.request.PostRequest;
import top.yzljc.qqbot.botservice.request.RequestType;
import top.yzljc.qqbot.utils.Logger;

import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName SendPrivateMessage
 * @Created_at 2026/04/04
 * @Project AtriBot
 * @Package top.yzljc.qqbot.chat
 */
public class SendPrivateMessage {
    @SuppressWarnings("UnusedReturnValue")
    public static long unionChatMessage(long userId, List<MessageSegment> data) {
        if (data == null || data.isEmpty()) return 0L;
        try {
            JsonNode resp = PostRequest.getPostResult(
                    RequestType.SEND_PRIVATE_MSG,
                    Map.of("user_id", userId, "message", data)
            );

            long messageId = resp.path("data").path("message_id").asLong(0L);

            if (messageId != 0L) return messageId;

            Logger.error("消息发送失败，返回内容: {}", resp);

        } catch (Exception e) {
            Logger.error("推送异常：{}", e.getMessage(), e);
        }

        return 0L;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static long singleTextMessage(long userId, String text) {
        return unionChatMessage(
                userId,
                List.of(new MessageSegment(
                        "text",
                        Map.of("text", text)
                ))
        );
    }
}