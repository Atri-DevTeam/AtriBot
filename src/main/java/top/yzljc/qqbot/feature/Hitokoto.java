package top.yzljc.qqbot.feature;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import top.yzljc.qqbot.botkits.message.MessageSender;

import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

public class Hitokoto {

    private static final Logger log = LoggerFactory.getLogger(Hitokoto.class);

    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static final String API_URL = "https://v1.hitokoto.cn/";
    private static final String LOCAL_JSON_PATH = "OneText-Library.json";
    private static List<OneTextEntry> localEntries = null;
    private static final Random RANDOM = new Random();

    static {
        releaseResourceJsonIfAbsent();
    }

    private static void releaseResourceJsonIfAbsent() {
        try {
            if (!Files.exists(Paths.get(LOCAL_JSON_PATH))) {
                log.info("未找到一言 json，自动释放资源到当前目录: " + LOCAL_JSON_PATH);
                try (InputStream in = Hitokoto.class.getClassLoader().getResourceAsStream(LOCAL_JSON_PATH)) {
                    if (in != null) {
                        Files.copy(in, Paths.get(LOCAL_JSON_PATH), StandardCopyOption.REPLACE_EXISTING);
                    } else {
                        log.error("无法找到默认的一言文件资源: " + LOCAL_JSON_PATH + "，请联系开发者！");
                    }
                }
            }
        } catch (Exception e) {
            log.error("释放一言 json 失败: " + e.getMessage());
        }
    }

    public static void processHitokoto(long groupId) {
        Executors.newSingleThreadExecutor().submit(() -> {
            String feedback = fetchEitherHitokotoOrLocal();
            MessageSender.sendGroupMessage(groupId, feedback);
        });
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
            HttpURLConnection conn = (HttpURLConnection) new URI(API_URL).toURL().openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            String respStr = new String(conn.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            JsonNode respJson = jsonMapper.readTree(respStr);

            String hitokoto = respJson.path("hitokoto").asText();
            String from = respJson.path("from").asText();
            JsonNode fromWhoNode = respJson.path("from_who");

            StringBuilder result = new StringBuilder(hitokoto).append("\n—— ").append(from);

            if (!fromWhoNode.isNull() && !fromWhoNode.asText().isEmpty()) {
                result.append(" · ").append(fromWhoNode.asText());
            }

            log.info("一言发送成功(API) => {}", result.toString());
            return result.toString();

        } catch (Exception ex) {
            log.warn("一言获取异常: {}", ex.getMessage());
            return "一言获取失败：接口异常。";
        }
    }

    private static String fetchLocalOneText() {
        try {
            if (localEntries == null) {
                try (InputStream in = new FileInputStream(LOCAL_JSON_PATH)) {
                    localEntries = jsonMapper.readValue(in, new TypeReference<List<OneTextEntry>>() {});
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
            log.info("一言发送成功(本地) => {}", sb.toString());
            return sb.toString();
        } catch (Exception ex) {
            log.warn("本地一言获取异常: {}", ex.getMessage());
            return null;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class OneTextEntry {
        public String text;
        public String by;
        public String from;
    }
}