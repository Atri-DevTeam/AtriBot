package top.yzljc.atribot.function.napcat.github;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.chat.napcat.GroupInformation;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.impl.MessageUtils;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.runtime.ThreadManager;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class WebhookServer implements CommandExecutor {

    private static final Logger log = LoggerFactory.getLogger(WebhookServer.class);
    public static final Set<String> TARGET_GROUPS = GroupInformation.fetchAllGroupIds();
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final String CONFIG_FILE_PATH = Properties.GITHUB_REPOSITORY;
    private static final Map<String, List<String>> repoConfig = new HashMap<>();
    private static final String TEMP_IMAGE_PATH = "tmp/last_github_update.png";
    private static HttpServer server;

    static { loadConfig(); new File("tmp").mkdirs(); }

    public static void start(int port, String secret) {
        try { stop(); server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/github-webhook", new WebhookHandler(secret));
            server.setExecutor(ThreadManager.getExecutor()); server.start();
            log.info("Webhook server started on port {}", port);
        } catch (IOException e) { log.error("Failed to start webhook server", e); }
    }

    public static void stop() { if (server != null) { server.stop(0); server = null; } }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.NAPCAT_GROUP) return true;
        if (!GroupConfigManager.isFeatureEnabled(sender.getGroupId(), "github_info")) return true;
        if (!sender.hasPermission()) { sender.sendMessage("你没有权限执行此命令"); return true; }
        if (args.length > 1) {
            String targetRepo = args[0];
            List<String> targetGroupIds = new ArrayList<>();
            for (int i = 1; i < args.length; i++) targetGroupIds.add(args[i]);
            synchronized (repoConfig) { repoConfig.put(targetRepo.toLowerCase(), targetGroupIds); saveConfig(); }
            sender.sendMessage("配置成功！仓库 [" + targetRepo + "] 将仅推送到群: " + targetGroupIds);
            return true;
        }
        return false;
    }

    private static void loadConfig() {
        File file = new File(CONFIG_FILE_PATH); if (!file.exists()) return;
        try { synchronized (repoConfig) {
            Map<String, List<String>> loaded = objectMapper.readValue(file, new TypeReference<>() {});
            if (loaded != null) { repoConfig.clear(); loaded.forEach((k, v) -> repoConfig.put(k.toLowerCase(), v)); }
        }} catch (IOException e) { log.error("Failed to load github repository config", e); }
    }

    private static void saveConfig() {
        try { synchronized (repoConfig) { objectMapper.writerWithDefaultPrettyPrinter().writeValue(new File(CONFIG_FILE_PATH), repoConfig); }
        } catch (IOException e) { log.error("Failed to save github repository config", e); }
    }

    static class WebhookHandler implements HttpHandler {
        private final String secret;
        public WebhookHandler(String secret) { this.secret = secret; }
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) { exchange.sendResponseHeaders(405, -1); return; }
            InputStream is = exchange.getRequestBody(); ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int nRead; byte[] data = new byte[1024];
            while ((nRead = is.read(data, 0, data.length)) != -1) buffer.write(data, 0, nRead);
            byte[] payloadBytes = buffer.toByteArray(); String payload = new String(payloadBytes, StandardCharsets.UTF_8);
            String signature = exchange.getRequestHeaders().getFirst("X-Hub-Signature-256");
            if (secret != null && !secret.isEmpty() && !verifySignature(payloadBytes, signature, secret)) { exchange.sendResponseHeaders(403, -1); return; }
            exchange.sendResponseHeaders(200, 0); exchange.close();
            if ("push".equals(exchange.getRequestHeaders().getFirst("X-GitHub-Event"))) processPushEvent(payload);
        }

        private void processPushEvent(String json) {
            CommitDisplay.GithubPayload data = parseJson(json);
            String repoNameForFilter = getSimpleRepoName(json);
            CommitDisplay generator = new CommitDisplay();
            String base64Image = generator.generateBase64(data);
            if (base64Image != null) {
                saveImageLocally(base64Image);
                Collection<String> destinationGroups = new ArrayList<>();
                if (repoNameForFilter != null && repoConfig.containsKey(repoNameForFilter.toLowerCase()))
                    destinationGroups = repoConfig.get(repoNameForFilter.toLowerCase());
                else for (String groupId : TARGET_GROUPS)
                    if (GroupConfigManager.isFeatureEnabled(groupId, "github_info")) destinationGroups.add(groupId);
                for (String groupId : destinationGroups)
                    ThreadManager.execute(() -> GroupMessage.chatMessage(groupId, base64Image, MessageUtils.ImageType.BASE64));
            }
        }

        private void saveImageLocally(String base64Image) {
            try {
                String b64 = base64Image.contains(",") ? base64Image.split(",")[1] : base64Image;
                try (FileOutputStream fos = new FileOutputStream(TEMP_IMAGE_PATH)) { fos.write(Base64.getDecoder().decode(b64)); }
            } catch (Exception e) { log.error("Failed to save github push image locally", e); }
        }

        private String getSimpleRepoName(String json) {
            int i = json.indexOf("\"repository\":"); return i != -1 ? extractString(json.substring(i), "\"name\":\"") : null;
        }

        private CommitDisplay.GithubPayload parseJson(String json) {
            CommitDisplay.GithubPayload p = new CommitDisplay.GithubPayload();
            try {
                p.repoName = extractString(json, "\"full_name\":\""); String ref = extractString(json, "\"ref\":\"");
                p.branch = ref.contains("/") ? ref.substring(ref.lastIndexOf("/") + 1) : ref;
                int si = json.indexOf("\"sender\":"); if (si != -1) p.avatarUrl = extractString(json.substring(si), "\"avatar_url\":\"");
                int hci = json.indexOf("\"head_commit\":"); if (hci != -1) { String hj = json.substring(hci);
                    p.hash = extractString(hj, "\"id\":\""); p.message = extractString(hj, "\"message\":\"").replace("\\n","\n").replace("\\r","\r").replace("\\\"","\"");
                    int ai = hj.indexOf("\"author\":"); if (ai != -1) p.pusherName = extractString(hj.substring(ai), "\"name\":\"");
                    p.addedCount = countArrayElements(hj, "\"added\":"); p.removedCount = countArrayElements(hj, "\"removed\":");
                    p.changedFilesCount = p.addedCount + p.removedCount + countArrayElements(hj, "\"modified\":");
                }
            } catch (Exception e) { log.error("Github Webhook JSON parsing error", e); }
            return p;
        }

        private String extractString(String s, String t) { int st = s.indexOf(t); if (st == -1) return "unknown"; st += t.length(); int e = s.indexOf("\"", st); return e == -1 ? "unknown" : s.substring(st, e); }
        private int countArrayElements(String s, String k) {
            int ki = s.indexOf(k); if (ki == -1) return 0; int as = s.indexOf("[", ki), ae = s.indexOf("]", as); if (as == -1 || ae == -1) return 0;
            String c = s.substring(as+1, ae).trim(); if (c.isEmpty()) return 0; int n = 1; for (char ch : c.toCharArray()) if (ch == ',') n++; return n;
        }

        private boolean verifySignature(byte[] p, String sig, String sec) {
            try { if (sig == null || !sig.startsWith("sha256=")) return false;
                Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(sec.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                byte[] h = mac.doFinal(p); StringBuilder sb = new StringBuilder(); for (byte b : h) sb.append(String.format("%02x", b));
                return sb.toString().equals(sig.substring(7));
            } catch (NoSuchAlgorithmException | InvalidKeyException e) { return false; }
        }
    }
}
