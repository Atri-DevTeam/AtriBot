package top.yzljc.atribot.platform.official;

import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName FileType
 * @Created_at 2026/07/24
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.official
 *
 * QQ 官方 API 富媒体文件类型
 */
@Getter
public enum FileType {

    IMAGE(1, "图片"),
    VIDEO(2, "视频"),
    AUDIO(3, "语音"),
    FILE(4, "文件");

    private final int value;
    private final String label;

    FileType(int value, String label) {
        this.value = value;
        this.label = label;
    }
}
