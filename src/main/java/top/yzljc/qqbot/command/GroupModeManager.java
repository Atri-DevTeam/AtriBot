package top.yzljc.qqbot.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.GroupConfigManager;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.messages.MessageSender;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class GroupModeManager {
    static Settings settings = Config.getInstance();
    private static final List<Long> ADMIN_LIST = settings.getAdminUids();
    private static final Map<Long, String> userSession = new ConcurrentHashMap<>();
    private static final Map<Long, List<Long>> groupSelectionCache = new ConcurrentHashMap<>();
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    public static void process(JsonNode json) {
        String postType = json.path("post_type").asText();
        if (!"message".equals(postType)) return;

        long userId = json.path("user_id").asLong();
        long groupId = json.path("group_id").asLong();
        String rawMsg = json.path("raw_message").asText().trim();

        if (!ADMIN_LIST.contains(userId)) return;

        if ("/save".equalsIgnoreCase(rawMsg)) {
            GroupConfigManager.manualSave();
            userSession.remove(userId);
            groupSelectionCache.remove(userId);
            MessageSender.sendGroupMessage(groupId, "✅ 配置已保存，并退出了配置模式");
            return;
        }

        if ("/cfg".equalsIgnoreCase(rawMsg)) {
            userSession.remove(userId); // 重置会话
            groupSelectionCache.remove(userId);
            sendFeatureList(groupId, userId);
            return;
        }

        if (rawMsg.startsWith("#")) {
            String content = rawMsg.substring(1).trim(); // 去掉 #

            String currentFeature = userSession.get(userId);

            if (currentFeature == null) {
                try {
                    int index = Integer.parseInt(content);
                    selectFeature(groupId, userId, index);
                } catch (NumberFormatException e) {
                }
            } else {
                if ("0".equals(content)) {
                    userSession.remove(userId);
                    groupSelectionCache.remove(userId);
                    sendFeatureList(groupId, userId);
                } else {
                    try {
                        int index = Integer.parseInt(content); // 这里现在解析的是序号

                        // 获取该管理员当前的群列表缓存
                        List<Long> cachedGroups = groupSelectionCache.get(userId);

                        if (cachedGroups != null && index > 0 && index <= cachedGroups.size()) {
                            // 通过序号找到真实的群号
                            long targetGroupId = cachedGroups.get(index - 1);
                            toggleGroup(groupId, targetGroupId, currentFeature);
                        } else {
                            MessageSender.sendGroupMessage(groupId, "序号无效，请输入列表中的数字。");
                        }
                    } catch (NumberFormatException e) {
                        // 忽略
                    }
                }
            }
        }
    }

    private static void sendFeatureList(long fromGroup, long userId) {
        List<String> features = GroupConfigManager.getFeatureList();
        StringBuilder sb = new StringBuilder();
        sb.append("【Bot 功能配置菜单】\n");
        sb.append("请发送 #序号 进入对应管理：\n");
        sb.append("------------------\n");

        for (int i = 0; i < features.size(); i++) {
            sb.append("#").append(i + 1).append("  ").append(features.get(i)).append("\n");
        }
        sb.append("------------------\n");
        sb.append("发送 /save 退出或重置");

        MessageSender.sendGroupMessage(fromGroup, sb.toString());
    }

    private static void selectFeature(long fromGroup, long userId, int index) {
        List<String> features = GroupConfigManager.getFeatureList();
        if (index < 1 || index > features.size()) {
            MessageSender.sendGroupMessage(fromGroup, "序号不存在，请重新输入。");
            return;
        }

        String selectedFeature = features.get(index - 1);
        userSession.put(userId, selectedFeature); // 更新会话状态

        Map<Long, Boolean> statusMap = GroupConfigManager.getStatusMapForFeature(selectedFeature);

        List<Long> groupIds = new ArrayList<>(statusMap.keySet());
        Collections.sort(groupIds);
        groupSelectionCache.put(userId, groupIds);

        StringBuilder sb = new StringBuilder();
        sb.append("【配置：").append(selectedFeature).append("】\n");
        sb.append("发送 #群号 切换开关，发送 #0 返回：\n");
        sb.append("------------------\n");

        for (int i = 0; i < groupIds.size(); i++) {
            Long gid = groupIds.get(i);
            Boolean enabled = statusMap.get(gid);

            String name = fetchGroupName(gid);

            sb.append("#").append(i + 1).append(" ")
                    .append(name).append("(").append(gid).append(") : ")
                    .append(enabled ? "✅" : "❌")
                    .append("\n");
        }

        MessageSender.sendGroupMessage(fromGroup, sb.toString());
    }

    private static void toggleGroup(long fromGroup, long targetGroupId, String featureName) {
        // 执行切换
        GroupConfigManager.toggleFeature(targetGroupId, featureName);
        boolean newState = GroupConfigManager.isFeatureEnabled(targetGroupId, featureName);

        String reply = String.format("已%s群 %d 的 [%s] 功能。",
                newState ? "开启" : "关闭", targetGroupId, featureName);

        MessageSender.sendGroupMessage(fromGroup, reply);
    }

    private static String fetchGroupName(long groupId) {
        String apiUrl = settings.getHttpUrl() + "/get_group_info";
        String groupName = "未知群聊";

        try {
            Map<String, String> reqData = new HashMap<>();
            reqData.put("group_id", String.valueOf(groupId));
            String payload = jsonMapper.writeValueAsString(reqData);

            HttpURLConnection conn = (HttpURLConnection) new URL(apiUrl).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(5000);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");

            try (OutputStream out = conn.getOutputStream()) {
                out.write(payload.getBytes(StandardCharsets.UTF_8));
            }

            if (conn.getResponseCode() == 200) {
                try (InputStream in = conn.getInputStream()) {
                    JsonNode root = jsonMapper.readTree(in);
                    if (root.has("data")) {
                        JsonNode data = root.get("data");
                        if (data.has("group_name")) {
                            groupName = data.get("group_name").asText();
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[GroupModeManager] 获取群 " + groupId + " 名称失败: " + e.getMessage());
        }
        return groupName;
    }
}