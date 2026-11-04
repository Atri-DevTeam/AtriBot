package top.yzljc.atricore.chat.official.media;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * 已上传的 QQ 富媒体资源，不负责上传文件
 *
 * @param fileInfo 上传接口返回的 file_info，按原值传入
 * @Author YZ_Ljc_
 * @ClassName Media
 * @Created_at 2026/11/04
 * @Project AtriMeow
 * @Package top.yzljc.atricore.chat.official
 */
public record Media(@JsonProperty("file_info") String fileInfo) {
    public Media {
        Objects.requireNonNull(fileInfo, "file_info 不能为空");
    }
}
