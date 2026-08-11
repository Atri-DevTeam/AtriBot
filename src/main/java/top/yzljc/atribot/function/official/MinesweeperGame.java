package top.yzljc.atribot.function.official;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.platform.Platform;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class MinesweeperGame implements Listener, CommandExecutor {

    private static final Map<String, GameState> activeGames = new ConcurrentHashMap<>();

    private static final int ROWS = 5;
    private static final int COLS = 6;
    private static final int DEFAULT_MINES = 6;
    private static final int MAX_MINES = 29;
    private static final int MIN_POOL = 5;
    private static final int MID_POOL = 45;
    private static final int MAX_POOL = 80;
    private static final String[] NUM_EMOJIS = {"⬜", "1️⃣", "2️⃣", "3️⃣", "4️⃣", "5️⃣", "6️⃣", "7️⃣", "8️⃣"};

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof QQCommandSender qq)) {
            return true;
        }

        String sessionId = qq.getPlatform() == Platform.OFFICIAL_C2C ? qq.getUserId() : qq.getGroupId();

        // 1. 发送欢迎与规则界面
        if (args.length == 0) {
            sendWelcomeScreen(sessionId, qq);
            return true;
        }

        String action = args[0].toLowerCase();

        // 2. 处理游戏开始指令
        if (action.equals("start")) {
            int mines = DEFAULT_MINES;
            if (args.length > 1) {
                try {
                    mines = Integer.parseInt(args[1]);
                } catch (NumberFormatException ignored) {
                }
            }
            mines = Math.max(1, Math.min(mines, MAX_MINES));
            handleStartGame(sessionId, qq, mines);
            return true;
        }

        // --- 游戏内操作拦截 ---
        GameState game = activeGames.get(sessionId);
        if (game == null) {
            qq.sendMessage("当前没有正在进行的扫雷游戏喵，请发送 /扫雷 开始新游戏！");
            return true;
        }

        game.participants.add(qq.getUserId());

        if (args.length < 2) return true;

        String coordStr = args[1].toUpperCase();
        if (!coordStr.matches("^[A-E][1-6]$")) return true;

        int row = coordStr.charAt(0) - 'A';
        int col = Integer.parseInt(coordStr.substring(1)) - 1;

        synchronized (game) {
            if (game.isGameOver) {
                qq.sendMessage("本局游戏已经结束了喵！请重新发送 /扫雷 开始新游戏！");
                return true;
            }

            if (action.equals("l")) {
                handleDig(game, row, col, sessionId, qq);
            } else if (action.equals("q")) {
                handleFlag(game, row, col, sessionId, qq);
            }
        }
        return true;
    }

    private void sendWelcomeScreen(String sessionId, QQCommandSender sender) {
        if (activeGames.containsKey(sessionId)) {
            sender.sendMessage("当前环境已经有一个正在进行的扫雷游戏了喵！请通关或等它结束后再开！");
            return;
        }

        String markdown = """
                **扫雷小游戏 (6x5)**

                **规则与操作:**
                1. 使用“扫雷区”按钮挖开格子。
                2. 使用“插旗区”按钮插旗或拔旗。
                3. 与传统扫雷游戏一致，排除所有地雷后获胜！

                请点击下方按钮开始游戏：
                """;

        List<List<Button>> layout = new ArrayList<>();
        layout.add(List.of(new Button("btn_start", "开始游戏 (默认" + DEFAULT_MINES + "雷)", "/扫雷 start " + DEFAULT_MINES,
                true, ButtonStyle.BLUE, ButtonType.COMMAND)));
        layout.add(List.of(new Button("btn_custom", "自定义雷数并开始", "/扫雷 start ",
                false, ButtonStyle.GRAY, ButtonType.COMMAND)));

        Object keyboard = TC.keyboard(layout);
        sender.sendMessage(TC.md(markdown), keyboard);
    }

    private void handleStartGame(String sessionId, QQCommandSender sender, int minesCount) {
        if (activeGames.containsKey(sessionId)) return;

        GameState newGame = new GameState(minesCount);
        activeGames.put(sessionId, newGame);

        sendOrUpdateGameBoard(newGame, sessionId, sender, "游戏已开始！");
    }

    private void handleDig(GameState game, int r, int c, String sessionId, QQCommandSender sender) {
        // --- 非法操作提示：已插旗不允许挖开 ---
        if (game.flagged[r][c]) {
            sendOrUpdateGameBoard(game, sessionId, sender, "⚠️ 此处已插旗，无法挖开！请先拔旗。");
            return;
        }
        // --- 非法操作提示：已经挖开了 ---
        if (game.revealed[r][c]) {
            sendOrUpdateGameBoard(game, sessionId, sender, "⚠️ 已经挖开啦，不要重复挖喵！");
            return;
        }

        game.operations.merge(sender.getUserId(), 1, Integer::sum);
        game.revealed[r][c] = true;

        if (game.board[r][c] == -1) {
            game.isGameOver = true;
            game.isWin = false;
            revealAllMines(game);
            finishGame(game, sessionId, sender, "💥 踩到雷了！游戏失败！");
            return;
        }

        if (game.board[r][c] == 0) floodFill(game, r, c);

        checkWinCondition(game, sessionId, sender);
        if (!game.isGameOver) {
            sendOrUpdateGameBoard(game, sessionId, sender, "挖开了 " + (char) ('A' + r) + (c + 1));
        }
    }

    private void handleFlag(GameState game, int r, int c, String sessionId, QQCommandSender sender) {
        // --- 非法操作提示：已经挖开了的格子不能插旗 ---
        if (game.revealed[r][c]) {
            sendOrUpdateGameBoard(game, sessionId, sender, "⚠️ 已经挖开的区域不能插旗喵！");
            return;
        }

        game.operations.merge(sender.getUserId(), 1, Integer::sum);
        game.flagged[r][c] = !game.flagged[r][c];
        sendOrUpdateGameBoard(game, sessionId, sender, game.flagged[r][c] ? "成功插旗 🚩" : "已拔除旗帜 🔲");
    }

    private void floodFill(GameState game, int r, int c) {
        int[] dr = {-1, -1, -1, 0, 0, 1, 1, 1};
        int[] dc = {-1, 0, 1, -1, 1, -1, 0, 1};

        for (int i = 0; i < 8; i++) {
            int nr = r + dr[i];
            int nc = c + dc[i];
            if (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS) {
                if (!game.revealed[nr][nc] && !game.flagged[nr][nc]) {
                    game.revealed[nr][nc] = true;
                    if (game.board[nr][nc] == 0) floodFill(game, nr, nc);
                }
            }
        }
    }

    private void checkWinCondition(GameState game, String sessionId, QQCommandSender sender) {
        int unrevealedCount = 0;
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                if (!game.revealed[i][j]) unrevealedCount++;
            }
        }
        if (unrevealedCount == game.minesCount) {
            game.isGameOver = true;
            game.isWin = true;
            finishGame(game, sessionId, sender, "🎉 恭喜通关！");
        }
    }

    private void revealAllMines(GameState game) {
        for (int i = 0; i < ROWS; i++) {
            for (int j = 0; j < COLS; j++) {
                if (game.board[i][j] == -1) game.revealed[i][j] = true;
            }
        }
    }

    private void finishGame(GameState game, String sessionId, QQCommandSender sender, String resultMsg) {
        activeGames.remove(sessionId);
        long timeTaken = (System.currentTimeMillis() - game.startTime) / 1000;

        Map<String, Integer> rewards = grantRewards(game);

        StringBuilder participantsMarkdown = new StringBuilder();
        if (sender.getPlatform() == Platform.OFFICIAL_GROUP) {
            for (String uid : game.participants) {
                participantsMarkdown.append(String.format("<qqbot-at-user id=\"%s\" />(+%d金粒) ", uid, rewards.getOrDefault(uid, 0)));
            }
        } else {
            String uid = game.participants.iterator().next();
            participantsMarkdown.append("你(+").append(rewards.getOrDefault(uid, 0)).append("金粒) ");
        }

        String markdown = String.format("""
                **扫雷小游戏 - 结算**
                **结果:** %s
                **雷数:** %d
                **耗时:** %d 秒
                **参与者:** %s

                %s

                最终棋盘如下：
                """, resultMsg, game.minesCount, timeTaken, participantsMarkdown,
                buildInlineCmd("/扫雷", "再来一局"));

        sendKeyboardMessage(game, sender, markdown);
    }

    /**
     * 结算金粒奖励：按难度奖池 + 参与度占比瓜分
     * 奖池分段映射：1~6 雷 5→45（陡），6~29 雷 45→80（缓）
     * 个人奖励 = 奖池 × 个人操作数 / 全员总操作数
     * 操作数为 0 的（含只开局的发起者）不发；操作 ≥1 保底 1 金粒；四舍五入尾差归操作最多者
     */
    private Map<String, Integer> grantRewards(GameState game) {
        Map<String, Integer> rewards = new HashMap<>();

        // 难度奖池分段：1~6 雷线性到 45，6~29 雷线性到 80
        int pool;
        if (game.minesCount <= DEFAULT_MINES) {
            pool = MIN_POOL + (game.minesCount - 1) * (MID_POOL - MIN_POOL) / (DEFAULT_MINES - 1);
        } else {
            pool = MID_POOL + (int) Math.round((game.minesCount - DEFAULT_MINES) * (double) (MAX_POOL - MID_POOL) / (MAX_MINES - DEFAULT_MINES));
        }

        int totalOps = game.operations.values().stream().mapToInt(Integer::intValue).sum();
        if (totalOps <= 0) {
            return rewards;
        }

        // 按参与度占比瓜分
        int allocated = 0;
        for (String uid : game.participants) {
            int ops = game.operations.getOrDefault(uid, 0);
            if (ops <= 0) {
                rewards.put(uid, 0);
                continue;
            }
            int reward = Math.max(1, (int) Math.round((double) pool * ops / totalOps));
            rewards.put(uid, reward);
            allocated += reward;
        }

        // 四舍五入尾差归操作最多者
        int diff = pool - allocated;
        if (diff != 0) {
            String top = game.operations.entrySet().stream()
                    .max(Map.Entry.comparingByValue())
                    .map(Map.Entry::getKey)
                    .orElse(null);
            if (top != null) {
                rewards.put(top, rewards.getOrDefault(top, 0) + diff);
            }
        }

        for (Map.Entry<String, Integer> entry : rewards.entrySet()) {
            LootRepository.addCoins(entry.getKey(), entry.getValue());
        }
        return rewards;
    }

    private void sendOrUpdateGameBoard(GameState game, String sessionId, QQCommandSender sender, String actionMsg) {
        long timeElapsed = (System.currentTimeMillis() - game.startTime) / 1000;

        int flagCount = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (game.flagged[r][c]) flagCount++;
            }
        }

        StringBuilder flagBoard = new StringBuilder();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                String coord = "" + (char) ('A' + r) + (c + 1);
                String displayChar;
                String command;
                if (game.revealed[r][c]) {
                    displayChar = displayCell(game, r, c);
                    command = "/扫雷 l " + coord;
                } else {
                    displayChar = game.flagged[r][c] ? "🚩" : "🔲";
                    command = "/扫雷 q " + coord;
                }
                flagBoard.append(buildInlineCmd(command, displayChar)).append(" ");
            }
            flagBoard.append("\n");
        }

        String markdown = String.format("""
                **扫雷** | 用时: %d秒
                > **状态: %s**
                > 旗帜数量: %d/%d

                **插旗区**
                %s

                **扫雷区**
                """, timeElapsed, actionMsg, flagCount, game.minesCount, flagBoard);

        sendKeyboardMessage(game, sender, markdown);
    }

    private String displayCell(GameState game, int r, int c) {
        if (game.board[r][c] == -1) return "💥";
        if (game.board[r][c] == 0) return "⬜";
        return NUM_EMOJIS[game.board[r][c]];
    }

    private String displayButtonCell(GameState game, int r, int c) {
        if (game.board[r][c] == -1) return "💥";
        if (game.board[r][c] == 0) return "⬜";
        return String.valueOf(game.board[r][c]);
    }

    private String buildInlineCmd(String text, String show) {
        try {
            String encodedText = URLEncoder.encode(text, StandardCharsets.UTF_8);
            String encodedShow = URLEncoder.encode(show, StandardCharsets.UTF_8);
            return String.format("<qqbot-cmd-input text=\"%s\" show=\"%s\" reference=\"false\" />", encodedText, encodedShow);
        } catch (Exception e) {
            return show;
        }
    }

    private void sendKeyboardMessage(GameState game, QQCommandSender sender, String markdown) {

        List<List<Button>> layout = new ArrayList<>();

        for (int r = 0; r < ROWS; r++) {
            List<Button> rowBtn = new ArrayList<>();
            for (int c = 0; c < COLS; c++) {

                String coord = "" + (char) ('A' + r) + (c + 1);
                String btnId = "ms_" + coord;
                String data = "/扫雷 l " + coord;
                String text;
                ButtonStyle style;

                if (game.revealed[r][c]) {
                    style = ButtonStyle.BLUE;
                    text = displayButtonCell(game, r, c);
                } else {
                    style = ButtonStyle.GRAY;
                    text = game.flagged[r][c] ? "🚩" : "🔲";
                }

                rowBtn.add(new Button(btnId, text, data, true, style, ButtonType.COMMAND));
            }
            layout.add(rowBtn);
        }

        Object keyboard = TC.keyboard(layout);

        String newMessageId = sender.sendMessage(TC.md(markdown), keyboard);

        if (game.lastMessageId != null) {
            String recordedMessageId = game.lastMessageId;
            try {
                if (sender.getPlatform() == Platform.OFFICIAL_C2C) {
                    String userId = sender.getUserId();
                    Atri.getInstance().getScheduler().runTaskLater(() ->
                            C2CChat.recallMessage(userId, recordedMessageId), 15 * 1000);
                } else {
                    String groupId = sender.getGroupId();
                    Atri.getInstance().getScheduler().runTaskLater(() ->
                            GroupChat.recallMessage(groupId, recordedMessageId), 15 * 1000);
                }
            } catch (Exception e) {
                log.warn("撤回扫雷旧消息失败: ", e);
            }
        }

        game.lastMessageId = newMessageId;
    }

    private static class GameState {
        int[][] board = new int[ROWS][COLS];
        boolean[][] revealed = new boolean[ROWS][COLS];
        boolean[][] flagged = new boolean[ROWS][COLS];

        long startTime = System.currentTimeMillis();
        int minesCount;

        String lastMessageId = null;

        boolean isGameOver = false;
        boolean isWin = false;

        Set<String> participants = ConcurrentHashMap.newKeySet();
        Map<String, Integer> operations = new ConcurrentHashMap<>();

        public GameState(int minesCount) {
            this.minesCount = minesCount;
            initBoard();
        }

        private void initBoard() {
            Random random = new Random();
            int placedMines = 0;
            while (placedMines < minesCount) {
                int r = random.nextInt(ROWS);
                int c = random.nextInt(COLS);
                if (board[r][c] != -1) {
                    board[r][c] = -1;
                    placedMines++;
                }
            }
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    if (board[r][c] == -1) continue;
                    int count = 0;
                    for (int i = -1; i <= 1; i++) {
                        for (int j = -1; j <= 1; j++) {
                            int nr = r + i;
                            int nc = c + j;
                            if (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS && board[nr][nc] == -1) {
                                count++;
                            }
                        }
                    }
                    board[r][c] = count;
                }
            }
        }
    }
}
