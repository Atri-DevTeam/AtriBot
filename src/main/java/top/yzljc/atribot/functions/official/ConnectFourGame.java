package top.yzljc.atribot.functions.official;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Keyboard;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.event.Listener;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class ConnectFourGame implements Listener, CommandExecutor {

    private static final Map<String, GameState> activeGames = new ConcurrentHashMap<>();

    // Board dimensions
    private static final int ROWS = 6;
    private static final int COLS = 7;
    private static final int WIN_COUNT = 4;

    // Timeouts (milliseconds)
    private static final long JOIN_TIMEOUT_MS = 60_000;
    private static final long MOVE_TIMEOUT_MS = 60_000;
    private static final long GAME_TIMEOUT_MS = 900_000;
    private static final long RECALL_DELAY_MS = 15_000;

    // Emoji constants
    private static final String EMPTY = "⬜";
    private static final String BLACK = "⚫";
    private static final String WHITE = "⚪";
    private static final String GREEN = "🟢";

    // Cell states
    private static final int EMPTY_CELL = 0;
    private static final int PLAYER_A = 1;
    private static final int PLAYER_B = 2;

    enum Phase {
        WAITING,
        PLAYING,
        FINISHED
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        // Private chat not supported
        if (label.equals("1")) return true;

        String sessionId = sender.groupOpenId();

        // No args → show welcome / rules screen
        if (args.length == 0) {
            if (activeGames.containsKey(sessionId)) {
                sender.replyText(label, "当前群聊已经有一个正在进行的四子棋游戏了喵！请等它结束后再开！");
            } else {
                sendWelcomeScreen(sessionId, label, sender);
            }
            return true;
        }

        String action = args[0].toLowerCase();

        // "join" — valid even without an active game reference
        if (action.equals("join")) {
            handleJoin(sessionId, label, sender, args);
            return true;
        }

        // All other commands require an active game
        GameState game = activeGames.get(sessionId);
        if (game == null) {
            sender.replyText(label, "当前没有正在进行的四子棋游戏喵！请发送 /四子棋 开始新游戏！");
            return true;
        }

        if (action.equals("drop")) {
            handleDrop(game, sessionId, label, sender, args);
        }

        return true;
    }

    private void sendWelcomeScreen(String sessionId, String label, CommandSender sender) {
        GameState game = new GameState();
        game.groupOpenId = sessionId;
        game.label = label;
        activeGames.put(sessionId, game);

        String markdown = """
                **四子棋 (Connect Four)**

                **规则:**
                - 7列 × 6行棋盘，双方轮流落子
                - 棋子会因重力下沉到该列最底部的空位
                - 率先在横、竖、斜方向连成 4 子者获胜
                - 若棋盘填满仍无人连成四子，则为平局

                **时限:**
                - 每人每步限时 60 秒，超时判对方胜
                - 整场对局不超过 15 分钟，超时自动平局

                请点击下方按钮选择你的阵营:
                > 游戏将在 1 分钟内未满员时自动取消
                """;

        // Keyboard: one button per row, both GRAY (empty slots)
        List<List<Button>> layout = new ArrayList<>();
        layout.add(List.of(new Button("join_a", "⚫ 玩家A", "/四子棋 join A",
                true, ButtonStyle.GRAY, ButtonType.COMMAND)));
        layout.add(List.of(new Button("join_b", "⚪ 玩家B", "/四子棋 join B",
                true, ButtonStyle.GRAY, ButtonType.COMMAND)));

        Object keyboard = Keyboard.compose(layout);
        sender.replyMarkdown(label, TC.md(markdown), keyboard);
        game.lastCmdMsgId = sender.messageOpenId();

        scheduleJoinTimeout(sessionId, game);
    }

    private void handleJoin(String sessionId, String label, CommandSender sender, String[] args) {
        GameState game = activeGames.get(sessionId);
        if (game == null) {
            sender.replyText(label, "请先发送 /四子棋 创建游戏！");
            return;
        }
        if (game.phase != Phase.WAITING) {
            sender.replyText(label, "游戏已经开始，无法加入喵！");
            return;
        }
        if (args.length < 2) {
            sender.replyText(label, "请指定加入阵营：A 或 B");
            return;
        }

        String side = args[1].toUpperCase();
        String playerId = sender.unionOpenId();

        if (side.equals("A")) {
            if (game.playerAOpenId != null) {
                if (game.playerAOpenId.equals(playerId)) {
                    return; // Already joined — no-op
                }
                sender.replyText(label, "玩家A已经被占用了喵！");
                return;
            }
            if (playerId.equals(game.playerBOpenId)) {
                sender.replyText(label, "你已经加入了玩家B，不能同时加入两个阵营喵！");
                return;
            }
            game.playerAOpenId = playerId;
        } else if (side.equals("B")) {
            if (game.playerBOpenId != null) {
                if (game.playerBOpenId.equals(playerId)) {
                    return; // Already joined — no-op
                }
                sender.replyText(label, "玩家B已经被占用了喵！");
                return;
            }
            if (playerId.equals(game.playerAOpenId)) {
                sender.replyText(label, "你已经加入了玩家A，不能同时加入两个阵营喵！");
                return;
            }
            game.playerBOpenId = playerId;
        } else {
            return;
        }

        // Send updated waiting message
        sendWaitingUpdate(game, sessionId, label, sender);

        // Both players joined → start game
        if (game.playerAOpenId != null && game.playerBOpenId != null) {
            startGame(game, sessionId, label, sender);
        }
    }

    private void sendWaitingUpdate(GameState game, String sessionId, String label, CommandSender sender) {
        StringBuilder sb = new StringBuilder();
        sb.append("**四子棋 (Connect Four)**\n\n");
        sb.append("**规则:**\n");
        sb.append("- 7列 × 6行棋盘，双方轮流落子\n");
        sb.append("- 棋子会因重力下沉到该列最底部的空位\n");
        sb.append("- 率先在横、竖、斜方向连成 4 子者获胜\n");
        sb.append("- 若棋盘填满仍无人连成四子，则为平局\n\n");
        sb.append("**时限:**\n");
        sb.append("- 每人每步限时 60 秒，超时判对方胜\n");
        sb.append("- 整场对局不超过 15 分钟，超时自动平局\n\n");

        sb.append("**当前状态:**\n");
        if (game.playerAOpenId != null) {
            sb.append("> ⚫ 玩家A: ").append(Markdown.at(game.playerAOpenId));
        } else {
            sb.append("> ⚫ 玩家A: 等待加入");
        }
        sb.append("\n");
        if (game.playerBOpenId != null) {
            sb.append("> ⚪ 玩家B: ").append(Markdown.at(game.playerBOpenId));
        } else {
            sb.append("> ⚪ 玩家B: 等待加入");
        }
        sb.append("\n\n> 游戏将在 1 分钟内未满员时自动取消");

        // Keyboard buttons: one per row, GRAY = empty, BLUE = taken
        List<List<Button>> layout = new ArrayList<>();

        ButtonStyle styleA = game.playerAOpenId != null ? ButtonStyle.BLUE : ButtonStyle.GRAY;
        String displayA = game.playerAOpenId != null ? "⚫ 玩家A ✓" : "⚫ 玩家A";
        layout.add(List.of(new Button("join_a", displayA, "/四子棋 join A",
                true, styleA, ButtonType.COMMAND)));

        ButtonStyle styleB = game.playerBOpenId != null ? ButtonStyle.BLUE : ButtonStyle.GRAY;
        String displayB = game.playerBOpenId != null ? "⚪ 玩家B ✓" : "⚪ 玩家B";
        layout.add(List.of(new Button("join_b", displayB, "/四子棋 join B",
                true, styleB, ButtonType.COMMAND)));

        Object keyboard = Keyboard.compose(layout);

        String messageId = sender.replyMarkdown(label, TC.md(sb.toString()), keyboard);
        recallOldMessage(game);
        game.lastMessageId = messageId;
        game.lastCmdMsgId = sender.messageOpenId();
    }

    private void startGame(GameState game, String sessionId, String label, CommandSender sender) {
        game.phase = Phase.PLAYING;
        game.startTime = System.currentTimeMillis();
        game.currentPlayer = PLAYER_A;
        game.board = new int[ROWS][COLS];

        String firstPlayerName = "⚫ 玩家A";
        String firstPlayerId = game.playerAOpenId;

        sendGameBoard(game, sessionId, label, sender,
                "游戏开始！由 " + firstPlayerName + " " + Markdown.at(firstPlayerId) + " 先手落子");

        scheduleMoveTimeout(sessionId, game);
        scheduleGameTimeout(sessionId, game);
    }

    private void handleDrop(GameState game, String sessionId, String label, CommandSender sender, String[] args) {
        if (game.phase != Phase.PLAYING) {
            sender.replyText(label, "游戏已经结束了喵！请发送 /四子棋 开始新游戏！");
            return;
        }

        String playerId = sender.unionOpenId();

        // Non-participant → silent ignore
        if (!playerId.equals(game.playerAOpenId) && !playerId.equals(game.playerBOpenId)) {
            return;
        }

        // Wrong turn
        String currentPlayerId = game.currentPlayer == PLAYER_A ? game.playerAOpenId : game.playerBOpenId;
        if (!playerId.equals(currentPlayerId)) {
            sender.replyText(label, "还没轮到你落子喵！请等待对手下完！");
            return;
        }

        if (args.length < 2) {
            sender.replyText(label, "请指定要落子的列号 (1-7)");
            return;
        }

        int col;
        try {
            col = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            return;
        }

        // Convert 1-based display column to 0-based internal index
        col = col - 1;

        if (col < 0 || col >= COLS) {
            sender.replyText(label, "无效的列号！请输入 1-7 之间的数字。");
            return;
        }

        int row = findDropRow(game.board, col);
        if (row == -1) {
            sender.replyText(label, "该列已满，请选择其他列喵！");
            return;
        }

        // Place the piece
        game.board[row][col] = game.currentPlayer;

        // Check win
        List<int[]> winningLine = findWinningLine(game.board, row, col, game.currentPlayer);
        if (winningLine != null) {
            game.phase = Phase.FINISHED;
            game.winningLine = winningLine;
            game.winner = game.currentPlayer;

            String winnerName = game.currentPlayer == PLAYER_A ? "⚫ 玩家A" : "⚪ 玩家B";
            String winnerId = game.currentPlayer == PLAYER_A ? game.playerAOpenId : game.playerBOpenId;

            finishGame(game, sessionId, label, sender,
                    "🎉 " + winnerName + " " + Markdown.at(winnerId) + " 获胜！连成四子！");
            return;
        }

        // Check tie
        if (isBoardFull(game.board)) {
            game.phase = Phase.FINISHED;
            finishGame(game, sessionId, label, sender, "🤝 平局！棋盘已满，无人连成四子！");
            return;
        }

        // Switch turns
        game.currentPlayer = (game.currentPlayer == PLAYER_A) ? PLAYER_B : PLAYER_A;
        String nextPlayerName = game.currentPlayer == PLAYER_A ? "⚫ 玩家A" : "⚪ 玩家B";
        String nextPlayerId = game.currentPlayer == PLAYER_A ? game.playerAOpenId : game.playerBOpenId;

        sendGameBoard(game, sessionId, label, sender,
                "轮到 " + nextPlayerName + " " + Markdown.at(nextPlayerId) + " 落子");

        scheduleMoveTimeout(sessionId, game);
    }

    private void sendGameBoard(GameState game, String sessionId, String label, CommandSender sender, String statusMsg) {
        StringBuilder md = new StringBuilder();

        // Header — show elapsed time
        long elapsed = (System.currentTimeMillis() - game.startTime) / 1000;
        md.append("**四子棋** | 用时: ").append(elapsed).append(" 秒\n\n");
        if (statusMsg != null && !statusMsg.isEmpty()) {
            md.append("\n\n> ").append(statusMsg).append("\n\n");
        }

        // Board (row 5 = top, row 0 = bottom)
        for (int r = ROWS - 1; r >= 0; r--) {
            for (int c = 0; c < COLS; c++) {
                if (game.winningLine != null && isWinningCell(game.winningLine, r, c)) {
                    md.append(GREEN);
                } else {
                    switch (game.board[r][c]) {
                        case PLAYER_A -> md.append(BLACK);
                        case PLAYER_B -> md.append(WHITE);
                        default -> md.append(Markdown.enterCommand("/四子棋 drop " + (c + 1), EMPTY));
                    }
                }
                if (c < COLS - 1) md.append(" ");
            }
            md.append("\n");
        }

        md.append("\n\n>⚫ ").append(Markdown.at(game.playerAOpenId)).append("\n>")
                .append("⚪ ").append(Markdown.at(game.playerBOpenId));

        String messageId = sender.replyMarkdown(label, TC.md(md.toString()));
        recallOldMessage(game);
        game.lastMessageId = messageId;
        game.lastCmdMsgId = sender.messageOpenId();
    }

    private void finishGame(GameState game, String sessionId, String label, CommandSender sender, String resultMsg) {
        activeGames.remove(sessionId);

        String markdown = buildSettlementMarkdown(game, resultMsg);
        markdown += "\n\n" + Markdown.enterCommand("/四子棋", "点击此处再次开始");

        String messageId = sender.replyMarkdown(label, TC.md(markdown));
        recallOldMessage(game);
        game.lastMessageId = messageId;
    }

    private String buildSettlementMarkdown(GameState game, String resultMsg) {
        long timeTaken = (System.currentTimeMillis() - game.startTime) / 1000;

        StringBuilder md = new StringBuilder();
        md.append("**四子棋 (Connect Four) - 结算**\n\n");
        md.append("**结果:** ").append(resultMsg).append("\n");
        md.append("**耗时:** ").append(timeTaken).append(" 秒\n\n");

        // Final board
        for (int r = ROWS - 1; r >= 0; r--) {
            for (int c = 0; c < COLS; c++) {
                if (game.winningLine != null && isWinningCell(game.winningLine, r, c)) {
                    md.append(GREEN);
                } else {
                    switch (game.board[r][c]) {
                        case PLAYER_A -> md.append(BLACK);
                        case PLAYER_B -> md.append(WHITE);
                        default -> md.append(EMPTY);
                    }
                }
                if (c < COLS - 1) md.append(" ");
            }
            md.append("\n");
        }

        md.append("\n\n>⚫ ").append(Markdown.at(game.playerAOpenId)).append("\n>")
                .append("⚪ ").append(Markdown.at(game.playerBOpenId));

        return md.toString();
    }

    // ================================================================
    // Game Logic
    // ================================================================

    /**
     * Find the lowest empty row in a column (row 0 = bottom).
     * Returns -1 if the column is full.
     */
    private int findDropRow(int[][] board, int col) {
        for (int r = 0; r < ROWS; r++) {
            if (board[r][col] == EMPTY_CELL) return r;
        }
        return -1;
    }

    /**
     * Check whether the board has no empty cells left.
     */
    private boolean isBoardFull(int[][] board) {
        for (int c = 0; c < COLS; c++) {
            if (board[ROWS - 1][c] == EMPTY_CELL) return false;
        }
        return true;
    }

    /**
     * Check whether the last-placed piece at (r, c) completes a line of WIN_COUNT
     * for the given player. Returns the winning cell list, or null.
     */
    private List<int[]> findWinningLine(int[][] board, int r, int c, int player) {
        int[][] dirs = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};

        for (int[] dir : dirs) {
            List<int[]> line = new ArrayList<>();
            line.add(new int[]{r, c});

            for (int i = 1; i < WIN_COUNT; i++) {
                int nr = r + dir[0] * i;
                int nc = c + dir[1] * i;
                if (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS && board[nr][nc] == player) {
                    line.add(new int[]{nr, nc});
                } else break;
            }

            for (int i = 1; i < WIN_COUNT; i++) {
                int nr = r - dir[0] * i;
                int nc = c - dir[1] * i;
                if (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS && board[nr][nc] == player) {
                    line.add(new int[]{nr, nc});
                } else break;
            }

            if (line.size() >= WIN_COUNT) return line;
        }
        return null;
    }

    private boolean isWinningCell(List<int[]> winningLine, int r, int c) {
        for (int[] pos : winningLine) {
            if (pos[0] == r && pos[1] == c) return true;
        }
        return false;
    }

    private void scheduleJoinTimeout(String sessionId, GameState game) {
        Atri.getInstance().getScheduler().runTaskLater(() -> {
            GameState current = activeGames.get(sessionId);
            if (current != game) return;
            if (current.phase != Phase.WAITING) return;

            String targetOpenId = current.playerAOpenId != null ? current.playerAOpenId : current.playerBOpenId;
            activeGames.remove(sessionId);

            if (targetOpenId != null && current.lastCmdMsgId != null) {
                try {
                    GroupChat.replyMessage(current.groupOpenId, targetOpenId, current.lastCmdMsgId,
                            TC.md("⏰ 四子棋游戏因 1 分钟内未满员而自动取消 " + Markdown.at(targetOpenId)));
                } catch (Exception e) {
                    log.warn("发送加入超时通知失败: ", e);
                }
            }
            log.info("四子棋游戏在群 {} 因超时未满员而自动取消", sessionId);
        }, JOIN_TIMEOUT_MS);
    }

    private void scheduleMoveTimeout(String sessionId, GameState game) {
        final int expectedGeneration = ++game.timeoutGeneration;

        Atri.getInstance().getScheduler().runTaskLater(() -> {
            GameState current = activeGames.get(sessionId);
            if (current != game) return;
            if (current.phase != Phase.PLAYING) return;
            if (current.timeoutGeneration != expectedGeneration) return;

            // Timeout — current player loses, opponent wins
            current.phase = Phase.FINISHED;
            current.winner = (current.currentPlayer == PLAYER_A) ? PLAYER_B : PLAYER_A;

            String loserId = current.currentPlayer == PLAYER_A ? current.playerAOpenId : current.playerBOpenId;
            String winnerId = current.winner == PLAYER_A ? current.playerAOpenId : current.playerBOpenId;
            String loserName = current.currentPlayer == PLAYER_A ? "⚫ 玩家A" : "⚪ 玩家B";
            String winnerName = current.winner == PLAYER_A ? "⚫ 玩家A" : "⚪ 玩家B";

            activeGames.remove(sessionId);

            try {
                String resultMsg = "⏰ " + Markdown.at(loserId) + " (" + loserName
                        + ") 超时未落子，" + winnerName + " " + Markdown.at(winnerId) + " 获胜！";
                String markdown = buildSettlementMarkdown(current, resultMsg);

                List<List<Button>> layout = new ArrayList<>();
                layout.add(List.of(new Button("play_again", "再来一局", "/四子棋",
                        true, ButtonStyle.BLUE, ButtonType.COMMAND)));
                Object keyboard = Keyboard.compose(layout);

                GroupChat.replyMessage(current.groupOpenId, loserId, current.lastCmdMsgId,
                        TC.md(markdown), keyboard);
            } catch (Exception e) {
                log.warn("发送落子超时结算面板失败: ", e);
            }
            log.info("四子棋游戏在群 {} 因玩家 {} 超时未落子而自动结束",
                    sessionId, loserId);
        }, MOVE_TIMEOUT_MS);
    }

    private void scheduleGameTimeout(String sessionId, GameState game) {
        Atri.getInstance().getScheduler().runTaskLater(() -> {
            GameState current = activeGames.get(sessionId);
            if (current != game) return;
            if (current.phase != Phase.PLAYING) return;

            // Game exceeded 5 minutes — auto tie
            current.phase = Phase.FINISHED;
            String playerAId = current.playerAOpenId;
            String playerBId = current.playerBOpenId;
            activeGames.remove(sessionId);

            try {
                String resultMsg = "⏰ 对局已超过 15 分钟，"
                        + Markdown.at(playerAId) + " " + Markdown.at(playerBId)
                        + " 自动判为平局！";
                String markdown = buildSettlementMarkdown(current, resultMsg);

                List<List<Button>> layout = new ArrayList<>();
                layout.add(List.of(new Button("play_again", "再来一局", "/四子棋",
                        true, ButtonStyle.BLUE, ButtonType.COMMAND)));
                Object keyboard = Keyboard.compose(layout);

                GroupChat.replyMessage(current.groupOpenId, playerAId, current.lastCmdMsgId,
                        TC.md(markdown), keyboard);
            } catch (Exception e) {
                log.warn("发送对局超时结算面板失败: ", e);
            }
            log.info("四子棋游戏在群 {} 因超过5分钟对局时长而自动平局", sessionId);
        }, GAME_TIMEOUT_MS);
    }

    private void recallOldMessage(GameState game) {
        if (game.lastMessageId != null) {
            String recordedMessageId = game.lastMessageId;
            String groupOpenId = game.groupOpenId;
            try {
                Atri.getInstance().getScheduler().runTaskLater(() ->
                        GroupChat.recallMessage(groupOpenId, recordedMessageId), RECALL_DELAY_MS);
            } catch (Exception e) {
                log.warn("撤回四子棋旧消息失败: ", e);
            }
        }
    }

    private static class GameState {
        Phase phase = Phase.WAITING;

        String groupOpenId;
        String label;

        String playerAOpenId;
        String playerBOpenId;

        int[][] board;
        int currentPlayer;

        int winner;
        List<int[]> winningLine;

        String lastMessageId;
        String lastCmdMsgId;

        int timeoutGeneration;

        long startTime = System.currentTimeMillis();
    }
}
