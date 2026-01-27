package top.yzljc.qqbot.config.groups;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import top.yzljc.qqbot.config.Config;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.config.ConfigFile;

/**
 * &#064;群功能配置管理核心文件
 * &#064;Author：YZ_Ljc_
 */
public class GroupConfigManager {

    private static final Logger log = LoggerFactory.getLogger(GroupConfigManager.class);

    private static final String CONFIG_FILE = ConfigFile.GROUP_CONFIG.getFileName();
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
        log.info("正在同步群配置（补全/清理）……");

        Set<Long> currentOnlineGroups = GroupList.fetchAllGroupIds();

        boolean isDebug = Config.getInstance().isDebugMode();

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
                    log.info("为群 {} 补全：{}={}", groupId, featureName, defValue);
                }
            }
        }


        Iterator<Long> iterator = groupConfigCache.keySet().iterator();
        while (iterator.hasNext()) {
            Long cachedGroupId = iterator.next();

            if (!isDebug && !currentOnlineGroups.contains(cachedGroupId)) {
                // 从缓存中移除
                iterator.remove();
                isUpdated = true;
                log.info("检测到已退出群 {}，自动清理残留配置", cachedGroupId);
            }
        }

        if (isUpdated) {
            saveConfigToFile();
            log.info("配置同步完成（有变动已保存）");
        } else {
            log.info("配置同步完成（无变动）");
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
            log.error("配置文件读取失败：{}", e.getMessage());
            groupConfigCache = new ConcurrentHashMap<>();
        }
    }

    private static void saveConfigToFile() {
        try {
            jsonMapper.writeValue(new File(CONFIG_FILE), groupConfigCache);
        } catch (IOException e) {
            log.error("配置文件保存失败：{}", e.getMessage());
        }
    }
}
