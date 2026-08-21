package top.yzljc.atribot.event.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Author YZ_Ljc_
 * @ClassName FriendAddScene
 * @Created_at 2026/08/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.event.impl
 */
@Getter
@AllArgsConstructor
public enum FriendAddScene {
    /* 加好友场景值。1000=缺省默认, 1001=网络搜索（全部tab）, 1002=网络搜索（机器人tab）,
     * 1003=群场景, 1004=空间场景, 2001=站内分享资料页, 2002=站外分享资料页,
     * 2003=开发者生成的分享链接（站内）, 2004=开发者生成的分享链接（站外） */
    DEFAULT(1000, "缺省默认"),
    NETWORK_SEARCH_ALL(1001, "网络搜索（全部tab）"),
    NETWORK_SEARCH_BOT(1002, "网络搜索（机器人tab）"),
    GROUP(1003, "群场景"),
    SPACE(1004, "空间场景"),
    SHARE_PROFILE_INTERNAL(2001, "站内分享资料页"),
    SHARE_PROFILE_EXTERNAL(2002, "站外分享资料页"),
    SHARE_LINK_INTERNAL(2003, "开发者生成的分享链接（站内）"),
    SHARE_LINK_EXTERNAL(2004, "开发者生成的分享链接（站外）");

    private final int code;
    private final String tip;

    public static FriendAddScene fromCode(int code) {
        for (FriendAddScene scene : FriendAddScene.values()) {
            if (scene.code == code) {
                return scene;
            }
        }
        return DEFAULT;
    }
}