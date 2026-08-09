package top.yzljc.atribot.function.official;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.impl.ImageDTO;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Identifier;
import top.yzljc.atribot.platform.Platform;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName BanTracker
 * @Created_at 2026/06/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official
 */
public final class BanTracker {

    private static final Set<String> VALID_TYPES = Set.of("30min", "1h", "3h", "24h", "7d", "30d");

    public static boolean handle(CommandSender sender, Command command, String label, String[] args) {

        if (!Objects.equals(label, "bt") && !Objects.equals(label, "bantracker")) {
            sender.sendMessage(Identifier.ONLY_OFFICIAL);
            return true;
        }

        if (sender.getPlatform() != Platform.OFFICIAL_GROUP && sender.getPlatform() != Platform.OFFICIAL_C2C) {
            return true;
        }

        String type;
        if (args.length < 1) {
            type = "30min";
        } else {
            type = args[0];
            if (!VALID_TYPES.contains(type)) {
                type = "30min";
            }
        }

        String url = ResourcesProperties.BAN_TRACKER + "?" + System.currentTimeMillis();
        Map<String, String> req = Map.of(
                "window", type
        );

        ImageDTO data = PreImageGenerate.dump(url, req);
        if (data.isError()) {
            sender.sendMessage(data.errorMessage());
            return true;
        }
        if (data.url() == null) {
            sender.sendMessage("数据获取失败: 发生未知错误!");
            return true;
        }

        Markdown md = TC.md(
                Markdown.img(data.url(), data.width(), data.height())
        );

        Object buttons = TC.keyboard(
                List.of(
                        List.of(new Button("c1", "30分钟", "/bantracker 30min", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c2", "1小时", "/bantracker 1h", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c2", "3小时", "/bantracker 3h", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c3", "24小时", "/bantracker 24h", true, ButtonStyle.BLUE, ButtonType.COMMAND)
                        ),
                        List.of(
                                new Button("c5", "7天", "/bantracker 7d", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c6", "30天", "/bantracker 30d", true, ButtonStyle.BLUE, ButtonType.COMMAND)
                        )
                )
        );

        sender.sendMessage(md, buttons);

        return true;
    }
}