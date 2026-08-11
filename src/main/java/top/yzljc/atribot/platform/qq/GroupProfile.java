package top.yzljc.atribot.platform.qq;

import lombok.AllArgsConstructor;
import lombok.Getter;
import top.yzljc.atribot.platform.PlatformRole;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupProfile
 * @Created_at 2026/08/10
 * @Project AtriMeow
 * @Package top.yzljc.atribot.platform.official
 */
public record GroupProfile(
        /* 机器人群内状态 */
        String memberOpenId,
        String joinTime,
        boolean allowProactiveMsg,
        Scope receiveMsgSetting,
        PlatformRole memberRole,

        /* 群信息 */
        String groupId,
        String groupName,
        String groupFingerMemo,
        String groupClassText,
        List<String> groupTags,
        int groupMemberNum


) {
    @Getter
    @AllArgsConstructor
    public enum Scope {
        ONLY_MENTION("only_mention"),
        MENTION_AND_CONTEXT("mention_and_context"),
        ALL("all");

        private final String jsonValue;

        public static Scope from(String jsonValue) {
            for (Scope scope : Scope.values()) {
                if (scope.jsonValue.equals(jsonValue)) {
                    return scope;
                }
            }
            return ONLY_MENTION;
        }
    }
}
