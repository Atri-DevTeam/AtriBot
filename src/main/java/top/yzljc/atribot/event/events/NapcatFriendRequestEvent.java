package top.yzljc.atribot.event.events;

import lombok.Getter;
import top.yzljc.atribot.chat.napcat.PrivateMessage;
import top.yzljc.atribot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName FriendRequestEvent
 * @Created_at 2026/04/03
 * @Project Yzljc-QQ-Bot
 * @Package top.yzljc.qqbot.event.impl
 */
@Getter
public class NapcatFriendRequestEvent extends Event {
    private final String time;
    private final String selfId;
    private final String userId;
    private final String flag;

    public NapcatFriendRequestEvent(String time, String selfId, String userId, String flag) {
        this.time = time;
        this.selfId = selfId;
        this.userId = userId;
        this.flag = flag;
    }

    public void sendMessage(String message) {
        PrivateMessage.chatMessage(userId, message);
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