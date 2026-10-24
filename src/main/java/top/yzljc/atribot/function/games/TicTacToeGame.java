package top.yzljc.atribot.function.games;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.function.ToIntBiFunction;
import java.util.function.BiConsumer;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.database.repo.UserGameDataRepository;
import top.yzljc.atribot.database.repo.UserGameDataRepository.Counters;
import top.yzljc.atricore.chat.official.GroupChat;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.button.Button;
import top.yzljc.atricore.chat.official.button.ButtonStyle;
import top.yzljc.atricore.chat.official.button.ButtonType;
import top.yzljc.atricore.chat.official.button.Keyboard;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atricore.service.Scheduler;

/**
 * @Author YZ_Ljc_
 * @ClassName TicTacToeGame
 * @Created_at 2026/10/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.games
 */
public class TicTacToeGame implements CommandExecutor {
    private static final String CROSS = "❌";
    private static final String CIRCLE = "⭕";
    private static final long TIMEOUT_MS = 60_000;
    private static final int[][] WINNING_LINES = {
            {0, 1, 2}, {3, 4, 5}, {6, 7, 8},
            {0, 3, 6}, {1, 4, 7}, {2, 5, 8}, {0, 4, 8}, {2, 4, 6}
    };
    private final Map<String, GameState> activeGames = new ConcurrentHashMap<>();
    private final Scheduler scheduler;
    private final ToIntBiFunction<String, Integer> reward;
    private final BiConsumer<String, List<Counters>> statistics;

    private enum Phase {WAITING, PLAYING, FINISHED}
    private enum Outcome {NORMAL, FORFEIT, TIMED_OUT}

    public TicTacToeGame() {
        this(Atri.getInstance().getScheduler(), LootRepository::addCoins);
    }

    TicTacToeGame(Scheduler scheduler, ToIntBiFunction<String, Integer> reward) {
        this(scheduler, reward, (eventId, counters) ->
                UserGameDataRepository.recordCounters(UserGameDataRepository.Game.tictactoe, eventId, counters));
    }

    TicTacToeGame(Scheduler scheduler, ToIntBiFunction<String, Integer> reward, BiConsumer<String, List<Counters>> statistics) {
        this.scheduler = scheduler;
        this.reward = reward;
        this.statistics = statistics;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)) return true;
        if (qq.getPlatform() != Platform.OFFICIAL_GROUP) {
            qq.sendMessage("请在群聊中使用 /井字棋，邀请另一位玩家对战");
            return true;
        }
        if (qq.isBot()) return true;
        String groupId = qq.getGroupId();
        if (args.length == 0) {
            GameState created = new GameState(groupId, qq);
            GameState existing = activeGames.putIfAbsent(groupId, created);
            GameState game = existing == null ? created : existing;
            synchronized (game) {
                if (existing == null) scheduleTimeout(game);
                sendBoard(game, qq);
            }
            return true;
        }
        GameState game = activeGames.get(groupId);
        if (game == null) {
            qq.sendMessage("当前没有井字棋对局，请发送 /井字棋 开始");
            return true;
        }
        synchronized (game) {
            if (activeGames.get(groupId) != game || args.length < 2 || !game.id.equals(args[1])) {
                qq.sendMessage("这个棋盘已失效，请发送 /井字棋 查看当前对局");
                return true;
            }
            switch (args[0]) {
                case "join" -> join(game, qq);
                case "move" -> move(game, qq, args);
                case "resign" -> resign(game, qq);
                default -> qq.sendMessage("请使用棋盘按钮落子，或发送 /井字棋 查看当前对局");
            }
        }
        return true;
    }

    private void join(GameState game, QQCommandSender sender) {
        if (game.crossPlayer.equals(sender.getUserId())) {
            sender.sendMessage("你已执 ❌，请等待另一位玩家加入");
            return;
        }
        if (game.phase != Phase.WAITING) {
            sender.sendMessage("本局已有两位玩家，请等待对局结束");
            return;
        }
        game.circlePlayer = sender.getUserId();
        game.phase = Phase.PLAYING;
        game.replyTo = sender;
        scheduleTimeout(game);
        sendBoard(game, sender);
    }

    private void move(GameState game, QQCommandSender sender, String[] args) {
        if (game.phase != Phase.PLAYING) {
            sender.sendMessage("请先等待另一位玩家加入");
            return;
        }
        if (!game.currentPlayer().equals(sender.getUserId())) {
            sender.sendMessage("还没轮到你落子");
            return;
        }
        if (args.length != 4 || !Integer.toString(game.moves).equals(args[2]) || !args[3].matches("[1-9]")) {
            sender.sendMessage("请在最新棋盘上选择位置");
            return;
        }
        int cell = Integer.parseInt(args[3]) - 1;
        if (game.board[cell] != 0) {
            sender.sendMessage("当前位置已有棋子");
            return;
        }
        int piece = game.moves % 2 == 0 ? 1 : 2;
        game.board[cell] = piece;
        game.moves++;
        game.replyTo = sender;
        if (hasWon(game.board, piece)) {
            finish(game, "获胜：" + (piece == 1 ? CROSS : CIRCLE) + " " + Markdown.at(sender.getUserId()), sender.getUserId());
        } else if (game.moves == 9) {
            finish(game, "棋盘已满，本局平局");
        } else {
            scheduleTimeout(game);
        }
        sendBoard(game, sender);
    }

    private void resign(GameState game, QQCommandSender sender) {
        String userId = sender.getUserId();
        if (!game.crossPlayer.equals(userId) && !userId.equals(game.circlePlayer)) {
            sender.sendMessage("只有本局玩家可以取消对局或放弃");
            return;
        }
        if (game.phase == Phase.WAITING) finish(game, "发起者已取消对局");
        else {
            String winner = game.crossPlayer.equals(userId) ? game.circlePlayer : game.crossPlayer;
            finish(game, Markdown.at(userId) + " 已放弃，获胜：" + Markdown.at(winner), winner, Outcome.FORFEIT);
        }
        sendBoard(game, sender);
    }

    private void finish(GameState game, String result) {
        finish(game, result, null);
    }

    private void finish(GameState game, String result, String winner) {
        finish(game, result, winner, Outcome.NORMAL);
    }

    private void finish(GameState game, String result, String winner, Outcome outcome) {
        if (game.phase == Phase.FINISHED) return;
        game.phase = Phase.FINISHED;
        game.result = result;
        activeGames.remove(game.groupId, game);
        if (game.timeout != null) game.timeout.cancel(false);
        if (game.circlePlayer != null) {
            statistics.accept(game.id, List.of(
                    matchCounters(game, game.crossPlayer, 1, winner, outcome),
                    matchCounters(game, game.circlePlayer, 2, winner, outcome)));
        }
        if (winner != null) {
            String loser = game.crossPlayer.equals(winner) ? game.circlePlayer : game.crossPlayer;
            int winnerGolds = reward.applyAsInt(winner, 15);
            int loserGolds = reward.applyAsInt(loser, 8);
            game.result += "\n\n> " + Markdown.at(winner) + rewardText(winnerGolds)
                    + "\n> " + Markdown.at(loser) + rewardText(loserGolds);
        }
    }

    private Counters matchCounters(GameState game, String userId, int piece, String winner, Outcome outcome) {
        boolean won = userId.equals(winner);
        boolean lost = winner != null && !won;
        return new Counters(userId, Map.of(
                "plays", 1L, "wins", won ? 1L : 0L, "losses", lost ? 1L : 0L, "draws", winner == null ? 1L : 0L,
                "moves", Arrays.stream(game.board).filter(value -> value == piece).count(),
                "normalWins", won && outcome == Outcome.NORMAL ? 1L : 0L,
                "forfeitWins", won && outcome == Outcome.FORFEIT ? 1L : 0L,
                "timeoutWins", won && outcome == Outcome.TIMED_OUT ? 1L : 0L,
                "forfeits", lost && outcome == Outcome.FORFEIT ? 1L : 0L,
                "moveTimeouts", lost && outcome == Outcome.TIMED_OUT ? 1L : 0L));
    }

    private String rewardText(int golds) {
        return golds < 0 ? " 金粒发放失败" : " +" + golds + " 金粒";
    }

    private void scheduleTimeout(GameState game) {
        if (game.timeout != null) game.timeout.cancel(false);
        Phase phase = game.phase;
        int moves = game.moves;
        game.timeout = scheduler.runTaskLater(() -> {
            synchronized (game) {
                if (activeGames.get(game.groupId) != game || game.phase != phase || game.moves != moves) return;
                if (phase == Phase.WAITING) finish(game, "等待加入超时，本局已取消");
                else {
                    String winner = game.moves % 2 == 0 ? game.circlePlayer : game.crossPlayer;
                    finish(game, Markdown.at(game.currentPlayer()) + " 落子超时，获胜：" + Markdown.at(winner), winner, Outcome.TIMED_OUT);
                }
                sendBoard(game, game.replyTo);
            }
        }, TIMEOUT_MS);
    }

    private boolean hasWon(int[] board, int piece) {
        for (int[] line : WINNING_LINES) {
            if (board[line[0]] == piece && board[line[1]] == piece && board[line[2]] == piece) return true;
        }
        return false;
    }

    private void sendBoard(GameState game, QQCommandSender sender) {
        StringBuilder text = new StringBuilder("**井字棋**\n\n");
        text.append("> ").append(CROSS).append(' ').append(Markdown.at(game.crossPlayer)).append('\n');
        text.append("> ").append(CIRCLE).append(' ').append(game.circlePlayer == null ? "等待加入" : Markdown.at(game.circlePlayer)).append("\n\n");
        if (game.phase == Phase.WAITING) {
            text.append("横、竖或斜线连成三子获胜\n\n")
                    .append(Markdown.enterCommand("/井字棋 join " + game.id, "⭕ 加入对局"))
                    .append("\n\n> 60 秒内无人加入则取消对局\n\n")
                    .append(Markdown.enterCommand("/井字棋 resign " + game.id, "取消对局"));
        } else if (game.phase == Phase.PLAYING) {
            text.append("**轮到：** ").append(game.moves % 2 == 0 ? CROSS : CIRCLE).append(' ')
                    .append(Markdown.at(game.currentPlayer())).append("\n\n> 点击下方空位落子，每步限时 60 秒\n\n")
                    .append(Markdown.enterCommand("/井字棋 resign " + game.id, "放弃"));
        } else {
            text.append("**结果：** ").append(game.result).append("\n\n")
                    .append(Markdown.enterCommand("/井字棋", "再来一局"));
        }
        Markdown markdown = TC.md(text.toString()).setKeyboard(buildKeyboard(game));
        String messageId = sender.sendMessage(markdown, false);
        if (messageId != null) {
            String previous = game.lastMessageId;
            if (previous != null) scheduler.runTaskLater(() -> GroupChat.recallMessage(game.groupId, previous), 15_000);
            game.lastMessageId = messageId;
        }
    }

    private Keyboard buildKeyboard(GameState game) {
        List<List<Button>> rows = new ArrayList<>();
        for (int row = 0; row < 3; row++) {
            List<Button> buttons = new ArrayList<>();
            for (int col = 0; col < 3; col++) {
                int cell = row * 3 + col;
                int piece = game.board[cell];
                boolean playable = piece == 0 && game.phase == Phase.PLAYING;
                String text = piece == 1 ? CROSS : piece == 2 ? CIRCLE : "⬜";
                ButtonStyle style = piece == 1 ? ButtonStyle.RED : piece == 2 ? ButtonStyle.BLUE : ButtonStyle.GRAY;
                Button button = new Button("ttt_" + game.id + "_" + cell, text,
                        playable ? "/井字棋 move " + game.id + " " + game.moves + " " + (cell + 1) : "",
                        style, playable ? ButtonType.COMMAND : ButtonType.CUSTOM);
                if (playable) button.setAllowedOpenIds(List.of(game.currentPlayer()));
                else button.setUnsupportedTip(piece != 0 ? "当前位置已有棋子"
                        : game.phase == Phase.WAITING ? "请等待另一位玩家加入" : "本局游戏已结束");
                buttons.add(button);
            }
            rows.add(buttons);
        }
        return new Keyboard(rows);
    }

    private static final class GameState {
        final String id = UUID.randomUUID().toString().replace("-", "");
        final String groupId;
        final String crossPlayer;
        final int[] board = new int[9];
        String circlePlayer;
        Phase phase = Phase.WAITING;
        int moves;
        String result;
        QQCommandSender replyTo;
        String lastMessageId;
        ScheduledFuture<?> timeout;

        GameState(String groupId, QQCommandSender sender) {
            this.groupId = groupId;
            this.crossPlayer = sender.getUserId();
            this.replyTo = sender;
        }

        String currentPlayer() {
            return moves % 2 == 0 ? crossPlayer : circlePlayer;
        }
    }
}
