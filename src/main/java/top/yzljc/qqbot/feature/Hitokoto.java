package top.yzljc.qqbot.feature;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.service.request.HttpService;
import top.yzljc.qqbot.service.thread.ThreadManager;
import top.yzljc.qqbot.config.ConfigFile;

import java.util.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.impl.GroupMessageEvent;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.service.userinfo.GetUserInfo;
import top.yzljc.qqbot.config.Config;

public class Hitokoto implements Listener, CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(Hitokoto.class);

    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static final String API_URL = "https://v1.hitokoto.cn/";
    private static final String LOCAL_JSON_PATH = ConfigFile.HITOKOTO_LIBRARY.getFileName();
    private static List<OneTextEntry> localEntries = null;
    private static final Random RANDOM = new Random();

    static {
        releaseResourceJsonIfAbsent();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equals("0")) {
            ThreadManager.execute(() -> {
                String feedback = getLocalHitokoto(label, sender.userOpenId());
                sender.reply(feedback);
            });
        }
        if (label.equals("1")) {
            ThreadManager.execute(() -> {
                String feedback = getLocalHitokoto(label, sender.userOpenId());
                sender.officialPrivateReplyMarkdown(feedback);
            });
        }
        return true;
    }

    private static void releaseResourceJsonIfAbsent() {
        try {
            Path path = Paths.get(LOCAL_JSON_PATH);
            if (!Files.exists(path)) {
                log.info("未找到一言 json，自动释放资源到当前目录: {}", LOCAL_JSON_PATH);
                try (InputStream in = Hitokoto.class.getClassLoader().getResourceAsStream(LOCAL_JSON_PATH)) {
                    if (in != null) {
                        Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        log.error("无法找到默认的一言文件资源: {}，请联系开发者！", LOCAL_JSON_PATH);
                    }
                }
            }
        } catch (Exception e) {
            log.error("释放一言 json 失败: {}", e.getMessage());
        }
    }

    @EventHandler
    public void onGroupMessage(GroupMessageEvent event) {
        long groupId = event.getGroupId();
        long userId = event.getUserId();
        String rawMessage = event.getRawMessage();

        if (userId == GetUserInfo.getBotId()) {
            return;
        }

        if (rawMessage.contains("/")) return;

        if (!GroupConfigManager.isFeatureEnabled(groupId, "one_text")) {
            return;
        }

        boolean match = false;
        String[] keywords = Config.getInstance().getKeywordsHitokoto();
        for (String kw : keywords) {
            if (rawMessage.contains(kw)) {
                match = true;
                break;
            }
        }

        if (match) {
            ThreadManager.execute(() -> {
                String feedback = fetchEitherHitokotoOrLocal();
                event.getSender().reply(feedback);
            });
        }
    }


    private static String fetchEitherHitokotoOrLocal() {
        // 0表示API，1表示本地
        boolean useLocal = RANDOM.nextBoolean();
        if (useLocal) {
            String localOne = fetchLocalOneText();
            if (localOne != null) {
                return localOne;
            }
        }
        return fetchHitokoto();
    }

    private static String fetchHitokoto() {
        try {
            JsonNode respJson = HttpService.sendGetRequest(API_URL);

            if (respJson == null) {
                log.warn("一言获取异常: 接口未返回数据");
                return "一言获取失败：接口异常。";
            }

            String hitokoto = respJson.path("hitokoto").asText();
            String from = respJson.path("from").asText();
            JsonNode fromWhoNode = respJson.path("from_who");

            StringBuilder result = new StringBuilder(hitokoto).append("\n—— ").append(from);

            if (!fromWhoNode.isNull() && !fromWhoNode.asText().isEmpty()) {
                result.append(" · ").append(fromWhoNode.asText());
            }

            log.info("一言发送成功(API) => {}", result);
            return result.toString();

        } catch (Exception ex) {
            log.warn("一言获取异常: {}", ex.getMessage());
            return "一言获取失败：接口异常";
        }
    }

    private static String fetchLocalOneText() {
        try {
            if (localEntries == null) {
                try (InputStream in = new FileInputStream(LOCAL_JSON_PATH)) {
                    localEntries = jsonMapper.readValue(in, new TypeReference<>() {
                    });
                }
            }
            if (localEntries == null || localEntries.isEmpty()) return null;
            OneTextEntry entry = localEntries.get(RANDOM.nextInt(localEntries.size()));
            StringBuilder sb = new StringBuilder();
            sb.append(entry.text);
            sb.append("\n—— ").append(entry.from != null && !entry.from.isEmpty() ? entry.from : "");
            if (entry.by != null && !entry.by.isEmpty()) {
                sb.append(" · ").append(entry.by);
            }
            log.info("一言发送成功(本地) => {}", sb);
            return sb.toString();
        } catch (Exception ex) {
            log.warn("本地一言获取异常: {}", ex.getMessage());
            return null;
        }
    }

    // Official Bot Using Lib
    private static String getLocalHitokoto(String label, String userOpenId) {
        try {
            if (localEntries == null) {
                try (InputStream in = new FileInputStream(LOCAL_JSON_PATH)) {
                    localEntries = jsonMapper.readValue(in, new TypeReference<>() {
                    });
                }
            }
            if (localEntries == null || localEntries.isEmpty()) return null;
            OneTextEntry entry = localEntries.get(RANDOM.nextInt(localEntries.size()));
            StringBuilder sb = new StringBuilder();
            sb.append("---");
            sb.append("\n");
            sb.append(entry.text.replace("\n", "\n> "));
            sb.append("---\n");
            if (entry.by != null && !entry.by.isEmpty()) {
                sb.append("作者: ").append(entry.by).append("\n");
            }
            if (entry.from != null && !entry.from.isEmpty()) {
                sb.append("出自: ").append(entry.from).append("\n");
            }
            if (entry.time != null && !entry.time.isEmpty()) {
                sb.append("时间：").append(String.join(" ", entry.time)).append("\n");
            }
            log.info("一言发送成功(本地-官机) => {}", sb);
            return sb.toString();
        } catch (Exception ex) {
            log.warn("本地-官机一言获取异常: {}", ex.getMessage());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class OneTextEntry {
        public String text;
        public String by;
        public String from;
        public List<String> time;
    }
}