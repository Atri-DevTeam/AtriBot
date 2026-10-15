package top.yzljc.atricore.event.events;

import lombok.Getter;
import top.yzljc.atricore.chat.napcat.GroupMessage;
import top.yzljc.atricore.event.Event;
import top.yzljc.atricore.event.impl.GroupMemberChangeType;

import java.util.Objects;

/**
 * 用于接收群成员变动通知
 *
 * @Author YZ_Ljc_
 * @ClassName NapcatGroupMemberChangeEvent
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.events
 */
@Getter
public class NapcatGroupMemberChangeEvent extends Event {
    private final String time;
    private final String selfId;
    private final String groupId;
    private final String userId;
    private final String operatorId;
    private String subType;
    private final GroupMemberChangeType operateType;

    public NapcatGroupMemberChangeEvent(String time, String selfId, String groupId, String userId, String operatorId, String subType) {
        this.time = time;
        this.selfId = selfId;
        this.groupId = groupId;
        this.userId = userId;
        this.operatorId = operatorId;
        this.subType = subType;
        if (this.subType.equals("invite") && Objects.equals(this.userId, this.selfId)) {
            this.subType = "invite_me";
        }
        this.operateType = GroupMemberChangeType.fromNoticeType(this.subType);
    }

    public String sendMessage(String content) {
        return GroupMessage.chatMessage(this.groupId, content);
    }
}

