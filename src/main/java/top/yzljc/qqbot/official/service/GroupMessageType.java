package top.yzljc.qqbot.official.service;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupMessageType
 * @Created_at 2026/05/06
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official
 */
@Getter
@AllArgsConstructor
public enum GroupMessageType {
    TEXT(0, "纯文本"),
    MARKDOWN(2, "Markdown"),
    ARK(3, "Ark"),
    EMBED(4, "Embed"),
    MEDIA(7, "富媒体");

    private final int value;
    private final String desc;
}
