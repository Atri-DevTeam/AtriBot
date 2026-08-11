package top.yzljc.atribot.webui.repo;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.management.JoinApprovalStrategy;
import top.yzljc.atribot.configuration.Properties;

import java.io.File;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName JoinApprovalSnapshotStore
 * @Created_at 2026/08/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.impl
 * @Description 入群审批策略本地快照缓存
 *
 * 策略本体存在 QQ 官方，本地只缓存一份策略列表快照，供 WebUI 优先展示
 * 减少对官方接口的调用与限流压力，手动「刷新」时才重新拉取官方数据
 */
@Slf4j
public class JoinApprovalSnapshotRepo {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final List<JoinApprovalStrategy.StrategyData> strategies = new ArrayList<>();
    private static String savedAt;
    private static boolean loaded = false;

    /**
     * 读取策略快照，未存过快照时返回空列表
     *
     * @return 快照中的策略列表，可能为空
     */
    public static synchronized List<JoinApprovalStrategy.StrategyData> getSnapshot() {
        ensureLoaded();
        return new ArrayList<>(strategies);
    }

    /**
     * 覆盖保存策略快照
     *
     * @param list 最新的策略全量列表
     */
    public static synchronized void saveSnapshot(List<JoinApprovalStrategy.StrategyData> list) {
        loaded = true;
        strategies.clear();
        if (list != null) {
            strategies.addAll(list);
        }
        savedAt = OffsetDateTime.now().toString();
        save();
    }

    /**
     * 清空策略快照（文件被删时也清除内存态）
     */
    public static synchronized void clear() {
        loaded = true;
        strategies.clear();
        savedAt = null;
        File file = new File(Properties.JOIN_APPROVAL_STRATEGY_SNAPSHOT);
        if (file.exists() && !file.delete()) {
            log.warn("[!] 删除入群审批策略快照文件失败");
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = new File(Properties.JOIN_APPROVAL_STRATEGY_SNAPSHOT);
        if (!file.exists()) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(file);
            savedAt = root.path("savedAt").asText(null);
            JsonNode list = root.path("strategies");
            if (list.isArray()) {
                List<JoinApprovalStrategy.StrategyData> parsed = mapper.convertValue(list,
                        new TypeReference<List<JoinApprovalStrategy.StrategyData>>() {
                        });
                if (parsed != null) {
                    strategies.addAll(parsed);
                }
            }
            log.info("[!] 已加载入群审批策略快照，共 {} 条，保存于 {}", strategies.size(), savedAt);
        } catch (IOException e) {
            log.error("[!] 加载入群审批策略快照失败: {}", e.getMessage(), e);
        }
    }

    private static void save() {
        try {
            File file = new File(Properties.JOIN_APPROVAL_STRATEGY_SNAPSHOT);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("savedAt", savedAt);
            data.put("strategies", new ArrayList<>(strategies));
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
        } catch (IOException e) {
            log.error("[!] 保存入群审批策略快照失败: {}", e.getMessage(), e);
        }
    }
}
