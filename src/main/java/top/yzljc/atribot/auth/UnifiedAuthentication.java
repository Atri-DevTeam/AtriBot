package top.yzljc.atribot.auth;

import top.yzljc.atricore.auth.*;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atricore.auth.official.UnifiedRole;
import top.yzljc.atribot.database.UnifiedAccountDTO;
import top.yzljc.atribot.database.DatabaseManager;
import top.yzljc.atribot.database.repo.C2CRepository;
import top.yzljc.atribot.database.repo.UnifiedAccountRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.sql.SQLException;

/**
 * @Author YZ_Ljc_
 * @ClassName UnifiedAuthentication
 * @Created_at 2026/08/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.auth
 */
@Slf4j
public class UnifiedAuthentication {

    private static final Map<UUID, AtriAccount> cache = new ConcurrentHashMap<>();
    private static final Map<String, AtriAccount> qqOpenIdCache = new ConcurrentHashMap<>();

    public static void init() {
        try (var con = DatabaseManager.getConnection()) {
            UnifiedAccountRepository.init(con);
            replaceCache(UnifiedAccountRepository.findAll(con));
        } catch (SQLException e) {
            throw new IllegalStateException("统一账号状态初始化失败", e);
        }
        log.info("[!] 统一账号缓存数据加载完成，共 {} 条", cache.size());
    }

    static synchronized void replaceCache(List<UnifiedAccountDTO> accounts) {
        cache.clear();
        qqOpenIdCache.clear();
        accounts.forEach(UnifiedAuthentication::cachePut);
    }

    /**
     * 读取启动时加载并随账号更新刷新的状态索引，不触发数据库查询
     *
     * @param openId QQ 用户 OpenID
     * @return 统一账号状态，未注册的用户按 ACTIVE 处理
     */
    public static AccountStatus getQqUserStatus(String openId) {
        var account = getCachedQqUserAccount(openId);
        return account == null ? AccountStatus.ACTIVE : account.status();
    }

    /**
     * 查询已加载的 QQ 统一账号，不查询数据库
     *
     * @param openId QQ 用户 OpenID
     * @return 缓存中的统一账号，未注册时返回 null
     */
    public static AtriAccount getCachedQqUserAccount(String openId) {
        return openId == null ? null : qqOpenIdCache.get(openId);
    }

    /**
     * 保存统一账号状态，事务提交后更新缓存；原状态不符合约束时保留当前状态
     *
     * @param openId QQ 用户 OpenID
     * @param status 目标账号状态
     * @param expected 原状态约束，为 null 时直接设置
     * @return 数据库操作成功时返回 true，包括无需更改的情况
     */
    public static synchronized boolean setQqUserStatus(String openId, AccountStatus status, AccountStatus expected) {
        try (var con = DatabaseManager.getConnection()) {
            con.setAutoCommit(false);
            try {
                var account = UnifiedAccountRepository.setQqUserStatus(con, openId, status, expected);
                con.commit();
                cachePut(account);
                return true;
            } catch (SQLException | RuntimeException e) {
                try { con.rollback(); }
                catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            log.error("保存统一账号状态失败，用户 {}", openId, e);
            return false;
        }
    }

    /**
     * 保存统一账号权限和状态，并更新已有私聊用户的授权，不创建私聊记录
     *
     * @param openId QQ 用户 OpenID
     * @param role 统一账号权限组
     * @param permissions 统一账号权限节点
     * @param status 统一账号状态
     * @param c2cPush 私聊主动消息设置
     * @return 所有字段保存成功时返回 true
     */
    public static synchronized boolean saveOfficialUserProfile(String openId, UnifiedRole role, Set<String> permissions,
                                                               AccountStatus status, boolean c2cPush) {
        try (var con = DatabaseManager.getConnection()) {
            con.setAutoCommit(false);
            try {
                C2CRepository.setC2CPush(con, openId, c2cPush);
                UnifiedAccountRepository.setQqUserPermissions(con, openId, role, permissions);
                var account = UnifiedAccountRepository.setQqUserStatus(con, openId, status, null);
                con.commit();
                cachePut(account);
                return true;
            } catch (SQLException | RuntimeException e) {
                try { con.rollback(); }
                catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            log.error("保存官机用户档案及统一账号状态失败，用户 {}", openId, e);
            return false;
        }
    }

    public static AtriAccount register(String qqUserOpenId) {
        return register(null, qqUserOpenId, null, null, UnifiedRole.USER, List.of(), AccountStatus.ACTIVE);
    }

    /**
     * 保存统一账号权限，保留账号状态与其他平台绑定，不创建私聊记录
     *
     * @param openId QQ 用户 OpenID
     * @param role 权限组
     * @param permissions 权限节点
     * @return 事务提交成功时返回 true
     */
    public static synchronized boolean saveOfficialUserPermissions(String openId, UnifiedRole role, Set<String> permissions) {
        try (var con = DatabaseManager.getConnection()) {
            con.setAutoCommit(false);
            try {
                var account = UnifiedAccountRepository.setQqUserPermissions(con, openId, role, permissions);
                con.commit();
                cachePut(account);
                return true;
            } catch (SQLException | RuntimeException e) {
                try { con.rollback(); }
                catch (SQLException rollback) { e.addSuppressed(rollback); }
                throw e;
            }
        } catch (SQLException | RuntimeException e) {
            log.error("保存统一账号权限失败，用户 {}", openId, e);
            return false;
        }
    }

    public static AtriAccount register(String qqUserOpenId, String username) {
        return register(username, qqUserOpenId, null, null, UnifiedRole.USER, List.of(), AccountStatus.ACTIVE);
    }

    public static AtriAccount register(String username, String qqUserOpenId, String qqUserUin,
                                          String minecraftUuid, UnifiedRole role, List<String> permissions,
                                          AccountStatus status) {
        return cachePut(UnifiedAccountRepository.create(username, qqUserOpenId, qqUserUin, minecraftUuid, role, permissions, status));
    }

    public static AtriAccount ensureByQqUserOpenId(String openId, String username) {
        AtriAccount account = findByQqUserOpenId(openId);
        return account != null ? account : register(username, openId, null, null, UnifiedRole.USER, List.of(), AccountStatus.ACTIVE);
    }

    public static AtriAccount ensureByQqUserUin(String uin, String username) {
        AtriAccount account = findByQqUserUin(uin);
        return account != null ? account : register(username, null, uin, null, UnifiedRole.USER, List.of(), AccountStatus.ACTIVE);
    }

    public static AtriAccount get(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        AtriAccount cached = cache.get(uuid);
        return cached != null ? cached : cachePut(UnifiedAccountRepository.findByUuid(uuid));
    }

    public static AtriAccount findByQqUserOpenId(String openId) {
        if (openId == null || openId.isBlank()) {
            return null;
        }
        var account = qqOpenIdCache.get(openId);
        if (account != null) return account;
        return cachePut(UnifiedAccountRepository.findByQqUserOpenId(openId));
    }

    public static AtriAccount findByQqUserUin(String uin) {
        if (uin == null || uin.isBlank()) {
            return null;
        }
        for (AtriAccount account : cache.values()) {
            if (uin.equals(account.qqUserUin())) {
                return account;
            }
        }
        return cachePut(UnifiedAccountRepository.findByQqUserUin(uin));
    }

    public static AtriAccount findByQqConnect(String openId) throws SQLException {
        return cachePut(UnifiedAccountRepository.findByQqConnect(openId));
    }

    /**
     * 将已验证的 QQ 互联身份绑定到官机用户的统一账号
     *
     * @param userOpenId 通过私聊一次性凭证确认的官机用户 OpenID
     * @param username 新建统一账号时使用的用户名
     * @param openId 经 QQ 互联服务端确认的 OpenID
     * @return 新建或已存在相同绑定时返回 true，绑定冲突时返回 false
     * @throws SQLException 统一账号创建或绑定失败
     */
    public static synchronized boolean bindQqConnect(String userOpenId, String username, String openId) throws SQLException {
        var account = ensureByQqUserOpenId(userOpenId, username);
        if (account == null) throw new SQLException("统一账号创建失败");
        return refreshAfterUpdate(account.uuid(), UnifiedAccountRepository.bindQqConnect(account.uuid(), openId));
    }

    public static AtriAccount findByMinecraftUuid(String mcUuid) {
        if (mcUuid == null || mcUuid.isBlank()) {
            return null;
        }
        for (AtriAccount account : cache.values()) {
            if (mcUuid.equals(account.minecraftUuid())) {
                return account;
            }
        }
        return cachePut(UnifiedAccountRepository.findByMinecraftUuid(mcUuid));
    }

    /**
     * 按用户名查询（可能多个，按创建时间倒序）
     */
    public static List<AtriAccount> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return List.of();
        }
        List<AtriAccount> cached = cache.values().stream()
                .filter(account -> username.equals(account.username()))
                .sorted(Comparator.comparing(AtriAccount::createTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (!cached.isEmpty()) {
            return cached;
        }
        List<AtriAccount> result = new ArrayList<>();
        for (UnifiedAccountDTO dto : UnifiedAccountRepository.findByUsername(username)) {
            result.add(cachePut(dto));
        }
        return result;
    }

    public static List<AtriAccount> findAll() {
        return new ArrayList<>(cache.values());
    }

    /** 按统一账号任意字段精确查询，用户名允许命中多个账号。 */
    public static List<AtriAccount> findMatching(String value) {
        if (value == null || value.isBlank()) return List.of();
        String query = value.trim();
        Map<UUID, AtriAccount> matches = new LinkedHashMap<>();
        for (AtriAccount account : cache.values()) {
            if (matches(account, query)) matches.put(account.uuid(), account);
        }
        return new ArrayList<>(matches.values());
    }

    public static int countByMinecraftUuid(String minecraftUuid) {
        if (minecraftUuid == null || minecraftUuid.isBlank()) return 0;
        String query = minecraftUuid.trim();
        return (int) cache.values().stream()
                .filter(account -> query.equalsIgnoreCase(account.minecraftUuid()))
                .count();
    }

    public static boolean bindMinecraftUuid(UUID uuid, String minecraftUuid) {
        if (uuid == null || minecraftUuid == null || minecraftUuid.isBlank()) return false;
        return updateMinecraftUuid(uuid, minecraftUuid.trim());
    }

    public static int count() {
        return cache.size();
    }

    public static boolean updateUsername(UUID uuid, String username) {
        return refreshAfterUpdate(uuid, UnifiedAccountRepository.updateUsername(uuid, username));
    }

    public static boolean updateQqUserOpenId(UUID uuid, String openId) {
        return refreshAfterUpdate(uuid, UnifiedAccountRepository.updateQqUserOpenId(uuid, openId));
    }

    public static boolean updateQqUserUin(UUID uuid, String uin) {
        return refreshAfterUpdate(uuid, UnifiedAccountRepository.updateQqUserUin(uuid, uin));
    }

    public static boolean updateMinecraftUuid(UUID uuid, String mcUuid) {
        return refreshAfterUpdate(uuid, UnifiedAccountRepository.updateMinecraftUuid(uuid, mcUuid));
    }

    public static synchronized boolean updateRole(UUID uuid, UnifiedRole role) {
        return refreshAfterUpdate(uuid, UnifiedAccountRepository.updateRole(uuid, role));
    }

    public static synchronized boolean updatePermissions(UUID uuid, List<String> permissions) {
        return refreshAfterUpdate(uuid, UnifiedAccountRepository.updatePermissions(uuid, permissions));
    }

    public static synchronized boolean updateStatus(UUID uuid, AccountStatus status) {
        return refreshAfterUpdate(uuid, UnifiedAccountRepository.updateStatus(uuid, status));
    }

    public static synchronized boolean delete(UUID uuid) {
        if (UnifiedAccountRepository.delete(uuid)) {
            var removed = cache.remove(uuid);
            if (removed != null && removed.qqUserOpenId() != null) qqOpenIdCache.remove(removed.qqUserOpenId(), removed);
            return true;
        }
        return false;
    }

    private static synchronized AtriAccount cachePut(UnifiedAccountDTO dto) {
        if (dto == null) {
            return null;
        }
        AtriAccount account = dto.toAccount();
        var old = cache.put(account.uuid(), account);
        if (old != null && old.qqUserOpenId() != null && !old.qqUserOpenId().equals(account.qqUserOpenId()))
            qqOpenIdCache.remove(old.qqUserOpenId(), old);
        if (account.qqUserOpenId() != null && !account.qqUserOpenId().isBlank())
            qqOpenIdCache.put(account.qqUserOpenId(), account);
        return account;
    }

    private static boolean matches(AtriAccount account, String query) {
        return query.equalsIgnoreCase(String.valueOf(account.uuid()))
                || query.equals(account.username())
                || query.equals(account.qqUserOpenId())
                || query.equals(account.qqUserUin())
                || query.equals(account.qqConnectOpenId())
                || query.equalsIgnoreCase(account.minecraftUuid());
    }

    private static boolean refreshAfterUpdate(UUID uuid, boolean updated) {
        if (!updated) {
            return false;
        }
        cachePut(UnifiedAccountRepository.findByUuid(uuid));
        return true;
    }
}
