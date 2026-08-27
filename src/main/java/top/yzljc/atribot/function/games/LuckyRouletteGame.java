package top.yzljc.atribot.function.games;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
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
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.utils.tools.RandomGolds;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class LuckyRouletteGame implements CommandExecutor {

    private static final Map<String, GameState> activeGames = new ConcurrentHashMap<>();

    // 轮盘共 6 格，其中 1 格为魔法格，每轮过后不重置
    private static final int SLOTS = 6;
    private static final int MIN_PLAYERS = 2;
    private static final int MAX_PLAYERS = 5;

    // 指定自己为目标的哨兵
    private static final int TARGET_SELF = -1;

    // Timeouts (milliseconds)
    private static final long JOIN_TIMEOUT_MS = 60_000;
    private static final long MOVE_TIMEOUT_MS = 60_000;
    private static final long RECALL_DELAY_MS = 15_000;

    enum Phase {
        WAITING,
        PLAYING,
        FINISHED
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)) return true;
        if (qq.getPlatform() == Platform.OFFICIAL_C2C) {
            qq.sendMessage("单聊模式下暂不支持幸运轮盘喵！请在群聊中使用 /幸运轮盘 来开始游戏！");
            return true;
        }

        String sessionId = qq.getGroupId();

        // No args → show welcome / rules screen
        if (args.length == 0) {
            if (activeGames.containsKey(sessionId)) {
                qq.sendMessage("当前群聊已经有一个正在进行的幸运轮盘了喵！请等它结束后再开！");
            } else {
                sendWelcomeScreen(sessionId, qq);
            }
            return true;
        }

        String action = args[0].toLowerCase();

        // "start" — valid even without an active game reference
        if (action.equals("start")) {
            handleStart(sessionId, qq, args);
            return true;
        }

        // All other commands require an active game
        GameState game = activeGames.get(sessionId);
        if (game == null) {
            qq.sendMessage("当前没有正在进行的幸运轮盘喵！请发送 /幸运轮盘 开始新游戏！");
            return true;
        }

        switch (action) {
            case "join" -> handleJoin(game, sessionId, qq);
            case "self" -> {
                synchronized (game) {
                    handleAction(game, sessionId, qq.getUserId(), false, TARGET_SELF,
                            qq.getMessage().getMessageId());
                }
            }
            case "target" -> {
                int target = parseTarget(game, args);
                if (target != -2) {
                    synchronized (game) {
                        handleAction(game, sessionId, qq.getUserId(), false, target,
                                qq.getMessage().getMessageId());
                    }
                }
            }
        }

        return true;
    }

    /**
     * 解析目标：self → TARGET_SELF；target 玩家字母 (A-E，不分大小写) → 0 基下标；无效返回 -2
     */
    private int parseTarget(GameState game, String[] args) {
        if (args.length < 2) return -2;
        String arg = args[1];
        if (arg.equalsIgnoreCase("self")) return TARGET_SELF;
        if (arg.matches("(?i)^[A-E]$")) {
            int idx = Character.toUpperCase(arg.charAt(0)) - 'A';
            if (idx < game.players.size()) return idx;
        }
        return -2;
    }

    /** 玩家字母编号：0 → A, 1 → B ... */
    private static String playerLabel(int idx) {
        return String.valueOf((char) ('A' + idx));
    }

    private void sendWelcomeScreen(String sessionId, QQCommandSender sender) {
        String markdown = """
                **幸运轮盘** 🎲

                **规则:**
                > 轮盘共 6 格，其中 1 格是魔法格，每次转动过后不重置
                > 轮到你时需要指定一个目标：自己 或 任意一名玩家
                > 指定自己：目标安全 → 轮到下一位；魔法命中 → 你被淘汰
                > 指定他人：目标安全 → 轮到下一位；魔法命中 → 目标被淘汰，其余人获胜
                > 每次选择限时 60 秒，超时将随机指定目标喵！

                **奖励:** 幸存玩家每人 7~15 金粒，被淘汰者 1~6 金粒安慰奖

                请点击下方按钮选择人数开局:
                > 1 分钟内未满员将自动取消
                """;

        List<Button> row = new ArrayList<>();
        for (int n = MIN_PLAYERS; n <= MAX_PLAYERS; n++) {
            row.add(new Button("btn_size_" + n, "🎲 " + n + "人局", "/幸运轮盘 start " + n,
                    true, ButtonStyle.GRAY, ButtonType.COMMAND));
        }

        Object keyboard = TC.keyboard(List.of(row));
        sender.sendMessage(TC.md(markdown), keyboard);
    }

    private void handleStart(String sessionId, QQCommandSender sender, String[] args) {
        if (activeGames.containsKey(sessionId)) {
            sender.sendMessage("当前群聊已经有一个正在进行的幸运轮盘了喵！请等它结束后再开！");
            return;
        }

        int size;
        try {
            size = Integer.parseInt(args[1]);
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            sender.sendMessage("请指定人数 (2-5)，如 /幸运轮盘 start 3");
            return;
        }
        if (size < MIN_PLAYERS || size > MAX_PLAYERS) {
            sender.sendMessage("人数必须是 2-5 喵！");
            return;
        }

        GameState game = new GameState();
        game.groupOpenId = sessionId;
        game.maxPlayers = size;
        // 开局者自动作为 A 号玩家
        game.players.add(sender.getUserId());
        activeGames.put(sessionId, game);

        sendWaitingUpdate(game, sender);
        scheduleJoinTimeout(sessionId, game);
    }

    private void handleJoin(GameState game, String sessionId, QQCommandSender sender) {
        String playerId = sender.getUserId();

        synchronized (game) {
            if (game.phase != Phase.WAITING) {
                sender.sendMessage("游戏已经开始，无法加入喵！");
                return;
            }
            if (game.players.contains(playerId)) {
                return; // Already joined — no-op
            }
            if (game.players.size() >= game.maxPlayers) {
                sender.sendMessage("本局人数已经满了喵！");
                return;
            }

            game.players.add(playerId);

            if (game.players.size() >= game.maxPlayers) {
                beginPlay(game, sessionId, sender.getMessage().getMessageId());
            } else {
                sendWaitingUpdate(game, sender);
            }
        }
    }

    private void beginPlay(GameState game, String sessionId, String cmdMsgId) {
        game.phase = Phase.PLAYING;
        game.magicIndex = new Random().nextInt(SLOTS);
        game.currentPlayer = 0;

        sendGameBoard(game, sessionId, cmdMsgId,
                "游戏开始！由 " + playerLabel(0) + " 号 " + Markdown.at(game.players.getFirst()) + " 先选择目标");
        scheduleMoveTimeout(sessionId, game);
    }

    /**
     * 转动轮盘结算。forced = true 时由超时任务执行，目标随机 (含选择者自己)；
     * targetIdx 为 TARGET_SELF 或 0 基玩家下标
     */
    private void handleAction(GameState game, String sessionId, String senderId,
                              boolean forced, int targetIdx, String cmdMsgId) {
        if (game.phase != Phase.PLAYING) {
            return;
        }

        String chooserId = game.players.get(game.currentPlayer);

        if (!forced) {
            // Non-participant → silent ignore
            if (!game.players.contains(senderId)) {
                return;
            }
            // Wrong turn
            if (!senderId.equals(chooserId)) {
                GroupChat.sendMessage(sessionId, "还没轮到你选择喵！看看现在轮到谁！");
                return;
            }
        }

        // Resolve target
        int targetIndex;
        if (forced) {
            // 超时随机指定目标，可能指向任何人 (含选择者自己)
            targetIndex = new Random().nextInt(game.players.size());
        } else if (targetIdx == TARGET_SELF) {
            targetIndex = game.currentPlayer;
        } else {
            targetIndex = targetIdx;
        }
        String targetId = game.players.get(targetIndex);
        boolean selfTarget = targetIndex == game.currentPlayer;

        String prefix = forced ? "⏰ " + Markdown.at(chooserId) + " 犹豫太久，命运随机指定了目标！" : null;

        // Consume the next slot
        int slot = game.slotIndex++;

        if (slot == game.magicIndex) {
            // 魔法命中 — target eliminate
            game.phase = Phase.FINISHED;
            game.loserOpenId = targetId;
            game.turnLog.add("💥命中(" + playerLabel(targetIndex) + ")");

            grantRewards(game);
            finishGame(game, sessionId, targetId, chooserId, selfTarget, prefix, cmdMsgId);
            return;
        }

        // Safe slot — 无论指定谁，安全后都轮到下一位
        game.turnLog.add("安全(" + (selfTarget ? "自" : playerLabel(targetIndex)) + ")");
        game.currentPlayer = (game.currentPlayer + 1) % game.players.size();
        String nextId = game.players.get(game.currentPlayer);

        String status;
        if (selfTarget) {
            status = (prefix != null ? prefix + " " : "")
                    + "轮盘转动… 目标安全！" + Markdown.at(chooserId) + " 逃过一劫，轮到 "
                    + Markdown.at(nextId) + " 选择了";
        } else {
            status = (prefix != null ? prefix + " " : "")
                    + "轮盘转动… 目标 " + Markdown.at(targetId) + " 安全！轮到 "
                    + Markdown.at(nextId) + " 选择了";
        }

        sendGameBoard(game, sessionId, cmdMsgId, status);
        scheduleMoveTimeout(sessionId, game);
    }

    private void sendWaitingUpdate(GameState game, QQCommandSender sender) {
        StringBuilder sb = new StringBuilder();
        sb.append("**幸运轮盘 (").append(game.maxPlayers).append("人局)** 🎲\n\n");
        sb.append("**规则:** 轮盘 6 格含 1 魔法格且不重置，指定目标施展魔法，")
                .append("目标安全则轮到下家，魔法命中者淘汰、其余人获胜喵！\n\n");
        sb.append("**当前状态 (").append(game.players.size()).append("/").append(game.maxPlayers).append("):**\n");

        for (int i = 0; i < game.maxPlayers; i++) {
            if (i < game.players.size()) {
                sb.append("> ").append(playerLabel(i)).append(". ").append(Markdown.at(game.players.get(i))).append(" ✓");
            } else {
                sb.append("> ").append(playerLabel(i)).append(". 等待加入");
            }
            sb.append("\n");
        }
        sb.append("\n> 游戏将在 1 分钟内未满员时自动取消");

        // 加入按钮：显示当前进度，未满员前任何群友都可点
        List<List<Button>> layout = new ArrayList<>();
        layout.add(List.of(new Button("btn_join", "加入本局 ("
                        + game.players.size() + "/" + game.maxPlayers + ")", "/幸运轮盘 join",
                true, ButtonStyle.BLUE, ButtonType.COMMAND)));

        Object keyboard = TC.keyboard(layout);

        String messageId = sender.sendMessage(TC.md(sb.toString()), keyboard);
        recallOldMessage(game);
        game.lastMessageId = messageId;
        game.lastCmdMsgId = sender.getMessage().getMessageId();
    }

    private void sendGameBoard(GameState game, String sessionId, String cmdMsgId, String statusMsg) {
        int remaining = SLOTS - game.slotIndex;
        String currentPlayerId = game.players.get(game.currentPlayer);

        StringBuilder sb = new StringBuilder();
        sb.append("**幸运轮盘** 🎲 | 剩余格数: ").append(remaining).append("/").append(SLOTS)
                .append(" | 当前命中概率: 1/").append(remaining).append("\n\n");
        if (statusMsg != null && !statusMsg.isEmpty()) {
            sb.append("> ").append(statusMsg).append("\n\n");
        }

        sb.append("**玩家:**\n");
        for (int i = 0; i < game.players.size(); i++) {
            String playerId = game.players.get(i);
            sb.append("> ").append(playerLabel(i)).append(". ").append(Markdown.at(playerId));
            if (i == game.currentPlayer) {
                sb.append(" ← 轮到选择");
            }
            sb.append("\n");
        }

        sb.append("\n**轮盘记录:** ");
        if (game.turnLog.isEmpty()) {
            sb.append("还没有人转动过轮盘...");
        } else {
            sb.append(String.join(" ", game.turnLog));
        }

        sb.append("\n\n> 指定目标·安全轮转 | 魔法命中·目标淘汰");

        // 目标按钮：人数几个就几个 (自己 + 其他玩家)，仅当前玩家可点
        List<Button> row = new ArrayList<>();
        row.add(new Button("btn_self", "指定自己", "/幸运轮盘 self",
                true, ButtonStyle.GRAY, ButtonType.COMMAND)
                .setPermissionType(PermissionType.SPECIFIC_USER)
                .setAllowedOpenIds(List.of(currentPlayerId)));
        for (int i = 0; i < game.players.size(); i++) {
            if (i == game.currentPlayer) continue;
            row.add(new Button("btn_target_" + playerLabel(i), "指定" + playerLabel(i),
                    "/幸运轮盘 target " + playerLabel(i),
                    true, ButtonStyle.BLUE, ButtonType.COMMAND)
                    .setPermissionType(PermissionType.SPECIFIC_USER)
                    .setAllowedOpenIds(List.of(currentPlayerId)));
        }

        List<List<Button>> layout = new ArrayList<>();
        layout.add(row);
        Object keyboard = TC.keyboard(layout);

        String messageId = GroupChat.replyMessage(sessionId, cmdMsgId, TC.md(sb.toString()), keyboard);
        recallOldMessage(game);
        game.lastMessageId = messageId;
        game.lastCmdMsgId = cmdMsgId;
    }

    private void finishGame(GameState game, String sessionId, String loserId, String chooserId,
                            boolean selfTarget, String prefix, String cmdMsgId) {
        activeGames.remove(sessionId);

        StringBuilder survivors = new StringBuilder();
        for (String uid : game.players) {
            if (uid.equals(loserId)) continue;
            survivors.append(Markdown.at(uid)).append("(+").append(game.goldRewards.getOrDefault(uid, 0)).append("金粒) ");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**幸运轮盘 - 结算** 🎲\n\n");
        sb.append("**结果:** ");
        if (prefix != null) {
            sb.append(prefix).append("\n>");
        }
        if (selfTarget) {
            sb.append("💥 魔法命中！").append(Markdown.at(loserId))
                    .append("(+").append(game.goldRewards.getOrDefault(loserId, 0)).append("金粒) 指定的目标是本人，被淘汰！\n\n");
        } else {
            sb.append("💥 魔法命中！").append(Markdown.at(chooserId)).append(" 指定的目标 ")
                    .append(Markdown.at(loserId))
                    .append("(+").append(game.goldRewards.getOrDefault(loserId, 0)).append("金粒) 被淘汰！\n\n");
        }
        sb.append("**幸存玩家:** ").append(survivors).append("\n");
        sb.append("**轮盘:** 第 ").append(game.slotIndex).append("/").append(SLOTS).append(" 次转动命中魔法\n");
        sb.append("**轮盘记录:** ").append(String.join(" ", game.turnLog));

        List<List<Button>> layout = new ArrayList<>();
        layout.add(List.of(new Button("play_again", "再来一局", "/幸运轮盘",
                true, ButtonStyle.BLUE, ButtonType.COMMAND)));
        Object keyboard = TC.keyboard(layout);

        String messageId = GroupChat.replyMessage(sessionId, cmdMsgId, TC.md(sb.toString()), keyboard);
        recallOldMessage(game);
        game.lastMessageId = messageId;
    }

    /**
     * 结算金粒奖励（每局只发放一次）：
     * 幸存玩家每人 7~15 金粒，被淘汰者 1~6 金粒安慰奖
     */
    private Map<String, Integer> grantRewards(GameState game) {
        if (game.rewardsGranted) return game.goldRewards;
        game.rewardsGranted = true;

        for (String uid : game.players) {
            int golds = uid.equals(game.loserOpenId) ? RandomGolds.get(1, 6) : RandomGolds.get(7, 15);
            game.goldRewards.put(uid, golds);
            LootRepository.addCoins(uid, golds);
        }
        return game.goldRewards;
    }

    private void scheduleJoinTimeout(String sessionId, GameState game) {
        Atri.getInstance().getScheduler().runTaskLater(() -> {
            GameState current = activeGames.get(sessionId);
            if (current != game) return;
            if (current.phase != Phase.WAITING) return;

            String targetOpenId = current.players.isEmpty() ? null : current.players.getFirst();
            activeGames.remove(sessionId);

            if (targetOpenId != null && current.lastCmdMsgId != null) {
                try {
                    GroupChat.replyMessage(current.groupOpenId, targetOpenId, current.lastCmdMsgId,
                            TC.md("⏰ 幸运轮盘因 1 分钟内未满员而自动取消 " + Markdown.at(targetOpenId)));
                } catch (Exception e) {
                    log.warn("发送加入超时通知失败: ", e);
                }
            }
            log.info("幸运轮盘在群 {} 因超时未满员而自动取消", sessionId);
        }, JOIN_TIMEOUT_MS);
    }

    private void scheduleMoveTimeout(String sessionId, GameState game) {
        final int expectedGeneration = ++game.timeoutGeneration;

        Atri.getInstance().getScheduler().runTaskLater(() -> {
            GameState current = activeGames.get(sessionId);
            if (current != game) return;
            if (current.phase != Phase.PLAYING) return;
            if (current.timeoutGeneration != expectedGeneration) return;

            // 超时 — 命运随机指定目标 (forced 分支会随机选取)
            synchronized (current) {
                if (current.phase != Phase.PLAYING) return;
                if (current.timeoutGeneration != expectedGeneration) return;
                handleAction(current, sessionId, null, true, TARGET_SELF, current.lastCmdMsgId);
            }
        }, MOVE_TIMEOUT_MS);
    }

    private void recallOldMessage(GameState game) {
        if (game.lastMessageId != null) {
            String recordedMessageId = game.lastMessageId;
            String groupOpenId = game.groupOpenId;
            try {
                Atri.getInstance().getScheduler().runTaskLater(() ->
                        GroupChat.recallMessage(groupOpenId, recordedMessageId), RECALL_DELAY_MS);
            } catch (Exception e) {
                log.warn("撤回幸运轮盘旧消息失败: ", e);
            }
        }
    }

    private static class GameState {
        Phase phase = Phase.WAITING;

        String groupOpenId;

        int maxPlayers;
        final List<String> players = new ArrayList<>();

        int magicIndex;
        int slotIndex;
        int currentPlayer;
        final List<String> turnLog = new ArrayList<>();

        String loserOpenId;
        final Map<String, Integer> goldRewards = new LinkedHashMap<>();
        boolean rewardsGranted;

        String lastMessageId;
        String lastCmdMsgId;

        int timeoutGeneration;
    }
}
