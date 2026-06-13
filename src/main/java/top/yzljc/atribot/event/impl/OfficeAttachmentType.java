package top.yzljc.atribot.event.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName OfficeAttachmentType
 * @Created_at 2026/05/06
 * @Project AtriBot
 * @Package top.yzljc.qqbot.event.impl
 */
@Getter
@AllArgsConstructor
public enum OfficeAttachmentType {
    IMG_JPEG("image/jpeg"),
    IMG_PNG("image/png"),
    IMG_GIF("image/gif"),
    VIDEO_MP4("video/mp4"),
    FILE("file"),
    VOICE("voice");

    private final String typeKey;

    public static OfficeAttachmentType fromTypeKey(String typeKey) {
        for (OfficeAttachmentType type : values()) {
            if (type.typeKey.equals(typeKey)) {
                return type;
            }
        }
        return null;
    }
}
