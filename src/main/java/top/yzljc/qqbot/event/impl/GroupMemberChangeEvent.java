package top.yzljc.qqbot.event.impl;

import lombok.Getter;
import top.yzljc.qqbot.event.Event;
import top.yzljc.qqbot.event.Group;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupMemberChangeEvent
 * @Created_at 2026/04/03
 * @Project Yzljc-QQ-Bot
 * @Package top.yzljc.qqbot.event
 */
@Getter
public class GroupMemberChangeEvent extends Event {
    private final long time;
    private final long selfId;
    private final long groupId;
    private final long userId;
    private final long operatorId;
    private String subType;
    private final String noticeType;
    private final GroupMemberChangeType operateType;
    private final Group group;

    public GroupMemberChangeEvent(long time, long selfId, long groupId, long userId, long operatorId, String subType, String noticeType) {
        this.time = time;
        this.selfId = selfId;
        this.groupId = groupId;
        this.userId = userId;
        this.operatorId = operatorId;
        this.subType = subType;
        this.noticeType = noticeType;
        if (this.subType.equals("invite") && this.userId == this.selfId) {
            this.subType = "invite_me";
        }
        this.operateType = GroupMemberChangeType.fromNoticeType(this.subType);
        this.group = new Group(groupId);
    }
}

