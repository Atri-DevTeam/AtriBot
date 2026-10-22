package top.yzljc.atribot.database.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atricore.auth.AccountStatus;
import top.yzljc.atricore.auth.official.UnifiedRole;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.database.UnifiedAccountDTO;

import java.sql.PreparedStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * @Author YZ_Ljc_
 * @ClassName UnifiedAccountRepository
 * @Created_at 2026/08/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.database.repo
 */
@Slf4j
public class UnifiedAccountRepository {

    private static final String TABLE = "unified_account";

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void init() {
        try (var con = DatabaseManager.getConnection()) {
            init(con);
            log.info("统一账号表初始化完成");
        } catch (Exception e) {
            throw new IllegalStateException("初始化统一账号表失败", e);
        }
    }

    /**
     * 初始化统一账号表
     *
     * @param con 数据库连接，由调用方关闭
     * @throws SQLException 表结构初始化或状态迁移失败
     */
    public static void init(Connection con) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS `" + TABLE + "` (" +
                "  `uuid` CHAR(36) NOT NULL," +
                "  `username` VARCHAR(64) NULL," +
                "  `qq_user_open_id` VARCHAR(128) NULL," +
                "  `qq_user_uin` VARCHAR(64) NULL," +
                "  `qq_connect_open_id` VARCHAR(128) NULL," +
                "  `minecraft_uuid` VARCHAR(64) NULL," +
                "  `role` VARCHAR(32) NOT NULL DEFAULT 'USER'," +
                "  `permissions` TEXT NULL," +
                "  `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE'," +
                "  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  `last_update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "  PRIMARY KEY (`uuid`)," +
                "  UNIQUE KEY `uk_qq_user_open_id` (`qq_user_open_id`)," +
                "  UNIQUE KEY `uk_qq_user_uin` (`qq_user_uin`)," +
                "  UNIQUE KEY `uk_qq_connect_open_id` (`qq_connect_open_id`)," +
                "  KEY `idx_username` (`username`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (var ps = con.prepareStatement(sql)) {
            ps.execute();
        }
        var columns = new java.util.HashSet<String>();
        try (var ps = con.prepareStatement("SELECT * FROM `" + TABLE + "` WHERE 1=0"); var rs = ps.executeQuery()) {
            var metadata = rs.getMetaData();
            for (int i = 1; i <= metadata.getColumnCount(); i++) {
                columns.add(metadata.getColumnName(i).toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (!columns.contains("qq_connect_open_id")) {
            try (var statement = con.createStatement()) {
                statement.execute("ALTER TABLE `" + TABLE + "` ADD COLUMN `qq_connect_open_id` VARCHAR(128) NULL UNIQUE");
            }
        }
//        migrateOfficialUserStatus(con);
//        migrateOfficialUserPermissions(con);
    }

//    /**
//     * 将官机权限迁入统一账号，保留较高的权限组并合并权限节点
//     *
//     * @param con 启动阶段的自动提交连接
//     * @throws SQLException 迁移或删除旧权限列失败
//     */
//    static void migrateOfficialUserPermissions(Connection con) throws SQLException {
//        var columns = BotUserDataRepository.columns(con, "official_users");
//        boolean hasRole = columns.contains("role"), hasPermissions = columns.contains("permissions");
//        if (!hasRole && !hasPermissions) return;
//        if (!con.getAutoCommit()) throw new SQLException("用户权限迁移需要独立的自动提交连接");
//        con.setAutoCommit(false);
//        try {
//            String after = null;
//            while (true) {
//                var batch = new ArrayList<LegacyPermissions>();
//                String sql = "SELECT user_openId, " + (hasRole ? "role" : "'USER'") + ", "
//                        + (hasPermissions ? "permissions" : "''") + " FROM official_users"
//                        + (after == null ? "" : " WHERE user_openId > ?") + " ORDER BY user_openId LIMIT 100";
//                try (var ps = con.prepareStatement(sql)) {
//                    if (after != null) ps.setString(1, after);
//                    try (var rows = ps.executeQuery()) {
//                        while (rows.next()) batch.add(new LegacyPermissions(rows.getString(1), rows.getString(2), rows.getString(3)));
//                    }
//                }
//                if (batch.isEmpty()) break;
//                for (var legacy : batch) {
//                    var account = lockQqUser(con, legacy.openId(), true);
//                    UnifiedRole oldRole;
//                    try { oldRole = UnifiedRole.valueOf(legacy.role().trim().toUpperCase(Locale.ROOT)); }
//                    catch (IllegalArgumentException | NullPointerException e) { throw new SQLException("旧用户权限组无效：" + legacy.openId(), e); }
//                    UnifiedRole current = UnifiedRole.fromString(account.getRole());
//                    UnifiedRole role = current == UnifiedRole.OWNER || oldRole == UnifiedRole.OWNER ? UnifiedRole.OWNER
//                            : current == UnifiedRole.ADMIN || oldRole == UnifiedRole.ADMIN ? UnifiedRole.ADMIN : UnifiedRole.USER;
//                    var permissions = new LinkedHashSet<>(account.getPermissions());
//                    if (legacy.permissions() != null) {
//                        for (String permission : legacy.permissions().split(",")) {
//                            if (!permission.isBlank()) permissions.add(permission.trim());
//                        }
//                    }
//                    writePermissions(con, account, role, permissions);
//                }
//                after = batch.getLast().openId();
//            }
//            con.commit();
//        } catch (SQLException | RuntimeException e) {
//            try { con.rollback(); }
//            catch (SQLException rollback) { e.addSuppressed(rollback); }
//            throw e;
//        } finally {
//            con.setAutoCommit(true);
//        }
//        try (var statement = con.createStatement()) {
//            if (hasRole) statement.executeUpdate("ALTER TABLE official_users DROP COLUMN role");
//            if (hasPermissions) statement.executeUpdate("ALTER TABLE official_users DROP COLUMN permissions");
//        }
//        log.info("官机用户权限已迁入统一账号，已移除 official_users 的权限组和权限列");
//    }
//
//    private record LegacyPermissions(String openId, String role, String permissions) {}

    /**
     * 在调用方事务内设置统一账号权限，账号不存在时创建，不改变状态及身份绑定
     *
     * @param con         已开始事务的连接
     * @param openId      QQ 用户 OpenID
     * @param role        权限组
     * @param permissions 权限节点
     * @return 更新后的统一账号
     * @throws SQLException 查询或保存失败
     */
    public static UnifiedAccountDTO setQqUserPermissions(Connection con, String openId, UnifiedRole role,
                                                         Set<String> permissions) throws SQLException {
        if (con.getAutoCommit()) throw new SQLException("账号权限更新必须在事务中执行");
        if (role == null || permissions == null) throw new IllegalArgumentException("权限组和权限不能为空");
        var account = lockQqUser(con, openId, true);
        return writePermissions(con, account, role, Set.copyOf(permissions)) ? lockQqUser(con, openId, false) : account;
    }

    private static boolean writePermissions(Connection con, UnifiedAccountDTO account, UnifiedRole role,
                                            Set<String> permissions) throws SQLException {
        if (role.name().equals(account.getRole()) && permissions.equals(Set.copyOf(account.getPermissions())))
            return false;
        List<String> sorted = permissions.stream().sorted().toList();
        try (var ps = con.prepareStatement("UPDATE unified_account SET role = ?, permissions = ?, last_update_time = CURRENT_TIMESTAMP WHERE uuid = ?")) {
            ps.setString(1, role.name());
            setNullableString(ps, 2, toJson(sorted));
            ps.setString(3, account.getUuid().toString());
            if (ps.executeUpdate() != 1) throw new SQLException("账号权限未保存");
        }
        return true;
    }

//    /**
//     * 将旧用户限制迁入统一账号，屏蔽优先于拉黑；已有屏蔽或注销状态不降级
//     *
//     * @param con 启动阶段的自动提交连接
//     * @throws SQLException 迁移或删除旧状态列失败
//     */
//    static void migrateOfficialUserStatus(Connection con) throws SQLException {
//        var columns = new java.util.HashSet<String>();
//        try (var rows = con.getMetaData().getColumns(con.getCatalog(), null, "official_users", null)) {
//            while (rows.next()) {
//                if ("official_users".equalsIgnoreCase(rows.getString("TABLE_NAME")))
//                    columns.add(rows.getString("COLUMN_NAME").toLowerCase(java.util.Locale.ROOT));
//            }
//        }
//        boolean blocked = columns.contains("is_blocked"), ignored = columns.contains("is_ignored");
//        if (!blocked && !ignored) return;
//        if (!con.getAutoCommit()) throw new SQLException("账号状态迁移需要独立的自动提交连接");
//        String blockedValue = blocked ? "is_blocked" : "FALSE";
//        String ignoredValue = ignored ? "is_ignored" : "FALSE";
//        long migrated = 0;
//        con.setAutoCommit(false);
//        try {
//            String after = null;
//            while (true) {
//                var batch = new ArrayList<LegacyRestriction>();
//                String sql = "SELECT user_openId, " + blockedValue + ", " + ignoredValue + " FROM official_users WHERE ("
//                        + blockedValue + " = TRUE OR " + ignoredValue + " = TRUE)"
//                        + (after == null ? "" : " AND user_openId > ?") + " ORDER BY user_openId LIMIT 100";
//                try (var ps = con.prepareStatement(sql)) {
//                    if (after != null) ps.setString(1, after);
//                    try (var rows = ps.executeQuery()) {
//                        while (rows.next()) batch.add(new LegacyRestriction(rows.getString(1), rows.getBoolean(2), rows.getBoolean(3)));
//                    }
//                }
//                if (batch.isEmpty()) break;
//                for (var legacy : batch) {
//                    var account = lockQqUser(con, legacy.openId(), true);
//                    AccountStatus current = AccountStatus.fromString(account.getStatus());
//                    AccountStatus desired = legacy.ignored() ? AccountStatus.BANNED : AccountStatus.SUSPENDED;
//                    if (current == AccountStatus.ACTIVE || (current == AccountStatus.SUSPENDED && desired == AccountStatus.BANNED))
//                        updateStatus(con, account, desired);
//                }
//                migrated += batch.size();
//                after = batch.getLast().openId();
//            }
//            con.commit();
//        } catch (SQLException | RuntimeException e) {
//            try { con.rollback(); }
//            catch (SQLException rollback) { e.addSuppressed(rollback); }
//            throw e;
//        } finally {
//            con.setAutoCommit(true);
//        }
//        try (var statement = con.createStatement()) {
//            if (blocked) statement.executeUpdate("ALTER TABLE official_users DROP COLUMN is_blocked");
//            if (ignored) statement.executeUpdate("ALTER TABLE official_users DROP COLUMN is_ignored");
//        }
//        log.info("用户限制状态迁移完成，共 {} 条，已移除 official_users 的拉黑和屏蔽列", migrated);
//    }
//
//    private record LegacyRestriction(String openId, boolean blocked, boolean ignored) {}

    /**
     * 在调用方事务内修改官机账号状态；解除限制时可限定原状态，避免清除另一种限制
     *
     * @param con      已开始事务的连接
     * @param openId   QQ 用户 OpenID
     * @param status   目标账号状态
     * @param expected 原状态约束，为 null 时直接设置
     * @return 当前账号；解除未注册账号的限制时返回 null
     * @throws SQLException 数据库读取或更新失败
     */
    public static UnifiedAccountDTO setQqUserStatus(Connection con, String openId, AccountStatus status,
                                                    AccountStatus expected) throws SQLException {
        if (con.getAutoCommit()) throw new SQLException("账号状态更新必须在事务中执行");
        if (status == null) throw new IllegalArgumentException("账号状态不能为空");
        var account = lockQqUser(con, openId, status != AccountStatus.ACTIVE && expected == null);
        if (account == null) return null;
        AccountStatus current = AccountStatus.fromString(account.getStatus());
        if (expected != null && current != expected) return account;
        if (current != status) {
            updateStatus(con, account, status);
            return lockQqUser(con, openId, false);
        }
        return account;
    }

    private static UnifiedAccountDTO lockQqUser(Connection con, String openId, boolean create) throws SQLException {
        if (openId == null || openId.isBlank() || openId.length() > 128)
            throw new IllegalArgumentException("QQ 用户 OpenID 无效");
        if (create) {
            try (var ps = con.prepareStatement("INSERT INTO unified_account (uuid, qq_user_open_id, create_time, last_update_time) VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)"
                    + " ON DUPLICATE KEY UPDATE uuid = uuid")) {
                ps.setString(1, UUID.randomUUID().toString());
                ps.setString(2, openId);
                ps.executeUpdate();
            }
        }
        try (var ps = con.prepareStatement("SELECT * FROM unified_account WHERE qq_user_open_id = ? FOR UPDATE")) {
            ps.setString(1, openId);
            try (var rows = ps.executeQuery()) {
                return rows.next() ? rowToDTO(rows) : null;
            }
        }
    }

    private static void updateStatus(Connection con, UnifiedAccountDTO account, AccountStatus status) throws SQLException {
        try (var ps = con.prepareStatement("UPDATE unified_account SET status = ?, last_update_time = CURRENT_TIMESTAMP WHERE uuid = ?")) {
            ps.setString(1, status.name());
            ps.setString(2, account.getUuid().toString());
            if (ps.executeUpdate() != 1) throw new SQLException("账号状态未保存");
        }
    }

    // ==================== 创建 ====================

    /**
     * 创建统一账号。uuid 与 create_time 自动生成，last_update_time 初始等于 create_time。
     * 标识字段（openId / uin / mcUuid）唯一，冲突时返回 null。
     */
    public static UnifiedAccountDTO create(String username, String qqUserOpenId, String qqUserUin,
                                           String minecraftUuid, UnifiedRole role, List<String> permissions,
                                           AccountStatus status) {
        UUID uuid = UUID.randomUUID();
        String roleName = role == null ? UnifiedRole.USER.name() : role.name();
        String statusName = status == null ? AccountStatus.ACTIVE.name() : status.name();
        String permissionsJson = toJson(permissions);

        String sql = "INSERT INTO `" + TABLE + "` (`uuid`, `username`, `qq_user_open_id`, `qq_user_uin`, " +
                "`minecraft_uuid`, `role`, `permissions`, `status`, `create_time`, `last_update_time`) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, username);
            setNullableString(ps, 3, qqUserOpenId);
            setNullableString(ps, 4, qqUserUin);
            setNullableString(ps, 5, minecraftUuid);
            ps.setString(6, roleName);
            setNullableString(ps, 7, permissionsJson);
            ps.setString(8, statusName);
            ps.executeUpdate();
            try (var query = con.prepareStatement("SELECT * FROM unified_account WHERE uuid = ?")) {
                query.setString(1, uuid.toString());
                try (var rows = query.executeQuery()) {
                    if (!rows.next()) throw new SQLException("统一账号创建后无法读取");
                    return rowToDTO(rows);
                }
            }
        } catch (Exception e) {
            log.error("创建统一账号失败: openId={}, uin={}, mcUuid={}", qqUserOpenId, qqUserUin, minecraftUuid, e);
            return null;
        }
    }

    // ==================== 查询 ====================

    public static UnifiedAccountDTO findByUuid(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        return findByColumn("uuid", uuid.toString());
    }

    public static UnifiedAccountDTO findByQqUserOpenId(String openId) {
        return findByColumn("qq_user_open_id", openId);
    }

    public static UnifiedAccountDTO findByQqUserUin(String uin) {
        return findByColumn("qq_user_uin", uin);
    }

    public static UnifiedAccountDTO findByQqConnect(String openId) throws SQLException {
        try (var con = DatabaseManager.getConnection()) {
            return findByQqConnect(con, openId);
        }
    }

    /**
     * 按 QQ 互联 OpenID 查询统一账号
     *
     * @param con    数据库连接，由调用方关闭
     * @param openId 经 QQ 互联服务端确认的 OpenID
     * @return 对应的统一账号，未绑定时返回 null
     * @throws SQLException 查询失败
     */
    public static UnifiedAccountDTO findByQqConnect(Connection con, String openId) throws SQLException {
        if (openId == null || openId.isBlank()) return null;
        try (var ps = con.prepareStatement("SELECT * FROM `" + TABLE + "` WHERE `qq_connect_open_id`=?")) {
            ps.setString(1, openId);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rowToDTO(rs) : null;
            }
        }
    }

    public static boolean bindQqConnect(UUID uuid, String openId) throws SQLException {
        try (var con = DatabaseManager.getConnection()) {
            return bindQqConnect(con, uuid, openId);
        }
    }

    /**
     * 为统一账号绑定 QQ 互联身份，不覆盖已有的不同绑定
     *
     * @param con    数据库连接，由调用方关闭
     * @param uuid   统一账号 UUID
     * @param openId 经 QQ 互联服务端确认的 OpenID
     * @return 新建或已存在相同绑定时返回 true，账号不存在或绑定冲突时返回 false
     * @throws SQLException 数据库操作失败
     */
    public static boolean bindQqConnect(Connection con, UUID uuid, String openId) throws SQLException {
        if (uuid == null || openId == null || openId.isBlank() || openId.length() > 128) return false;
        String sql = "UPDATE `" + TABLE + "` SET `qq_connect_open_id`=?, `last_update_time`=CURRENT_TIMESTAMP "
                + "WHERE `uuid`=? AND (`qq_connect_open_id` IS NULL OR `qq_connect_open_id`=?)";
        try (var ps = con.prepareStatement(sql)) {
            ps.setString(1, openId);
            ps.setString(2, uuid.toString());
            ps.setString(3, openId);
            if (ps.executeUpdate() > 0) return true;
        } catch (SQLException failure) {
            if ("23000".equals(failure.getSQLState()) || "23505".equals(failure.getSQLState())) return false;
            throw failure;
        }
        var existing = findByQqConnect(con, openId);
        return existing != null && uuid.equals(existing.getUuid());
    }

    public static UnifiedAccountDTO findByMinecraftUuid(String mcUuid) {
        return findByColumn("minecraft_uuid", mcUuid);
    }

    /**
     * 按用户名查询（可能多个，按创建时间倒序）。
     */
    public static List<UnifiedAccountDTO> findByUsername(String username) {
        String sql = "SELECT * FROM `" + TABLE + "` WHERE `username` = ? ORDER BY `create_time` DESC";
        List<UnifiedAccountDTO> result = new ArrayList<>();
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            try (var rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rowToDTO(rs));
                }
            }
        } catch (Exception e) {
            log.error("按用户名查询统一账号失败: username={}", username, e);
        }
        return result;
    }

    public static List<UnifiedAccountDTO> findAll() {
        try (var con = DatabaseManager.getConnection()) {
            return findAll(con);
        } catch (Exception e) {
            log.error("查询全部统一账号失败", e);
            return List.of();
        }
    }

    /**
     * 读取全部统一账号，由调用方处理查询失败，避免将失败结果作为空缓存加载
     *
     * @param con 数据库连接，由调用方关闭
     * @return 全部统一账号
     * @throws SQLException 数据库查询失败
     */
    public static List<UnifiedAccountDTO> findAll(Connection con) throws SQLException {
        List<UnifiedAccountDTO> result = new ArrayList<>();
        try (var ps = con.prepareStatement("SELECT * FROM unified_account ORDER BY create_time DESC");
             var rs = ps.executeQuery()) {
            while (rs.next()) result.add(rowToDTO(rs));
        }
        return result;
    }

    public static int count() {
        String sql = "SELECT COUNT(*) FROM `" + TABLE + "`";
        try (var con = DatabaseManager.getConnection();
             var ps = con.prepareStatement(sql);
             var rs = ps.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (Exception e) {
            log.error("统计统一账号失败", e);
        }
        return 0;
    }

    // ==================== 修改（各字段独立，自动刷新 last_update_time） ====================

    public static boolean updateUsername(UUID uuid, String username) {
        return updateField(uuid, "username", username);
    }

    /**
     * 仅为用户名为空的账号写入昵称
     *
     * @param uuid 统一账号 UUID
     * @param username 已验证平台提供的昵称
     * @return 写入成功返回 true，已有用户名或参数无效时返回 false
     * @throws SQLException 数据库操作失败
     */
    public static boolean fillUsernameIfEmpty(UUID uuid, String username) throws SQLException {
        try (var con = DatabaseManager.getConnection()) {
            return fillUsernameIfEmpty(con, uuid, username);
        }
    }

    /**
     * 使用数据库条件更新保证已有用户名不会被覆盖
     *
     * @param con 数据库连接，由调用方关闭
     * @param uuid 统一账号 UUID
     * @param username 已验证平台提供的昵称
     * @return 写入成功返回 true，已有用户名或参数无效时返回 false
     * @throws SQLException 数据库操作失败
     */
    public static boolean fillUsernameIfEmpty(Connection con, UUID uuid, String username) throws SQLException {
        if (uuid == null || username == null || username.isBlank()) return false;
        String sql = "UPDATE `" + TABLE + "` SET `username`=?, `last_update_time`=CURRENT_TIMESTAMP "
                + "WHERE `uuid`=? AND (`username` IS NULL OR TRIM(`username`)='')";
        try (var ps = con.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, uuid.toString());
            return ps.executeUpdate() > 0;
        }
    }

    public static boolean updateQqUserOpenId(UUID uuid, String openId) {
        return updateField(uuid, "qq_user_open_id", openId);
    }

    public static boolean updateQqUserUin(UUID uuid, String uin) {
        return updateField(uuid, "qq_user_uin", uin);
    }

    public static boolean updateMinecraftUuid(UUID uuid, String mcUuid) {
        return updateField(uuid, "minecraft_uuid", mcUuid);
    }

    public static boolean updateRole(UUID uuid, UnifiedRole role) {
        return updateField(uuid, "role", role == null ? UnifiedRole.USER.name() : role.name());
    }

    public static boolean updateStatus(UUID uuid, AccountStatus status) {
        return updateField(uuid, "status", status == null ? AccountStatus.ACTIVE.name() : status.name());
    }

    public static boolean updatePermissions(UUID uuid, List<String> permissions) {
        return updateField(uuid, "permissions", toJson(permissions));
    }

    // ==================== 删除 ====================

    public static boolean delete(UUID uuid) {
        if (uuid == null) {
            return false;
        }
        String sql = "DELETE FROM `" + TABLE + "` WHERE `uuid` = ?";
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("删除统一账号失败: uuid={}", uuid, e);
            return false;
        }
    }

    // ==================== 私有辅助 ====================

    /**
     * 按单个字符串列查询，命中返回首条（列已建唯一索引，理论上最多一条）。
     */
    private static UnifiedAccountDTO findByColumn(String column, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String sql = "SELECT * FROM `" + TABLE + "` WHERE `" + column + "` = ? LIMIT 1";
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            ps.setString(1, value);
            try (var rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rowToDTO(rs);
                }
            }
        } catch (Exception e) {
            log.error("按 {} 查询统一账号失败: {}", column, value, e);
        }
        return null;
    }

    /**
     * 更新单个字段并自动刷新 last_update_time。column 为内部白名单列名，非用户输入。
     */
    private static boolean updateField(UUID uuid, String column, String value) {
        if (uuid == null) {
            return false;
        }
        String sql = "UPDATE `" + TABLE + "` SET `" + column + "` = ?, `last_update_time` = CURRENT_TIMESTAMP WHERE `uuid` = ?";
        try (var con = DatabaseManager.getConnection(); var ps = con.prepareStatement(sql)) {
            setNullableString(ps, 1, value);
            ps.setString(2, uuid.toString());
            return ps.executeUpdate() > 0;
        } catch (Exception e) {
            log.error("更新统一账号字段失败: uuid={}, column={}", uuid, column, e);
            return false;
        }
    }

    private static void setNullableString(PreparedStatement ps, int index, String value) throws SQLException {
        if (value == null || value.isBlank()) {
            ps.setNull(index, Types.VARCHAR);
        } else {
            ps.setString(index, value);
        }
    }

    private static UnifiedAccountDTO rowToDTO(ResultSet rs) throws SQLException {
        return new UnifiedAccountDTO(
                UUID.fromString(rs.getString("uuid")),
                rs.getString("username"),
                rs.getString("qq_user_open_id"),
                rs.getString("qq_user_uin"),
                rs.getString("minecraft_uuid"),
                rs.getString("role"),
                parseJson(rs.getString("permissions")),
                rs.getString("status"),
                rs.getObject("create_time", LocalDateTime.class),
                rs.getObject("last_update_time", LocalDateTime.class),
                rs.getString("qq_connect_open_id")
        );
    }

    /**
     * 权限列表序列化为 JSON 字符串存列，null / 空列表返回 null 由 setNullableString 归一为 NULL。
     */
    private static String toJson(List<String> permissions) {
        if (permissions == null || permissions.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(permissions);
        } catch (Exception e) {
            log.error("序列化权限列表失败", e);
            return null;
        }
    }

    /**
     * 列 JSON 反序列化回权限列表，空 / 非法返回空列表。
     */
    private static List<String> parseJson(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {
            });
        } catch (Exception e) {
            log.error("反序列化权限列表失败: {}", json, e);
            return List.of();
        }
    }
}
