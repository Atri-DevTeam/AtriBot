package top.yzljc.atribot.function.utils.official.minecraft;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.function.minecraft.MinecraftUserData;
import top.yzljc.atribot.utils.socket.BindResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author YZ_Ljc_
 * @ClassName BindMinecraft
 * @Created_at 2026/05/09
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions.impl
 */
@Slf4j
@Deprecated(since = "3.2.2")
public class MinecraftBind {

    private static final Map<String, MinecraftUserData> cache = new ConcurrentHashMap<>();

    public static void init() {
        String sql = "CREATE TABLE IF NOT EXISTS `qq_minecraft` (" +
                "  `member_openId` VARCHAR(256) NOT NULL," +
                "  `uuid` VARCHAR(36) NOT NULL," +
                "  `possible_qq` BIGINT NULL," +
                "  `group_openId` VARCHAR(256) NULL," +
                "  PRIMARY KEY (`member_openId`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.execute();
        } catch (Exception e) {
            log.error("初始化绑定数据库表失败: {}", e.getMessage());
        }

        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement("SELECT uuid, member_openId, possible_qq, group_openId FROM qq_minecraft")) {
            var rs = stmt.executeQuery();
            while (rs.next()) {
                String uuid = rs.getString("uuid");
                String memberOpenId = rs.getString("member_openId");
                long possibleQqNum = rs.getLong("possible_qq");
                if (rs.wasNull()) possibleQqNum = -1;
                String groupOpenId = rs.getString("group_openId");
                cache.put(memberOpenId, new MinecraftUserData(uuid, memberOpenId, possibleQqNum, groupOpenId));
            }
        } catch (Exception e) {
            log.error("加载绑定数据到缓存失败: {}", e.getMessage());
        }
    }

    public static MinecraftUserData getDataByOpenId(String openId) {
        if (cache.isEmpty() || !cache.containsKey(openId)) return new MinecraftUserData(null, "-1", -1, null);
        return cache.get(openId);
    }

    public static BindResponse bindAccount(String openId, long possibleQqNum, String verifyCode, String groupOpenId) {
        BindResponse response = Atri.getMinecraftSocket().sendRequest(verifyCode);

        if (response == null || response.code() != 200) {
            return response;
        }

        String uuid = response.uuid();
        String sql = "INSERT INTO qq_minecraft (uuid, member_openId, possible_qq, group_openId) VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE member_openId = VALUES(member_openId), possible_qq = VALUES(possible_qq), group_openId = VALUES(group_openId)";
        try (var conn = DatabaseManager.getConnection();
             var stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, uuid);
            stmt.setString(2, openId);
            stmt.setLong(3, possibleQqNum);
            stmt.setString(4, groupOpenId);
            stmt.executeUpdate();
            cache.put(openId, new MinecraftUserData(uuid, openId, possibleQqNum, groupOpenId));
            return response;
        } catch (Exception e) {
            log.error("绑定账号失败: {}", e.getMessage());
            return response;
        }
    }
}