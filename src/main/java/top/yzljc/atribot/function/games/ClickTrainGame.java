package top.yzljc.atribot.function.games;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.chat.official.button.PermissionType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialButtonInteractionEvent;
import top.yzljc.atribot.event.impl.AnswerCode;
import top.yzljc.atribot.platform.Platform;

import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ClickTrainGame implements Listener, CommandExecutor {

    private static final Map<String, GameState> activeGames = new ConcurrentHashMap<>();

    private static final int ROWS = 5;
    private static final int COLS = 6;
    private static final int TOTAL_CELLS = ROWS * COLS;
    private static final String CALLBACK_VALUE = "click_train";
    private static final long GAME_TIMEOUT_MS = 120_000;
    private static final int REWARD_PER_HIT = 2;
    private static final int MAX_REWARD = 40;
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final File RECORD_FILE = new File(Properties.CLICK_TRAIN_RECORD);

    enum Phase {
        PLAYING,
        FINISHED
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)) return true;

        boolean isC2C = qq.getPlatform() == Platform.OFFICIAL_C2C;
        String sessionId = isC2C ? qq.getUserId() : qq.getGroupId();
        String ownerId = qq.getUserId();

        if (activeGames.containsKey(ownerId)) {
            qq.sendMessage("你已经有一个正在进行的反应力测试了喵！请先完成或等它结束后再开！");
            return true;
        }

        GameState game = new GameState(sessionId, isC2C ? Platform.OFFICIAL_C2C : Platform.OFFICIAL_GROUP, ownerId);
        activeGames.put(ownerId, game);

        qq.sendMessage(TC.md(buildBoardMarkdown(game.ownerOpenId)), buildKeyboard(game));
        game.lastCmdMsgId = qq.getMessage().getMessageId();

        scheduleGameTimeout(sessionId, game);
        return true;
    }

    @EventHandler(ignoreCancelled = true)
    public void onCellCallback(OfficialButtonInteractionEvent event) {
        if (event.shouldIgnore()) return;
        String callbackValue = event.getButtonValue();
        String valuePrefix = CALLBACK_VALUE + ":";
        if (callbackValue == null || !callbackValue.startsWith(valuePrefix)) return;
        String ownerId = callbackValue.substring(valuePrefix.length());

        String sessionId = event.getChatType() == 1 ? event.getGroupOpenId() : event.getUserOpenId();
        GameState game = activeGames.get(ownerId);
        if (game == null || !game.sessionId.equals(sessionId)) {
            event.answer(AnswerCode.FAIL);
            return;
        }

        String userId = event.getUserOpenId();

        synchronized (game) {
            if (game.phase != Phase.PLAYING) {
                event.answer(AnswerCode.FAIL);
                return;
            }

            int[] cell = parseCellButtonId(event.getButtonId());
            if (cell == null) {
                event.answer(AnswerCode.FAIL);
                return;
            }

            int row = cell[0];
            int col = cell[1];

            // 已点过的格子再点 → 失误
            if (game.clicked[row][col]) {
                game.missCount++;
                game.misses.merge(userId, 1, Integer::sum);
                event.answer(AnswerCode.FAIL);
                return;
            }

            int number = game.cellNumbers[row][col];

            // 顺序不对 → 失误
            if (number != game.nextNumber) {
                game.missCount++;
                game.misses.merge(userId, 1, Integer::sum);
                event.answer(AnswerCode.FAIL);
                return;
            }

            // 顺序正确 → 首次点击开始计时，之后记录间隔，不重发面板
            long now = System.currentTimeMillis();
            if (game.startTime == 0) {
                game.startTime = now;
            } else {
                long interval = now - game.lastClickTime;
                game.totalIntervalMs += interval;
                if (game.bestIntervalMs <= 0 || interval < game.bestIntervalMs) {
                    game.bestIntervalMs = interval;
                }
            }
            game.clicked[row][col] = true;
            game.nextNumber++;
            game.completedCount++;
            game.hits.merge(userId, 1, Integer::sum);
            game.lastClickTime = now;

            // 点完 1~30 → 只发一次结算
            if (game.nextNumber > TOTAL_CELLS) {
                String markdown = endGame(game, true);
                event.answer(AnswerCode.SUCCESS);
                event.replyMessage(TC.md(markdown));
                return;
            }

            event.answer(AnswerCode.SUCCESS);
        }
    }

    private int[] parseCellButtonId(String buttonId) {
        if (buttonId == null || !buttonId.startsWith("ct_")) return null;
        String[] parts = buttonId.substring(3).split("_");
        if (parts.length != 2) return null;
        try {
            int row = Integer.parseInt(parts[0]);
            int col = Integer.parseInt(parts[1]);
            if (row < 0 || row >= ROWS || col < 0 || col >= COLS) return null;
            return new int[]{row, col};
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String cellButtonId(int row, int col) {
        return "ct_" + row + "_" + col;
    }

    private Object buildKeyboard(GameState game) {
        List<List<Button>> layout = new ArrayList<>();

        for (int row = 0; row < ROWS; row++) {
            List<Button> buttons = new ArrayList<>();
            for (int col = 0; col < COLS; col++) {
                buttons.add(new Button(
                        cellButtonId(row, col),
                        String.valueOf(game.cellNumbers[row][col]),
                        CALLBACK_VALUE + ":" + game.ownerOpenId,
                        true,
                        ButtonStyle.BLUE,
                        ButtonType.CALLBACK
                ).setAllowedOpenIds(List.of(game.ownerOpenId)).setPermissionType(PermissionType.SPECIFIC_USER));
            }
            layout.add(buttons);
        }
        return TC.keyboard(layout);
    }

    private String buildBoardMarkdown(String ownerId) {
        return """
                **反应力测试**
                
                > 请按照从1点到30的顺序点击，首次点击后开始计时
                > 手机端由于点击间隔无法完成，请使用PC端测试
                > 开始后限时 2 分钟，未完成自动取消
                > 你的最好记录: """ + formatBest(ownerId);
    }

    /**
     * 结束游戏并发放奖励：每次顺序正确 2 金粒，单人封顶 40，防止无脑刷分
     */
    private String endGame(GameState game, boolean completed) {
        game.phase = Phase.FINISHED;
        activeGames.remove(game.ownerOpenId);

        Map<String, Integer> rewards = new LinkedHashMap<>();
        if (completed) {
            for (Map.Entry<String, Integer> entry : game.hits.entrySet()) {
                int reward = Math.min(entry.getValue() * REWARD_PER_HIT, MAX_REWARD);
                rewards.put(entry.getKey(), LootRepository.addCoins(entry.getKey(), reward));
            }
        }

        long elapsedMs = game.startTime == 0 ? 0 : System.currentTimeMillis() - game.startTime;
        int intervalCount = Math.max(0, game.completedCount - 1);
        long avgInterval = intervalCount == 0 ? 0 : game.totalIntervalMs / intervalCount;
        boolean newBest = updatePersonalRecord(game.ownerOpenId, completed, elapsedMs, game.missCount);

        StringBuilder markdown = new StringBuilder();
        markdown.append(Markdown.at(game.ownerOpenId)).append("\n\n");
        markdown.append("**反应力测试 - 结算**\n\n");
        if (completed) {
            markdown.append("🎉 完成测验\n\n");
        } else {
            markdown.append("❌ 未在规定时间内完成，进度 ").append(game.completedCount).append("/").append(TOTAL_CELLS).append("\n\n");
        }
        markdown.append("**用时:** ").append(game.startTime == 0 ? "-" : String.format("%.1f", elapsedMs / 1000.0)).append(" 秒")
                .append(" | **失误:** ").append(game.missCount).append(" 次\n");
        markdown.append("**最快单步:** ").append(game.bestIntervalMs <= 0 ? "-" : game.bestIntervalMs)
                .append(" ms | **平均单步:** ").append(avgInterval <= 0 ? "-" : avgInterval).append(" ms\n");
        if (completed && newBest) {
            markdown.append("\n> 🏆 新纪录！突破了自己的最好成绩！\n");
        } else {
            markdown.append("**最好记录:** ").append(formatBest(game.ownerOpenId)).append("\n");
        }
        markdown.append("\n");

        if (game.hits.isEmpty()) {
            markdown.append("**参与程度:** 没有任何数字被点击…是不是忘了喵！\n");
        } else {
            markdown.append("**参与程度:**\n");
            game.hits.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .forEach(entry -> {
                        String uid = entry.getKey();
                        markdown.append("> ")
                                .append(game.platform == Platform.OFFICIAL_GROUP ? Markdown.at(uid) : "你")
                                .append(" ").append(entry.getValue()).append("分 / ")
                                .append(game.misses.getOrDefault(uid, 0)).append("失误 (+")
                                .append(rewards.getOrDefault(uid, 0)).append("金粒)\n");
                    });
        }

        markdown.append("\n").append(Markdown.enterCommand("/反应力测试", "再来一次"));
        return markdown.toString();
    }

    private static synchronized ObjectNode loadRecords() {
        try {
            if (RECORD_FILE.exists()) {
                JsonNode root = MAPPER.readTree(RECORD_FILE);
                if (root instanceof ObjectNode objectNode) return objectNode;
            }
        } catch (Exception e) {
            log.warn("读取反应力测试记录失败，将重建记录: {}", RECORD_FILE.getPath(), e);
        }
        return MAPPER.createObjectNode();
    }

    private static boolean saveRecords(ObjectNode root) {
        try {
            File parent = RECORD_FILE.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs()) {
                log.warn("创建反应力测试记录目录失败: {}", parent.getPath());
                return false;
            }
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(RECORD_FILE, root);
            return true;
        } catch (Exception e) {
            log.error("保存反应力测试记录失败", e);
            return false;
        }
    }

    /**
     * 完成时刷新个人最好记录，失败只累计场次
     */
    private static boolean updatePersonalRecord(String ownerId, boolean completed, long elapsedMs, int missCount) {
        ObjectNode root = loadRecords();
        ObjectNode user = userNode(root, ownerId);
        user.put("plays", user.path("plays").asInt(0) + 1);
        boolean newBest = false;
        if (completed) {
            user.put("wins", user.path("wins").asInt(0) + 1);
            long bestMs = user.path("bestMs").asLong(0);
            if (bestMs <= 0 || elapsedMs < bestMs) {
                user.put("bestMs", elapsedMs);
                user.put("bestMisses", missCount);
                user.put("bestDate", LocalDate.now().toString());
                newBest = true;
            }
        }
        saveRecords(root);
        return newBest;
    }

    private static ObjectNode userNode(ObjectNode root, String ownerId) {
        JsonNode node = root.get(ownerId);
        if (node instanceof ObjectNode objectNode) return objectNode;
        ObjectNode created = root.putObject(ownerId);
        created.put("bestMs", 0);
        created.put("bestMisses", 0);
        created.put("bestDate", "");
        created.put("plays", 0);
        created.put("wins", 0);
        return created;
    }

    private static String formatBest(String ownerId) {
        JsonNode user = loadRecords().path(ownerId);
        long bestMs = user.path("bestMs").asLong(0);
        if (bestMs <= 0) return "暂无";
        return String.format("%.1f 秒（%d 失误，%s）", bestMs / 1000.0, user.path("bestMisses").asInt(0), user.path("bestDate").asText(""));
    }

    private void scheduleGameTimeout(String sessionId, GameState game) {
        Atri.getInstance().getScheduler().runTaskLater(() -> {
            GameState current = activeGames.get(game.ownerOpenId);
            if (current != game) return;

            synchronized (current) {
                if (current.phase != Phase.PLAYING) return;

                String markdown = endGame(current, false);
                try {
                    if (current.platform == Platform.OFFICIAL_C2C) {
                        C2CChat.replyMessage(current.sessionId, current.lastCmdMsgId, TC.md(markdown));
                    } else {
                        GroupChat.replyMessage(current.sessionId, current.lastCmdMsgId, TC.md(markdown));
                    }
                } catch (Exception e) {
                    log.warn("反应力测试结算面板失败: ", e);
                }
                log.info("反应力测试游戏在会话 {} 超时结束，进度 {}/{}", sessionId, current.completedCount, TOTAL_CELLS);
            }
        }, GAME_TIMEOUT_MS);
    }

    private static class GameState {
        final String sessionId;
        final Platform platform;
        final String ownerOpenId;
        Phase phase = Phase.PLAYING;

        final int[][] cellNumbers = new int[ROWS][COLS];
        final boolean[][] clicked = new boolean[ROWS][COLS];
        int nextNumber = 1;
        int completedCount;

        int missCount;
        long totalIntervalMs;
        long bestIntervalMs;

        long startTime;
        long lastClickTime;

        final Map<String, Integer> hits = new ConcurrentHashMap<>();
        final Map<String, Integer> misses = new ConcurrentHashMap<>();

        String lastCmdMsgId;

        GameState(String sessionId, Platform platform, String ownerOpenId) {
            this.sessionId = sessionId;
            this.platform = platform;
            this.ownerOpenId = ownerOpenId;

            List<Integer> numbers = new ArrayList<>();
            for (int i = 1; i <= TOTAL_CELLS; i++) numbers.add(i);
            Collections.shuffle(numbers);

            int index = 0;
            for (int row = 0; row < ROWS; row++) {
                for (int col = 0; col < COLS; col++) {
                    cellNumbers[row][col] = numbers.get(index++);
                }
            }
        }
    }
}