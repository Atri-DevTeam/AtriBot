package top.yzljc.qqbot.tools;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.GroupConfigManager;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.messages.MessageProcessor;
import top.yzljc.qqbot.messages.MessageSender;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class AnnoyUser {

    static Settings settings = Config.getInstance();
    private static final String BASEURL = settings.getHttpUrl();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String EMOJI_API = BASEURL + "/set_msg_emoji_like";
    private static final String RECORD_FILE = "annoy_user_record.json";
    private static final Random RAND = new Random();

    private static final Map<Long, Map<Long, String>> annoyMap = new ConcurrentHashMap<>();
    private static final Map<Long, Set<Long>> fuckOnceMap = new ConcurrentHashMap<>();
    private static final ExecutorService PARALLEL_POOL = Executors.newCachedThreadPool();

    static {
        loadRecord();
    }

    public static void processMessage(JsonNode json) {
        if (!json.path("message_type").asText("").equals("group")) return;
        long groupId = json.path("group_id").asLong();

        if (!GroupConfigManager.isFeatureEnabled(groupId, "annoy_user")) {
            return;
        }

        long senderId = json.path("user_id").asLong();
        String rawMsg = json.path("raw_message").asText("");
        JsonNode messageArr = json.path("message");

        // 管理员命令
        if ((rawMsg.startsWith("/ay ") || rawMsg.startsWith("/ayr ")) && Config.getInstance().getAdminUids().contains(senderId)) {
            boolean isSuper = rawMsg.trim().endsWith(" -s");
            String atQq = null;
            for (JsonNode node : messageArr) {
                if ("at".equals(node.path("type").asText())) {
                    atQq = node.path("data").path("qq").asText();
                    break;
                }
            }
            if (atQq == null) return;
            long atId;
            try {
                atId = Long.parseLong(atQq);
            } catch (Exception e) {
                return;
            }
            if (rawMsg.startsWith("/ay ")) {
                if (isSuper) {
                    addAnnoy(groupId, atId, "super");
                    System.out.println("[INFO][DEBUG] Enable SUPER annoy for QQ " + atId + " in group " + groupId);
                    MessageSender.sendGroupMessage(groupId, "现在我将会对着 " + atId + " 疯狂发癫！");
                } else {
                    addAnnoy(groupId, atId, "normal");
                    System.out.println("[INFO][DEBUG] Enable NORMAL annoy for QQ " + atId + " in group " + groupId);
                    MessageSender.sendGroupMessage(groupId, "现在我将会对着 " + atId + " 发癫！");
                }
            } else { // /ayr
                if (isSuper) {
                    removeAnnoy(groupId, atId, "super");
                    System.out.println("[INFO][DEBUG] Disable SUPER annoy for QQ " + atId + " in group " + groupId);
                    MessageSender.sendGroupMessage(groupId, "现在我将不再会对着 " + atId + " 疯狂发癫！");
                } else {
                    removeAnnoy(groupId, atId, "normal");
                    System.out.println("[INFO][DEBUG] Disable NORMAL annoy for QQ " + atId + " in group " + groupId);
                    MessageSender.sendGroupMessage(groupId, "现在我将不再会对着 " + atId + " 发癫！");
                }
            }
            return;
        }
        // 增加/fuck @user 功能, 仅3199590352可用
        if (rawMsg.startsWith("/fuck ") && Config.getInstance().getAdminUids().contains(senderId)) {
            String atQq = null;
            for (JsonNode node : messageArr) {
                if ("at".equals(node.path("type").asText())) {
                    atQq = node.path("data").path("qq").asText();
                    break;
                }
            }
            if (atQq == null) return;
            long atId;
            try {
                atId = Long.parseLong(atQq);
            } catch (Exception e) {
                return;
            }
            fuckOnceMap.computeIfAbsent(groupId, k -> new HashSet<>()).add(atId);
            System.out.println("[INFO][DEBUG] NEXT MSG of QQ " + atId + " in group " + groupId + " will get 20-emoji!");
            MessageSender.sendGroupMessage(groupId, "收到，牢大正在准备派送惊喜！");
            return;
        }
        // 用户自助开关/ayme /ayrme 只允许normal
        if (rawMsg.trim().equalsIgnoreCase("/ayme")) {
            addAnnoy(groupId, senderId, "normal");
            System.out.println("[INFO][DEBUG] Enable NORMAL annoy for QQ " + senderId + " in group " + groupId);
            MessageSender.sendGroupMessage(groupId, "现在我将会对着 " + senderId + " 发癫！");
            return;
        }
        if (rawMsg.trim().equalsIgnoreCase("/ayrme")) {
            removeAnnoy(groupId, senderId, "normal");
            System.out.println("[INFO][DEBUG] Disable NORMAL annoy for QQ " + senderId + " in group " + groupId);
            MessageSender.sendGroupMessage(groupId, "现在我将不再会对着 " + senderId + " 发癫！");
            return;
        }
        // 新增：支持用户自助/fuckme
        if (rawMsg.trim().equalsIgnoreCase("/fuckme")) {
            fuckOnceMap.computeIfAbsent(groupId, k -> new HashSet<>()).add(senderId);
            System.out.println("[INFO][DEBUG] NEXT MSG of QQ " + senderId + " in group " + groupId + " will get 20-emoji! (from /fuckme)");
            MessageSender.sendGroupMessage(groupId, "收到，牢大正在准备派送惊喜！");
            return;
        }

        // 检查二十连once
        Set<Long> fuckSet = fuckOnceMap.get(groupId);
        long targetId = json.path("user_id").asLong();
        if (fuckSet != null && fuckSet.contains(targetId)) {
            long msgId = json.path("message_id").asLong();
            List<Integer> emojiIds = new ArrayList<>();
            for (int i = 1; i <= 20; i++) emojiIds.add(i);
            Collections.shuffle(emojiIds, RAND); // 模仿更真人
            emojiIds.forEach(emojiId ->
                    PARALLEL_POOL.execute(() -> sendEmojiLike(msgId, emojiId))
            );
            fuckSet.remove(targetId);
            if (fuckSet.isEmpty()) fuckOnceMap.remove(groupId);
            System.out.println("[INFO][DEBUG] FUCK 20-emoji at " + targetId + " in group " + groupId + " 已触发并移除！");
        }

        // 常规/超级监听
        Map<Long, String> annoySet = annoyMap.get(groupId);
        if (annoySet == null || annoySet.isEmpty()) return;

        long botId = json.path("self_id").asLong();
        if (targetId == botId) return;
        String t = annoySet.get(targetId);
        if (t == null) return;

        long msgId = json.path("message_id").asLong();
        if ("super".equals(t)) {
            List<Integer> emojiIds = new ArrayList<>();
            for (int i = 1; i <= 10; i++) emojiIds.add(i);
            for (int emojiId : emojiIds) {
                sendEmojiLike(msgId, emojiId);
                randomDelay(200, 500);
            }
        } else { // normal
            Set<Integer> emojiSet = new HashSet<>();
            while (emojiSet.size() < 3) {
                emojiSet.add(1 + RAND.nextInt(20));
            }
            List<Integer> emojiList = new ArrayList<>(emojiSet);
            Collections.shuffle(emojiList, RAND);
            for (int emojiId : emojiList) {
                sendEmojiLike(msgId, emojiId);
                randomDelay(200, 400);
            }
        }
    }

    private static void addAnnoy(long groupId, long qq, String type) {
        annoyMap.computeIfAbsent(groupId, k -> new HashMap<>()).put(qq, type);
        saveRecord();
    }

    private static void removeAnnoy(long groupId, long qq, String type) {
        Map<Long, String> group = annoyMap.get(groupId);
        if (group != null && type.equals(group.get(qq))) {
            group.remove(qq);
            if (group.isEmpty()) annoyMap.remove(groupId);
            saveRecord();
        }
    }

    private static void sendEmojiLike(long msgId, int emojiId) {
        try {
            Map<String, Object> req = new HashMap<>();
            req.put("message_id", String.valueOf(msgId));
            req.put("emoji_id", emojiId);
            req.put("set", true);
            String body = mapper.writeValueAsString(req);

            HttpURLConnection conn = (HttpURLConnection) new URL(EMOJI_API).openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(body.getBytes(StandardCharsets.UTF_8));
            }
            conn.getInputStream().close();
        } catch (Exception e) {
            System.err.println("[INFO] 点赞请求异常: " + e.getMessage());
        }
    }

    private static void randomDelay(int minMs, int maxMs) {
        int delta = maxMs - minMs + 1;
        int actual = minMs + RAND.nextInt(delta);
        try {
            Thread.sleep(actual);
        } catch (InterruptedException ignored) {}
    }

    private static void loadRecord() {
        annoyMap.clear();
        File file = new File(RECORD_FILE);
        if (file.exists()) {
            try (FileReader reader = new FileReader(file)) {
                Map<String, Map<String, String>> raw = mapper.readValue(reader, new TypeReference<>() {});
                for (Map.Entry<String, Map<String, String>> e : raw.entrySet()) {
                    long gid = Long.parseLong(e.getKey());
                    Map<Long, String> usermap = new HashMap<>();
                    for (Map.Entry<String, String> ue : e.getValue().entrySet()) {
                        usermap.put(Long.parseLong(ue.getKey()), ue.getValue());
                    }
                    annoyMap.put(gid, usermap);
                }
            } catch (Exception e) {
                // ignore
            }
        }
    }

    private static void saveRecord() {
        Map<String, Map<String, String>> raw = new HashMap<>();
        for (Map.Entry<Long, Map<Long, String>> e : annoyMap.entrySet()) {
            Map<String, String> groupVal = new HashMap<>();
            if (e.getValue() != null && !e.getValue().isEmpty()) {
                for (Map.Entry<Long, String> ue : e.getValue().entrySet()) {
                    groupVal.put(String.valueOf(ue.getKey()), ue.getValue());
                }
            }
            if (!groupVal.isEmpty()) raw.put(String.valueOf(e.getKey()), groupVal);
        }
        try (FileWriter writer = new FileWriter(RECORD_FILE, false)) {
            mapper.writeValue(writer, raw);
        } catch (Exception e) {
            System.err.println("[INFO] 记录文件保存错误：" + e.getMessage());
        }
    }
}