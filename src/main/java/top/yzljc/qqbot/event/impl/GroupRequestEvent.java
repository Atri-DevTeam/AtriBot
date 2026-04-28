package top.yzljc.qqbot.event.impl;

import lombok.Getter;
import top.yzljc.qqbot.chat.GroupMessage;
import top.yzljc.qqbot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupRequestEvent
 * @Created_at 2026/04/03
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event
 */
@Getter
public class GroupRequestEvent extends Event {
    private final long time;
    private final long selfId;
    private final long groupId;
    private final long userId;
    private final String flag;
    private final String subType;
    private final String comment;

    public GroupRequestEvent(long time, long selfId, long groupId, long userId, String flag, String subType, String comment) {
        this.time = time;
        this.selfId = selfId;
        this.groupId = groupId;
        this.userId = userId;
        this.flag = flag;
        this.subType = subType;
        this.comment = comment;
    }

    public void reject() {
        GroupMessage.handleRequest(false, this.flag, null);
    }

    public void reject(String reason) {
        GroupMessage.handleRequest(false, this.flag, reason);
    }
}