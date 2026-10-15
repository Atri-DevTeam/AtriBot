package top.yzljc.atricore.chat;

import lombok.Getter;

/**
 * 图片来源的数据类型
 *
 * @Author YZ_Ljc_
 * @ClassName ImageType
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat
 */
@Getter
public enum ImageType {
    URL("url"),
    BASE64("file_data");

    private final String dataKey;

    ImageType(String dataKey) {
        this.dataKey = dataKey;
    }
}