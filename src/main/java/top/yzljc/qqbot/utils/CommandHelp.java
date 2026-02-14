package top.yzljc.qqbot.utils;

import top.yzljc.qqbot.botkits.findinfo.GetProjectInfo;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.command.CommandContext;
import top.yzljc.qqbot.command.ExecuteCommand;

/**
 * Bot 帮助/介绍信息
 */
public class CommandHelp implements ExecuteCommand {

    @Override
    public void execute(CommandContext ct) {
        processHelp(ct.getGroupId());
    }

    public static void processHelp(long groupId) {
        sendHelpMenu(groupId);
    }

    private static void sendHelpMenu(long groupId) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== YZ_Ljc_ Bot 项目说明 ===\n\n");
        sb.append("这是一个新手拿来练手的项目，并没有什么有用的功能。\n");
        sb.append("但是既然你能看到这个消息，首先非常感谢你对该项目的支持！\n\n");

        sb.append("Bot 的设计初衷是用来高度自定义连接 MC/QQ 群的\n");
        sb.append("因此它有一个配套的MC插件能与 Bot 互联，与服务器交互，如果你有需求可以联系一下开发\n\n");

        sb.append("【Bot 功能介绍】\n");
        sb.append("● 自动解析哔哩哔哩视频 [默认关闭]\n");
        sb.append("● Hypixel/Minecraft 新闻自动检索推送\n");
        sb.append("● 发送 \"赞我\" 获取名片赞\n");
        sb.append("● 贴表情恶搞机制 (使用 /emj 查看)\n");
        sb.append("● 新年倒计时推送\n");
        sb.append("● 发送 \"一言\" 获得随机一言\n");
        sb.append("● 每日 7 时发送起床表情包 [默认关闭]\n");
        sb.append("● 每日自动群打卡\n");
        sb.append("● 自动复读\n");
        sb.append("● MOJANG 验证服务器状态查询 (/mojang)\n");
        sb.append("● 自动回复戳一戳\n");
        sb.append("● Github 仓库更新推送 [默认仅推送 Bot 更新]\n");

        sb.append("● 见证/敏感词检测撤回/消息统计/批量撤回 [需联系开发]\n");
        sb.append("● MOTD 查询 [需联系开发]\n\n");


        sb.append("您可以发送 /groupinfo 查询本群功能开启情况\n");
        sb.append("如需进行功能调整请联系开发，再次感谢您的支持！\n");
        String buildTime = GetProjectInfo.getBuildTime();
        String commitId = GetProjectInfo.getCommitId();
        String branch = GetProjectInfo.getBranch();
        String version = GetProjectInfo.getVersion();
        String buildInfo = "版本信息 -> Build Time: " + buildTime + " | " + commitId + "/" + branch + " " + version + "\n";
        sb.append(buildInfo);
        sb.append("使用/update查看最新版本更新信息");

        MessageSender.sendGroupMessage(groupId, sb.toString());
    }
}