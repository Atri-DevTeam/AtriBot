package top.yzljc.atribot.event.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupJoinApplySource
 * @Created_at 2026/08/05
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.impl
 */
@Getter
@AllArgsConstructor
public enum GroupJoinRequestSource {

    INVITED("invited"),
    SELF_APPLY("self_apply");

    private final String applySourceName;

    public static GroupJoinRequestSource from(String applySourceName) {
        for (GroupJoinRequestSource source : GroupJoinRequestSource.values()) {
            if (source.getApplySourceName().equals(applySourceName)) {
                return source;
            }
        }
        return null;
    }
}
