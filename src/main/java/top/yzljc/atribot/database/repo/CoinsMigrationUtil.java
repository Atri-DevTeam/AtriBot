package top.yzljc.atribot.database.repo;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.database.DatabaseManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 一次性把 check_in_total.total_coins 迁移到 user_loots.coins。
 * 迁移完成后写入 {@link Properties#COINS_MIGRATION_FLAG} 标记文件，防止重复执行覆盖新数据。
 * 与迁移逻辑完全独立，确认迁移完成后可安全删除本类及其在 Atri.java 中的调用处。
 *
 * @Author YZ_Ljc_
 * @ClassName CoinsMigrationUtil
 * @Created_at 2026/07/31
 * @Project AtriBot
 * @Package top.yzljc.atribot.database.repo
 */
@Slf4j
public class CoinsMigrationUtil {

    public static void migrateIfNeeded() {
        Path flag = Path.of(Properties.COINS_MIGRATION_FLAG);
        if (Files.exists(flag)) {
            return;
        }

        int migrated = 0;
        String sql = "SELECT `user_open_id`, `total_coins` FROM `check_in_total`";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            String upsertSql = "INSERT INTO `user_loots` (`user_id`, `loots`, `coins`) VALUES (?, '[]', ?) " +
                    "ON DUPLICATE KEY UPDATE `coins` = VALUES(`coins`)";
            try (var upsertPs = con.prepareStatement(upsertSql)) {
                while (rs.next()) {
                    upsertPs.setString(1, rs.getString("user_open_id"));
                    upsertPs.setInt(2, rs.getInt("total_coins"));
                    upsertPs.executeUpdate();
                    migrated++;
                }
            }
        } catch (Exception e) {
            log.error("金粒迁移失败，将在下次启动时重试", e);
            return;
        }

        try {
            if (flag.getParent() != null) {
                Files.createDirectories(flag.getParent());
            }
            Files.createFile(flag);
        } catch (IOException e) {
            log.error("写入金粒迁移标记文件失败: {}", flag, e);
        }

        log.info("金粒迁移完成，共迁移 {} 名用户的 total_coins -> user_loots.coins", migrated);
    }
}
