package top.yzljc.atribot.miniapp.service;

import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.service.minecraft.MinecraftModerationClient;
import top.yzljc.atribot.utils.tools.FetchMinecraftProfile;

import java.net.URI;
import java.sql.SQLException;
import java.util.Base64;

/**
 * 读取当前用户绑定的 Minecraft 审核资料
 *
 * @Author YZ_Ljc_
 * @ClassName MiniappMinecraftService
 * @Created_at 2026/10/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.miniapp.service
 */
public final class MiniappMinecraftService {
    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(MiniappMinecraftService.class);

    public record Profile(String uuid, String username, String avatarUrl) {}

    /**
     * @param userId 已验证会话中的官机用户标识
     * @return 审核后的名字与头像，未绑定时资料为空
     * @throws SQLException 绑定记录读取失败
     */
    public Profile load(String userId) throws SQLException {
        String uuid;
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(
                "SELECT minecraft_uuid FROM unified_account WHERE qq_user_open_id=? LIMIT 1")) {
            ps.setString(1, userId);
            ps.setQueryTimeout(5);
            try (var rows = ps.executeQuery()) {
                if (!rows.next()) return new Profile(null, null, null);
                uuid = rows.getString(1);
            }
        }
        if (uuid == null || uuid.isBlank()) return new Profile(null, null, null);
        String name = "Minecraft 玩家";
        String avatar = null;
        try {
            var reviewed = FetchMinecraftProfile.getPlayerProfile(uuid);
            if (reviewed != null) {
                if (reviewed.username() != null && !reviewed.username().isBlank()) name = reviewed.username();
                if (reviewed.avatarUrl() != null) {
                    String path = URI.create(reviewed.avatarUrl()).getPath();
                    String skinId = path.substring(path.lastIndexOf('/') + 1);
                    byte[] bytes = MinecraftModerationClient.avatar(skinId);
                    if (bytes.length > 0 && bytes.length <= 1048576)
                        avatar = "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
                }
            }
        } catch (RuntimeException e) {
            LOG.warn("个人中心 Minecraft 审核资料读取失败，UUID: {}", uuid, e);
        }
        return new Profile(uuid, name, avatar);
    }
}
