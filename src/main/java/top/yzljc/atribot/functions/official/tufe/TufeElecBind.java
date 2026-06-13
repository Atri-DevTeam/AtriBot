package top.yzljc.atribot.functions.official.tufe;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.util.ArrayList;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName TufeElecBind
 * @Created_at 2026/05/30
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official.tufe
 */
@Slf4j
public class TufeElecBind {

    public static void init() {
        String sql = "CREATE TABLE IF NOT EXISTS `tufe_elec_bind` (" +
                "  `id` BIGINT AUTO_INCREMENT," +
                "  `user_open_id` VARCHAR(255) NOT NULL," +
                "  `room_num` BIGINT NOT NULL," +
                "  `school_region` INT DEFAULT 0," +
                "  `type` INT DEFAULT 0," +
                "  PRIMARY KEY (`id`)," +
                "  UNIQUE KEY `uk_user_type` (`user_open_id`, `type`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.execute();
        } catch (Exception e) {
            log.error("初始化tufe-elec绑定数据库表失败", e);
        }
    }

    public static ElecDTO getDataByOpenIdAndType(String userOpenId, int type) {
        String sql = "SELECT `room_num`, `school_region`, `type` FROM `tufe_elec_bind` WHERE `user_open_id` = ? AND `type` = ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            ps.setInt(2, type);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return new ElecDTO(
                            rs.getLong("room_num"),
                            rs.getInt("school_region"),
                            rs.getInt("type")
                    );
                }
            }
        } catch (Exception e) {
            log.error("查询tufe-elec绑定数据失败", e);
        }
        return null;
    }

    public static List<ElecDTO> getAllDataByOpenId(String userOpenId) {
        String sql = "SELECT `room_num`, `school_region`, `type` FROM `tufe_elec_bind` WHERE `user_open_id` = ? ORDER BY `type`";
        List<ElecDTO> list = new ArrayList<>();
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new ElecDTO(
                            rs.getLong("room_num"),
                            rs.getInt("school_region"),
                            rs.getInt("type")
                    ));
                }
            }
        } catch (Exception e) {
            log.error("查询tufe-elec所有绑定数据失败", e);
        }
        return list;
    }

    public static boolean bind(String userOpenId, long roomNum, int schoolRegion, int type) {
        String sql = "INSERT INTO `tufe_elec_bind` (`user_open_id`, `room_num`, `school_region`, `type`) " +
                "VALUES (?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE `room_num` = VALUES(`room_num`), `school_region` = VALUES(`school_region`)";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            ps.setLong(2, roomNum);
            ps.setInt(3, schoolRegion);
            ps.setInt(4, type);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("绑定tufe-elec数据失败", e);
            return false;
        }
    }

    public static boolean unbind(String userOpenId, int type) {
        String sql = "DELETE FROM `tufe_elec_bind` WHERE `user_open_id` = ? AND `type` = ?";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql)) {
            ps.setString(1, userOpenId);
            ps.setInt(2, type);
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("解绑tufe-elec数据失败", e);
            return false;
        }
    }

    public static int getBindCount(String userOpenId) {
        return getAllDataByOpenId(userOpenId).size();
    }
}