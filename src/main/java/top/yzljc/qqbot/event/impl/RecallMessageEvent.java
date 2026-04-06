package top.yzljc.qqbot.event.impl;

import lombok.Getter;
import top.yzljc.qqbot.event.Event;

/**
 * @Author YZ_Ljc_
 * @ClassName RecallMessageEvent
 * @Created_at 2026/04/04
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
 */
@Getter
public class RecallMessageEvent extends Event {
    private final long time;
    private final long selfId;
    private final long groupId;
    private final long userId;
    private final long operatorId;
    private final long messageId;
    private final RecallType type;

    public RecallMessageEvent(long time, long selfId, long groupId, long userId,
                              long operatorId, long messageId, String rawType) {
        this.time = time;
        this.selfId = selfId;
        this.groupId = groupId;
        this.userId = userId;
        this.operatorId = operatorId;
        this.messageId = messageId;
        this.type = RecallType.fromRecallType(rawType);
    }
}