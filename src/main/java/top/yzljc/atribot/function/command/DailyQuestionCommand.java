package top.yzljc.atribot.function.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.configuration.RS;
import top.yzljc.atribot.database.repo.DailyQuestionRepository;
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
import top.yzljc.atricore.service.runtime.ThreadManager;

import java.util.List;
import java.util.Locale;

/**
 * @Author YZ_Ljc_
 * @ClassName DailyQuestionCommand
 * @Created_at 2026/11/3
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
public final class DailyQuestionCommand implements CommandExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(DailyQuestionCommand.class);
    private final DailyQuestionRepository repository;

    public DailyQuestionCommand(DailyQuestionRepository repository) {
        this.repository = repository;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq) || qq.getPlatform() != Platform.OFFICIAL_GROUP && qq.getPlatform() != Platform.OFFICIAL_C2C)
            return true;
        String answer = args.length == 0 ? null : args[0].toUpperCase(Locale.ROOT);
        if (args.length > 2 || answer != null && !answer.equals("A") && !answer.equals("B")) {
            qq.sendMessage("/question 查看今日问题\n/question A 或 /question B 提交选择，提交后不可修改");
            return true;
        }
        String key = args.length == 2 ? args[1] : null;
        ThreadManager.execute(() -> execute(qq, answer, key));
        return true;
    }

    private void execute(QQCommandSender sender, String answer, String key) {
        DailyQuestionRepository.View view;
        try {
            view = repository.visit(sender.getUserId(), answer, key);
        } catch (Exception failure) {
            LOG.error("今日问题处理失败，用户: {}", sender.getUserId(), failure);
            sender.sendMessage("今日问题暂时不可用，请稍后使用 /question 查看");
            return;
        }
        if (view.result() == DailyQuestionRepository.Result.EXPIRED) {
            sender.sendMessage("无效或不支持回答的题目，请使用 /question 查看今日问题");
            return;
        }
        var q = view.day().question();
        StringBuilder text = new StringBuilder(Markdown.img(RS.get("resource.image.daily_question"), 2172, 724))
//                .append("\n\n**今日问题 · ").append(view.day().date()).append("**\n\n")
                .append(q.text()).append("\n\n> A. ").append(q.optionA()).append("\n> B. ").append(q.optionB());
        if (view.answer() != null) {
            text.append("\n\n你").append(view.result() == DailyQuestionRepository.Result.ALREADY_ANSWERED ? "已经" : "").append("选择了**").append(view.answer()).append("**，今日共有**")
                    .append(view.countA()).append("**人选择了A，**")
                    .append(view.countB()).append("**人选择了B");
        }
//
//        }
//        text.append(view.result() == DailyQuestionRepository.Result.ALREADY_ANSWERED
//                ? "\n\n你今天已经作答，答案不可修改" : "");
//        long total = view.countA() + view.countB();
        var md = TC.md(text.toString());
//        if (view.answer() == null) {
//            var buttons = new Keyboard(List.of(
//                    List.of(new Button("A. " + q.optionA(), "/question A " + view.day().key(), ButtonStyle.BLUE)),
//                    List.of(new Button("B. " + q.optionB(), "/question B " + view.day().key(), ButtonStyle.BLUE))));
//            md.setKeyboard(buttons, OfficialUsers.isUserUnsupportedKeyboard(sender.getUserId()));
//        }
        sender.sendMessage(md.setKeyboard(get(view.answer() != null, view.day().key())));
    }

//    private static String percent(long count, long total) {
//        return String.format(Locale.ROOT, "%.1f", total == 0 ? 0.0 : count * 100.0 / total);
//    }

    private static Keyboard get(boolean answered, String key) {
        var ks = Keyboard.of(
                List.of(
                        List.of(new Button("c1", "打卡", "/sign", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c3", "钓鱼", "/fish", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c2", "MC物品", "/item", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c4", "任意门", "/door", true, ButtonStyle.RED, ButtonType.COMMAND)
                        ),
                        List.of(
                                new Button("c6", "\uD83D\uDCCC 今日问题", "/question", true, ButtonStyle.RED, ButtonType.COMMAND),
                                new Button("c5", "\uD83E\uDDE8 春节倒计时", "/newyear", true, ButtonStyle.BLUE_WITH_BACKGROUND, ButtonType.COMMAND)
                        )
                )
        );

        if (!answered) {
            ks = ks.append(List.of(List.of(new Button("我要选 \uD83C\uDD70\uFE0F", "/question A " + key, ButtonStyle.GRAY), new Button("我要选 \uD83C\uDD71\uFE0F", "/question B " + key, ButtonStyle.GRAY))), true);
        }

        return ks;
    }
}
