package top.yzljc.atribot.functions.official;

import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.functions.official.permission.GroupList;
import top.yzljc.atribot.utils.FormatTools;

/**
 * @Author YZ_Ljc_
 * @ClassName WhitelistCommand
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official.function
 */
public class GroupCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (label.equals("0")) {
            return true;
        }

        if (!sender.isAdmin()) {
            sender.replyText(label, "❌ 权限不足！只有管理员可以使用此命令！");
            return true;
        }

        if (args.length < 1) {
            sender.replyText(label,
                    """
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
                sender.replyText(label, "用法：/ogroup whitelist <add|remove> <groupOpenId>");
                return true;
            }

            String subAction = args[1];

            if (subAction.equalsIgnoreCase("add")) {
                if (args.length < 3) {
                    sender.replyText(label, "用法：/ogroup whitelist add <groupOpenId>");
                    return true;
                }
                String groupOpenId = resolveGroupOpenId(sender, args[2]);
                boolean success = GroupList.addWhitelist(groupOpenId);
                sender.replyText(label, success ? "群白名单添加成功" : "群白名单添加失败");
                return true;
            }

            if (subAction.equalsIgnoreCase("remove")) {
                if (args.length < 3) {
                    sender.replyText(label, "用法：/ogroup whitelist remove <groupOpenId>");
                    return true;
                }
                String groupOpenId = resolveGroupOpenId(sender, args[2]);
                boolean success = GroupList.removeWhitelist(groupOpenId);
                sender.replyText(label, success ? "群白名单删除成功" : "群白名单删除失败");
                return true;
            }

            sender.replyText(label, "用法：/ogroup whitelist <add|remove> <groupOpenId>");
            return true;
        }

        /*
         * blacklist
         */

        if (action.equalsIgnoreCase("blacklist")) {
            if (args.length < 2) {
                sender.replyText(label, "用法：/ogroup blacklist <add|remove> <groupOpenId>");
                return true;
            }

            String subAction = args[1];

            if (subAction.equalsIgnoreCase("add")) {
                if (args.length < 3) {
                    sender.replyText(label, "用法：/ogroup blacklist add <groupOpenId>");
                    return true;
                }
                String groupOpenId = resolveGroupOpenId(sender, args[2]);
                boolean success = GroupList.setGroupBlacklisted(groupOpenId, true);
                sender.replyText(label, success ? "群黑名单添加成功" : "群黑名单添加失败");
                return true;
            }

            if (subAction.equalsIgnoreCase("remove")) {
                if (args.length < 3) {
                    sender.replyText(label, "用法：/ogroup blacklist remove <groupOpenId>");
                    return true;
                }
                String groupOpenId = resolveGroupOpenId(sender, args[2]);
                boolean success = GroupList.setGroupBlacklisted(groupOpenId, false);
                sender.replyText(label, success ? "群黑名单删除成功" : "群黑名单删除失败");
                return true;
            }

            sender.replyText(label, "用法：/ogroup blacklist <add|remove> <groupOpenId>");
            return true;
        }

        /*
         * query
         */

        if (action.equalsIgnoreCase("query")) {

            if (args.length < 2) {
                sender.replyText(label, "用法：/ogroup query <groupOpenId>");
                return true;
            }

            String groupOpenId = resolveGroupOpenId(sender, args[1]);

            GroupList.WhitelistData data = GroupList.getData(groupOpenId);

            sender.replyMarkdown(label,
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
                            FormatTools.formatTimestamp(data.timestamp())
                    ));

            return true;
        }

        return true;
    }

    private static String resolveGroupOpenId(CommandSender sender, String groupOpenId) {
        if (groupOpenId.equalsIgnoreCase("this")) {
            return sender.groupOpenId();
        }
        return groupOpenId;
    }
}