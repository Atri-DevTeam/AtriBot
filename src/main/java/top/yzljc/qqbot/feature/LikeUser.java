package top.yzljc.qqbot.feature;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botkits.request.RequestType;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.botkits.message.MessageSender;

import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LikeUser {
    
    private static final Logger log = LoggerFactory.getLogger(LikeUser.class);

    public static void processCommand(long userId, long groupId) {
        sendLike(userId, groupId);
    }

    /**
     * 执行点赞操作
     * @param userId 被点赞的QQ
     * @param groupId 来源群组（用于发送反馈消息）
     */
    public static void sendLike(long userId, long groupId) {
        Executors.newSingleThreadExecutor().submit(() -> {
            String likeResult = "点赞成功！";
            try {
                String respStr = "";
                JsonNode respJson = null;

                try {
                    java.util.Map<String, Object> params = new java.util.HashMap<>();
                    params.put("user_id", String.valueOf(userId));
                    params.put("times", 10);
                    respJson = PostRequest.getPostResult(RequestType.SEND_LIKE, params);
                    if (respJson != null) {
                        respStr = respJson.toString();
                    }
                } catch (Exception e) {
                    log.warn("点赞接口请求异常: {}", e.getMessage());
                }

                if (respJson != null) {
                    String status = respJson.path("status").asText();
                    String msg = respJson.path("msg").asText("");

                    if ("ok".equalsIgnoreCase(status)) {
                        likeResult = "点赞成功！(+10 Social Credits!)，没加好友可能无法收到点赞哦！";
                        log.info("点赞成功 => QQ: {}", userId);
                    }
                    else if (status.contains("fail")) {
                        likeResult = "点赞失败，可能是由于该用户今日已被赞过啦~";
                        log.info("点赞失败 => QQ: {} | 用户今日获赞数量达到上限", userId);
                    } else {
                        log.info("点赞未知响应 => QQ: {} | 原始: {}", userId, respStr);
                    }
                } else {
                    log.warn("点赞接口返回非预期格式 => QQ: {} | 原始: {}", userId, respStr);
                }

                MessageSender.sendGroupMessage(groupId, likeResult);

            } catch (Exception ex) {
                MessageSender.sendGroupMessage(groupId, "点赞接口异常，请稍后再试。");
                log.warn("点赞接口异常: {}", ex.getMessage());
            }
        });
    }
}
