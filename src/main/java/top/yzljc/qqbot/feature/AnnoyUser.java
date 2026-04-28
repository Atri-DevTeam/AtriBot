package top.yzljc.qqbot.feature;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.qqbot.service.request.PostRequest;
import top.yzljc.qqbot.service.request.RequestType;
import top.yzljc.qqbot.service.thread.ThreadManager;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.config.ConfigFile;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.event.EventHandler;
import top.yzljc.qqbot.event.Listener;
import top.yzljc.qqbot.event.impl.GroupMessageEvent;
import top.yzljc.qqbot.utils.Logger;

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

public class AnnoyUser implements CommandExecutor, Listener {

    private static final ObjectMapper mapper = new ObjectMapper();
    private static final String RECORD_FILE = ConfigFile.ANNOY_RECORD.getFileName();
    private static final Map<Long, Map<Long, AnnoyMode>> annoyMap = new ConcurrentHashMap<>();
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
        if (args.length < 1) {
            return false;
        }

        String modeStr = args[0];
        AnnoyMode mode = AnnoyMode.fromString(modeStr);

        if (mode == null) {
            sender.reply("❌ 模式错误！可用模式: normal, medium, insane, animation。\n再次输入相同模式可关闭。", false);
            return true;
        }

        long targetId = sender.userId();

        if (args.length >= 2) {
            if (!sender.isAdmin()) {
                sender.reply("❌ 只有管理员可以指定目标！", false);
                return true;
            }
            try {
                targetId = Long.parseLong(args[1]);
            } catch (NumberFormatException e) {
                sender.reply("❌ 目标QQ格式错误，请输入纯数字QQ号。", false);
                return true;
            }
        }

        long groupId = sender.groupId();
        Map<Long, AnnoyMode> groupMap = annoyMap.get(groupId);
        boolean isAlreadyInThisMode = (groupMap != null && groupMap.get(targetId) == mode);

        if (isAlreadyInThisMode) {
            removeAnnoy(groupId, targetId);
            sender.reply("✅ 已关闭对 " + targetId + " 的 [" + mode + "] 模式。", false);
        } else {
            addAnnoy(groupId, targetId, mode);
            sender.reply("😈 对 " + targetId + " 开启 [" + mode + "] 模式！\n(再次输入该指令即可关闭)", false);
        }

        Logger.info("{} {} {} annoy mode [{}] for user {}", sender.isAdmin() ? "Admin" : "User", sender.userId(), isAlreadyInThisMode ? "removed" : "set", mode, targetId);

        return true;
    }

    @EventHandler
    public void onGroupMessage(GroupMessageEvent event) {
        long groupId = event.getGroupId();

        if (!GroupConfigManager.isFeatureEnabled(groupId, "annoy_user")) {
            return;
        }

        long senderId = event.getUserId();
        long botId = event.getSelfId();
        if (senderId == botId) return;

        Map<Long, AnnoyMode> groupConfig = annoyMap.get(groupId);
        if (groupConfig == null || !groupConfig.containsKey(senderId)) {
            return;
        }

        AnnoyMode mode = groupConfig.get(senderId);
        long msgId = event.getMessageId();

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
                if (!(e instanceof InterruptedException)) {
                    Logger.error("Annoy execution error", e);
                }
            } finally {
                runningTasks.remove(key, Thread.currentThread()); // 只移除当前线程的引用
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

    private static void sendEmojis(long msgId, int count, boolean randomSelect) throws InterruptedException {
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
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException();
            postEmoji(msgId, eid, true);

            int delay = (count < 15) ? (200 + random.nextInt(200)) : 50;
            Thread.sleep(delay);
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
            Logger.warn("Emoji API fail: {}", e.getMessage());
        }
    }

    private static void addAnnoy(long groupId, long qq, AnnoyMode mode) {
        annoyMap.computeIfAbsent(groupId, k -> new ConcurrentHashMap<>()).put(qq, mode);
        saveRecord();
    }

    private static void removeAnnoy(long groupId, long qq) {
        Map<Long, AnnoyMode> group = annoyMap.get(groupId);
        if (group != null) {
            group.remove(qq);
            // 移除的同时，取消正在进行的任务
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
                long gid = Long.parseLong(e.getKey());
                Map<Long, AnnoyMode> uMap = new ConcurrentHashMap<>();
                e.getValue().forEach((k, v) -> {
                    AnnoyMode m = AnnoyMode.fromString(v);
                    if (m != null) uMap.put(Long.parseLong(k), m);
                });
                if (!uMap.isEmpty()) annoyMap.put(gid, uMap);
            }
        } catch (Exception e) {
            Logger.error("Load annoy record error", e);
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
            Logger.error("Save annoy record error", e);
        }
    }
}