package top.yzljc.atricore.event.events;

import lombok.Getter;
import top.yzljc.atricore.event.Event;
import top.yzljc.atricore.event.impl.RecallType;

/**
 * 收到 {@code group_recall} 或 {@code friend_recall} 通知时，按配置范围触发
 *
 * @Author YZ_Ljc_
 * @ClassName NapcatRecallMessageEvent
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
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