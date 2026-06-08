package top.yzljc.atribot.event.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName AnswerCode
 * @Created_at 2026/06/10
 * @Project AtriBot
 * @Package top.yzljc.atribot.event.impl
 */
@Getter
@AllArgsConstructor
public enum AnswerCode {
    SUCCESS(0),
    FAIL(1),
    TOO_FAST(2),
    REPEAT(3),
    NO_PERMISSION(4),
    ONLY_ADMIN(5);

    private final int code;

    public static AnswerCode fromCode(int code) {
        for (AnswerCode answerCode : values()) {
            if (answerCode.code == code) {
                return answerCode;
            }
        }
        return FAIL;
    }
}
