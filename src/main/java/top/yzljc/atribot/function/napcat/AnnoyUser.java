package top.yzljc.atribot.function.napcat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.NapcatGroupMessageEvent;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.napcat.PostRequest;
import top.yzljc.atribot.platform.napcat.RequestType;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.IntStream;

@Slf4j
public class AnnoyUser implements CommandExecutor, Listener {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String RECORD_FILE = Properties.ANNOY_RECORD;
    private static final Map<String, Map<String, AnnoyMode>> annoyMap = new ConcurrentHashMap<>();
    private static final Map<String, Future<?>> runningTasks = new ConcurrentHashMap<>();

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

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.NAPCAT_GROUP) return true;
        if (!GroupConfigManager.isFeatureEnabled(sender.getGroupId(), "annoy_user")) return true;
        if (args.length < 1) {
            return false;
        }

        String modeStr = args[0];
        AnnoyMode mode = AnnoyMode.fromString(modeStr);

        if (mode == null) {
            sender.sendMessage("❌ 模式错误！可用模式: normal, medium, insane, animation。\n再次输入相同模式可关闭。");
            return true;
        }

        String targetId = sender.getUserId();

        if (args.length >= 2) {
            if (!sender.hasPermission()) {
                sender.sendMessage("❌ 只有管理员可以指定目标！");
                return true;
            }
            targetId = args[1];
        }

        String groupId = sender.getGroupId();
        Map<String, AnnoyMode> groupMap = annoyMap.get(groupId);
        boolean isAlreadyInThisMode = (groupMap != null && groupMap.get(targetId) == mode);

        if (isAlreadyInThisMode) {
            removeAnnoy(groupId, targetId);
            sender.sendMessage("✅ 已关闭对 " + targetId + " 的 [" + mode + "] 模式。");
        } else {
            addAnnoy(groupId, targetId, mode);
            sender.sendMessage("😈 对 " + targetId + " 开启 [" + mode + "] 模式！\n(再次输入该指令即可关闭)");
        }

        log.info("{} {} {} annoy mode [{}] for user {}", sender.hasPermission() ? "Admin" : "User", sender.getUserId(), isAlreadyInThisMode ? "removed" : "set", mode, targetId);

        return true;
    }

    @EventHandler
    public void onGroupMessage(NapcatGroupMessageEvent event) {
        String groupId = event.getGroupId();

        if (!GroupConfigManager.isFeatureEnabled(groupId, "annoy_user")) {
            return;
        }

        if (event.getUser().isBot()) return;

        String senderId = event.getUser().getUserId();

        Map<String, AnnoyMode> groupConfig = annoyMap.get(groupId);
        if (groupConfig == null || !groupConfig.containsKey(senderId)) {
            return;
        }

        AnnoyMode mode = groupConfig.get(senderId);
        String msgId = event.getMessage().getMessageId();

        submitTask(groupId, senderId, msgId, mode);
    }

    private static void submitTask(String groupId, String userId, String msgId, AnnoyMode mode) {
        String key = groupId + "_" + userId;

        Future<?> prevTask = runningTasks.get(key);
        if (prevTask != null && !prevTask.isDone()) {
            prevTask.cancel(true);
        }

        Future<?> future = ThreadManager.setExecute(() -> {
            try {
                switch (mode) {
                    case NORMAL:
                        sendEmojis(msgId, 3, true);
                        break;
                    case MEDIUM:
                        sendEmojis(msgId, 10, false);
                        break;
                    case INSANE:
                        sendEmojis(msgId, 20, false);
                        break;
                    case ANIMATION:
                        runAnimationLogic(msgId);
                        break;
                }
            } catch (Exception e) {
                if (!(e instanceof InterruptedException)) {
                    log.error("Annoy execution error", e);
                }
            } finally {
                runningTasks.remove(key, Thread.currentThread());
            }
        });

        runningTasks.put(key, future);
    }

    private static void runAnimationLogic(String msgId) throws InterruptedException {
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

    private static void sendEmojis(String msgId, int count, boolean randomSelect) throws InterruptedException {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        int[] emojis;

        if (randomSelect) {
            emojis = random.ints(1, 21).distinct().limit(count).toArray();
        } else {
            emojis = IntStream.rangeClosed(1, count).toArray();
            if (count < 15) {
                for (int i = emojis.length - 1; i > 0; i--) {
                    int index = random.nextInt(i + 1);
                    int a = emojis[index]; emojis[index] = emojis[i]; emojis[i] = a;
                }
            }
        }

        for (int eid : emojis) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            postEmoji(msgId, eid, true);

            int delay = (count < 15) ? (200 + random.nextInt(200)) : 50;
            Thread.sleep(delay);
        }
    }

    private static void postEmoji(String msgId, int emojiId, boolean set) {
        try {
            Map<String, Object> req = new HashMap<>(4);
            req.put("message_id", msgId);
            req.put("emoji_id", emojiId);
            req.put("set", set);
            PostRequest.sendPost(RequestType.PUT_EMOJI, req);
        } catch (Exception e) {
            log.warn("Emoji API fail: {}", e.getMessage());
        }
    }

    private static void addAnnoy(String groupId, String qq, AnnoyMode mode) {
        annoyMap.computeIfAbsent(groupId, k -> new ConcurrentHashMap<>()).put(qq, mode);
        saveRecord();
    }

    private static void removeAnnoy(String groupId, String qq) {
        Map<String, AnnoyMode> group = annoyMap.get(groupId);
        if (group != null) {
            group.remove(qq);
            String key = groupId + "_" + qq;
            Future<?> f = runningTasks.remove(key);
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
                String gid = e.getKey();
                Map<String, AnnoyMode> uMap = new ConcurrentHashMap<>();
                e.getValue().forEach((k, v) -> {
                    AnnoyMode m = AnnoyMode.fromString(v);
                    if (m != null) uMap.put(k, m);
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
            uMap.forEach((uid, mode) -> sMap.put(uid, mode.name().toLowerCase()));
            raw.put(gid, sMap);
        });

        try (FileWriter writer = new FileWriter(RECORD_FILE, false)) {
            mapper.writeValue(writer, raw);
        } catch (IOException e) {
            log.error("Save annoy record error", e);
        }
    }
}
