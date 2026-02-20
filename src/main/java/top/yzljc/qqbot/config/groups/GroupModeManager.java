package top.yzljc.qqbot.config.groups;

import com.fasterxml.jackson.databind.JsonNode;
import top.yzljc.qqbot.botkits.userinfo.GetGroupName;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.botkits.message.MessageSender;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GroupModeManager {

    private static final Logger log = LoggerFactory.getLogger(GroupModeManager.class);
    static Settings settings = Config.getInstance();
    private static final List<Long> ADMIN_LIST = settings.getAdminUids();
    private static final Map<Long, String> userSession = new ConcurrentHashMap<>();

    private static final Map<Long, List<?>> selectionCache = new ConcurrentHashMap<>();

    public static void process(JsonNode json) {
        String postType = json.path("post_type").asText();
        if (!"message".equals(postType)) return;

        long userId = json.path("user_id").asLong();
        long groupId = json.path("group_id").asLong();
        String rawMsg = json.path("raw_message").asText().trim();

        if (!ADMIN_LIST.contains(userId)) return;

        if ("/save".equalsIgnoreCase(rawMsg)) {
            GroupConfigManager.manualSave();
            clearSession(userId);
            MessageSender.sendGroupMessage(groupId, "✅ 配置已保存，并退出了配置模式");
            return;
        }

        if ("/cfg".equalsIgnoreCase(rawMsg)) {
            clearSession(userId);
            userSession.put(userId, "FEATURE_ROOT");
            sendFeatureRootList(groupId, userId);
            return;
        }

        if ("/gcfg".equalsIgnoreCase(rawMsg)) {
            clearSession(userId);
            userSession.put(userId, "GROUP_ROOT");
            sendGroupRootList(groupId, userId);
            return;
        }

        if (rawMsg.startsWith("#")) {
            String session = userSession.get(userId);
            if (session == null) return; // 未在配置模式中

            String content = rawMsg.substring(1).trim();

            // 返回/退出
            if ("0".equals(content)) {
                if (session.startsWith("FEATURE:") || session.startsWith("GROUP:")) {
                    // 如果在二级菜单，按0返回上一级
                    if (session.startsWith("FEATURE:")) {
                        userSession.put(userId, "FEATURE_ROOT");
                        sendFeatureRootList(groupId, userId);
                    } else {
                        userSession.put(userId, "GROUP_ROOT");
                        sendGroupRootList(groupId, userId);
                    }
                } else {
                    // 如果在根菜单，按0退出
                    clearSession(userId);
                    MessageSender.sendGroupMessage(groupId, "已退出配置模式");
                }
                return;
            }

            try {
                int index = Integer.parseInt(content);
                List<?> cache = selectionCache.get(userId);

                if (cache == null || index < 1 || index > cache.size()) {
                    MessageSender.sendGroupMessage(groupId, "序号无效，请输入列表中的数字");
                    return;
                }

                Object selectedObj = cache.get(index - 1);

                if ("FEATURE_ROOT".equals(session)) {
                    String selectedFeature = (String) selectedObj;
                    userSession.put(userId, "FEATURE:" + selectedFeature);
                    sendGroupStatusForFeature(groupId, userId, selectedFeature);

                } else if ("GROUP_ROOT".equals(session)) {
                    Long selectedGroup = (Long) selectedObj;
                    userSession.put(userId, "GROUP:" + selectedGroup);
                    sendFeatureStatusForGroup(groupId, userId, selectedGroup);

                } else if (session.startsWith("FEATURE:")) {
                    String feature = session.split(":")[1];
                    Long targetGroup = (Long) selectedObj;
                    toggleAndRefresh(groupId, targetGroup, feature, true); // true表示刷新群列表

                } else if (session.startsWith("GROUP:")) {
                    long targetGroup = Long.parseLong(session.split(":")[1]);
                    String targetFeature = (String) selectedObj;
                    toggleAndRefresh(groupId, targetGroup, targetFeature, false); // false表示刷新功能列表
                }

            } catch (NumberFormatException e) {
                // 忽略非数字
            } catch (Exception e) {
                log.error("Config process error", e);
                clearSession(userId);
                MessageSender.sendGroupMessage(groupId, "发生错误，会话已重置");
            }
        }
    }

    private static void clearSession(long userId) {
        userSession.remove(userId);
        selectionCache.remove(userId);
    }

    private static void sendFeatureRootList(long fromGroup, long userId) {
        List<String> features = GroupConfigManager.getFeatureList();
        selectionCache.put(userId, features);

        StringBuilder sb = new StringBuilder();
        sb.append("【功能配置模式 (/cfg)】\n");
        sb.append("请发送 #序号 选择功能：\n");
        sb.append("------------------\n");
        for (int i = 0; i < features.size(); i++) {
            sb.append("#").append(i + 1).append("  ").append(features.get(i)).append("\n");
        }
        sb.append("------------------\n发送 #0 退出");
        MessageSender.sendGroupMessage(fromGroup, sb.toString());
    }

    private static void sendGroupRootList(long fromGroup, long userId) {
        // 聚合所有已知群号
        Set<Long> allGroups = new HashSet<>();
        List<String> features = GroupConfigManager.getFeatureList();
        for (String f : features) {
            allGroups.addAll(GroupConfigManager.getStatusMapForFeature(f).keySet());
        }

        List<Long> groupList = new ArrayList<>(allGroups);
        Collections.sort(groupList);
        selectionCache.put(userId, groupList);

        StringBuilder sb = new StringBuilder();
        sb.append("【群聊配置模式 (/gcfg)】\n");
        sb.append("请发送 #序号 选择群聊：\n");
        sb.append("------------------\n");
        for (int i = 0; i < groupList.size(); i++) {
            Long gid = groupList.get(i);
            sb.append("#").append(i + 1).append("  ")
                    .append(fetchGroupName(gid)).append("(").append(gid).append(")\n");
        }
        sb.append("------------------\n发送 #0 退出");
        MessageSender.sendGroupMessage(fromGroup, sb.toString());
    }

    private static void sendGroupStatusForFeature(long fromGroup, long userId, String feature) {
        Map<Long, Boolean> map = GroupConfigManager.getStatusMapForFeature(feature);
        List<Long> groups = new ArrayList<>(map.keySet());
        Collections.sort(groups);
        selectionCache.put(userId, groups);

        StringBuilder sb = new StringBuilder();
        sb.append("【配置功能：").append(feature).append("】\n");
        sb.append("发送 #序号 切换开关，#0 返回：\n------------------\n");
        for (int i = 0; i < groups.size(); i++) {
            Long gid = groups.get(i);
            boolean on = map.get(gid);
            sb.append("#").append(i + 1).append(" ")
                    .append(fetchGroupName(gid)).append(" : ").append(on ? "✅" : "❌").append("\n");
        }
        MessageSender.sendGroupMessage(fromGroup, sb.toString());
    }

    private static void sendFeatureStatusForGroup(long fromGroup, long userId, long targetGroup) {
        List<String> features = GroupConfigManager.getFeatureList();
        selectionCache.put(userId, features);

        String groupName = fetchGroupName(targetGroup);
        StringBuilder sb = new StringBuilder();
        sb.append("【配置群：").append(groupName).append("(").append(targetGroup).append(")").append("】\n");
        sb.append("发送 #序号 切换开关，#0 返回：\n------------------\n");
        for (int i = 0; i < features.size(); i++) {
            String f = features.get(i);
            boolean on = GroupConfigManager.isFeatureEnabled(targetGroup, f);
            sb.append("#").append(i + 1).append(" ")
                    .append(f).append(" : ").append(on ? "✅" : "❌").append("\n");
        }
        MessageSender.sendGroupMessage(fromGroup, sb.toString());
    }

    private static void toggleAndRefresh(long fromGroup, long targetGroupId, String feature, boolean refreshGroupList) {
        GroupConfigManager.toggleFeature(targetGroupId, feature);
        boolean newState = GroupConfigManager.isFeatureEnabled(targetGroupId, feature);

        String msg = String.format("已%s群 %d 的 [%s] 功能", newState ? "开启" : "关闭", targetGroupId, feature);
        MessageSender.sendGroupMessage(fromGroup, msg);

    }

    private static String fetchGroupName(long groupId) {
        try {
            return GetGroupName.getGroupName(groupId);
        } catch (Exception e) {
            return "未知群聊";
        }
    }
}