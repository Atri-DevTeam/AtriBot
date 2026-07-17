package top.yzljc.atribot.event.impl;

import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupMemberChangeType
 * @Created_at 2026/04/03
 * @Project Yzljc-QQ-Bot
 * @Package top.yzljc.qqbot.event.impl
 */
@Getter
public enum GroupMemberChangeType {
    MEMBER_APPROVE_JOIN("approve"),
    MEMBER_ACTIVE_LEAVE("leave"),
    MEMBER_KICK_LEAVE("kick"),
    MEMBER_INVITE_JOIN("invite"),
    ME_PASSIVE_INVITE("invite_me"),
    ME_PASSIVE_KICK("kick_me");

    private final String noticeType;

    GroupMemberChangeType(String noticeType) {
        this.noticeType = noticeType;
    }

    public static GroupMemberChangeType fromNoticeType(String noticeType) {
        for (GroupMemberChangeType type : GroupMemberChangeType.values()) {
            if (type.getNoticeType().equals(noticeType)) {
                return type;
            }
        }
        return null;
    }
}