package top.yzljc.atricore.auth;

import top.yzljc.atricore.auth.official.UnifiedRole;

import java.util.List;
import java.util.UUID;

/**
 * 运行服务返回的账号数据，不负责持久化或账号绑定
 *
 * @Author YZ_Ljc_
 * @ClassName AtriAccount
 * @Created_at 2026/08/13
 * @Project AtriMeow
 * @Package top.yzljc.atricore.auth
 */
public record AtriAccount(
        UUID uuid,
        String username,
        String qqUserOpenId,
        String qqUserUin,
        String minecraftUuid,
        UnifiedRole role,
        List<String> permissions,
        AccountStatus status,
        String createTime,
        String lastUpdateTime,
        String qqConnectOpenId
) {
    public AtriAccount(UUID uuid, String username, String qqUserOpenId, String qqUserUin,
                       String minecraftUuid, UnifiedRole role, List<String> permissions,
                       AccountStatus status, String createTime, String lastUpdateTime) {
        this(uuid, username, qqUserOpenId, qqUserUin, minecraftUuid, role, permissions, status,
                createTime, lastUpdateTime, null);
    }
}