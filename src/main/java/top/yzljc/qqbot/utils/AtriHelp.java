package top.yzljc.qqbot.utils;

import top.yzljc.qqbot.botservice.userinfo.GetProjectInfo;
import top.yzljc.qqbot.botservice.message.MessageUtils;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Bot 帮助/介绍信息
 */
public class AtriHelp implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        MessageUtils.sendGroupForwardMessage(sender.groupId(), getAtriHelp(), "ATRI - YZ_Ljc_ Bot 帮助文档", "查看项目帮助信息",
                "项目开发说明", "指令帮助", "功能介绍");
        return true;
    }

    public static List<Map<String, Object>> getAtriHelp(){
        // Map<String, Object> payload = new HashMap<>();
        List<Map<String,Object>> help = new ArrayList<>();
        String sb = """
                这是一个新手拿来练手的项目，并没有什么有用的功能。
                但是既然你能看到这个消息，首先非常感谢你对该项目的支持！
                """;
        String subSb = """
                Bot 的设计初衷是用来高度自定义连接 MC/QQ 群的
                因此它有一个配套的MC插件能与 Bot 互联，与服务器交互，如果你有需求可以联系一下开发
                """;
        help.add(MessageUtils.createTextNode(sb,"3199590352","YZ_Ljc_"));
        help.add(MessageUtils.createTextNode(subSb,"3199590352","YZ_Ljc_"));
        help.add(MessageUtils.createTextNode(featureHelp(),"3199590352","YZ_Ljc_"));
        help.add(MessageUtils.createTextNode(commandHelp(),"3199590352","YZ_Ljc_"));
        help.add(MessageUtils.createTextNode(lastInfo(),"3199590352","YZ_Ljc_"));
        help.add(MessageUtils.createTextNode(versionInfo(),"3199590352","YZ_Ljc_"));

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

        return """
                【指令帮助信息】
                ● 发送 "赞我" 获取名片赞
                ● 发送 "一言" 获得随机一言
                ● /emj 贴表情恶搞机制相关
                ● /calendar 日历推送
                ● /mojang MOJANG 验证服务器状态查询
                ● /motd MOTD 查询 [默认关闭]
                ● /cl <url> 领取Hypixel每日签到 [默认关闭]
                ● /rc <服务器编号> <指令> MC服务器关联指令 [默认关闭]
                """;
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