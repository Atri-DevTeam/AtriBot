package top.yzljc.atribot.platform.napcat.groupfunction;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.napcat.GroupInformation;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatGroupMessageEvent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class GroupModeManager implements Listener {

    private static final List<String> ADMIN_LIST = Config.getInstance().getNapcatAdminUins();
    private static final Map<String, String> userSession = new ConcurrentHashMap<>();
    private static final Map<String, List<?>> selectionCache = new ConcurrentHashMap<>();

    @EventHandler
    public void onGroupMessage(NapcatGroupMessageEvent event) {

        String userId = event.getUser().getUserId();
        String groupId = event.getGroupId();
        String rawMsg = event.getMessage().getContent().trim();

        if (!ADMIN_LIST.contains(userId)) return;

        if ("/save".equalsIgnoreCase(rawMsg)) {
            GroupConfigManager.manualSave();
            clearSession(userId);
            GroupMessage.chatMessage(groupId, "✅ 配置已保存，并退出了配置模式");
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
            if (session == null) return;

            String content = rawMsg.substring(1).trim();

            if ("0".equals(content)) {
                if (session.startsWith("FEATURE:") || session.startsWith("GROUP:")) {
                    if (session.startsWith("FEATURE:")) {
                        userSession.put(userId, "FEATURE_ROOT");
                        sendFeatureRootList(groupId, userId);
                    } else {
                        userSession.put(userId, "GROUP_ROOT");
                        sendGroupRootList(groupId, userId);
                    }
                } else {
                    clearSession(userId);
                    GroupMessage.chatMessage(groupId, "已退出配置模式");
                }
                return;
            }

            try {
                int index = Integer.parseInt(content);
                List<?> cache = selectionCache.get(userId);

                if (cache == null || index < 1 || index > cache.size()) {
                    GroupMessage.chatMessage(groupId, "序号无效，请输入列表中的数字");
                    return;
                }

                Object selectedObj = cache.get(index - 1);

                if ("FEATURE_ROOT".equals(session)) {
                    String selectedFeature = (String) selectedObj;
                    userSession.put(userId, "FEATURE:" + selectedFeature);
                    sendGroupStatusForFeature(groupId, userId, selectedFeature);

                } else if ("GROUP_ROOT".equals(session)) {
                    String selectedGroup = (String) selectedObj;
                    userSession.put(userId, "GROUP:" + selectedGroup);
                    sendFeatureStatusForGroup(groupId, userId, selectedGroup);

                } else if (session.startsWith("FEATURE:")) {
                    String feature = session.split(":")[1];
                    String targetGroup = (String) selectedObj;
                    toggleAndRefresh(groupId, targetGroup, feature, true);

                } else if (session.startsWith("GROUP:")) {
                    String targetGroup = session.split(":")[1];
                    String targetFeature = (String) selectedObj;
                    toggleAndRefresh(groupId, targetGroup, targetFeature, false);
                }

            } catch (NumberFormatException e) {
                // 忽略非数字
            } catch (Exception e) {
                log.error("处理配置选择时发生错误: " + e.getMessage(), e);
                clearSession(userId);
                GroupMessage.chatMessage(groupId, "发生错误，会话已重置");
            }
        }
    }

    private static void clearSession(String userId) {
        userSession.remove(userId);
        selectionCache.remove(userId);
    }

    private static void sendFeatureRootList(String fromGroup, String userId) {
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
        GroupMessage.chatMessage(fromGroup, sb.toString());
    }

    private static void sendGroupRootList(String fromGroup, String userId) {
        Set<String> allGroups = new HashSet<>();
        List<String> features = GroupConfigManager.getFeatureList();
        for (String f : features) {
            allGroups.addAll(GroupConfigManager.getStatusMapForFeature(f).keySet());
        }

        List<String> groupList = new ArrayList<>(allGroups);
        Collections.sort(groupList);
        selectionCache.put(userId, groupList);

        StringBuilder sb = new StringBuilder();
        sb.append("【群聊配置模式 (/gcfg)】\n");
        sb.append("请发送 #序号 选择群聊：\n");
        sb.append("------------------\n");
        for (int i = 0; i < groupList.size(); i++) {
            String gid = groupList.get(i);
            sb.append("#").append(i + 1).append("  ")
                    .append(fetchGroupName(gid)).append("(").append(gid).append(")\n");
        }
        sb.append("------------------\n发送 #0 退出");
        GroupMessage.chatMessage(fromGroup, sb.toString());
    }

    private static void sendGroupStatusForFeature(String fromGroup, String userId, String feature) {
        Map<String, Boolean> map = GroupConfigManager.getStatusMapForFeature(feature);
        List<String> groups = new ArrayList<>(map.keySet());
        Collections.sort(groups);
        selectionCache.put(userId, groups);

        StringBuilder sb = new StringBuilder();
        sb.append("【配置功能：").append(feature).append("】\n");
        sb.append("发送 #序号 切换开关，#0 返回：\n------------------\n");
        for (int i = 0; i < groups.size(); i++) {
            String gid = groups.get(i);
            boolean on = map.get(gid);
            sb.append("#").append(i + 1).append(" ")
                    .append(fetchGroupName(gid)).append(" : ").append(on ? "✅" : "❌").append("\n");
        }
        GroupMessage.chatMessage(fromGroup, sb.toString());
    }

    private static void sendFeatureStatusForGroup(String fromGroup, String userId, String targetGroup) {
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
        GroupMessage.chatMessage(fromGroup, sb.toString());
    }

    private static void toggleAndRefresh(String fromGroup, String targetGroupId, String feature, boolean refreshGroupList) {
        GroupConfigManager.toggleFeature(targetGroupId, feature);
        boolean newState = GroupConfigManager.isFeatureEnabled(targetGroupId, feature);

        String msg = String.format("已%s群 %s 的 [%s] 功能", newState ? "开启" : "关闭", targetGroupId, feature);
        GroupMessage.chatMessage(fromGroup, msg);
    }

    private static String fetchGroupName(String groupId) {
        try {
            return GroupInformation.getGroupName(groupId);
        } catch (Exception e) {
            return "未知群聊";
        }
    }
}
