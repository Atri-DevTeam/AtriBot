package top.yzljc.qqbot.feature;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.botkits.request.RequestType;
import top.yzljc.qqbot.botkits.thread.ThreadManager;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.ConfigFile;
import top.yzljc.qqbot.config.groups.GroupConfigManager;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.IntStream;

public class AnnoyUser {

    private static final Logger log = LoggerFactory.getLogger(AnnoyUser.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String RECORD_FILE = ConfigFile.ANNOY_RECORD.getFileName();
    private static final Map<Long, Map<Long, AnnoyMode>> annoyMap = new ConcurrentHashMap<>();
    private static final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
            Runtime.getRuntime().availableProcessors(),
            200, // 最大并发
            60L, TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            r -> {
                Thread t = new Thread(r);
                t.setName("Annoy-Worker-" + t.getId());
                t.setDaemon(true);
                return t;
            }
    );

    public enum AnnoyMode {
        NORMAL, MEDIUM, INSANE, ANIMATION;

        public static AnnoyMode fromString(String s) {
            if (s == null) return null;
            return switch (s.trim().toLowerCase()) {
                case "normal" -> NORMAL;
                case "medium", "super" -> MEDIUM;
                case "insane" -> INSANE;
                case "animation" -> ANIMATION;
                default -> null;
            };
        }
    }

    static {
        loadRecord();
    }

    public static void processMessage(JsonNode json) {
        if (!"group".equals(json.path("message_type").asText())) return;
        long groupId = json.path("group_id").asLong();

        if (!GroupConfigManager.isFeatureEnabled(groupId, "annoy_user")) {
            return;
        }

        long senderId = json.path("user_id").asLong();
        String rawMsg = json.path("raw_message").asText("").trim();

        if (rawMsg.startsWith("/emj")) {
            handleCommand(groupId, senderId, rawMsg, json);
            return;
        }

        Map<Long, AnnoyMode> groupConfig = annoyMap.get(groupId);
        if (groupConfig == null || !groupConfig.containsKey(senderId)) {
            return;
        }

        long botId = json.path("self_id").asLong();
        if (senderId == botId) return;

        AnnoyMode mode = groupConfig.get(senderId);
        long msgId = json.path("message_id").asLong();

        submitTask(groupId, senderId, msgId, mode);
    }

    private static void submitTask(long groupId, long userId, long msgId, AnnoyMode mode) {
        String key = groupId + "_" + userId;

        Future<?> prevTask = runningTasks.get(key);
        if (prevTask != null && !prevTask.isDone()) {
            prevTask.cancel(true);
        }

        // 提交新任务
        Future<?> future = ThreadManager.setExecute(() -> {
            try {
                switch (mode) {
                    case NORMAL:
                        sendEmojis(msgId, 3, true); // 3个随机
                        break;
                    case MEDIUM:
                        sendEmojis(msgId, 10, false); // 10个顺序
                        break;
                    case INSANE:
                        sendEmojis(msgId, 20, false); // 20个顺序（一口气）
                        break;
                    case ANIMATION:
                        runAnimationLogic(msgId); // 20个渐变
                        break;
                }
            } catch (Exception e) {
                // 忽略被中断的异常
                if (!(e instanceof InterruptedException)) {
                    log.error("Annoy execution error", e);
                }
            } finally {
                // 任务结束移除引用
                runningTasks.remove(key, Thread.currentThread());
            }
        });

        runningTasks.put(key, future);
    }

    private static void runAnimationLogic(long msgId) throws InterruptedException {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        int[] selectedEmojis = random.ints(1, 101)
                .distinct()
                .limit(20)
                .toArray();

        int loopCount = 5;
        long stepDelay = 4000 / selectedEmojis.length;

        for (int i = 0; i < loopCount; i++) {
            for (int emojiId : selectedEmojis) {
                if (Thread.interrupted()) throw new InterruptedException();
                postEmoji(msgId, emojiId, true);
                Thread.sleep(stepDelay);
            }

            for (int emojiId : selectedEmojis) {
                if (Thread.interrupted()) throw new InterruptedException();
                postEmoji(msgId, emojiId, false);
                Thread.sleep(stepDelay);
            }
        }
    }

    private static void sendEmojis(long msgId, int count, boolean randomSelect) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int[] emojis;

        if (randomSelect) {
            emojis = random.ints(1, 21).distinct().limit(count).toArray();
        } else {
            emojis = IntStream.rangeClosed(1, count).toArray();
            if (count < 15) { // Medium 稍微打乱
                for (int i = emojis.length - 1; i > 0; i--) {
                    int index = random.nextInt(i + 1);
                    int a = emojis[index]; emojis[index] = emojis[i]; emojis[i] = a;
                }
            }
        }

        for (int eid : emojis) {
            if (Thread.currentThread().isInterrupted()) return;
            postEmoji(msgId, eid, true);

            int delay = (count < 15) ? (200 + random.nextInt(200)) : 5;
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private static void postEmoji(long msgId, int emojiId, boolean set) {
        try {
            Map<String, Object> req = new HashMap<>(4);
            req.put("message_id", String.valueOf(msgId));
            req.put("emoji_id", emojiId);
            req.put("set", set);
            PostRequest.sendPost(RequestType.PUT_EMOJI, req);
        } catch (Exception e) {
            log.warn("Emoji API fail: {}", e.getMessage());
        }
    }

    private static void handleCommand(long groupId, long senderId, String rawMsg, JsonNode json) {
        String[] parts = rawMsg.split("\\s+");
        String modeStr = parts.length > 1 ? parts[1] : "";

        long targetId = senderId;
        if (parts.length > 2) {
            if (!Config.getInstance().getAdminUids().contains(senderId)) {
                return;
            }
            try {
                targetId = Long.parseLong(parts[2]);
            } catch (NumberFormatException e) {
                targetId = parseAtTarget(json, targetId);
            }
        }

        AnnoyMode mode = AnnoyMode.fromString(modeStr);
        if (mode == null) {
            MessageSender.sendGroupMessage(groupId, "指令错误。模式: normal, medium, insane, animation。\n再次输入相同模式可关闭。");
            return;
        }

        Map<Long, AnnoyMode> groupMap = annoyMap.get(groupId);
        boolean isAlreadyInThisMode = (groupMap != null && groupMap.get(targetId) == mode);

        if (isAlreadyInThisMode) {
            // 关闭逻辑
            removeAnnoy(groupId, targetId);
            MessageSender.sendGroupMessage(groupId, "已关闭对 " + targetId + " 的 [" + mode + "] 模式。");
        } else {
            // 开启/切换逻辑
            addAnnoy(groupId, targetId, mode);
            MessageSender.sendGroupMessage(groupId, "对 " + targetId + " 开启 [" + mode + "] 模式！\n(再次输入该指令即可关闭)");
        }
    }

    private static long parseAtTarget(JsonNode json, long defaultId) {
        for (JsonNode node : json.path("message")) {
            if ("at".equals(node.path("type").asText())) {
                try { return Long.parseLong(node.path("data").path("qq").asText()); }
                catch (Exception e) { break; }
            }
        }
        return defaultId;
    }

    private static void addAnnoy(long groupId, long qq, AnnoyMode mode) {
        annoyMap.computeIfAbsent(groupId, _ -> new ConcurrentHashMap<>()).put(qq, mode);
        saveRecord();
    }

    private static void removeAnnoy(long groupId, long qq) {
        Map<Long, AnnoyMode> group = annoyMap.get(groupId);
        if (group != null) {
            group.remove(qq);
            // 移除的同时，取消正在进行的任务
            Future<?> f = runningTasks.remove(groupId + "_" + qq);
            if (f != null) f.cancel(true);

            if (group.isEmpty()) annoyMap.remove(groupId);
            saveRecord();
        }
    }

    private static void loadRecord() {
        annoyMap.clear();
        File file = new File(RECORD_FILE);
        if (!file.exists()) return;

        try (FileReader reader = new FileReader(file)) {
            Map<String, Map<String, String>> raw = mapper.readValue(reader, new TypeReference<>() {});
            for (Map.Entry<String, Map<String, String>> e : raw.entrySet()) {
                long gid = Long.parseLong(e.getKey());
                Map<Long, AnnoyMode> uMap = new ConcurrentHashMap<>();
                e.getValue().forEach((k, v) -> {
                    AnnoyMode m = AnnoyMode.fromString(v);
                    if (m != null) uMap.put(Long.parseLong(k), m);
                });
                if (!uMap.isEmpty()) annoyMap.put(gid, uMap);
            }
        } catch (Exception e) {
            log.error("Load annoy record error", e);
        }
    }

    private static void saveRecord() {
        Map<String, Map<String, String>> raw = new HashMap<>();
        annoyMap.forEach((gid, uMap) -> {
            if (uMap.isEmpty()) return;
            Map<String, String> sMap = new HashMap<>();
            uMap.forEach((uid, mode) -> sMap.put(String.valueOf(uid), mode.name().toLowerCase()));
            raw.put(String.valueOf(gid), sMap);
        });

        try (FileWriter writer = new FileWriter(RECORD_FILE, false)) {
            mapper.writeValue(writer, raw);
        } catch (IOException e) {
            log.error("Save annoy record error", e);
        }
    }
}