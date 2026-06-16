package top.yzljc.atribot.event.events;

import lombok.Getter;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.event.Event;
import top.yzljc.atribot.event.impl.GroupMemberChangeType;

import java.util.Objects;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupMemberChangeEvent
 * @Created_at 2026/04/03
 * @Project Yzljc-QQ-Bot
 * @Package top.yzljc.qqbot.event
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

