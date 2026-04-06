package top.yzljc.qqbot.feature;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.chat.impl.MessageUtils;
import top.yzljc.qqbot.botservice.userinfo.GetFriendList;
import top.yzljc.qqbot.botservice.userinfo.GetUserInfo;
import top.yzljc.qqbot.botservice.message.MessageSender;
import top.yzljc.qqbot.botservice.request.PostRequest;
import top.yzljc.qqbot.botservice.request.RequestType;
import top.yzljc.qqbot.botservice.thread.ThreadManager;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.data.VarData;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.GroupMessageEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class LikeUser implements Listener {

    private static final Logger log = LoggerFactory.getLogger(LikeUser.class);

    public static void addToAutoLikeList(long userId) {
        List<Long> list = VarData.loadLikeUserUids();
        if (!list.contains(userId)) {
            list.add(userId);
            VarData.saveLikeUserUids(list);
        }
    }

    public static void removeFromAutoLikeList(long userId) {
        List<Long> list = VarData.loadLikeUserUids();
        if (list.remove(userId)) {
            VarData.saveLikeUserUids(list);
        }
    }

    public static List<Long> getAutoLikeList() {
        return VarData.loadLikeUserUids();
    }

    public static void likeAllinList() {
        List<Long> list = getAutoLikeList();
        if (list.isEmpty()) {
            return;
        }

        List<Map<String, Object>> result = new ArrayList<>();
        int i = 0;
        for (Long userId : list) {
            String userName = GetUserInfo.getUserName(userId);
            if (userName == null) {
                userName = String.valueOf(userId);
            }

            String resultLine = sendLike(userId, 818804507L, GetFriendList.isFriend(userId), true);
            result.add(MessageUtils.createTextNode("自动点赞 " + userName + " " + resultLine));
            log.info("已向群 818804507 自动点赞用户 {}", userName);
            i++;

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        }

        MessageUtils.sendGroupForwardMessage(818804507L, result, "自动点赞结果", "点击查看详细", "本次共点赞 " + i + " 位用户");
    }

    private enum LikeStatus {
        SUCCESS,        // 200
        DAILY_LIMIT,    // 100 (fail)
        UNKNOWN,        // 404
        FORMAT_ERROR,   // 505
        REQUEST_ERROR   // -1 (Exception)
    }

    @EventHandler
    public void onGroupMessage(GroupMessageEvent event) {
        if (!GroupConfigManager.isFeatureEnabled(event.getGroupId(), "like_user")) return;
        String msg = event.getRawMessage().trim();
        boolean isFriend = GetFriendList.isFriend(event.getUserId());
        for (String kw : Config.getInstance().getKeywordsLikeUser()) {
            if (msg.equalsIgnoreCase(kw)) {
                ThreadManager.execute(() -> sendLike(event.getUserId(), event.getGroupId(), isFriend, false));
            }
        }
    }

    private static String sendLike(long userId, long groupId, boolean isFriend, boolean isAuto) {
        String feedback;

        if (isFriend) {
            LikeStatus status = postLike(userId);
            feedback = generalResult(userId, status);
        } else {
            feedback = unfriendLike(userId);
        }

        if (!isAuto) {
            MessageSender.sendGroupMessage(groupId, feedback);
        }
        return feedback;
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
            log.info("得手 => {} | +50", userId);
            return "赞成，增五十善，然未与友者或不得见也！";
        } else if (failCount == 5) {
            log.info("受阻 => {} | 今日已极", userId);
            return "今日已赞，不可复也！";
        } else {
            log.info("半成 => {} | 成: {} | 败: {}", userId, successCount * 10, failCount * 10);
            return String.format("半成。成 %d 次，败 %d 次，或为今日已赞故！", successCount * 10, failCount * 10);
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
                log.info("得手 => {} | +10", userId);
                yield "已赞，增十善";
            }
            case DAILY_LIMIT -> {
                log.info("受阻 => {} | 今日已极", userId);
                yield "今日已赞，不可复";
            }
            case UNKNOWN -> {
                log.info("不明 => {} | 接口无应", userId);
                yield "接口无应，未成";
            }
            case FORMAT_ERROR -> {
                log.warn("谬乱 => {} | 格式误", userId);
                yield "格式谬，赞未成";
            }
            default -> {
                log.warn("失常 => {} | 请重试", userId);
                yield "异常，姑且待之";
            }
        };
    }
}