package top.yzljc.atribot.webui.repo;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutionException;

/**
 * 公开统计与个人排名共用的统计快照
 *
 * @Author YZ_Ljc_
 * @ClassName BotStatsCache
 * @Created_at 2026/10/25
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.repo
 */
public final class BotStatsCache {
    private static final Cache<String, BotStatsRepository.Snapshot> CACHE = CacheBuilder.newBuilder()
            .maximumSize(1).expireAfterWrite(Duration.ofSeconds(60)).build();
    private static final BotStatsRepository REPOSITORY = new BotStatsRepository();

    private BotStatsCache() {
    }

    /**
     * @return 缓存有效期为 60 秒的全量统计快照，同一时刻的加载请求合并执行
     * @throws IllegalStateException 快照加载失败
     */
    public static BotStatsRepository.Snapshot snapshot() {
        try {
            return CACHE.get("snapshot", REPOSITORY::load);
        } catch (ExecutionException e) {
            throw new IllegalStateException("统计快照读取失败", e.getCause());
        }
    }

    /**
     * @param snapshot 全量统计快照
     * @param userId   当前登录用户的 OpenID
     * @return 按模块组织的用户排名，不包含其他用户的资料
     */
    public static Map<String, Map<String, Integer>> ranks(BotStatsRepository.Snapshot snapshot, String userId) {
        var result = new LinkedHashMap<String, Map<String, Integer>>();
        if (snapshot.games() != null)
            snapshot.games().forEach((game, section) -> result.put(game, section.ranks(userId)));
        if (snapshot.loot() != null) result.put("loot", snapshot.loot().ranks(userId));
        if (snapshot.sign() != null) result.put("sign", snapshot.sign().ranks(userId));
        return Collections.unmodifiableMap(result);
    }
}
