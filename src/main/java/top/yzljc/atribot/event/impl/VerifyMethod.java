package top.yzljc.atribot.event.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName VerifyMethod
 * @Created_at 2026/08/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.impl
 */
@Getter
@AllArgsConstructor
public enum VerifyMethod {
    VERIFY_MESSAGE("verify_message"),
    ADMIN_REVIEW_QA("admin_review_qa");

    private final String methodJson;

    public static VerifyMethod from(String methodJson) {
        for (VerifyMethod method : VerifyMethod.values()) {
            if (method.getMethodJson().equals(methodJson)) {
                return method;
            }
        }
        return null;
    }
}
