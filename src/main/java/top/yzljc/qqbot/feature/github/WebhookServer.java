package top.yzljc.qqbot.feature.github;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.botkits.thread.ThreadManager;
import top.yzljc.qqbot.command.process.Command;
import top.yzljc.qqbot.command.process.CommandExecutor;
import top.yzljc.qqbot.command.process.CommandSender;
import top.yzljc.qqbot.config.ConfigFile;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.botkits.findinfo.GetGroupList;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class WebhookServer implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(WebhookServer.class);
    public static final Set<Long> TARGET_GROUPS = GetGroupList.fetchAllGroupIds();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String CONFIG_FILE_PATH = ConfigFile.GITHUB_REPOSITORY.getFileName();
    private static final Map<String, List<Long>> repoConfig = new HashMap<>();
    private static final String TEMP_IMAGE_PATH = "tmp/last_github_update.png";

    static {
        loadConfig();
        new File("tmp").mkdirs();
    }

    public static void start(int port, String secret) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/github-webhook", new WebhookHandler(secret));
            server.setExecutor(ThreadManager.getExecutor());
            server.start();
            log.info("Webhook server started on port {}", port);
        } catch (IOException e) {
            log.error("Failed to start webhook server", e);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isAdmin()) {
            sender.reply("你没有权限执行此命令", false);
            return true;
        }
        if (args.length > 1) {
            String targetRepo = args[0];
            List<Long> targetGroupIds = new ArrayList<>();

            try {
                for (int i = 1; i < args.length; i++) {
                    targetGroupIds.add(Long.parseLong(args[i]));
                }

                synchronized (repoConfig) {
                    repoConfig.put(targetRepo.toLowerCase(), targetGroupIds);
                    saveConfig();
                }

                sender.reply("配置成功！仓库 [" + targetRepo + "] 将仅推送到群: " + targetGroupIds, false);
            } catch (NumberFormatException e) {
                sender.reply("指令错误：群号必须为数字。", false);
            } catch (Exception e) {
                log.warn("Failed to process /github command", e);
                sender.reply("配置更新失败，发生内部错误。", false);
            }
            return true;
        } else {
            return false;
        }
    }

    private static void loadConfig() {
        File file = new File(CONFIG_FILE_PATH);
        if (!file.exists()) return;
        try {
            synchronized (repoConfig) {
                Map<String, List<Long>> loaded = objectMapper.readValue(file, new TypeReference<>() {
                });
                if (loaded != null) {
                    repoConfig.clear();
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
                saveImageLocally(base64Image);

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
                        ThreadManager.execute(() -> MessageSender.sendGroupMessage(groupId, null, base64Image));
                    }
                }
            }
        }

        private void saveImageLocally(String base64Image) {
            try {
                String base64Data = base64Image;
                if (base64Data.contains(",")) {
                    base64Data = base64Data.split(",")[1];
                }

                byte[] imageBytes = Base64.getDecoder().decode(base64Data);
                File outputFile = new File(TEMP_IMAGE_PATH);

                try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                    fos.write(imageBytes);
                }
            } catch (Exception e) {
                log.error("Failed to save github push image locally", e);
            }
        }

        private String getSimpleRepoName(String json) {
            int repoIndex = json.indexOf("\"repository\":");
            if (repoIndex != -1) {
                String repoPart = json.substring(repoIndex);
                return extractString(repoPart, "\"name\":\"");
            }
            return null;
        }

        private CommitDisplay.GithubPayload parseJson(String json) {
            CommitDisplay.GithubPayload payload = new CommitDisplay.GithubPayload();
            try {
                payload.repoName = extractString(json, "\"full_name\":\"");
                String ref = extractString(json, "\"ref\":\"");
                payload.branch = ref.contains("/") ? ref.substring(ref.lastIndexOf("/") + 1) : ref;

                int senderIndex = json.indexOf("\"sender\":");
                if (senderIndex != -1) {
                    payload.avatarUrl = extractString(json.substring(senderIndex), "\"avatar_url\":\"");
                }

                int headCommitIndex = json.indexOf("\"head_commit\":");
                if (headCommitIndex != -1) {
                    String headJson = json.substring(headCommitIndex);
                    payload.hash = extractString(headJson, "\"id\":\"");

                    payload.message = extractString(headJson, "\"message\":\"")
                            .replace("\\n", "\n")
                            .replace("\\r", "\r")
                            .replace("\\\"", "\"");

                    int authorIndex = headJson.indexOf("\"author\":");
                    if (authorIndex != -1) {
                        payload.pusherName = extractString(headJson.substring(authorIndex), "\"name\":\"");
                    }

                    payload.addedCount = countArrayElements(headJson, "\"added\":");
                    payload.removedCount = countArrayElements(headJson, "\"removed\":");
                    int modifiedCount = countArrayElements(headJson, "\"modified\":");
                    payload.changedFilesCount = payload.addedCount + payload.removedCount + modifiedCount;
                }
            } catch (Exception e) {
                log.error("Github Webhook JSON parsing error", e);
            }
            return payload;
        }

        private String extractString(String source, String startToken) {
            int start = source.indexOf(startToken);
            if (start == -1) return "unknown";
            start += startToken.length();
            int end = source.indexOf("\"", start);
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