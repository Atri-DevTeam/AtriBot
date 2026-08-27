package top.yzljc.atribot.function.games;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialButtonInteractionEvent;
import top.yzljc.atribot.event.impl.AnswerCode;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.utils.tools.RandomGolds;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class RockPaperScissorsGame implements Listener, CommandExecutor {

    private static final Map<String, GameState> activeGames = new ConcurrentHashMap<>();

    // Timeouts (milliseconds)
    private static final long CHOICE_TIMEOUT_MS = 60_000;
    private static final long RECALL_DELAY_MS = 15_000;

    // Choice constants
    private static final String ROCK = "rock";
    private static final String SCISSORS = "scissors";
    private static final String PAPER = "paper";

    // Emoji display
    private static final String ROCK_EMOJI = "✊";
    private static final String SCISSORS_EMOJI = "✌️";
    private static final String PAPER_EMOJI = "🖐️";

    enum Phase {
        CHOOSING,
        FINISHED
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)) return true;
        if (qq.getPlatform() == Platform.OFFICIAL_C2C) {
            qq.sendMessage("单聊模式下暂不支持石头剪刀布喵！请在群聊中使用 /rsp 来开始游戏！");
            return true;
        }

        String sessionId = qq.getGroupId();

        if (activeGames.containsKey(sessionId)) {
            qq.sendMessage("当前群聊已经有一个正在进行的石头剪刀布游戏了喵！请等它结束后再开！");
            return true;
        }

        // 发指令的人就是玩家A
        GameState game = new GameState();
        game.groupOpenId = sessionId;
        game.playerAOpenId = qq.getUserId();
        game.phase = Phase.CHOOSING;
        activeGames.put(sessionId, game);

        String markdown = "**石头剪刀布**\n\n"
                + Markdown.at(game.playerAOpenId) + " 发起了挑战！\n\n"
                + "请点击下方按钮出拳：\n"
                + "> 双方选定后揭晓结果，60 秒内未完成自动取消";

        List<List<Button>> layout = buildChoiceButtons();
        Object keyboard = TC.keyboard(layout);

        String messageId = qq.sendMessage(TC.md(markdown), keyboard, false);
        game.lastCmdMsgId = qq.getMessage().getMessageId();
        game.lastMessageId = messageId;

        scheduleChoiceTimeout(sessionId, game);

        return true;
    }

    private List<List<Button>> buildChoiceButtons() {
        return List.of(
                List.of(new Button("rps_rock", ROCK_EMOJI + " 石头", "rps_choice", true, ButtonStyle.GRAY, ButtonType.CALLBACK),
                        new Button("rps_scissors", SCISSORS_EMOJI + " 剪刀", "rps_choice", true, ButtonStyle.GRAY, ButtonType.CALLBACK),
                        new Button("rps_paper", PAPER_EMOJI + " 布", "rps_choice", true, ButtonStyle.GRAY, ButtonType.CALLBACK))
        );
    }

    @EventHandler(ignoreCancelled = true)
    public void onChoiceCallback(OfficialButtonInteractionEvent event) {
        if (event.shouldIgnore()) return;
        if (!"rps_choice".equals(event.getButtonValue())) return;

        String sessionId = event.getGroupOpenId();
        GameState game = activeGames.get(sessionId);
        if (game == null || game.phase != Phase.CHOOSING) {
            event.answer(AnswerCode.FAIL);
            return;
        }

        String userId = event.getUserOpenId();
        boolean isPlayerA = userId.equals(game.playerAOpenId);

        // 玩家A不能选两次
        if (isPlayerA && game.playerAChoice != null) {
            event.answer(AnswerCode.REPEAT);
            return;
        }

        // 如果既不是玩家A也不是已确定的玩家B → 自动成为玩家B
        if (!isPlayerA && game.playerBOpenId == null) {
            // 不允许玩家A之外的同一个人用两个号
            game.playerBOpenId = userId;
        }

        boolean isPlayerB = userId.equals(game.playerBOpenId);

        // 不是参与者 → 拒绝
        if (!isPlayerA && !isPlayerB) {
            event.answer(AnswerCode.FAIL);
            return;
        }

        // 玩家B不能选两次
        if (isPlayerB && game.playerBChoice != null) {
            event.answer(AnswerCode.REPEAT);
            return;
        }

        // 记录选择
        String buttonId = event.getButtonId();
        String choice = buttonIdToChoice(buttonId);
        if (choice == null) {
            event.answer(AnswerCode.FAIL);
            return;
        }

        if (isPlayerA) {
            game.playerAChoice = choice;
        } else {
            game.playerBChoice = choice;
        }

        event.answer(AnswerCode.SUCCESS);

        // 双方都选了 → 结果
        if (game.playerAChoice != null && game.playerBChoice != null) {
            game.phase = Phase.FINISHED;
            activeGames.remove(sessionId);
            sendResult(game, event);
        } else {
            // 第一个人选了 → 只发 @谁选了
            sendWaitingForOther(game, event);
        }
    }

    private void sendWaitingForOther(GameState game, OfficialButtonInteractionEvent event) {
        String whoChose;
        String whoWaiting;

        if (game.playerAChoice != null) {
            // 玩家A选了，等玩家B（可能还没确定是谁）
            whoChose = Markdown.at(game.playerAOpenId);
            whoWaiting = game.playerBOpenId != null
                    ? Markdown.at(game.playerBOpenId)
                    : "另一位玩家";
        } else {
            // 玩家B先选了（玩家B先点了按钮），等玩家A
            whoChose = Markdown.at(game.playerBOpenId);
            whoWaiting = Markdown.at(game.playerAOpenId);
        }

        String markdown = "**石头剪刀布**\n\n"
                + whoChose + " 已出拳 ✅\n"
                + "等待 " + whoWaiting + " 出拳…\n\n"
                + "> 60 秒内未完成自动取消";

        List<List<Button>> layout = buildChoiceButtons();
        Object keyboard = TC.keyboard(layout);

        String messageId = event.replyMessage(TC.md(markdown), keyboard);
        recallOldMessage(game);
        game.lastMessageId = messageId;
    }

    private void sendResult(GameState game, OfficialButtonInteractionEvent event) {
        String choiceA = game.playerAChoice;
        String choiceB = game.playerBChoice;

        String emojiA = choiceToEmoji(choiceA);
        String emojiB = choiceToEmoji(choiceB);

        int winner = determineWinner(choiceA, choiceB);

        // 结果行
        String resultLine;
        int playerAGolds = 0;
        int playerBGolds = 0;
        if (winner == 0) {
            resultLine = "🤝 平局！";
            playerAGolds = LootRepository.addCoins(game.playerAOpenId, RandomGolds.get(3, 10));
            playerBGolds = LootRepository.addCoins(game.playerBOpenId, playerAGolds);
        } else if (winner == 1) {
            resultLine = "🎉 " + Markdown.at(game.playerAOpenId) + " 获胜！";
            playerAGolds = LootRepository.addCoins(game.playerAOpenId, RandomGolds.get(7, 15));
            playerBGolds = LootRepository.addCoins(game.playerBOpenId, RandomGolds.get(1, 6));
        } else {
            resultLine = "🎉 " + Markdown.at(game.playerBOpenId) + " 获胜！";
            playerAGolds = LootRepository.addCoins(game.playerAOpenId, RandomGolds.get(1, 6));
            playerBGolds = LootRepository.addCoins(game.playerBOpenId, RandomGolds.get(7, 15));
        }

        // VS 对决行
        String vsLine = Markdown.at(game.playerAOpenId) + "(+" + playerAGolds + "金粒)  " + emojiA
                + "  **VS**  " + emojiB + "(+" + playerBGolds + "金粒)  " + Markdown.at(game.playerBOpenId);

        String markdown = "**石头剪刀布 - 结算**\n\n"
                + vsLine + "\n\n"
                + resultLine + "\n\n"
                + Markdown.enterCommand("/rsp", "再来一局");

        String messageId = event.replyMessage(TC.md(markdown));
        recallOldMessage(game);
        game.lastMessageId = messageId;
    }

    /**
     * @return 0 = tie, 1 = playerA wins, 2 = playerB wins
     */
    private int determineWinner(String choiceA, String choiceB) {
        if (choiceA.equals(choiceB)) return 0;

        return switch (choiceA) {
            case ROCK -> choiceB.equals(SCISSORS) ? 1 : 2;
            case SCISSORS -> choiceB.equals(PAPER) ? 1 : 2;
            case PAPER -> choiceB.equals(ROCK) ? 1 : 2;
            default -> 0;
        };
    }

    private String buttonIdToChoice(String buttonId) {
        return switch (buttonId) {
            case "rps_rock" -> ROCK;
            case "rps_scissors" -> SCISSORS;
            case "rps_paper" -> PAPER;
            default -> null;
        };
    }

    private String choiceToEmoji(String choice) {
        return switch (choice) {
            case ROCK -> ROCK_EMOJI + "石头";
            case SCISSORS -> SCISSORS_EMOJI + "剪刀";
            case PAPER -> PAPER_EMOJI + "布";
            default -> choice;
        };
    }

    // ================================================================
    // Timeout
    // ================================================================

    private void scheduleChoiceTimeout(String sessionId, GameState game) {
        final int expectedGeneration = ++game.timeoutGeneration;

        Atri.getInstance().getScheduler().runTaskLater(() -> {
            GameState current = activeGames.get(sessionId);
            if (current != game) return;
            if (current.phase != Phase.CHOOSING) return;
            if (current.timeoutGeneration != expectedGeneration) return;

            // 超时 — 取消游戏
            current.phase = Phase.FINISHED;
            activeGames.remove(sessionId);

            String notifyId = current.playerBOpenId != null ? current.playerBOpenId : current.playerAOpenId;

            try {
                String markdown = "**石头剪刀布**\n\n"
                        + "⏰ 超时未完成，游戏已取消\n\n"
                        + Markdown.enterCommand("/rsp", "再来一局");

                GroupChat.replyMessage(current.groupOpenId, notifyId, current.lastCmdMsgId,
                        TC.md(markdown));
            } catch (Exception e) {
                log.warn("发送出拳超时取消面板失败: ", e);
            }
            log.info("石头剪刀布游戏在群 {} 因超时未完成而自动取消", sessionId);
        }, CHOICE_TIMEOUT_MS);
    }

    private void recallOldMessage(GameState game) {
        if (game.lastMessageId != null) {
            String recordedMessageId = game.lastMessageId;
            String groupOpenId = game.groupOpenId;
            try {
                Atri.getInstance().getScheduler().runTaskLater(() ->
                        GroupChat.recallMessage(groupOpenId, recordedMessageId), RECALL_DELAY_MS);
            } catch (Exception e) {
                log.warn("撤回石头剪刀布旧消息失败: ", e);
            }
        }
    }

    private static class GameState {
        Phase phase = Phase.CHOOSING;
        String groupOpenId;
        // 玩家A = 发指令的人
        String playerAOpenId;
        // 玩家B = 第二个点按钮的人（自动成为）
        String playerBOpenId;
        String playerAChoice;
        String playerBChoice;
        String lastMessageId;
        String lastCmdMsgId;
        int timeoutGeneration;
    }
}