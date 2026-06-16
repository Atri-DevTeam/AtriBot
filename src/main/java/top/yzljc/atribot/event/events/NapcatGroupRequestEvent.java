package top.yzljc.atribot.event.events;

import lombok.Getter;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupRequestEvent
 * @Created_at 2026/04/03
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event
 */
@Getter
public class NapcatGroupRequestEvent extends Event {
    private final String time;
    private final String selfId;
    private final String groupId;
    private final String userId;
    private final String flag;
    private final String subType;
    private final String comment;

    public NapcatGroupRequestEvent(String time, String selfId, String groupId, String userId, String flag, String subType, String comment) {
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