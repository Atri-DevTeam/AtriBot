package top.yzljc.qqbot.official.function;

import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;
import top.yzljc.qqbot.official.permission.GroupList;
import top.yzljc.qqbot.utils.FormatTools;

/**
 * @Author YZ_Ljc_
 * @ClassName WhitelistCommand
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official.function
 */
public class WhitelistCommand implements CommandExecutor {

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
                            /whitelist add <groupOpenId>
                            /whitelist remove <groupOpenId>
                            /whitelist query <groupOpenId>
                            """);
            return true;
        }

        String action = args[0];

        /*
         * add
         */

        if (action.equalsIgnoreCase("add")) {

            if (args.length < 2) {
                sender.replyText(label,
                        "用法：/whitelist add <groupOpenId>");
                return true;
            }

            String groupOpenId = args[1];

            if (groupOpenId.equalsIgnoreCase("this")) {
                groupOpenId = sender.groupOpenId();
            }

            boolean success = GroupList.addWhitelist(groupOpenId);

            sender.replyText(label, success ? "群白名单添加成功" : "群白名单添加失败");

            return true;
        }

        /*
         * remove
         */

        if (action.equalsIgnoreCase("remove")) {

            if (args.length < 2) {
                sender.replyText(label, "用法：/whitelist remove <groupOpenId>");
                return true;
            }

            String groupOpenId = args[1];

            if (groupOpenId.equalsIgnoreCase("this")) {
                groupOpenId = sender.groupOpenId();
            }

            boolean success = GroupList.removeWhitelist(groupOpenId);

            sender.replyText(label,
                    success ? "群白名单删除成功" : "群白名单删除失败");

            return true;
        }

        /*
         * query
         */

        if (action.equalsIgnoreCase("query")) {

            if (args.length < 2) {
                sender.replyText(label,
                        "用法：/whitelist query <groupOpenId>");
                return true;
            }

            String groupOpenId = args[1];

            if (groupOpenId.equalsIgnoreCase("this")) {
                groupOpenId = sender.groupOpenId();
            }

            GroupList.WhitelistData data =
                    GroupList.getData(groupOpenId);

            sender.replyMarkdown(label,
                    """
                            ### 群信息

                            ---
                            
                            ### 🏘️ 群 OpenId
                            `%s`
                            
                            ### ✅ 白名单状态
                            `%s`
                            
                            ### 👤 邀请人
                            `%s`
                            
                            ### 🕒 加群时间
                            `%s`
                            """.formatted(
                            data.groupOpenId(),
                            data.isWhitelist() ? "是" : "否",
                            data.opMemberOpenId(),
                            FormatTools.formatTimestamp(data.timestamp())
                    ));

            return true;
        }

        return true;
    }
}