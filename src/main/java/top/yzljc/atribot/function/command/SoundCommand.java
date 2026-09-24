package top.yzljc.atribot.function.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.QQMessageSendException;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.chat.official.RT;
import top.yzljc.atribot.chat.official.button.Keyboard;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.database.repo.UserGameDataRepository;
import top.yzljc.atribot.database.repo.CoinGainLogRepository;
import top.yzljc.atribot.function.games.sound.SoundCatalog;
import top.yzljc.atribot.function.games.sound.SoundRound;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.ToIntBiFunction;

/**
 * @Author YZ_Ljc_
 * @ClassName SoundCommand
 * @Created_at 2026/09/17
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
public final class SoundCommand implements CommandExecutor, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(SoundCommand.class);
    private static final int CORRECT_REWARD = 15;
    private final Map<String, SoundRound> rounds = new ConcurrentHashMap<>();
    private volatile boolean closed;
    private SoundCatalog catalog;
    private String catalogKey;
    private long catalogExpires;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)) {
            return true;
        }
        if (qq.getPlatform() != Platform.OFFICIAL_GROUP && qq.getPlatform() != Platform.OFFICIAL_C2C) {
            qq.sendMessage("请在官机群聊或私聊中使用 /sound。");
            return true;
        }
        ThreadManager.execute(() -> {
            try {
                handle(qq, args);
            } catch (Exception e) {
                log.warn("听声辨物指令失败", e);
                if (e instanceof InterruptedException) Thread.currentThread().interrupt();
                qq.sendMessage(TC.md("听声辨物资源或消息暂时不可用，请稍后再试。"));
            }
        });
        return true;
    }

    private void handle(QQCommandSender qq, String[] args) throws Exception {
        if (closed) return;
        if (args.length == 1 && "help".equalsIgnoreCase(args[0])) {
            qq.sendMessage(TC.md("**Minecraft 听声辨物**\n\n听声音，判断该声音出自哪种场景"
                    + "。首个作答无论对错都会立即结束并公布答案，最多等待 2 分钟。\n\n"
                    + "使用 /sound 随机开始一道题。"));
            return;
        }
        if (args.length > 0 && ("answer".equalsIgnoreCase(args[0])
                || (args.length == 1 && args[0].matches("(?i)[a-d]")))) {
            answer(qq, args);
            return;
        }
        if (args.length > 0) {
            qq.sendMessage(TC.md("使用 /sound 随机开始听声辨物，或使用 /sound help 查看玩法。"));
            return;
        }
        String session = sessionKey(qq);
        if (rounds.containsKey(session)) {
            qq.sendMessage(TC.md("当前会话已有一道题，请在原题下点击答案按钮，等待揭晓后再开题。"));
            return;
        }
        SoundCatalog current = catalog();
        SoundCatalog.Question question = current.question(0, ThreadLocalRandom.current());
        SoundRound round = new SoundRound(question, current.audioUrl(question));
        synchronized (rounds) {
            if (closed || rounds.size() >= 1000) return;
            if (rounds.putIfAbsent(session, round) != null) return;
        }
        String stage = "题面发送";
        try {
            log.info("听声辨物开题: round={}, session={}, sourceMsg={}, answer={}, event={}, playback={}, sha1={}, options={}",
                    round.id, session, qq.getMessage().getMessageId(), (char) ('A' + question.answer()),
                    question.options().get(question.answer()).id(), question.audio().path(), question.audio().sha1(),
                    question.options().stream().map(option -> option.id() + "=" + option.name()).toList());
            int seconds = Math.clamp(Config.getInstance().getSoundAnswerSeconds(), 15, 120);

            String cardId = qq.sendMessage(TC.md(questionText(round)).setKeyboard(answerKeyboard(round, commandPrefix()), OfficialUsers.isUserUnsupportedKeyboard(qq.getUserId())), false);

            if (cardId == null || cardId.isBlank()) throw new IllegalStateException("Question card send failed");
            log.info("听声辨物题面已发送: round={}, session={}, cardMsg={}, audioUrl={}",
                    round.id, session, cardId, round.audioUrl);
            stage = "音频上传或发送";
            String audioId = qq.getPlatform() == Platform.OFFICIAL_C2C
                    ? C2CChat.replyAudioMessage(qq.getUserId(), RT.message(qq.getMessage().getMessageId()), round.audioUrl)
                    : GroupChat.replyAudioMessage(qq.getGroupId(), RT.message(qq.getMessage().getMessageId()), round.audioUrl);
            if (audioId == null) throw new IllegalStateException("Audio upload/send failed");
            log.info("听声辨物音频已发送: round={}, session={}, cardMsg={}, audioMsg={}",
                    round.id, session, cardId, audioId);
            round.start(System.currentTimeMillis(), seconds * 1000L);
            ThreadManager.schedule(() -> expire(qq, round), seconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            logFailure(round, stage, e);
            round.cancel();
            rounds.remove(session, round);
            throw e;
        }
    }

    private synchronized SoundCatalog catalog() throws Exception {
        Config config = Config.getInstance();
        String prefix = config.getSoundResourceBaseUrl().trim();
        if (prefix.isBlank()) throw new IllegalStateException("Configure sound.resource-base-url first");
        String key = prefix + "\n" + config.getSoundIndexPath();
        if (catalog == null || !key.equals(catalogKey) || System.currentTimeMillis() >= catalogExpires) {
            catalog = SoundCatalog.download(prefix, config.getSoundIndexPath());
            catalogKey = key;
            catalogExpires = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10);
        }
        return catalog;
    }

    public record AnswerInput(String token, int choice) {
    }

    public static AnswerInput parseAnswer(String[] args) {
        String token = null;
        String choice;
        if (args.length == 1) {
            choice = args[0];
        } else if ((args.length == 2 || args.length == 3) && "answer".equalsIgnoreCase(args[0])) {
            choice = args[args.length - 1];
            if (args.length == 3) token = args[1];
        } else {
            return null;
        }
        if (!choice.matches("(?i)[a-d]")) return null;
        return new AnswerInput(token, choice.toUpperCase(Locale.ROOT).charAt(0) - 'A');
    }

    private void answer(QQCommandSender qq, String[] args) {
        AnswerInput input = parseAnswer(args);
        if (input == null) {
            qq.sendMessage("请点击答案按钮，或使用 /sound answer A（可选 A、B、C、D）");
            return;
        }
        SoundRound round = rounds.get(sessionKey(qq));
        if (round == null) {
            qq.sendMessage(TC.md("当前会话没有正在进行的题目，使用 " + Markdown.enterCommand("/sound", "/sound") + "开始"));
            return;
        }
        SoundRound.Answer result = round.answer(input.token() == null ? round.id : input.token(),
                qq.getUserId(), input.choice(), System.currentTimeMillis());
        switch (result.status()) {
            case CORRECT, WRONG -> {
                UserGameDataRepository.record(UserGameDataRepository.Game.sound, round.id,
                        List.of(UserGameDataRepository.Delta.operation(qq.getUserId(), result.status() == SoundRound.Vote.CORRECT)));
                reveal(qq, round, result.result());
            }
            case EXPIRED -> qq.sendMessage(TC.md("这道题尚未开始或已经结束，请使用当前题目的按钮作答。"));
        }
    }

    private void expire(QQCommandSender qq, SoundRound round) {
        if (closed || rounds.get(sessionKey(qq)) != round) return;
        SoundRound.Result result = round.expire(System.currentTimeMillis());
        if (result != null) reveal(qq, round, result);
    }

    private void reveal(QQCommandSender qq, SoundRound round, SoundRound.Result result) {
        try {
            if (closed) return;
            if (CommandDisableService.isDisabled("sound",
                    qq.getPlatform() == Platform.OFFICIAL_GROUP ? qq.getGroupId() : null)) return;
            int correct = round.question.answer();
            String ending = result.winner() != null ? "判断正确！该声音出现的场景为："
                    : result.answers().isEmpty() ? "时间到，无人回答，正确答案是：" : "❌回答错误，正确答案是：";
            // The round atomically returns a settlement result only once. Credit before sending the result.
            String reward = rewardText(result, LootRepository::addCoins);
            String sent = qq.sendMessage(TC.md(ending + (char) ('A' + correct) + ". " + round.question.options().get(correct).name()
                    + reward + "\n\n" + Markdown.enterCommand("/sound", "♻ 再来一次")));
            if (sent == null) logFailure(round, "答案发送", null);
        } catch (Exception e) {
            logFailure(round, "答案发送", e);
        } finally {
            rounds.remove(sessionKey(qq), round);
        }
    }

    static String rewardText(SoundRound.Result result, ToIntBiFunction<String, Integer> credit) {
        if (result.winner() == null) return "";
        if (credit.applyAsInt(result.winner(), CORRECT_REWARD) == CORRECT_REWARD) {
            return "（+" + CORRECT_REWARD + " 金粒）";
        }
        log.warn("听声辨物金粒奖励发放失败: winner={}, amount={}", result.winner(), CORRECT_REWARD);
        return "\n\n金粒奖励发放失败，请向开发者报告此问题";
    }

    private static void logFailure(SoundRound round, String stage, Exception error) {
        QQMessageSendException official = error instanceof QQMessageSendException e ? e : null;
        log.warn("Minecraft 听声辨物失败: stage={}, round={}, event={}, playback={}, sha1={}, options={}, qqCode={}, qqTrace={}, reason={}",
                stage, round.id, round.question.options().get(round.question.answer()).id(),
                round.question.audio().path(), round.question.audio().sha1(),
                round.question.options().stream().map(SoundCatalog.Sound::id).toList(),
                official == null ? null : official.getCode(), official == null ? null : official.getTraceId(),
                error == null ? "消息接口未返回 ID；请核对官机发送日志" : error.getMessage());
    }

    private String questionText(SoundRound round) {
        var q = round.question;
        StringBuilder text = new StringBuilder("**Minecraft 听声辨物**\n\n下述声音可能出现在什么场景？\n\n");
        for (int i = 0; i < 4; i++)
            text.append("> ").append((char) ('A' + i)).append(". ").append(escape(q.options().get(i).name())).append("\n");
        return text.append("\n\uD83D\uDD0D 请点击下方按键做出判断").toString();
    }

    public static Keyboard answerKeyboard(SoundRound round, String prefix) {
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < 4; i++)
            buttons.add(new Button("sound_" + i, String.valueOf((char) ('A' + i)),
                    prefix + "sound answer " + round.id + " " + (char) ('A' + i), true, ButtonStyle.BLUE, ButtonType.COMMAND));
        return new Keyboard(List.of(buttons));
    }

    private String commandPrefix() {
        return Config.getInstance().getCommandPrefix();
    }

    private static String sessionKey(QQCommandSender qq) {
        return sessionKey(qq.getPlatform(), qq.getGroupId(), qq.getUserId());
    }

    public static String sessionKey(Platform platform, String groupId, String userId) {
        String id = switch (platform) {
            case OFFICIAL_GROUP -> groupId;
            case OFFICIAL_C2C -> userId;
            default -> throw new IllegalArgumentException("Unsupported sound platform");
        };
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Missing sound conversation ID");
        return platform.name() + ":" + id;
    }

    private static String escape(String text) {
        return text.replaceAll("[\\\\`*_{}\\[\\]()<>#|!\\r\\n]", " ");
    }

    @Override
    public void close() {
        synchronized (rounds) {
            closed = true;
            rounds.values().forEach(SoundRound::cancel);
            rounds.clear();
        }
    }
}
