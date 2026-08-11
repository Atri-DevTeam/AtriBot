package top.yzljc.atribot.webui.repo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Properties;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName JoinApprovalWhitelistStore
 * @Created_at 2026/08/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.impl
 * @Description 入群审批白名单号码本地记录
 *
 * 白名单号码由管理员在 WebUI 页面自行填写，本地镜像一份用于展示与单删
 * 增删操作仍会同步到官方接口，本地记录只是页面侧的可读镜像
 * 官方策略列表接口只返回白名单数量，不提供号码明细
 */
@Slf4j
public class JoinApprovalWhitelistRepo {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Map<String, Set<String>> whitelist = new LinkedHashMap<>();
    private static boolean loaded = false;

    /**
     * 读取某策略的本地白名单号码列表
     *
     * @param strategyId 策略 ID
     * @return 号码列表，可能为空
     */
    public static synchronized List<String> list(String strategyId) {
        ensureLoaded();
        Set<String> users = whitelist.get(strategyId);
        return users == null ? new ArrayList<>() : new ArrayList<>(users);
    }

    /**
     * 向某策略本地白名单追加号码（去重）
     *
     * @param strategyId 策略 ID
     * @param users      待追加的号码列表
     */
    public static synchronized void addUsers(String strategyId, List<String> users) {
        ensureLoaded();
        if (users == null || users.isEmpty()) {
            return;
        }
        Set<String> set = whitelist.computeIfAbsent(strategyId, k -> new LinkedHashSet<>());
        boolean changed = false;
        for (String user : users) {
            if (user != null && !user.isBlank() && set.add(user)) {
                changed = true;
            }
        }
        if (changed) {
            save();
        }
    }

    /**
     * 从某策略本地白名单移除号码
     *
     * @param strategyId 策略 ID
     * @param users      待移除的号码列表
     */
    public static synchronized void removeUsers(String strategyId, List<String> users) {
        ensureLoaded();
        Set<String> set = whitelist.get(strategyId);
        if (set == null || users == null || users.isEmpty()) {
            return;
        }
        if (set.removeAll(users)) {
            save();
        }
    }

    /**
     * 清空某策略的本地白名单记录（删除策略时调用）
     *
     * @param strategyId 策略 ID
     */
    public static synchronized void clearStrategy(String strategyId) {
        ensureLoaded();
        if (whitelist.remove(strategyId) != null) {
            save();
        }
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = new File(Properties.JOIN_APPROVAL_WHITELIST);
        if (!file.exists()) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(file);
            Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                LinkedHashSet<String> set = new LinkedHashSet<>();
                for (JsonNode node : entry.getValue()) {
                    String value = node.asText(null);
                    if (value != null && !value.isBlank()) {
                        set.add(value);
                    }
                }
                if (!set.isEmpty()) {
                    whitelist.put(entry.getKey(), set);
                }
            }
            log.info("[!] 已加载入群审批白名单本地记录，覆盖 {} 条策略", whitelist.size());
        } catch (IOException e) {
            log.error("[!] 加载入群审批白名单本地记录失败: {}", e.getMessage(), e);
        }
    }

    private static void save() {
        try {
            File file = new File(Properties.JOIN_APPROVAL_WHITELIST);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            Map<String, Object> data = new LinkedHashMap<>();
            for (Map.Entry<String, Set<String>> entry : whitelist.entrySet()) {
                data.put(entry.getKey(), new ArrayList<>(entry.getValue()));
            }
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
        } catch (IOException e) {
            log.error("[!] 保存入群审批白名单本地记录失败: {}", e.getMessage(), e);
        }
    }
}
