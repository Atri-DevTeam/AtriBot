package top.yzljc.atribot.function.napcat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.chat.napcat.GroupInformation;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.impl.MessageSegment;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.function.napcat.impl.MojiraIssueSummarizer;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.service.taskscheduler.DefaultTaskSchedule;
import top.yzljc.atribot.service.taskscheduler.ScheduleMode;
import top.yzljc.atribot.service.taskscheduler.ScheduledTask;
import top.yzljc.atribot.service.taskscheduler.TaskSchedule;
import top.yzljc.atribot.utils.FormatTools;

import java.io.*;
import java.util.*;

/**
 * @Author YZ_Ljc_
 * @ClassName CheckMojira
 * @Created_at 2026/06/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.napcat
 */
public final class MojiraStatus implements CommandExecutor, ScheduledTask {

    private static final Logger log = LoggerFactory.getLogger(MojiraStatus.class);
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    private static final String CACHE_FILE = Properties.MOJIRA_CACHE;
    private static final String FEATURE_KEY = "mojira_tracker";
    private static final String MOJIRA_API = "https://bugs.mojang.com/api/jql-search-post";
    private static final int MAX_RESULTS = 25;

    private static final int MAX_CACHE_SIZE = 100;
    private static final Set<String> knownIssues = Collections.synchronizedSet(new LinkedHashSet<>());
    private static volatile boolean running = false;

    static {
        loadCache();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.NAPCAT_GROUP) return true;
        if (!sender.hasPermission()) return true;
        checkNewIssues();
        sender.sendMessage("已触发 Mojira 手动检查!");
        log.info("[Mojira] 用户 {} 在群 {} 触发了手动检查", sender.getUserId(), sender.getGroupId());
        return true;
    }

    @Override
    public TaskSchedule schedule() {
        return new DefaultTaskSchedule().setMode(ScheduleMode.half_hour);
    }

    @Override
    public void run() {
        checkNewIssues();
    }

    public record MojiraIssue(
            String id,
            String key,
            String summary,
            String description,
            String status,
            String created,
            String updated,
            List<String> versions,
            List<Attachment> attachments
    ) {
    }

    public record Attachment(
            String id,
            String filename,
            String url
    ) {
    }

    private static void loadCache() {
        File file = new File(CACHE_FILE);
        if (!file.exists()) {
            knownIssues.clear();
            saveCache();
            log.info("Mojira 缓存文件不存在，已创建初始缓存文件");
            return;
        }
        try {
            JsonNode root = jsonMapper.readTree(file);
            knownIssues.clear();
            for (JsonNode node : root.path("issues")) {
                knownIssues.add(node.asText());
            }
            log.info("Mojira 缓存加载成功, 缓存 Issue 数={}", knownIssues.size());
        } catch (IOException e) {
            log.warn("Mojira 缓存文件读取失败，将重置缓存: {}", e.getMessage());
            knownIssues.clear();
            saveCache();
        }
    }

    private static void saveCache() {
        try {
            File file = new File(CACHE_FILE);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Map<String, Object> cache = new LinkedHashMap<>();
            cache.put("issues", new ArrayList<>(knownIssues));
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(file, cache);
            log.info("Mojira 缓存已保存, 共 {} 条记录", knownIssues.size());
        } catch (IOException e) {
            log.warn("Mojira 缓存保存失败: {}", e.getMessage());
        }
    }

    /**
     * 按插入顺序淘汰最旧记录，缓存不超过 {@link #MAX_CACHE_SIZE} 条
     */
    private static void trimCache() {
        synchronized (knownIssues) {
            if (knownIssues.size() <= MAX_CACHE_SIZE) return;
            Iterator<String> it = knownIssues.iterator();
            int toRemove = knownIssues.size() - MAX_CACHE_SIZE;
            for (int i = 0; i < toRemove && it.hasNext(); i++) {
                it.next();
                it.remove();
            }
            log.info("Mojira 缓存清理完成, 移除了 {} 条旧记录, 当前 {} 条", toRemove, knownIssues.size());
        }
    }

    private static List<MojiraIssue> requestLatestIssues() {
        String requestBody = "{"
                + "\"method\":\"POST\","
                + "\"headers\":{"
                + "\"Accept\":\"application/json\","
                + "\"Content-Type\":\"application/json\""
                + "},"
                + "\"advanced\":false,"
                + "\"search\":\"\","
                + "\"project\":\"MC\","
                + "\"isForge\":false,"
                + "\"sortField\":\"created\","
                + "\"sortAsc\":false,"
                + "\"filter\":\"open\","
                + "\"page\":0,"
                + "\"maxResults\":" + MAX_RESULTS + ","
                + "\"workspaceId\":\"\""
                + "}";

        JsonNode root = HttpService.postJson(MOJIRA_API, requestBody);
        if (root == null) {
            return Collections.emptyList();
        }
        return parseIssues(root);
    }

    private static List<MojiraIssue> parseIssues(JsonNode root) {
        List<MojiraIssue> issues = new ArrayList<>();
        JsonNode issuesNode = root.path("issues");
        if (!issuesNode.isArray()) return issues;

        for (JsonNode issueNode : issuesNode) {
            try {
                String id = issueNode.path("id").asText();
                String key = issueNode.path("key").asText();
                JsonNode fields = issueNode.path("fields");

                String summary = fields.path("summary").asText();
                String description = extractDescription(fields.path("description"));
                String status = fields.path("status").path("name").asText();
                String created = fields.path("created").asText();
                String updated = fields.path("updated").asText();

                List<String> versions = new ArrayList<>();
                for (JsonNode v : fields.path("versions")) {
                    String name = v.path("name").asText();
                    if (name != null && !name.isEmpty()) {
                        versions.add(name);
                    }
                }

                List<Attachment> attachments = new ArrayList<>();
                for (JsonNode att : fields.path("attachment")) {
                    attachments.add(new Attachment(
                            att.path("id").asText(),
                            att.path("filename").asText(),
                            att.path("content").asText()
                    ));
                }

                issues.add(new MojiraIssue(id, key, summary, description, status, created, updated, versions, attachments));
            } catch (Exception e) {
                log.warn("[Mojira API] 解析单个 Issue 失败: {}", e.getMessage());
            }
        }
        return issues;
    }

    private static String extractDescription(JsonNode descriptionNode) {
        try {
            StringBuilder sb = new StringBuilder();
            collectText(descriptionNode.path("content"), sb);
            return sb.toString().trim();
        } catch (Exception e) {
            return "";
        }
    }

    private static void collectText(JsonNode content, StringBuilder sb) {
        if (content == null || !content.isArray()) return;
        for (JsonNode node : content) {
            JsonNode text = node.get("text");
            if (text != null && !text.isNull()) {
                sb.append(text.asText());
            }
            JsonNode children = node.get("content");
            if (children != null && children.isArray()) {
                collectText(children, sb);
            }
        }
    }

    public static void checkNewIssues() {
        synchronized (MojiraStatus.class) {
            if (running) return;
            running = true;
        }
        ThreadManager.execute(() -> {
            try {
                List<MojiraIssue> latestIssues = requestLatestIssues();
                if (latestIssues.isEmpty()) {
                    log.warn("[Mojira] 未能获取到 Issue 列表，跳过本轮检查");
                    return;
                }

                if (knownIssues.isEmpty()) {
                    // 首次启动：仅初始化缓存，不推送
                    for (MojiraIssue issue : latestIssues) {
                        knownIssues.add(issue.key());
                    }
                    trimCache();
                    saveCache();
                    log.info("[Mojira] 初始化完成，缓存了 {} 个已存在的 Issue，不发送推送", knownIssues.size());
                    return;
                }

                int newCount = 0;
                for (MojiraIssue issue : latestIssues) {
                    if (knownIssues.add(issue.key())) {
                        sendIssue(issue);
                        saveCache();
                        newCount++;
                    }
                }

                if (newCount > 0) {
                    log.info("[Mojira] 发现并推送了 {} 个新 Issue", newCount);
                } else {
                    log.info("[Mojira] 没有新 Issue");
                }

                trimCache();
                saveCache();

            } catch (Exception e) {
                log.warn("[Mojira] checkNewIssues 执行异常: {}", e.getMessage());
            } finally {
                running = false;
            }
        });
    }

    private static void sendIssue(MojiraIssue issue) {
        String time = FormatTools.formatMojiraTime(issue.created());
        String link = "https://bugs.mojang.com/browse/" + issue.key();
        String versions = issue.versions().isEmpty() ? "无" : String.join(", ", issue.versions());

        String sb = "编号：" + issue.key() + "\n" +
                "标题：" + issue.summary() + "\n" +
                "状态：" + issue.status() + "\n" +
                "创建时间：" + time + "\n" +
                "影响版本：" + versions + "\n" +
                "链接：" + link;

        String desc = issue.description();
        if (desc == null || desc.isEmpty()) {
            desc = "（无描述）";
        }

        List<MessageSegment> nodes = new ArrayList<>();
        nodes.add(GroupMessage.createTextNode(sb));
        nodes.add(GroupMessage.createTextNode("描述：\n" + desc));

        // AI 中文翻译作为第三条
        if (!desc.equals("（无描述）")) {
            String translated = MojiraIssueSummarizer.translate(issue.key(), issue.summary(), desc);
            if (!translated.equals(desc)) {
                nodes.add(GroupMessage.createTextNode("中文翻译：\n" + translated));
            }
        }

        for (String groupId : GroupInformation.fetchAllGroupIds()) {
            if (!GroupConfigManager.isFeatureEnabled(groupId, FEATURE_KEY)) continue;
            ThreadManager.execute(() -> {
                try {
                    GroupMessage.forwardMessage(groupId, nodes, "Mojira 漏洞追踪器动态", "查看MOJANG新的石山代码", "编号: " + issue.key, "时间: " + time, "版本: " + versions, "标题: " + issue.summary());
                    log.info("[Mojira] 已推送 {} 到群 {}", issue.key(), groupId);
                } catch (Exception e) {
                    log.warn("[Mojira] 推送 {} 到群 {} 失败: {}", issue.key(), groupId, e.getMessage());
                }
            });
        }
    }
}
