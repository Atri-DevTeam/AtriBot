package top.yzljc.qqbot.utils.deprecated;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.botservice.message.MessageSender;

import java.io.File;
import java.io.IOException;
import java.util.*;

@Deprecated(since = "GroupModeManager更新", forRemoval = true)
public class SendNewsGroups {
    private static final String GROUPS_FILE = "record_groups.json";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    // 只读的群号列表，随文件变化而自动更新
    public static List<Long> TARGET_GROUPS_MC = Collections.unmodifiableList(new ArrayList<>());
    public static List<Long> TARGET_GROUPS_HYP = Collections.unmodifiableList(new ArrayList<>());

    // 可用标签
    public static final Set<String> ALLOWED_TAGS = new HashSet<>(Arrays.asList("mc", "hyp"));

    static Settings settings = Config.getInstance();
    private static final List<Long> admins = settings.getAdminUids();

    static {
        reloadGroups();
    }

    /**
     * 一句话外部调用，自动判断和群反馈
     */
    public static void processAcCommand(JsonNode json) {
        String rawMessage = json.path("raw_message").asText("");
        long userId = json.path("user_id").asLong();
        long groupId = json.path("group_id").asLong();

        if (rawMessage.startsWith("/ac list")) {
            // 列出所有群及标签，仅管理员可见
            if (!admins.contains(userId)) {
                // 调用 MessageSender
                MessageSender.sendGroupMessage(groupId, "You have no permission to view full list!");
                return;
            }
            String resp = buildListMessage();
            MessageSender.sendGroupMessage(groupId, resp);
        } else if (rawMessage.startsWith("/acr")) {
            String resp = processAdminRemoveCommand(rawMessage, userId);
            MessageSender.sendGroupMessage(groupId, resp);
        } else if (rawMessage.startsWith("/ac")) {
            String resp = processAdminAcCommand(rawMessage, userId);
            MessageSender.sendGroupMessage(groupId, resp);
        }
    }

    /** 群组列表展示 */
    private static String buildListMessage() {
        StringBuilder sb = new StringBuilder();
        sb.append("【群号标签记录】\n");
        File file = new File(GROUPS_FILE);
        if (!file.exists()) {
            sb.append("暂无群聊记录。");
        } else {
            try {
                JsonNode root = objectMapper.readTree(file);
                if (root.isArray() && root.size() > 0) {
                    for (JsonNode node : root) {
                        long group = node.path("group").asLong();
                        String tag = node.path("tag").asText();
                        sb.append("群: ").append(group).append(" | 标签: ").append(tag).append("\n");
                    }
                } else {
                    sb.append("暂无群聊记录。");
                }
            } catch (IOException e) {
                sb.append("记录解析失败: ").append(e.getMessage());
            }
        }
        return sb.toString();
    }

    /** 加载群组配置文件 */
    public static synchronized void reloadGroups() {
        List<Long> mcList = new ArrayList<>();
        List<Long> hypList = new ArrayList<>();
        File file = new File(GROUPS_FILE);
        if (!file.exists()) {
            saveGroups(mcList, hypList); // 首次自动写空文件
        } else {
            try {
                JsonNode root = objectMapper.readTree(file);
                if (root.isArray()) {
                    for (JsonNode node : root) {
                        String tag = node.path("tag").asText();
                        long group = node.path("group").asLong();
                        if ("mc".equalsIgnoreCase(tag)) mcList.add(group);
                        if ("hyp".equalsIgnoreCase(tag)) hypList.add(group);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        TARGET_GROUPS_MC = Collections.unmodifiableList(mcList);
        TARGET_GROUPS_HYP = Collections.unmodifiableList(hypList);
    }

    /** 保存群组配置文件 */
    public static synchronized void saveGroups(List<Long> mcList, List<Long> hypList) {
        ArrayNode arr = objectMapper.createArrayNode();
        for (Long v : mcList) {
            arr.add(objectMapper.createObjectNode().put("group", v).put("tag", "mc"));
        }
        for (Long v : hypList) {
            arr.add(objectMapper.createObjectNode().put("group", v).put("tag", "hyp"));
        }
        try {
            objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(GROUPS_FILE), arr);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /** 管理员通过/ac添加群聊，如/ac 123456 mc */
    public static String processAdminAcCommand(String rawMessage, long userId) {
        if (!admins.contains(userId)) {
            return "You have no permission to modify announce groups!";
        }
        String[] parts = rawMessage.trim().split("\\s+");
        if (parts.length != 3 || !parts[0].equalsIgnoreCase("/ac")) {
            return "格式错误: /ac <群号> <mc|hyp>";
        }
        long gid;
        try {
            gid = Long.parseLong(parts[1]);
        } catch (Exception e) {
            return "群号请填写数字";
        }
        String tag = parts[2].toLowerCase();
        if (!ALLOWED_TAGS.contains(tag)) {
            return "标签不合法，请用 mc 或 hyp";
        }

        // 加载当前所有
        List<Long> mcList = new ArrayList<>(TARGET_GROUPS_MC);
        List<Long> hypList = new ArrayList<>(TARGET_GROUPS_HYP);

        boolean changed = false;
        if (tag.equals("mc") && !mcList.contains(gid)) {
            mcList.add(gid);
            changed = true;
        } else if (tag.equals("hyp") && !hypList.contains(gid)) {
            hypList.add(gid);
            changed = true;
        }
        if (!changed) {
            return "该群已经存在于 " + tag + " 推广列表";
        }
        saveGroups(mcList, hypList);
        reloadGroups();
        return "添加成功！已将群 " + gid + " 加入 " + tag + " 新闻推广列表";
    }

    /** 管理员通过/acr删除群聊，如/acr 123456 mc */
    public static String processAdminRemoveCommand(String rawMessage, long userId) {
        if (!admins.contains(userId)) {
            return "You have no permission to modify announce groups!";
        }
        String[] parts = rawMessage.trim().split("\\s+");
        if (parts.length != 3 || !parts[0].equalsIgnoreCase("/acr")) {
            return "格式错误: /acr <群号> <mc|hyp>";
        }
        long gid;
        try {
            gid = Long.parseLong(parts[1]);
        } catch (Exception e) {
            return "群号请填写数字";
        }
        String tag = parts[2].toLowerCase();
        if (!ALLOWED_TAGS.contains(tag)) {
            return "标签不合法，请用 mc 或 hyp";
        }

        // 加载当前所有
        List<Long> mcList = new ArrayList<>(TARGET_GROUPS_MC);
        List<Long> hypList = new ArrayList<>(TARGET_GROUPS_HYP);

        boolean changed = false;
        if (tag.equals("mc") && mcList.contains(gid)) {
            mcList.remove(gid);
            changed = true;
        } else if (tag.equals("hyp") && hypList.contains(gid)) {
            hypList.remove(gid);
            changed = true;
        }
        if (!changed) {
            return "该群不存在于 " + tag + " 推广列表";
        }
        saveGroups(mcList, hypList);
        reloadGroups();
        return "删除成功！已将群 " + gid + " 从 " + tag + " 新闻推广列表移除";
    }
}