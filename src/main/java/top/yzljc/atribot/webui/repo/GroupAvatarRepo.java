package top.yzljc.atribot.webui.repo;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import top.yzljc.atribot.database.DatabaseManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupAvatarRepo
 * @Created_at 2026/10/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.repo
 */
public final class GroupAvatarRepo {
    private static final Cache<String, List<String>> CACHE = CacheBuilder.newBuilder()
            .maximumSize(256).expireAfterWrite(5, TimeUnit.MINUTES).build();

    private GroupAvatarRepo() {}

    public static List<String> members(String groupOpenId) {
        try {
            return CACHE.get(groupOpenId, () -> {
                String sql = "SELECT r.union_openId FROM official_group_record r "
                        + "JOIN (SELECT MAX(id) AS id FROM official_group_record WHERE group_openId=? "
                        + "AND union_openId IS NOT NULL AND union_openId<>'' GROUP BY union_openId) m ON r.id=m.id "
                        + "ORDER BY CASE r.member_role WHEN 'OWNER' THEN 0 WHEN 'ADMIN' THEN 1 ELSE 2 END, "
                        + "COALESCE(r.event_timestamp,CAST(r.created_at AS CHAR)) DESC,r.id DESC LIMIT 9";
                List<String> ids = new ArrayList<>();
                try (var conn = DatabaseManager.getConnection(); var stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, groupOpenId);
                    try (var rs = stmt.executeQuery()) {
                        while (rs.next()) ids.add(rs.getString(1));
                    }
                }
                return List.copyOf(ids);
            });
        } catch (Exception e) {
            throw new IllegalStateException("查询群头像成员失败", e);
        }
    }
}
