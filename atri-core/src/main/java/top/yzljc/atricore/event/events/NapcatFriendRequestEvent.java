package top.yzljc.atricore.event.events;

import lombok.Getter;
import top.yzljc.atricore.chat.napcat.PrivateMessage;
import top.yzljc.atricore.event.Event;

/**
 * 收到 {@code request_type = friend} 的好友申请时触发
 *
 * @Author YZ_Ljc_
 * @ClassName NapcatFriendRequestEvent
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
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