package top.yzljc.atribot.chat.official.moderation;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Properties;

import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
* @Author AndyOctopus
* @ClassName GroupModerationStore
* @Created_at 2026/08/20
* @Project AtriMeow
* @Package top.yzljc.atribot.chat.official.moderation
*/
@Slf4j
public final class GroupModerationStore {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, GroupModerationSettings> CACHE = new LinkedHashMap<>();
    private static boolean loaded = false;

    public static synchronized GroupModerationSettings get(String groupOpenId) {
        ensureLoaded();
        GroupModerationSettings settings = CACHE.get(groupOpenId);
        return settings == null ? new GroupModerationSettings() : settings;
    }

    public static synchronized void save(String groupOpenId, GroupModerationSettings settings) {
        ensureLoaded();
        CACHE.put(groupOpenId, settings);
        persist();
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = new File(Properties.GROUP_MODERATION_CONFIG);
        if (!file.exists()) {
            return;
        }
        try {
            Map<String, GroupModerationSettings> data = MAPPER.readValue(file,
                    MAPPER.getTypeFactory().constructMapType(LinkedHashMap.class, String.class, GroupModerationSettings.class));
            CACHE.putAll(data);
            log.info("已加载群管系统配置，共 {} 个群", CACHE.size());
        } catch (IOException e) {
            log.error("加载群管系统配置失败: {}", e.getMessage(), e);
        }
    }

    private static void persist() {
        try {
            File file = new File(Properties.GROUP_MODERATION_CONFIG);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(file, CACHE);
        } catch (IOException e) {
            log.error("保存群管系统配置失败: {}", e.getMessage(), e);
        }
    }
}
