package top.yzljc.atribot.function.impl.pic;

import lombok.Getter;

/**
 * 图源审核状态。
 *
 * <p>以字符串落库而非序号，方便后续扩展新状态而不影响存量数据。
 *
 * @Author YZ_Ljc_
 * @ClassName ImageReviewStatus
 * @Created_at 2026/07/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database
 */
@Getter
public enum ImageReviewStatus {
    PENDING("未审核"),
    REVIEWED("已通过"),
    DENIED("已拒绝");

    private final String display;

    ImageReviewStatus(String display) {
        this.display = display;
    }

    public static ImageReviewStatus of(String raw) {
        if (raw == null) return PENDING;
        for (ImageReviewStatus status : values()) {
            if (status.name().equalsIgnoreCase(raw.trim())) {
                return status;
            }
        }
        return PENDING;
    }

    public static boolean isValid(String raw) {
        if (raw == null) return false;
        for (ImageReviewStatus status : values()) {
            if (status.name().equalsIgnoreCase(raw.trim())) {
                return true;
            }
        }
        return false;
    }
}
