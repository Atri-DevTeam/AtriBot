package top.yzljc.atribot.function.command;

import top.yzljc.atricore.core.CoreConfig;

import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atricore.chat.napcat.GroupMessage;
import top.yzljc.atricore.chat.napcat.impl.MessageSegment;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.button.*;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.command.QQGuildCommandSender;
import top.yzljc.atricore.command.NapcatCommandSender;
import top.yzljc.atribot.configuration.RS;
import top.yzljc.atricore.platform.qq.QQBot;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName HelpCommand
 * @Created_at 2026/08/26
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
public class HelpCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof QQCommandSender user) {
            Markdown md = TC.md("✨ **" + QQBot.BOT_NAME + "帮助菜单**\n\n" +
                    "> \uD83D\uDCA1小提示: 下方内容可直接点击触发\n\n" +
                    "---\n\n" +
                    "> " + Markdown.enterCommand("/打卡", "\uD83D\uDCCC每日打卡签到") + "\n" +
                    "> " + Markdown.img(RS.get("resource.image.console-logo"), 16, 16) + Markdown.enterCommand("/drawitem", "抽取随机ᴍɪɴᴇᴄʀᴀғᴛ物品") + "\n" +
                    "> " + Markdown.img(RS.get("resource.image.grass-block"), 16, 16) + Markdown.enterCommand("/mctool", "ᴍɪɴᴇᴄʀᴀғᴛ工具二级菜单") + "\n" +
                    "> " + Markdown.img(RS.get("resource.image.dice-result-template").replace("<id>", "6"), 16, 16) + Markdown.enterCommand("/hyp dice", "sᴋʏʙʟᴏᴄᴋ运气测试") + "\n" +
                    "> " + Markdown.img(RS.get("resource.image.hypixel-header"), 16, 16) + Markdown.enterCommand("/hyp", "ʜʏᴘɪxᴇʟ数据查询二级菜单") + "\n" +
                    "> " + Markdown.enterCommand("/cl ", "\uD83C\uDF81领取ʜʏᴘɪxᴇʟ每日签到奖励") + "\n" +
                    "> " + Markdown.enterCommand("/rsp", "✊一场酣畅淋漓的石头剪刀布") + "\n" +
                    "> " + Markdown.enterCommand("/mojang", "\uD83D\uDEE0查询ᴍᴄ验证服务器状态") + "\n" +
                    "> " + Markdown.enterCommand("/newyear", "\uD83E\uDDE8查看新年倒计时") + "\n" +
                    "> " + Markdown.enterCommand("/tasks", "\uD83D\uDD14设置推送任务") + "\n" +
                    "> " + Markdown.img(RS.get("resource.image.minecraft-cape-example"), 16, 16) + Markdown.enterCommand("/mctool cape", "查看ᴍᴄ披风拥有情况") + "\n" +
                    "> " + Markdown.enterCommand("/bantrack", "\uD83D\uDEABʜʏᴘɪxᴇʟ ʙᴀɴ ᴛʀᴀᴄᴋ") + "\n" +
                    "> " + Markdown.img(RS.get("resource.image.icon-ec"), 16, 16) + Markdown.enterCommand("/ec", "ᴍɪɴᴇᴄʀᴀғᴛ物品附魔模拟") + "\n" +
                    "> " + Markdown.enterCommand("/games", "\uD83E\uDDE9游玩更多经典小游戏"));

//            Object keyboard = TC.keyboard(
//            );

            Keyboard keyboard = new Keyboard(
                    List.of(
                            List.of(
                                    new Button("s1", "问题反馈", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND).setModal("对" + QQBot.BOT_NAME + "的部分内容有更改建议？遇到了问题？欢迎向开发者反馈喵~", "我要反馈", "以后再说"),
                                    new Button("s6", "全量消息", "/全量消息", true, ButtonStyle.BLUE, ButtonType.COMMAND)
                            ),
                            List.of(
                                    new Button("l2", "添加到群", "https://web.qun.qq.com/qunrobot/jump.html?robot_uin=" + QQBot.BOT_UIN + "&target=2", true, ButtonStyle.BLUE, ButtonType.LINK),
                                    new Button("l3", "添加到频道", "https://qun.qq.com/qunpro/robot/share?robot_appid=" + CoreConfig.getInstance().getSettings().getQqAppId(), true, ButtonStyle.BLUE, ButtonType.LINK)
                            )
                    )
            );

            user.sendMessage(md.setKeyboard(keyboard, OfficialUsers.isUserUnsupportedKeyboard(user.getUserId())));

            return true;
        }

        if (sender instanceof QQGuildCommandSender guild) {
            guild.sendMessage("""
                    ======== 频道指令帮助 ========
                    /zs <玩家>  查询 Hypixel 僵尸末日数据
                    /wz <玩家>  查询 Hypixel 法师掘战数据
                    /help  查看帮助
                    /whoami  查看当前账号及场景信息
                    /feedback <内容>  提交反馈
                    /hypstatus  查询 Hypixel 服务状态
                    /mojang  查询 Mojang 服务状态
                    /today  查看今日日历
                    /newyear  查看新年倒计时
                    /time [地区]  查询地区时间
                    /mcv  查询 Minecraft 最新版本
                    /mccape  查询 Minecraft 披风状态
                    /skbpack  查询 Skyblock 资源包版本
                    /sign  每日打卡签到
                    /bantrack [时间范围]  查询 Hypixel 封禁统计
                    """.trim());
            return true;
        }

        if (sender instanceof NapcatCommandSender napcat) {
            GroupMessage.forwardMessage(
                    napcat.getGroupId(),
                    napcatHelp(),
                    QQBot.BOT_NAME + " Napcat 帮助",
                    "查看 Napcat 端支持的指令",
                    "常用指令", "群功能", "管理指令"
            );
            return true;
        }

        return true;
    }


    private static List<MessageSegment> napcatHelp() {
        return List.of(
                GroupMessage.createTextNode("""
                        【Napcat 常用指令】
                        /help  查看本帮助
                        /ping  检查机器人响应
                        /hitokoto  获取随机一言
                        /chat [y|overall|@用户]  查看消息统计
                        /贡献名单  查看项目贡献名单
                        /search "关键词"  搜索群聊记录
                        """.trim()),
                GroupMessage.createTextNode("""
                        【按群配置开放的功能】
                        /gt <文字>  生成表情内容
                        /anan <文字> [模式]  生成表情内容
                        /py <文字>  转换为拼音
                        /emj <模式> [用户]  表情互动
                        /newyear  查看新年倒计时
                        /cl <Hypixel 链接>  领取每日奖励
                        /groupinfo  查看本群功能配置

                        实际可用项以本群当前功能配置为准。
                        """.trim()),
                GroupMessage.createTextNode("""
                        【管理与维护指令】
                        /recall、/rollback  撤回消息
                        /github、/autolike  管理群功能
                        /check-mc、/check-mojira  检查新闻源
                        /signall  执行自动签到
                        /info @用户、/debug  排查运行信息
                        /reboot  重启机器人

                        此部分仅对具有相应权限的用户开放。
                        """.trim())
        );
    }
}
