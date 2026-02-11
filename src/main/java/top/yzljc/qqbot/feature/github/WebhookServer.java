package top.yzljc.qqbot.feature.github;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.config.ConfigFile;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.botkits.findinfo.GetGroupList;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.Executors;

public class WebhookServer {

    private static final Logger log = LoggerFactory.getLogger(WebhookServer.class);
    public static final Set<Long> TARGET_GROUPS = GetGroupList.fetchAllGroupIds();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String CONFIG_FILE_PATH = ConfigFile.GITHUB_REPOSITORY.getFileName();
    private static final Map<String, List<Long>> repoConfig = new HashMap<>();

    static {
        loadConfig();
    }

    public static void start(int port, String secret) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/github-webhook", new WebhookHandler(secret));
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            log.info("Webhook server started on port " + port);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void processCommand(long groupId, String rawMessage) {
        String[] parts = rawMessage.split("\\s+");

        // 指令格式: /github 仓库名 群号1 群号2 ...
        if (parts.length >= 3) {
            String targetRepo = parts[1];
            List<Long> targetGroupIds = new ArrayList<>();

            try {
                for (int i = 2; i < parts.length; i++) {
                    targetGroupIds.add(Long.parseLong(parts[i]));
                }

                // 存入配置 (key转小写以忽略大小写)
                synchronized (repoConfig) {
                    repoConfig.put(targetRepo.toLowerCase(), targetGroupIds);
                    saveConfig();
                }

                MessageSender.sendGroupMessage(groupId, "配置成功！仓库 [" + targetRepo + "] 将仅推送到群: " + targetGroupIds);

            } catch (NumberFormatException e) {
                MessageSender.sendGroupMessage(groupId, "指令错误：群号必须为数字。");
            } catch (Exception e) {
                log.warn("Failed to process /github command", e);
                MessageSender.sendGroupMessage(groupId, "配置更新失败，发生内部错误。");
            }
        } else {
            MessageSender.sendGroupMessage(groupId, "用法: /github <仓库名> <群号1> [群号2...]");
        }
    }

    private static void loadConfig() {
        File file = new File(CONFIG_FILE_PATH);
        if (!file.exists()) return;
        try {
            synchronized (repoConfig) {
                Map<String, List<Long>> loaded = objectMapper.readValue(file, new TypeReference<Map<String, List<Long>>>() {});
                if (loaded != null) {
                    repoConfig.clear();
                    // 确保key为小写
                    for (Map.Entry<String, List<Long>> entry : loaded.entrySet()) {
                        repoConfig.put(entry.getKey().toLowerCase(), entry.getValue());
                    }
                }
            }
        } catch (IOException e) {
            log.error("Failed to load github repository config", e);
        }
    }

    private static void saveConfig() {
        try {
            synchronized (repoConfig) {
                objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(CONFIG_FILE_PATH), repoConfig);
            }
        } catch (IOException e) {
            log.error("Failed to save github repository config", e);
        }
    }

    static class WebhookHandler implements HttpHandler {
        private final String secret;

        public WebhookHandler(String secret) {
            this.secret = secret;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            InputStream is = exchange.getRequestBody();
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead;
            byte[] data = new byte[1024];
            while ((nRead = is.read(data, 0, data.length)) != -1) {
                buffer.write(data, 0, nRead);
            }
            byte[] payloadBytes = buffer.toByteArray();
            String payload = new String(payloadBytes, StandardCharsets.UTF_8);

            String signature = exchange.getRequestHeaders().getFirst("X-Hub-Signature-256");
            if (secret != null && !secret.isEmpty()) {
                if (!verifySignature(payloadBytes, signature, secret)) {
                    exchange.sendResponseHeaders(403, -1);
                    return;
                }
            }

            exchange.sendResponseHeaders(200, 0);
            exchange.close();

            String event = exchange.getRequestHeaders().getFirst("X-GitHub-Event");
            if ("push".equals(event)) {
                processPushEvent(payload);
            }
        }

        private void processPushEvent(String json) {
            CommitDisplay.GithubPayload data = parseJson(json);
            String repoNameForFilter = getSimpleRepoName(json);
            CommitDisplay generator = new CommitDisplay();
            String base64Image = generator.generateBase64(data);

            if (base64Image != null) {
                Collection<Long> destinationGroups = new ArrayList<>();

                if (repoNameForFilter != null && repoConfig.containsKey(repoNameForFilter.toLowerCase())) {
                    destinationGroups = repoConfig.get(repoNameForFilter.toLowerCase());
                } else {
                    for (Long groupId : TARGET_GROUPS) {
                        if (GroupConfigManager.isFeatureEnabled(groupId, "github_info")) {
                            destinationGroups.add(groupId);
                        }
                    }
                }

                if (destinationGroups != null) {
                    for (Long groupId : destinationGroups) {
                        MessageSender.sendGroupMessage(groupId, null, base64Image);
                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ignored) {
                        }
                    }
                }
            }
        }

        private String getSimpleRepoName(String json) {
            int repoIndex = json.indexOf("\"repository\":");
            if (repoIndex != -1) {
                String repoPart = json.substring(repoIndex);
                return extractString(repoPart, "\"name\":\"", "\"");
            }
            return null;
        }

        private CommitDisplay.GithubPayload parseJson(String json) {
            CommitDisplay.GithubPayload payload = new CommitDisplay.GithubPayload();
            try {
                payload.repoName = extractString(json, "\"full_name\":\"", "\"");
                String ref = extractString(json, "\"ref\":\"", "\"");
                payload.branch = ref.contains("/") ? ref.substring(ref.lastIndexOf("/") + 1) : ref;

                int senderIndex = json.indexOf("\"sender\":");
                if (senderIndex != -1) {
                    payload.avatarUrl = extractString(json.substring(senderIndex), "\"avatar_url\":\"", "\"");
                }

                int headCommitIndex = json.indexOf("\"head_commit\":");
                if (headCommitIndex != -1) {
                    String headJson = json.substring(headCommitIndex);
                    payload.hash = extractString(headJson, "\"id\":\"", "\"");

                    payload.message = extractString(headJson, "\"message\":\"", "\"")
                            .replace("\\n", "\n")
                            .replace("\\r", "\r")
                            .replace("\\\"", "\"");

                    int authorIndex = headJson.indexOf("\"author\":");
                    if (authorIndex != -1) {
                        payload.pusherName = extractString(headJson.substring(authorIndex), "\"name\":\"", "\"");
                    }

                    payload.addedCount = countArrayElements(headJson, "\"added\":");
                    payload.removedCount = countArrayElements(headJson, "\"removed\":");
                    int modifiedCount = countArrayElements(headJson, "\"modified\":");
                    payload.changedFilesCount = payload.addedCount + payload.removedCount + modifiedCount;
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return payload;
        }

        private String extractString(String source, String startToken, String endToken) {
            int start = source.indexOf(startToken);
            if (start == -1) return "unknown";
            start += startToken.length();
            int end = source.indexOf(endToken, start);
            if (end == -1) return "unknown";
            return source.substring(start, end);
        }

        private int countArrayElements(String source, String key) {
            int keyIndex = source.indexOf(key);
            if (keyIndex == -1) return 0;
            int arrayStart = source.indexOf("[", keyIndex);
            int arrayEnd = source.indexOf("]", arrayStart);
            if (arrayStart == -1 || arrayEnd == -1) return 0;
            String content = source.substring(arrayStart + 1, arrayEnd).trim();
            if (content.isEmpty()) return 0;
            int count = 1;
            for (char c : content.toCharArray()) {
                if (c == ',') count++;
            }
            return count;
        }

        private boolean verifySignature(byte[] payload, String signature, String secret) {
            try {
                if (signature == null || !signature.startsWith("sha256=")) return false;
                String sha256Sig = signature.substring(7);
                Mac mac = Mac.getInstance("HmacSHA256");
                SecretKeySpec secretKeySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
                mac.init(secretKeySpec);
                byte[] hmacBytes = mac.doFinal(payload);
                StringBuilder sb = new StringBuilder();
                for (byte b : hmacBytes) sb.append(String.format("%02x", b));
                return sb.toString().equals(sha256Sig);
            } catch (NoSuchAlgorithmException | InvalidKeyException e) {
                return false;
            }
        }
    }
}