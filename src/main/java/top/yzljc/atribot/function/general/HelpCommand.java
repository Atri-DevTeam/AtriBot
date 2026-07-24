package top.yzljc.atribot.function.general;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.configuration.ResourcesProperties;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.impl.MessageSegment;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.function.general.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.official.OfficialBot;
import top.yzljc.atribot.service.runtime.ThreadManager;
import top.yzljc.atribot.utils.GetProjectInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName HelpCommand
 * @Created_at 2026/05/25
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions.official
 */
@Slf4j
public class HelpCommand implements CommandExecutor {

    private static final String secret = Config.getInstance().getAtribotKeySecret();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender.getPlatform() == Platform.OFFICIAL_GROUP || sender.getPlatform() == Platform.OFFICIAL_C2C) {

            Markdown md = TC.md("✨ **" + OfficialBot.BOT_NAME + "帮助菜单**\n\n" +
                    "> \uD83D\uDCA1小提示: 下方内容可直接点击触发\n\n" +
                    Markdown.img(ResourcesProperties.GRASS_BLOCK_IMG, 16, 16) + Markdown.enterCommand("/mc",  "MC功能  ") + " | " + Markdown.enterCommand("/打卡", "\uD83D\uDCCC每日打卡") + "\n\n" +
                    Markdown.enterCommand("/games", "\uD83C\uDF40小游戏   ") + " | " + Markdown.enterCommand("/hitokoto", "\uD83D\uDCAB随机一言") + "\n\n" +
                    Markdown.enterCommand("/mojang", "\uD83D\uDEE0\uFE0FMC状态 ") + " | " + Markdown.enterCommand("/hypstatus", "\uD83D\uDCA4Hyp状态") + "\n\n" +
                    Markdown.enterCommand("/newyear", "⏳新年倒数") + " | " + Markdown.enterCommand("/today", "\uD83D\uDCC5今日日历") + "\n\n" +
                    "> " + Markdown.enterCommand("/rsp", "✊一场酣畅淋漓的石头剪刀布") + "\n" +
                    "> " + Markdown.img(ResourcesProperties.MINECRAFT_CAPE_EXAMPLE, 16, 16) + Markdown.enterCommand("/mc capes", "Minecraft披风实况")  + "\n" +
                    "> " + Markdown.img(ResourcesProperties.HYPIXEL_HEADER_IMG, 16, 16) + Markdown.enterCommand("/bantracker", "Hypixel BanTracker") + "\n" +
                    "> " + Markdown.enterCommand("/cl ", "\uD83C\uDF81领取Hypixel每日签到奖励"));

            List<List<Button>> buttons = List.of(
                    List.of(
                            new Button("s1", "问题反馈", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND),
                            new Button("s2", "详细帮助", "/help -m", false, ButtonStyle.BLUE, ButtonType.COMMAND)
                    ),
                    List.of(
                            new Button("s3", "贡献名单", "/贡献名单", false, ButtonStyle.BLUE, ButtonType.COMMAND),
                            new Button("s4", "推送任务", "/推送任务", false, ButtonStyle.BLUE, ButtonType.COMMAND)
                    ),
                    List.of(
                            new Button("s5", "场景信息", "/whoami", false, ButtonStyle.BLUE, ButtonType.COMMAND),
                            new Button("s6", "全量消息", "/全量消息", false, ButtonStyle.BLUE, ButtonType.COMMAND)
                    ),
                    List.of(
                            new Button("l1", "社区交流", "https://qm.qq.com/q/UXrrpLsICG", true, ButtonStyle.BLUE, ButtonType.LINK),
                            new Button("l2", "邀我进群", "https://qun.qq.com/qunpro/robot/qunshare?robot_uin=3889798968&robot_appid=102808581&sceneData=Y2m6DyvYd2SX5MyQfppq4axrIRdiOobQ6HDJim4ofdHS4e7PXVyNveeS8neRVhk4WdeLSBVcwJqpoXQTamuFFFC", true, ButtonStyle.BLUE, ButtonType.LINK)
                    )
            );

            if (args.length > 0 && args[0].equals("-m")) {
                ThreadManager.execute(() -> {
                    String apiUrl = ResourcesProperties.HELP_API + "?key=" + secret;
                    try {
                        var data = PreImageGenerate.dump(apiUrl, Map.of());
                        if (data.isError()) {
                            String errMsg = data.errorMessage();
                            log.warn("获取帮助信息失败: {}", errMsg);
                            sender.sendMessage("获取帮助信息失败: " + errMsg);
                            return;
                        }

                        String help = "![today #2240px #1280px](" + data.url() + ")";


                        Object keyboard = TC.keyboard(buttons);

                        sender.sendMessage(TC.md(help), keyboard);

                    } catch (Exception e) {
                        log.error("获取帮助信息 API 失败: ", e);
                    }
                });
                return true;
            }
            sender.sendMessage(md, TC.keyboard(buttons));
            return true;
        }

        if (sender.getPlatform() == Platform.NAPCAT_GROUP || sender.getPlatform() == Platform.NAPCAT_PRIVATE) {
            GroupMessage.forwardMessage(sender.getGroupId(), getAtriHelp(), "ATRI - YZ_Ljc_ Bot 帮助文档", "查看项目帮助信息",
                    "项目开发说明", "指令帮助", "功能介绍");
            return true;
        }
        return true;
    }

    public static List<MessageSegment> getAtriHelp() {
        List<MessageSegment> help = new ArrayList<>();
        String sb = """
                这是一个新手拿来练手的项目，并没有什么有用的功能。
                但是既然你能看到这个消息，首先非常感谢你对该项目的支持！
                
                注意！我们部分内容已转接到官方机器人，如果你想了解更多关于官方机器人的信息可以加入交流群或者联系开发！官方机器人也有一些独特的功能哦！
                
                官方机器人：3889798968（亚托莉喵）
                
                联系开发者的最快方式是使用 /feedback 指令，或者直接联系开发者 QQ: 3199590352
                """;
        String subSb = """
                本机器人的大部分内容已转移到官方机器人！
                """;
        help.add(GroupMessage.createTextNode(sb));
        help.add(GroupMessage.createTextNode(subSb));
        help.add(GroupMessage.createTextNode(featureHelp()));
        help.add(GroupMessage.createTextNode(commandHelp()));
        help.add(GroupMessage.createTextNode(lastInfo()));
        help.add(GroupMessage.createTextNode(versionInfo()));

        return help;
    }

    private static String featureHelp() {

        return """
                【功能介绍】
                ● 自动解析哔哩哔哩视频 [默认关闭]
                ● Hypixel/Minecraft 新闻自动检索推送 [默认开启]
                ● Github 仓库更新推送 [默认仅推送 Bot 更新]
                ● 见证/敏感词检测撤回/消息统计/批量撤回 [默认关闭]
                ● 每日自动群打卡 [默认开启]
                ● 自动复读 [默认开启]
                ● 每日日历推送 [默认开启]
                ● 自动回复戳一戳 [默认开启]
                """;
    }

    private static String commandHelp() {

        return """
                【指令帮助信息】
                ● 发送 "赞我" 获取名片赞
                ● 发送 "一言" 获得随机一言
                ● /emj 贴表情恶搞机制相关
                ● /calendar 日历推送
                ● /mojang MC验证服务器状态查询
                ● /hypstatus 查看Hypixel各服务器运行状态（可缩写为/hs）
                ● /cl <url> 领取Hypixel每日签到 [默认关闭]
                ● /gt <文字内容> 生成表情包 [默认关闭]
                ● /anan <文字内容> [-参数] 生成表情包 [默认关闭]
                ● /py <文字内容> 转换为拼音 [默认关闭]
                ● /feedback <内容> 反馈建议
                """;
    }

    private static String lastInfo() {

        return """
                您可以发送 /groupinfo 查询本群功能开启情况
                如需进行功能调整请联系开发，再次感谢您的支持！
                如有简单MC服务器插件需求也可以联系我!
                联系方式:
                ● QQ: 3199590352
                ● 交流群: 818804507
                ● B站: https://space.bilibili.com/592616376
                ● E-mail: contact@yzljc.top
                ● 测试服: mc.yzljc.top
                """;
    }

    private static String versionInfo() {
        String buildTime = GetProjectInfo.getBuildTime();
        String commitId = GetProjectInfo.getCommitId();
        String branch = GetProjectInfo.getBranch();
        String version = GetProjectInfo.getVersion();
        return "版本信息 -> Build Time: " + buildTime + " | " + commitId + "/" + branch + " " + version + "\n";
    }
}
