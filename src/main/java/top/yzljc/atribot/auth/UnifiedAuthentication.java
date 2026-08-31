package top.yzljc.atribot.auth;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.UnifiedRole;
import top.yzljc.atribot.database.UnifiedAccountDTO;
import top.yzljc.atribot.database.repo.UnifiedAccountRepository;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author YZ_Ljc_
 * @ClassName UnifiedAuthentication
 * @Created_at 2026/08/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.auth
 */
@Slf4j
public class UnifiedAuthentication {

    private static final Map<UUID, UnifiedAccount> cache = new ConcurrentHashMap<>();

    public static void init() {
        UnifiedAccountRepository.init();
        for (UnifiedAccountDTO dto : UnifiedAccountRepository.findAll()) {
            cache.put(dto.getUuid(), dto.toAccount());
        }
        log.info("[!] 统一账号缓存数据加载完成，共 {} 条", cache.size());
    }

    public static UnifiedAccount register(String qqUserOpenId) {
        return register(null, qqUserOpenId, null, null, UnifiedRole.USER, List.of(), AccountStatus.ACTIVE);
    }

    public static UnifiedAccount register(String qqUserOpenId, String username) {
        return register(username, qqUserOpenId, null, null, UnifiedRole.USER, List.of(), AccountStatus.ACTIVE);
    }

    public static UnifiedAccount register(String username, String qqUserOpenId, String qqUserUin,
                                          String minecraftUuid, UnifiedRole role, List<String> permissions,
                                          AccountStatus status) {
        return cachePut(UnifiedAccountRepository.create(username, qqUserOpenId, qqUserUin, minecraftUuid, role, permissions, status));
    }

    public static UnifiedAccount ensureByQqUserOpenId(String openId, String username) {
        UnifiedAccount account = findByQqUserOpenId(openId);
        return account != null ? account : register(username, openId, null, null, UnifiedRole.USER, List.of(), AccountStatus.ACTIVE);
    }

    public static UnifiedAccount ensureByQqUserUin(String uin, String username) {
        UnifiedAccount account = findByQqUserUin(uin);
        return account != null ? account : register(username, null, uin, null, UnifiedRole.USER, List.of(), AccountStatus.ACTIVE);
    }

    public static UnifiedAccount get(UUID uuid) {
        if (uuid == null) {
            return null;
        }
        UnifiedAccount cached = cache.get(uuid);
        return cached != null ? cached : cachePut(UnifiedAccountRepository.findByUuid(uuid));
    }

    public static UnifiedAccount findByQqUserOpenId(String openId) {
        if (openId == null || openId.isBlank()) {
            return null;
        }
        for (UnifiedAccount account : cache.values()) {
            if (openId.equals(account.qqUserOpenId())) {
                return account;
            }
        }
        return cachePut(UnifiedAccountRepository.findByQqUserOpenId(openId));
    }

    public static UnifiedAccount findByQqUserUin(String uin) {
        if (uin == null || uin.isBlank()) {
            return null;
        }
        for (UnifiedAccount account : cache.values()) {
            if (uin.equals(account.qqUserUin())) {
                return account;
            }
        }
        return cachePut(UnifiedAccountRepository.findByQqUserUin(uin));
    }

    public static UnifiedAccount findByMinecraftUuid(String mcUuid) {
        if (mcUuid == null || mcUuid.isBlank()) {
            return null;
        }
        for (UnifiedAccount account : cache.values()) {
            if (mcUuid.equals(account.minecraftUuid())) {
                return account;
            }
        }
        return cachePut(UnifiedAccountRepository.findByMinecraftUuid(mcUuid));
    }

    /**
     * 按用户名查询（可能多个，按创建时间倒序）
     */
    public static List<UnifiedAccount> findByUsername(String username) {
        if (username == null || username.isBlank()) {
            return List.of();
        }
        List<UnifiedAccount> cached = cache.values().stream()
                .filter(account -> username.equals(account.username()))
                .sorted(Comparator.comparing(UnifiedAccount::createTime,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
        if (!cached.isEmpty()) {
            return cached;
        }
        List<UnifiedAccount> result = new ArrayList<>();
        for (UnifiedAccountDTO dto : UnifiedAccountRepository.findByUsername(username)) {
            result.add(cachePut(dto));
        }
        return result;
    }

    public static List<UnifiedAccount> findAll() {
        return new ArrayList<>(cache.values());
    }

    /** 按统一账号任意字段精确查询，用户名允许命中多个账号。 */
    public static List<UnifiedAccount> findMatching(String value) {
        if (value == null || value.isBlank()) return List.of();
        String query = value.trim();
        Map<UUID, UnifiedAccount> matches = new LinkedHashMap<>();
        for (UnifiedAccount account : cache.values()) {
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

    public static boolean updateRole(UUID uuid, UnifiedRole role) {
        return refreshAfterUpdate(uuid, UnifiedAccountRepository.updateRole(uuid, role));
    }

    public static boolean updatePermissions(UUID uuid, List<String> permissions) {
        return refreshAfterUpdate(uuid, UnifiedAccountRepository.updatePermissions(uuid, permissions));
    }

    public static boolean updateStatus(UUID uuid, AccountStatus status) {
        return refreshAfterUpdate(uuid, UnifiedAccountRepository.updateStatus(uuid, status));
    }

    public static boolean delete(UUID uuid) {
        if (UnifiedAccountRepository.delete(uuid)) {
            cache.remove(uuid);
            return true;
        }
        return false;
    }

    private static UnifiedAccount cachePut(UnifiedAccountDTO dto) {
        if (dto == null) {
            return null;
        }
        UnifiedAccount account = dto.toAccount();
        cache.put(account.uuid(), account);
        return account;
    }

    private static boolean matches(UnifiedAccount account, String query) {
        return query.equalsIgnoreCase(String.valueOf(account.uuid()))
                || query.equals(account.username())
                || query.equals(account.qqUserOpenId())
                || query.equals(account.qqUserUin())
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
