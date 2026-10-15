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
    ONLY_ADMIN(5),
    // 2026-10-14 发现：已屏蔽，love from 工单小助手，之后通过穷举顺手找到了 99 屏蔽失败
    IGNORED(66),
    IGNORE_FAILED(99);

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