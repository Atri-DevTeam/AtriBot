package top.yzljc.qqbot.event.impl;

import lombok.Getter;
import top.yzljc.qqbot.botservice.message.MessageSender;
import top.yzljc.qqbot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName FriendRequestEvent
 * @Created_at 2026/04/03
 * @Project Yzljc-QQ-Bot
 * @Package top.yzljc.qqbot.event.impl
 */
@Getter
public class FriendRequestEvent extends Event {
    private final long time;
    private final long selfId;
    private final long userId;
    private final String flag;

    public FriendRequestEvent(long time, long selfId, long userId, String flag) {
        this.time = time;
        this.selfId = selfId;
        this.userId = userId;
        this.flag = flag;
    }

    public void sendMessage(String message) {
        MessageSender.sendPrivateMessage(userId, message);
    }
}
/*
  {
    "time" : 1775195054,
    "self_id" : 970717559,
    "post_type" : "notice",
    "notice_type" : "friend_add",
    "user_id" : 3614865692
  }
 */