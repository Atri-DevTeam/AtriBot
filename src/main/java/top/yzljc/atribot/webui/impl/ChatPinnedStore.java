package top.yzljc.atribot.webui.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.Properties;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName ChatPinnedStore
 * @Created_at 2026/07/26
 * @Project AtriMeow
 * @Package top.yzljc.atribot.webui.impl
 */
@Slf4j
public class ChatPinnedStore {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Set<String> pinned = new LinkedHashSet<>();
    private static boolean loaded = false;

    public static synchronized List<String> list() {
        ensureLoaded();
        return new ArrayList<>(pinned);
    }

    public static synchronized boolean isPinned(String key) {
        ensureLoaded();
        return pinned.contains(key);
    }

    /**
     * @return 操作后该会话是否处于置顶状态
     */
    public static synchronized boolean setPinned(String key, boolean value) {
        ensureLoaded();
        if (isBlank(key)) {
            return false;
        }
        boolean changed = value ? pinned.add(key) : pinned.remove(key);
        if (changed) {
            save();
        }
        return value;
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        File file = new File(Properties.CHAT_PINNED);
        if (!file.exists()) {
            return;
        }
        try {
            JsonNode root = mapper.readTree(file);
            JsonNode list = root.path("pinned");
            if (list.isArray()) {
                for (JsonNode node : list) {
                    String key = node.asText(null);
                    if (!isBlank(key)) {
                        pinned.add(key);
                    }
                }
            }
            log.info("已加载会话置顶配置，共 {} 条", pinned.size());
        } catch (IOException e) {
            log.error("加载会话置顶配置失败: {}", e.getMessage(), e);
        }
    }

    private static void save() {
        try {
            File file = new File(Properties.CHAT_PINNED);
            File parentDir = file.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("pinned", new ArrayList<>(pinned));
            mapper.writerWithDefaultPrettyPrinter().writeValue(file, data);
        } catch (IOException e) {
            log.error("保存会话置顶配置失败: {}", e.getMessage(), e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
