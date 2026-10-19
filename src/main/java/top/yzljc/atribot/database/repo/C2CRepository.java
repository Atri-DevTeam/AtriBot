package top.yzljc.atribot.database.repo;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.database.DatabaseManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName C2CRepository
 * @Created_at 2026/06/15
 * @Project AtriBot
 * @Package top.yzljc.atribot.database.repo
 */
@Slf4j
public class C2CRepository {
    public static void initTable() {
        try (var con = DatabaseManager.getConnection(); var statement = con.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS official_users (
                      user_openId VARCHAR(256) NOT NULL,
                      c2c_push BOOLEAN NOT NULL DEFAULT TRUE,
                      PRIMARY KEY (user_openId)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        } catch (SQLException e) {
            throw new IllegalStateException("初始化官机私聊用户表失败", e);
        }
    }

    public static List<UserRow> loadAll() {
        var users = new ArrayList<UserRow>();
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement("SELECT user_openId, c2c_push FROM official_users");
             var rows = ps.executeQuery()) {
            while (rows.next()) users.add(new UserRow(rows.getString(1), rows.getBoolean(2)));
        } catch (SQLException e) {
            throw new IllegalStateException("加载官机私聊用户失败", e);
        }
        return users;
    }

    public static boolean registerUser(String userOpenId) {
        try (var con = DatabaseManager.getConnection()) {
            ensureUser(con, userOpenId);
            return true;
        } catch (SQLException e) {
            log.error("注册私聊用户 {} 失败", userOpenId, e);
            return false;
        }
    }

    /**
     * 确保私聊用户记录存在，保留已有的主动消息授权
     *
     * @param con 调用方数据库连接
     * @param userOpenId QQ 用户 OpenID
     * @throws SQLException 记录创建失败
     */
    public static void ensureUser(Connection con, String userOpenId) throws SQLException {
        try (var ps = con.prepareStatement("INSERT INTO official_users (user_openId) VALUES (?)"
                + " ON DUPLICATE KEY UPDATE user_openId = user_openId")) {
            ps.setString(1, userOpenId);
            ps.executeUpdate();
        }
    }

    public static boolean setC2CPush(String userOpenId, boolean c2cPush) {
        try (var con = DatabaseManager.getConnection()) {
            setC2CPush(con, userOpenId, c2cPush);
            return true;
        } catch (SQLException e) {
            log.error("保存私聊用户 {} 的主动消息授权失败", userOpenId, e);
            return false;
        }
    }

    /**
     * 在调用方事务内更新已有私聊用户的授权，不创建私聊记录
     *
     * @param con 调用方事务连接
     * @param userOpenId QQ 用户 OpenID
     * @param c2cPush 是否允许主动消息
     * @throws SQLException 保存失败
     */
    public static void setC2CPush(Connection con, String userOpenId, boolean c2cPush) throws SQLException {
        try (var ps = con.prepareStatement("UPDATE official_users SET c2c_push = ? WHERE user_openId = ?")) {
            ps.setBoolean(1, c2cPush);
            ps.setString(2, userOpenId);
            ps.executeUpdate();
        }
    }

    /**
     * 删除私聊用户记录并清空功能订阅，保留统一账号及其他业务数据
     *
     * @param userOpenId QQ 用户 OpenID
     * @return 删除语句执行成功时返回 true
     */
    public static boolean delete(String userOpenId) {
        if (userOpenId == null || userOpenId.isBlank()) return false;
        try (var con = DatabaseManager.getConnection()) {
            delete(con, userOpenId);
            return true;
        } catch (SQLException e) {
            log.error("删除私聊用户 {} 的记录失败", userOpenId, e);
            return false;
        }
    }

    static void delete(Connection con, String userOpenId) throws SQLException {
        boolean autoCommit = con.getAutoCommit();
        con.setAutoCommit(false);
        try {
            try (var ps = con.prepareStatement("DELETE FROM official_users WHERE user_openId = ?")) {
                ps.setString(1, userOpenId);
                ps.executeUpdate();
            }
            try (var ps = con.prepareStatement("UPDATE bot_userdata SET functions = NULL WHERE user_id = ?")) {
                ps.setString(1, userOpenId);
                ps.executeUpdate();
            }
            con.commit();
        } catch (SQLException e) {
            try {
                con.rollback();
            } catch (SQLException rollbackError) {
                e.addSuppressed(rollbackError);
            }
            throw e;
        } finally {
            con.setAutoCommit(autoCommit);
        }
    }

    public record UserRow(String userOpenId, boolean c2cPush) {}
}
