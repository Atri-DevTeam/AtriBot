package top.yzljc.atribot.connect;

import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.database.repo.BackgroundRepository;
import top.yzljc.atribot.function.background.BackgroundSettings;
import top.yzljc.atribot.utils.HashUtils;

import java.nio.charset.StandardCharsets;

/** 按统一账号关联读取背景信息，不创建账号，也不请求远端图片 */
public final class ConnectBackgroundService {
    private ConnectBackgroundService() {}

    public record Background(String owner, String id) {}
    public record Submission(String id, String status, int width, int height, long size, long createdAt, long reviewedAt) {}
    public record Info(String u_id, String b_id, Background background, Submission submission) {}

    /** 未找到账号时返回 null，已存在但未设置背景时 background 为 null */
    public static Info query(ConnectIdentity identity) throws Exception {
        String uuid;
        String botId;
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement("SELECT uuid,qq_user_open_id FROM unified_account WHERE " + identity.column() + "=?")) {
            ps.setString(1, identity.value());
            try (var rows = ps.executeQuery()) {
                if (!rows.next()) return null;
                uuid = rows.getString(1);
                botId = rows.getString(2);
            }
        }
        return info(uuid, botId, botId == null || botId.isBlank() ? BackgroundSettings.EMPTY : BackgroundRepository.get(botId));
    }

    static Info info(String uuid, String botId, BackgroundSettings settings) {
        Background background = settings.activeId() == null ? null : new Background(
                HashUtils.sha256Hex(botId.getBytes(StandardCharsets.UTF_8)), settings.activeId());
        var s = settings.submission();
        Submission submission = s == null ? null : new Submission(s.id(), s.status(), s.width(), s.height(), s.size(), s.createdAt(), s.reviewedAt());
        return new Info(uuid, botId, background, submission);
    }
}
