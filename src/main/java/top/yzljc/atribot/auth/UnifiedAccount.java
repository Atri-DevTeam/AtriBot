package top.yzljc.atribot.auth;

import top.yzljc.atribot.auth.official.UnifiedRole;

import java.util.List;
import java.util.UUID;

/**
 * @Author YZ_Ljc_
 * @ClassName UnifiedAccount
 * @Created_at 2026/08/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.auth
 */
public record UnifiedAccount(
        UUID uuid,
        String username,
        String qqUserOpenId,
        String qqUserUin,
        String minecraftUuid,
        UnifiedRole role,
        List<String> permissions,
        AccountStatus status,
        String createTime,
        String lastUpdateTime
) {
}