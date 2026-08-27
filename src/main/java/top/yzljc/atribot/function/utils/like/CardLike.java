package top.yzljc.atribot.function.utils.like;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.napcat.FriendList;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.PrivateMessage;
import top.yzljc.atribot.chat.napcat.UserInformation;
import top.yzljc.atribot.chat.napcat.impl.MessageSegment;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatGroupMessageEvent;
import top.yzljc.atribot.event.events.NapcatPrivateMessageEvent;
import top.yzljc.atribot.platform.napcat.PostRequest;
import top.yzljc.atribot.platform.napcat.RequestType;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.service.taskscheduler.TaskPlan;
import top.yzljc.atribot.service.taskscheduler.ScheduleMode;
import top.yzljc.atribot.service.taskscheduler.ScheduledTask;
import top.yzljc.atribot.service.taskscheduler.TaskSchedule;
import top.yzljc.atribot.utils.statistic.BotRuntimeData;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CardLike implements Listener, ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(CardLike.class);

    public static void addToAutoLikeList(long userId) {
        List<Long> list = LikeUserListRecord.loadLikeUserUids();
        if (!list.contains(userId)) {
            list.add(userId);
            LikeUserListRecord.saveLikeUserUids(list);
        }
    }

    public static void removeFromAutoLikeList(long userId) {
        List<Long> list = LikeUserListRecord.loadLikeUserUids();
        if (list.remove(userId)) {
            LikeUserListRecord.saveLikeUserUids(list);
        }
    }

    public static List<Long> getAutoLikeList() {
        return LikeUserListRecord.loadLikeUserUids();
    }

    public static void likeAllinList() {
        List<Long> list = getAutoLikeList();
        if (list.isEmpty()) {
            return;
        }

        List<MessageSegment> result = new ArrayList<>();
        int i = 0;
        for (Long userId : list) {
            String userIdStr = String.valueOf(userId);
            String userName = UserInformation.getUserName(userIdStr);
            if (userName == null) {
                userName = userIdStr;
            }

            String resultLine = sendLike(userIdStr, null, FriendList.isFriend(userIdStr), true);
            result.add(GroupMessage.createTextNode("自动点赞 " + userName + " " + resultLine));
            log.info("已向群 818804507 自动点赞用户 {}", userName);
            i++;

            try {
                Thread.sleep(1000);
            } catch (InterruptedException ignored) {
            }
        }

        GroupMessage.forwardMessage("818804507", result, "自动点赞结果", "点击查看详细", "本次共点赞 " + i + " 位用户");
    }

    @Override
    public TaskSchedule schedule() {
        return new TaskPlan().setMode(ScheduleMode.daily).setTime(LocalTime.of(0, 3, 0));
    }

    @Override
    public void run() {
        likeAllinList();
    }

    private enum LikeStatus {
        SUCCESS,        // 200
        DAILY_LIMIT,    // 100 (fail)
        UNKNOWN,        // 404
        FORMAT_ERROR,   // 505
        REQUEST_ERROR   // -1 (Exception)
    }

    @EventHandler
    public void onGroupMessage(NapcatGroupMessageEvent event) {
        if (!GroupConfigManager.isFeatureEnabled(event.getGroupId(), "like_user")) return;
        String msg = event.getMessage().getContent().trim();
        String userId = event.getUser().getUserId();
        boolean isFriend = FriendList.isFriend(userId);
        for (String kw : Config.getInstance().getKeywordsLikeUser()) {
            if (msg.equalsIgnoreCase(kw)) {
                ThreadManager.execute(() -> sendLike(userId, event.getMessage().getMessageId(), isFriend, false));
            }
        }
    }

    @EventHandler
    public void onPrivateMessage(NapcatPrivateMessageEvent event) {
        String msg = event.getMessage().getContent().trim();
        String userId = event.getUser().getUserId();
        boolean isFriend = FriendList.isFriend(userId);
        for (var k : Config.getInstance().getKeywordsLikeUser()) {
            if (msg.equalsIgnoreCase(k)) {
                Atri.getInstance().getScheduler().runTaskAsynchronously(() -> sendLike(userId, isFriend));
            }
        }
    }

    private static String sendLike(String userId, String messageId, boolean isFriend, boolean isAuto) {
        String feedback;

        if (isFriend) {
            LikeStatus status = postLike(userId);
            feedback = generalResult(userId, status);
        } else {
            feedback = unfriendLike(userId);
        }

        int emojiId;
        if (!isAuto) {
            switch (feedback) {
                case "已赞，得十！" -> emojiId = 10024;
                case "赞成，增五十，但或不得见！", "半成，成 10 次，败 40 次，或为今日已赞故！" -> emojiId = 76;
                default -> emojiId = 10060;
            }
            GroupMessage.setEmoji(messageId, emojiId, true);
        }
        BotRuntimeData.callLikeUser();
        return feedback;
    }

    private static String sendLike(String userId, boolean isFriend) {
        String feedback;

        if (isFriend) {
            LikeStatus status = postLike(userId);
            feedback = generalResult(userId, status);
        } else {
            feedback = unfriendLike(userId);
        }
        String messageId = PrivateMessage.chatMessage(userId, feedback);
        Atri.getInstance().getScheduler().runTaskLater(() -> GroupMessage.recallMessage(messageId), 15 * 1000L);

        BotRuntimeData.callLikeUser();
        return feedback;
    }

    private static String unfriendLike(String userId) {
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
            return "赞成，增五十，但或不得见！";
        } else if (failCount == 5) {
            log.info("受阻 => {} | 今日已极", userId);
            return "今日已赞毕，明日请早！";
        } else {
            log.info("半成 => {} | 成: {} | 败: {}", userId, successCount * 10, failCount * 10);
            return String.format("半成，成 %d 次，败 %d 次，或为今日已赞故！", successCount * 10, failCount * 10);
        }
    }

    private static LikeStatus postLike(String userId) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("user_id", userId);
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

    private static String generalResult(String userId, LikeStatus status) {
        return switch (status) {
            case SUCCESS -> {
                log.info("得手 => {} | +10", userId);
                yield "已赞，得十！";
            }
            case DAILY_LIMIT -> {
                log.info("受阻 => {} | 今日已极", userId);
                yield "今日已赞毕，明日请早！";
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
