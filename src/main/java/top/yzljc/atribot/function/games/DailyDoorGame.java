package top.yzljc.atribot.function.games;

import java.time.format.DateTimeFormatter;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import top.yzljc.atribot.configuration.RS;
import top.yzljc.atribot.database.repo.DailyDoorRepository;
import top.yzljc.atribot.database.repo.DailyDoorRepository.Result;
import top.yzljc.atribot.database.repo.DailyDoorRepository.View;
import top.yzljc.atribot.function.impl.ImageDTO;
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

/**
 * @Author YZ_Ljc_
 * @ClassName DailyDoorGame
 * @Created_at 2026/10/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.games
 */
public final class DailyDoorGame implements CommandExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(DailyDoorGame.class);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private final DailyDoorRepository repository = new DailyDoorRepository();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)
                || qq.getPlatform() != Platform.OFFICIAL_GROUP && qq.getPlatform() != Platform.OFFICIAL_C2C) return true;
        if (args.length != 0) {
            qq.sendMessage("/door 查看今日门状态\n/open 尝试开门\n每天全服共享一扇门，每人只能尝试一次");
            return true;
        }
        boolean attempt = "open".equalsIgnoreCase(label);
        ThreadManager.execute(() -> execute(qq, attempt));
        return true;
    }

    private void execute(QQCommandSender sender, boolean attempt) {
        View view;
        try {
            view = repository.visit(sender.getUserId(), attempt);
        } catch (Exception failure) {
            LOG.error("每日任意门状态读取失败，用户: {}", sender.getUserId(), failure);
            sender.sendMessage("暂时无法读取门的状态，请稍后使用 /door 查看");
            return;
        }
        ImageDTO image = null;
        try {
            String key = view.today().open() ? "resource.image.door-open" : "resource.image.door-closed";
            image = new ImageDTO(RS.get(key), 2172, 724);
        } catch (Exception failure) {
            LOG.warn("每日任意门配图暂不可用: {}", failure.getMessage());
        }
        var buttons = view.today().open()
                ? List.of(new Button("door_status", "查看状态", "/door", ButtonStyle.GRAY, ButtonType.COMMAND))
                : List.of(new Button("door_open", "尝试开门", "/open", ButtonStyle.BLUE, ButtonType.COMMAND),
                        new Button("door_status", "查看状态", "/door", ButtonStyle.GRAY, ButtonType.COMMAND));
        sender.sendMessage(TC.md(render(view, image)).setKeyboard(new Keyboard(List.of(buttons))));
    }

    static String render(View view, ImageDTO image) {
        var door = view.today();
        String text = "**任意门 #" + door.number() + "**\n\n";
        if (image != null && !image.isError() && image.url() != null && image.width() > 0 && image.height() > 0) {
            text += Markdown.img(image.url(), image.width(), image.height()) + "\n\n";
        }
        text += switch (view.result()) {
            case ALREADY_OPEN -> "今天的门已经打开了。\n\n";
            case ALREADY_ATTEMPTED -> "你今天已经试过了。\n\n";
            case VIEW -> "";
            default -> view.feedback() + "\n\n";
        };
        if (door.open()) {
            text += "> 状态：已打开\n> 开启时间：" + TIME.format(door.openedAt())
                    + "（北京时间）\n> 今日共有 " + door.attempts() + " 人尝试过（+50金粒）\n\n" + door.ending();
        } else {
            text += "> 状态：关闭\n> 今日已有 " + door.attempts() + " 人尝试打开\n> 你今天"
                    + (view.attempted() ? "已尝试" : "未尝试");
        }
        var yesterday = view.yesterday();
        if (view.result() == Result.VIEW && yesterday != null) {
            text += "\n\n---\n\n昨日任意门 #" + yesterday.number() + "\n\n";
            if (yesterday.open()) {
                text += "> 昨日 " + TIME.format(yesterday.openedAt()) + " 开启，共 " + yesterday.attempts() + " 人尝试\n\n" + yesterday.ending();
            } else {
                text += "> 昨天的门没有打开，一共留下了 " + yesterday.attempts() + " 次尝试";
            }
        }
        return text;
    }
}
