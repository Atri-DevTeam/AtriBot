package top.yzljc.atribot.function.official;

import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupCommand
 * @Created_at 2026/05/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official
 */
public class GroupCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof QQCommandSender qq)) return true;

        if (!sender.hasPermission()) {
            qq.sendMessage("❌ 权限不足！只有管理员可以使用此命令！");
            return true;
        }

        if (args.length < 1) {
            qq.sendMessage("""
                    用法：
                    /ogroup whitelist add <groupOpenId>
                    /ogroup whitelist remove <groupOpenId>
                    /ogroup blacklist add <groupOpenId>
                    /ogroup blacklist remove <groupOpenId>
                    /ogroup query <groupOpenId>
                    """);
            return true;
        }

        String action = args[0];

        /*
         * whitelist
         */

        if (action.equalsIgnoreCase("whitelist")) {
            if (args.length < 2) {
                qq.sendMessage("用法：/ogroup whitelist <add|remove> <groupOpenId>");
                return true;
            }

            String subAction = args[1];

            if (subAction.equalsIgnoreCase("add")) {
                if (args.length < 3) {
                    qq.sendMessage("用法：/ogroup whitelist add <groupOpenId>");
                    return true;
                }
                String groupOpenId = resolveGroupOpenId(qq, args[2]);
                boolean success = OfficialGroups.addWhitelist(groupOpenId);
                qq.sendMessage(success ? "群白名单添加成功" : "群白名单添加失败");
                return true;
            }

            if (subAction.equalsIgnoreCase("remove")) {
                if (args.length < 3) {
                    qq.sendMessage("用法：/ogroup whitelist remove <groupOpenId>");
                    return true;
                }
                String groupOpenId = resolveGroupOpenId(qq, args[2]);
                boolean success = OfficialGroups.removeWhitelist(groupOpenId);
                qq.sendMessage(success ? "群白名单删除成功" : "群白名单删除失败");
                return true;
            }

            qq.sendMessage("用法：/ogroup whitelist <add|remove> <groupOpenId>");
            return true;
        }

        /*
         * blacklist
         */

        if (action.equalsIgnoreCase("blacklist")) {
            if (args.length < 2) {
                qq.sendMessage("用法：/ogroup blacklist <add|remove> <groupOpenId>");
                return true;
            }

            String subAction = args[1];

            if (subAction.equalsIgnoreCase("add")) {
                if (args.length < 3) {
                    qq.sendMessage("用法：/ogroup blacklist add <groupOpenId>");
                    return true;
                }
                String groupOpenId = resolveGroupOpenId(qq, args[2]);
                boolean success = OfficialGroups.setGroupBlacklisted(groupOpenId, true);
                qq.sendMessage(success ? "群黑名单添加成功" : "群黑名单添加失败");
                return true;
            }

            if (subAction.equalsIgnoreCase("remove")) {
                if (args.length < 3) {
                    qq.sendMessage("用法：/ogroup blacklist remove <groupOpenId>");
                    return true;
                }
                String groupOpenId = resolveGroupOpenId(qq, args[2]);
                boolean success = OfficialGroups.setGroupBlacklisted(groupOpenId, false);
                qq.sendMessage(success ? "群黑名单删除成功" : "群黑名单删除失败");
                return true;
            }

            qq.sendMessage("用法：/ogroup blacklist <add|remove> <groupOpenId>");
            return true;
        }

        /*
         * query
         */

        if (action.equalsIgnoreCase("query")) {

            if (args.length < 2) {
                qq.sendMessage("用法：/ogroup query <groupOpenId>");
                return true;
            }

            String groupOpenId = resolveGroupOpenId(qq, args[1]);

            OfficialGroups.GroupData data = OfficialGroups.getData(groupOpenId);

            qq.sendMessage(TC.md(
                    """
                            ### 群信息

                            ---

                            ### 🏘️ 群 OpenId
                            `%s`

                            ### ✅ 白名单状态
                            `%s`

                            ### 🚫 黑名单状态
                            `%s`

                            ### 👤 邀请人
                            `%s`

                            ### 🕒 加群时间
                            `%s`
                            """.formatted(
                            data.groupOpenId(),
                            data.isWhitelist() ? "是" : "否",
                            data.isBlacklisted() ? "是" : "否",
                            data.opMemberOpenId(),
                            data.joinedAt()
                    )));

            return true;
        }

        return true;
    }

    private static String resolveGroupOpenId(QQCommandSender sender, String groupOpenId) {
        if (groupOpenId.equalsIgnoreCase("this")) {
            return sender.getGroupId();
        }
        return groupOpenId;
    }
}
