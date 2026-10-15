package top.yzljc.atricore.event.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName RequestSource
 * @Created_at 2026/08/05
 * @Project AtriMeow
 * @Package top.yzljc.atricore.event.impl
 */
@Getter
@AllArgsConstructor
public enum RequestSource {

    INVITED("invited"),
    SELF_APPLY("self_apply");

    private final String applySourceName;

    public static RequestSource from(String applySourceName) {
        for (RequestSource source : RequestSource.values()) {
            if (source.getApplySourceName().equals(applySourceName)) {
                return source;
            }
        }
        return null;
    }
}
