package top.yzljc.qqbot.feature;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.userinfo.GetFriendList;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.botkits.request.RequestType;
import top.yzljc.qqbot.botkits.thread.ThreadManager;

import java.util.HashMap;
import java.util.Map;

public class LikeUser {

    private static final Logger log = LoggerFactory.getLogger(LikeUser.class);

    private enum LikeStatus {
        SUCCESS,        // 200
        DAILY_LIMIT,    // 100 (fail)
        UNKNOWN,        // 404
        FORMAT_ERROR,   // 505
        REQUEST_ERROR   // -1 (Exception)
    }

    public static void processCommand(long userId, long groupId) {
        boolean isFriend = GetFriendList.isFriend(userId);
        ThreadManager.execute(() -> sendLike(userId, groupId, isFriend));
    }

    private static void sendLike(long userId, long groupId, boolean isFriend) {
        String feedback;

        if (isFriend) {
            LikeStatus status = postLike(userId);
            feedback = generalResult(userId, status);
        } else {
            feedback = unfriendLike(userId);
        }

        MessageSender.sendGroupMessage(groupId, feedback);
    }

    private static String unfriendLike(long userId) {
        int successCount = 0;
        int failCount = 0;

        for (int i = 0; i < 5; i++) {
            LikeStatus status = postLike(userId);

            if (status == LikeStatus.SUCCESS) {
                successCount++;
            } else if (status == LikeStatus.DAILY_LIMIT) {
                failCount++;
            } else {
                return generalResult(userId, status);
            }
        }

        if (successCount == 5) {
            log.info("点赞成功 => QQ: {} | 获得点赞数: 50", userId);
            return "点赞成功！(+50 Social Credits!)，没加好友可能无法收到点赞哦！";
        } else if (failCount == 5) {
            log.info("点赞失败 => QQ: {} | 用户今日获赞数量达到上限", userId);
            return "点赞失败，该用户今日已被赞过啦~";
        } else {
            log.info("点赞部分成功 => QQ: {} | 成功: {} 次 | 失败: {} 次", userId, successCount * 10, failCount * 10);
            return String.format("点赞部分成功！成功 %d 次，失败 %d 次，可能是由于该用户今日已被赞过啦~", successCount * 10 , failCount * 10);
        }
    }

    private static LikeStatus postLike(long userId) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("user_id", String.valueOf(userId));
            params.put("times", 10);

            JsonNode respJson = PostRequest.getPostResult(RequestType.SEND_LIKE, params);

            if (respJson == null) return LikeStatus.FORMAT_ERROR;

            String statusStr = respJson.path("status").asText();
            if ("ok".equalsIgnoreCase(statusStr)) {
                return LikeStatus.SUCCESS;
            } else if (statusStr.contains("fail")) {
                return LikeStatus.DAILY_LIMIT;
            } else {
                return LikeStatus.UNKNOWN;
            }
        } catch (Exception e) {
            log.warn("点赞请求异常: {}", e.getMessage());
            return LikeStatus.REQUEST_ERROR;
        }
    }

    private static String generalResult(long userId, LikeStatus status) {
        return switch (status) {
            case SUCCESS -> {
                log.info("点赞成功 => QQ: {} | 获得点赞数: 10", userId);
                yield "点赞成功！(+10 Social Credits!)";
            }
            case DAILY_LIMIT -> {
                log.info("点赞失败 => QQ: {} | 用户今日获赞数量达到上限", userId);
                yield "点赞失败，该用户今日已被赞过啦~";
            }
            case UNKNOWN -> {
                log.info("点赞未知响应 => QQ: {} | 原始: 接口返回未知状态", userId);
                yield "点赞失败，接口返回未知状态";
            }
            case FORMAT_ERROR -> {
                log.warn("点赞接口返回非预期格式 => QQ: {} | 原始: 接口返回格式异常或无法解析", userId);
                yield "点赞失败，接口返回格式异常或无法解析";
            }
            default -> {
                log.warn("点赞接口请求异常 => QQ: {} | 原始: 接口请求异常", userId);
                yield "点赞失败，接口请求异常";
            }
        };
    }
}