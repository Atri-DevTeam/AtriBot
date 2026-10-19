package top.yzljc.atribot.function.games;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.button.Button;
import top.yzljc.atricore.chat.official.button.ButtonType;
import top.yzljc.atricore.chat.official.button.PermissionType;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atricore.service.Scheduler;

import static org.junit.jupiter.api.Assertions.*;

/**
 * @Author YZ_Ljc_
 * @ClassName TicTacToeGameTest
 * @Created_at 2026/10/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.games
 */
class TicTacToeGameTest {
    private final TestScheduler scheduler = new TestScheduler();
    private final List<Reward> rewards = new ArrayList<>();
    private final TicTacToeGame game = new TicTacToeGame(scheduler, (userId, amount) -> {
        rewards.add(new Reward(userId, amount));
        return amount;
    });
    private final List<Object> replies = new ArrayList<>();
    private final QQCommandSender cross = sender("cross");
    private final QQCommandSender circle = sender("circle");
    private final QQCommandSender spectator = sender("spectator");

    @Test void boardHasThreeRowsAndThreeColumnsAndOccupiedCellsUseCustomTips() {
        start();
        assertEquals(3, board().getKeyboard().getButtons().size());
        board().getKeyboard().getButtons().forEach(row -> assertEquals(3, row.size()));
        assertEquals(ButtonType.COMMAND, cell(1).getActionType());
        assertEquals(PermissionType.SPECIFIC_USER, cell(1).getPermissionType());
        assertEquals(List.of("cross"), cell(1).getAllowedOpenIds());
        click(cross, 1);
        assertEquals("❌", cell(1).getDisplayText());
        assertEquals(ButtonType.CUSTOM, cell(1).getActionType());
        assertEquals("当前位置已有棋子", cell(1).getUnsupportedTip());
        assertEquals(List.of("circle"), cell(2).getAllowedOpenIds());
        click(circle, 2);
        assertEquals("⭕", cell(2).getDisplayText());
        assertEquals(ButtonType.CUSTOM, cell(2).getActionType());
        assertEquals("当前位置已有棋子", cell(2).getUnsupportedTip());
    }

    @Test void rejectsSelfJoinSpectatorsWrongTurnsRepeatedCellsAndOldBoards() {
        command(cross);
        String id = id();
        command(cross, "join", id);
        assertTrue(replies.getLast().toString().contains("另一位玩家"));
        command(circle, "join", id);
        String oldMove = cell(2).getData();
        click(spectator, 1);
        assertEquals("⬜", cell(1).getDisplayText());
        click(circle, 1);
        assertEquals("⬜", cell(1).getDisplayText());
        click(cross, 1);
        command(circle, "move", id, "1", "1");
        assertEquals("当前位置已有棋子", replies.getLast());
        click(circle, 3);
        command(cross, oldMove.substring("/井字棋 ".length()).split(" "));
        assertEquals("请在最新棋盘上选择位置", replies.getLast());
        assertEquals("⬜", cell(2).getDisplayText());
        assertEquals(List.of("cross"), cell(2).getAllowedOpenIds());
    }

    @Test void recognizesAllEightWinningLinesAndLocksTheFinalBoard() {
        int[][] moves = {
                {1, 4, 2, 5, 3}, {4, 1, 5, 2, 6}, {7, 1, 8, 2, 9},
                {1, 2, 4, 3, 7}, {2, 1, 5, 3, 8}, {3, 1, 6, 2, 9},
                {1, 2, 5, 3, 9}, {3, 1, 5, 2, 7}
        };
        for (int[] sequence : moves) {
            start();
            for (int i = 0; i < sequence.length; i++) click(i % 2 == 0 ? cross : circle, sequence[i]);
            assertTrue(board().getText().contains("获胜：❌ <@cross>"));
            board().getKeyboard().getButtons().stream().flatMap(List::stream)
                    .forEach(button -> assertEquals(ButtonType.CUSTOM, button.getActionType()));
        }
    }

    @Test void circleCanWinAndAFullBoardCanDraw() {
        start();
        int[] win = {1, 4, 2, 5, 9, 6};
        for (int i = 0; i < win.length; i++) click(i % 2 == 0 ? cross : circle, win[i]);
        assertTrue(board().getText().contains("获胜：⭕ <@circle>"));
        start();
        int[] draw = {1, 2, 3, 5, 4, 6, 8, 7, 9};
        for (int i = 0; i < draw.length; i++) click(i % 2 == 0 ? cross : circle, draw[i]);
        assertTrue(board().getText().contains("本局平局"));
    }

    @Test void oldCommandsAndTimeoutsCannotAffectTheNextGame() {
        command(cross);
        String previousId = id();
        Runnable oldTimeout = scheduler.tasks.getLast();
        command(spectator, "resign", previousId);
        assertTrue(replies.getLast().toString().contains("只有本局玩家"));
        command(cross, "resign", previousId);
        command(cross);
        String nextId = id();
        assertNotEquals(previousId, nextId);
        int responses = replies.size();
        oldTimeout.run();
        assertEquals(responses, replies.size());
        command(circle, "join", previousId);
        assertTrue(replies.getLast().toString().contains("棋盘已失效"));
        command(circle, "join", nextId);
        assertEquals(ButtonType.COMMAND, cell(1).getActionType());
    }

    @Test void onlyTheCurrentTurnTimeoutCanFinishTheGame() {
        command(cross);
        Runnable waiting = scheduler.tasks.getLast();
        command(circle, "join", id());
        Runnable firstTurn = scheduler.tasks.getLast();
        click(cross, 1);
        Runnable currentTurn = scheduler.tasks.getLast();
        int responses = replies.size();
        waiting.run();
        firstTurn.run();
        assertEquals(responses, replies.size());
        currentTurn.run();
        assertTrue(board().getText().contains("<@circle> 落子超时，获胜：<@cross>"));
        command(cross);
        scheduler.tasks.getLast().run();
        assertTrue(board().getText().contains("等待加入超时"));
    }

    private void start() {
        command(cross);
        command(circle, "join", id());
    }

    @Test void winningRewardsAreGrantedOnceAndShownInTheSettlement() {
        start();
        int[] moves = {1, 4, 2, 5};
        for (int i = 0; i < moves.length; i++) click(i % 2 == 0 ? cross : circle, moves[i]);
        Runnable timeout = scheduler.tasks.getLast();
        String winningMove = cell(3).getData();
        click(cross, 3);
        assertEquals(List.of(new Reward("cross", 15), new Reward("circle", 8)), rewards);
        assertTrue(board().getText().contains("<@cross> +15 金粒"));
        assertTrue(board().getText().contains("<@circle> +8 金粒"));
        command(cross, winningMove.substring("/井字棋 ".length()).split(" "));
        timeout.run();
        assertEquals(2, rewards.size());
    }

    @Test void resignationAndTimeoutRewardTheWinnerAndLoser() {
        start();
        String gameId = cell(1).getData().split(" ")[2];
        command(cross, "resign", gameId);
        assertEquals(List.of(new Reward("circle", 15), new Reward("cross", 8)), rewards);
        rewards.clear();
        start();
        click(cross, 1);
        Runnable timeout = scheduler.tasks.getLast();
        timeout.run();
        timeout.run();
        assertEquals(List.of(new Reward("cross", 15), new Reward("circle", 8)), rewards);
    }

    @Test void drawsAndCancelledGamesDoNotGrantRewards() {
        command(cross);
        command(cross, "resign", id());
        command(cross);
        scheduler.tasks.getLast().run();
        start();
        int[] draw = {1, 2, 3, 5, 4, 6, 8, 7, 9};
        for (int i = 0; i < draw.length; i++) click(i % 2 == 0 ? cross : circle, draw[i]);
        assertTrue(rewards.isEmpty());
    }

    private record Reward(String userId, int amount) {}

    private void command(QQCommandSender sender, String... args) {
        game.onCommand(sender, null, "井字棋", args);
    }

    private void click(QQCommandSender sender, int position) {
        command(sender, cell(position).getData().substring("/井字棋 ".length()).split(" "));
    }

    private String id() {
        var matcher = Pattern.compile("/井字棋 join ([a-f0-9]{32})").matcher(board().getText());
        assertTrue(matcher.find());
        return matcher.group(1);
    }

    private Markdown board() {
        return replies.stream().filter(Markdown.class::isInstance).map(Markdown.class::cast).reduce((a, b) -> b).orElseThrow();
    }

    private Button cell(int position) {
        return board().getKeyboard().getButtons().get((position - 1) / 3).get((position - 1) % 3);
    }

    private QQCommandSender sender(String userId) {
        return (QQCommandSender) Proxy.newProxyInstance(QQCommandSender.class.getClassLoader(), new Class<?>[]{QQCommandSender.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "getPlatform" -> Platform.OFFICIAL_GROUP;
                    case "getGroupId" -> "test-group";
                    case "getUserId", "getUsername" -> userId;
                    case "isBot", "hasPermission" -> false;
                    case "sendMessage" -> {
                        if (args[0] instanceof Markdown) assertEquals(false, args[1]);
                        replies.add(args[0]);
                        yield null;
                    }
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    private static final class TestScheduler extends Scheduler {
        final List<Runnable> tasks = new ArrayList<>();

        @Override public ScheduledFuture<?> runTaskLater(Runnable task, long delayMillis) {
            tasks.add(task);
            return null;
        }
    }
}
