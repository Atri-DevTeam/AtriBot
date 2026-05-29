package top.yzljc.atribot.functions.official.minecraft;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.chat.official.At;
import top.yzljc.atribot.chat.official.ChatService;
import top.yzljc.atribot.functions.official.permission.GroupList;
import top.yzljc.atribot.service.official.CommandButton;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName NewsLogic
 * @Created_at 2026/06/04
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official
 */
public class NewsImpl {

    private static final ChatService service = Atri.getInstance().getChatService();

    public static void handle(CommandSender sender, String label, String[] args) {
        GroupList.FunctionInfo statusInfo = GroupList.getFunctionInfo(sender.groupOpenId(), "mc_news");
        String statusLine;
        if (statusInfo.operator() != null) {
            statusLine = statusInfo.enabled()
                    ? "> 当前状态：已由 " + At.at(statusInfo.operator()).replace("\n", "") + " 于 " + statusInfo.time() + " 开启"
                    : "> 当前状态：已由 " + At.at(statusInfo.operator()).replace("\n", "") + " 于 " + statusInfo.time() + " 关闭";
        } else {
            statusLine = "> 当前状态：未配置";
        }

        String markdown = """
                **Minecraft新闻动态**

                在启用此功能前，请先阅读以下使用条例：

                1. 本功能仅提供Minecraft相关的新闻动态，数据来源于官网，整点更新一次。

                2. 为了第一时间拿到内容，检索内容存在两个渠道，因此可能会出现内容相似的推送。

                3. 所有内容均为AI总结，如有不合适的内容，请使用/feedback向开发者反馈，我们会第一时间进行调整。

                4. 请确保你已打开机器人在本群的**主动消息推送**权限，否则将无法收到新闻动态推送。

                %s

                点击下方按钮开启本功能则表示您已阅读并知晓上述内容，如后续需要关闭可再次使用此指令关闭功能
                """.formatted(statusLine);

        Object keyboard = service.buildCmdKeyboard(List.of(
                List.of(
                        new CommandButton("c1", "开启", "/mc news on", false, 1, 2),
                        new CommandButton("c2", "关闭", "/mc news off", false, 3, 2)
                ),
                List.of(
                        new CommandButton("c3", "向开发者反馈", "/feedback ", true, 1, 2)
                )
        ));

        if (args.length >= 2) {
            if (args[1].equalsIgnoreCase("off")) {
                handleOff(sender, label);
            } else if (args[1].equalsIgnoreCase("on")) {
                handleOn(sender, label);
            } else {
                sender.replyMarkdown(label, "参数错误！请使用 /mc news on 来开启新闻动态推送，或 /mc news off 来关闭新闻动态推送");
            }
            return;
        }
        sender.replyMarkdown(label, markdown, keyboard);
    }

    public static void handleOn(CommandSender sender, String label) {
        GroupList.FunctionInfo info = GroupList.getFunctionInfo(sender.groupOpenId(), "mc_news");
        if (info.enabled() && info.operator() != null) {
            sender.replyMarkdown(label, "Minecraft新闻推送已经在 " + info.time() + " 被" + At.at(info.operator()).replace("\n", "") + "开启过了！");
        } else {
            GroupList.setFunctionEnabled(sender.groupOpenId(), "mc_news", true, sender.unionOpenId());
            sender.replyMarkdown(label, "本群内Minecraft新闻推送已被" + At.at(sender.unionOpenId()).replace("\n", "") + "开启！如果想关闭，请使用 /mc news off 来关闭新闻动态推送");
        }
    }

    public static void handleOff(CommandSender sender, String label) {
        GroupList.FunctionInfo info = GroupList.getFunctionInfo(sender.groupOpenId(), "mc_news");
        if (!info.enabled() && info.operator() != null) {
            sender.replyMarkdown(label, "Minecraft新闻推送已经在 " + info.time() + " 被" + At.at(info.operator()).replace("\n", "") + "关闭过了！");
        } else {
            GroupList.setFunctionEnabled(sender.groupOpenId(), "mc_news", false, sender.unionOpenId());
            sender.replyMarkdown(label, "本群内Minecraft新闻推送已被" + At.at(sender.unionOpenId()).replace("\n", "") + "关闭！如果想再次开启，请使用 /mc news on 来开启新闻动态推送");
        }
    }
}
