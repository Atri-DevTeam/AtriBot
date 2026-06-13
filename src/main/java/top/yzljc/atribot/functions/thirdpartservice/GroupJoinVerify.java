package top.yzljc.atribot.functions.thirdpartservice;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.onebot.GroupMessage;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.impl.GroupMemberChangeEvent;
import top.yzljc.atribot.event.impl.GroupMemberChangeType;
import top.yzljc.atribot.event.impl.GroupMessageEvent;
import top.yzljc.atribot.service.request.PostRequest;
import top.yzljc.atribot.service.request.RequestType;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupJoinVerify
 * @Created_at 2026/05/22
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions.thirdpartservice
 * @Description 本功能由3066828940 KziyR提出制作，为定制内容，后对方又提出不需要了
 */
public class GroupJoinVerify implements Listener {

    private static final Map<Long, ScheduledFuture<?>> pendingVerifys = new ConcurrentHashMap<>();

    @EventHandler
    public void onMemberJoin(GroupMemberChangeEvent event) {
        long groupId = Config.getInstance().getGroupJoinVerifyGroupId();
        if (event.getGroupId() != groupId) return;

        if (event.getOperateType() == GroupMemberChangeType.MEMBER_ACTIVE_LEAVE
                || event.getOperateType() == GroupMemberChangeType.MEMBER_KICK_LEAVE) {

            ScheduledFuture<?> future = pendingVerifys.remove(event.getUserId());

            if (future != null) {
                future.cancel(true);
            }

            return;
        }

        if (event.getOperateType() == GroupMemberChangeType.MEMBER_APPROVE_JOIN
                || event.getOperateType() == GroupMemberChangeType.MEMBER_INVITE_JOIN) {

            GroupMessage.chatMessage(event.getUserId(), groupId, Config.getInstance().getGroupJoinVerifyMessage(), true);

            var task = Atri.getInstance().getScheduler().runTaskLater(() -> {
                ScheduledFuture<?> future = pendingVerifys.remove(event.getUserId());
                if (future == null) {
                    return;
                }
                GroupMessage.chatMessage(groupId, "未在规定时间内完成验证！");
                kick(groupId, event.getUserId());
            }, Config.getInstance().getGroupJoinVerifyTimeoutSeconds() * 1000L);

            ScheduledFuture<?> old = pendingVerifys.put(event.getUserId(), task);

            if (old != null) {
                old.cancel(true);
            }
        }
    }

    @EventHandler
    public void onVerifySend(GroupMessageEvent event) {
        long groupId = Config.getInstance().getGroupJoinVerifyGroupId();

        if (event.getGroupId() != groupId) return;
        if (event.getUserId() == event.getSelfId()) return;

        if (event.getRawMessage().trim()
                .equalsIgnoreCase(Config.getInstance().getGroupJoinVerifyAnswer())) {

            ScheduledFuture<?> future = pendingVerifys.remove(event.getUserId());

            if (future != null) {

                future.cancel(true);

                GroupMessage.chatMessage(event.getUserId(), groupId, "验证成功！欢迎加入！", true);
            }
        }
    }

    private static void kick(long groupId, long userId) {
        Map<String, Object> request = Map.of(
                "group_id", groupId,
                "user_id", List.of(userId),
                "reject_add_request", false
        );
        PostRequest.sendPost(RequestType.SET_GROUP_KICK_MEMBER, request);
    }
}