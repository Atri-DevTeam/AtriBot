package top.yzljc.qqbot.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import top.yzljc.qqbot.utils.GroupList;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 群功能配置管理器 (全量存储 + 自动补全 + 自动清理版)
 */
public class GroupConfigManager {

    private static final String CONFIG_FILE = "groupconfig.json";
    private static final ObjectMapper jsonMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static Map<Long, Map<String, Boolean>> groupConfigCache = new ConcurrentHashMap<>();
    private static final Map<String, Boolean> registeredFeatures = new LinkedHashMap<>();

    static {
        loadConfigFromFile();
    }

    public static synchronized void registerFeature(String featureName, boolean defaultValue) {
        if (!registeredFeatures.containsKey(featureName)) {
            registeredFeatures.put(featureName, defaultValue);
        }
    }

    public static synchronized void refreshAllConfigs() {
        System.out.println("[Config] 正在同步群配置 (补全/清理)...");

        Set<Long> currentOnlineGroups = GroupList.fetchAllGroupIds();

        boolean isUpdated = false;

        for (Long groupId : currentOnlineGroups) {
            groupConfigCache.computeIfAbsent(groupId, k -> new HashMap<>());
            Map<String, Boolean> groupSettings = groupConfigCache.get(groupId);

            for (Map.Entry<String, Boolean> featureEntry : registeredFeatures.entrySet()) {
                String featureName = featureEntry.getKey();
                boolean defValue = featureEntry.getValue();

                if (!groupSettings.containsKey(featureName)) {
                    groupSettings.put(featureName, defValue);
                    isUpdated = true;
                    System.out.println("[Config] 为群 " + groupId + " 补全: " + featureName + "=" + defValue);
                }
            }
        }


        Iterator<Long> iterator = groupConfigCache.keySet().iterator();
        while (iterator.hasNext()) {
            Long cachedGroupId = iterator.next();
            if (!currentOnlineGroups.contains(cachedGroupId)) {
                // 从缓存中移除
                iterator.remove();
                isUpdated = true;
                System.out.println("[Config] 检测到已退出群 " + cachedGroupId + "，自动清理残留配置。");
            }
        }

        if (isUpdated) {
            saveConfigToFile();
            System.out.println("[Config] 配置同步完成 (有变动已保存)。");
        } else {
            System.out.println("[Config] 配置同步完成 (无变动)。");
        }
    }

    public static boolean isFeatureEnabled(long groupId, String featureName) {
        Map<String, Boolean> settings = groupConfigCache.get(groupId);
        if (settings == null) {
            return registeredFeatures.getOrDefault(featureName, false);
        }
        return settings.getOrDefault(featureName, registeredFeatures.getOrDefault(featureName, false));
    }

    public static synchronized void toggleFeature(long groupId, String featureName) {
        groupConfigCache.computeIfAbsent(groupId, k -> new HashMap<>());
        Map<String, Boolean> settings = groupConfigCache.get(groupId);

        boolean current = settings.getOrDefault(featureName, registeredFeatures.getOrDefault(featureName, false));
        settings.put(featureName, !current);

        saveConfigToFile();
    }

    public static void manualSave() {
        saveConfigToFile();
    }

    public static List<String> getFeatureList() {
        return new ArrayList<>(registeredFeatures.keySet());
    }

    public static Map<Long, Boolean> getStatusMapForFeature(String featureName) {
        Map<Long, Boolean> statusMap = new HashMap<>();
        boolean def = registeredFeatures.getOrDefault(featureName, false);
        for (Map.Entry<Long, Map<String, Boolean>> entry : groupConfigCache.entrySet()) {
            statusMap.put(entry.getKey(), entry.getValue().getOrDefault(featureName, def));
        }
        return statusMap;
    }

    private static void loadConfigFromFile() {
        File file = new File(CONFIG_FILE);
        if (!file.exists()) return;
        try {
            groupConfigCache = jsonMapper.readValue(file, new TypeReference<ConcurrentHashMap<Long, Map<String, Boolean>>>() {});
        } catch (IOException e) {
            System.err.println("[Config] 配置文件读取失败: " + e.getMessage());
            groupConfigCache = new ConcurrentHashMap<>();
        }
    }

    private static void saveConfigToFile() {
        try {
            jsonMapper.writeValue(new File(CONFIG_FILE), groupConfigCache);
        } catch (IOException e) {
            System.err.println("[Config] 保存失败: " + e.getMessage());
        }
    }
}