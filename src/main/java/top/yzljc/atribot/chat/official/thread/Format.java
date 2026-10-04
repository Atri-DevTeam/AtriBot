package top.yzljc.atribot.chat.official.thread;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName Format
 * @Created_at 2026/10/04
 * @Project AtriMeow
 * @Package top.yzljc.atribot.chat.official.thread
 */
@Getter
@AllArgsConstructor
public enum Format {
    FORMAT_TEXT(1, "普通文本"),
    FORMAT_HTML(2, "HTML"),
    FORMAT_MARKDOWN(3, "Markdown"),
    FORMAT_JSON(4, "JSON（content参数可参照RichText结构）");

    private final int value;
    private final String description;
}
