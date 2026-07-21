package top.yzljc.atribot.database;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

/**
 * 图源投稿数据对象
 *
 * <p>图片本体不落本地，仅保留远端图床返回的 uuid 与原始 CDN 链接，
 * 审核状态见 {@link ImageReviewStatus}。
 *
 * @Author YZ_Ljc_
 * @ClassName ImageSourceDTO
 * @Created_at 2026/07/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageSourceDTO {
    private String id;
    /** 远端图床返回的图片 uuid，是引用图片的唯一凭据 */
    private String imageUuid;
    private String platform;
    private String uploaderId;
    private String uploaderName;
    private String groupId;
    /** 平台原始 CDN 链接，带 rkey 会过期，仅作兜底展示 */
    private String sourceUrl;
    private String fileName;
    private String contentType;
    private int width;
    private int height;
    private long fileSize;
    /** 远端转储/压缩后的图片宽度 */
    private int processedWidth;
    /** 远端转储/压缩后的图片高度 */
    private int processedHeight;
    /** 远端转储/压缩后的图片大小 */
    private long processedFileSize;
    /** 图片内容 hash，用于查重 */
    private String hash;
    /** PENDING / REVIEWED / DENIED */
    private String reviewStatus;
    private String reviewer;
    private String reviewRemark;
    private Timestamp reviewTime;
    private Timestamp createTime;
    /** 审核结果是否已送达投稿人 */
    @JsonProperty("isNotified")
    private boolean isNotified;
}
