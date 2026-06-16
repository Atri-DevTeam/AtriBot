package top.yzljc.atribot.event.events;

import lombok.Getter;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.event.impl.RecallType;

/**
 * @Author YZ_Ljc_
 * @ClassName RecallMessageEvent
 * @Created_at 2026/04/04
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
 */
@Getter
public class NapcatRecallMessageEvent extends Event {
    private final String time;
    private final String selfId;
    private final String groupId;
    private final String userId;
    private final String operatorId;
    private final String messageId;
    private final RecallType type;

    public NapcatRecallMessageEvent(String time, String selfId, String groupId, String userId, String operatorId, String messageId, String type) {
        this.time = time;
        this.selfId = selfId;
        this.groupId = groupId;
        this.userId = userId;
        this.operatorId = operatorId;
        this.messageId = messageId;
        this.type = RecallType.fromRecallType(type);
    }
}