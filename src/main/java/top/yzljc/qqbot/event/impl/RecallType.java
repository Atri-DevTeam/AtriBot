package top.yzljc.qqbot.event.impl;

import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName RecallType
 * @Created_at 2026/04/04
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
 */
@Getter
public enum RecallType {
    GROUP("group_recall"),
    PRIVATE("friend_recall");

    private final String type;
    RecallType(String type) {
        this.type = type;
    }

    public static RecallType fromRecallType(String recallType) {
        for (RecallType t : RecallType.values()) {
            if (t.getType().equals(recallType)) {
                return t;
            }
        }
        return null;
    }
}
