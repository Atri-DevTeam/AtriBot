package top.yzljc.qqbot.utils;

import top.yzljc.qqbot.service.userinfo.GetProjectInfo;
import top.yzljc.qqbot.chat.GroupMessage;
import top.yzljc.qqbot.chat.MessageSegment;
import top.yzljc.qqbot.command.CommandDefinition;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandManager;
import top.yzljc.qqbot.command.CommandSender;

import java.util.ArrayList;
import java.util.List;

public class AtriHelp implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        GroupMessage.forwardMessage(sender.groupId(), getAtriHelp(), "ATRI - YZ_Ljc_ Bot 帮助文档", "查看项目帮助信息",
                "项目开发说明", "指令帮助", "功能介绍");
        return true;
    }

    public static List<MessageSegment> getAtriHelp(){
        // Map<String, Object> payload = new HashMap<>();
        List<MessageSegment> help = new ArrayList<>();
        String sb = """
                这是一个新手拿来练手的项目，并没有什么有用的功能。
                但是既然你能看到这个消息，首先非常感谢你对该项目的支持！
                """;
        String subSb = """
                Bot 的设计初衷是用来高度自定义连接 MC/QQ 群的
                因此它有一个配套的MC插件能与 Bot 互联，与服务器交互，如果你有需求可以联系一下开发
                """;
        help.add(GroupMessage.createTextNode(sb, "3199590352", "YZ_Ljc_"));
        help.add(GroupMessage.createTextNode(subSb, "3199590352", "YZ_Ljc_"));
        help.add(GroupMessage.createTextNode(featureHelp(), "3199590352", "YZ_Ljc_"));
        help.add(GroupMessage.createTextNode(commandHelp(), "3199590352", "YZ_Ljc_"));
        help.add(GroupMessage.createTextNode(lastInfo(), "3199590352", "YZ_Ljc_"));
        help.add(GroupMessage.createTextNode(versionInfo(), "3199590352", "YZ_Ljc_"));

        return help;
    }

    private static String featureHelp(){

        return """
                【功能介绍】
                ● 自动解析哔哩哔哩视频 [默认关闭]
                ● Hypixel/Minecraft 新闻自动检索推送 [默认开启]
                ● Github 仓库更新推送 [默认仅推送 Bot 更新]
                ● 见证/敏感词检测撤回/消息统计/批量撤回 [默认关闭]
                ● 每日自动群打卡 [默认开启]
                ● 每日 7 时发送起床表情包 [默认关闭]
                ● 自动复读 [默认开启]
                ● 每日日历推送 [默认开启]
                ● 自动回复戳一戳 [默认开启]
                """;
    }

    private static String commandHelp() {
        StringBuilder sb = new StringBuilder("【指令帮助信息】\n");
        List<CommandDefinition> definitions = CommandManager.getDefinitions();
        if (definitions.isEmpty()) {
            return sb.append("（暂无可用指令）").toString();
        }
        for (CommandDefinition definition : definitions) {
            sb.append("● ").append(definition.usage());
            if (!definition.description().isBlank()) {
                sb.append(" - ").append(definition.description());
            }
            if (!definition.aliases().isEmpty()) {
                sb.append(" [别名: ").append(String.join(", ", definition.aliases())).append("]");
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    private static String lastInfo(){

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

    private static String versionInfo(){
        String buildTime = GetProjectInfo.getBuildTime();
        String commitId = GetProjectInfo.getCommitId();
        String branch = GetProjectInfo.getBranch();
        String version = GetProjectInfo.getVersion();
        return "版本信息 -> Build Time: " + buildTime + " | " + commitId + "/" + branch + " " + version + "\n";
    }
}