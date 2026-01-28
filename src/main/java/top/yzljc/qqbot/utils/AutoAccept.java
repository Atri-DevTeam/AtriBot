package top.yzljc.qqbot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.botkits.request.RequestType;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AutoAccept {

    private static final Logger log = LoggerFactory.getLogger(AutoAccept.class);

    public static void handle(JsonNode json) {
        // 二次校验，防止错误调用
        if (!json.has("post_type") || !"request".equals(json.get("post_type").asText())) {
            return;
        }
        if (!json.has("request_type") || !"friend".equals(json.get("request_type").asText())) {
            return;
        }

        String flag = json.has("flag") ? json.get("flag").asText() : "";
        long userId = json.has("user_id") ? json.get("user_id").asLong() : 0;
        String comment = json.has("comment") ? json.get("comment").asText() : "";

        if (flag.isEmpty()) {
            log.warn("[WARN] 收到好友请求但 flag 为空，无法处理！");
            return;
        }

        log.info("收到好友请求 -> 用户: {} | 验证消息: {} | Flag: {}", userId, comment, flag);

        // 异步执行同意操作
        Executors.newSingleThreadExecutor().submit(() -> {
            try {
                approveFriendRequest(flag);
            } catch (Exception e) {
                log.warn("[INFO] 同意操作失败: {}", e.getMessage(), e);
            }
        });
    }

    private static void approveFriendRequest(String flag) {
        Map<String, Object> params = new HashMap<>();
        params.put("flag", flag);
        params.put("approve", true);
        params.put("remark", ""); // 备注留空

        try {
            PostRequest.sendPost(RequestType.ACCEPT_FRIEND_REQUEST, params);
            log.info("已成功同意好友请求 (Flag: {})", flag);
        } catch (Exception e) {
            log.warn("同意好友请求时发生异常 (Flag: {})，错误信息: {}", flag, e.getMessage());
        }
    }
}
