package top.yzljc.atribot.functions.official;

import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.functions.official.permission.PermissionGroup;
import top.yzljc.atribot.functions.official.permission.PermissionRole;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName PermissionCommand
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.official.function
 */
public class PermissionCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.isAdmin()) {
            sender.replyText(label, "❌ 权限不足！只有管理员可以使用此命令！");
            return true;
        }

        if (args.length < 1) {
            sender.replyText(label,
                    """
                            用法：
                            /permission setrole <unionOpenId> <role>
                            /permission add <unionOpenId> <permission1,permission2>
                            /permission remove <unionOpenId> <permission1,permission2>
                            /permission query <unionOpenId>
                            """);
            return true;
        }

        String action = args[0];

        if (args.length >= 2 && args[1].equalsIgnoreCase("me")) {
            args[1] = sender.unionOpenId();
        }

        /*
         * setrole
         */

        if (action.equalsIgnoreCase("setrole")) {

            if (args.length < 3) {
                sender.replyText(label,
                        "用法：/permission setrole <unionOpenId> <role>");
                return true;
            }

            String userOpenId = args[1];

            PermissionRole role = PermissionRole.fromString(args[2]);

            PermissionGroup.PermissionData data = PermissionGroup.getData(userOpenId);

            boolean success = PermissionGroup.setPermissionGroup(userOpenId, role, data.permissions());
            sender.replyText(label, success ? "权限组设置成功" : "权限组设置失败");

            return true;
        }

        /*
         * add
         */

        if (action.equalsIgnoreCase("add")) {

            if (args.length < 3) {
                sender.replyText(label,
                        "用法：/permission add <unionOpenId> <permission1,permission2>");
                return true;
            }

            String userOpenId = args[1];

            Set<String> permissions = new HashSet<>(Arrays.asList(args[2].split(",")));

            boolean success = true;

            for (String permission : permissions) {

                permission = permission.trim();

                if (permission.isBlank()) {
                    continue;
                }

                if (!PermissionGroup.addPermission(userOpenId, permission)) {
                    success = false;
                }
            }

            sender.replyText(label, success ? "权限节点添加成功" : "部分权限节点添加失败");

            return true;
        }

        /*
         * remove
         */

        if (action.equalsIgnoreCase("remove")) {

            if (args.length < 3) {
                sender.replyText(label,
                        "用法：/permission remove <unionOpenId> <permission1,permission2>");
                return true;
            }

            String userOpenId = args[1];

            Set<String> permissions = new HashSet<>(Arrays.asList(args[2].split(",")));

            boolean success = true;

            for (String permission : permissions) {

                permission = permission.trim();

                if (permission.isBlank()) {
                    continue;
                }

                if (!PermissionGroup.removePermission(userOpenId, permission)) {
                    success = false;
                }
            }

            sender.replyText(label,
                    success ? "权限节点删除成功" : "部分权限节点删除失败");

            return true;
        }

        /*
         * query
         */

        if (action.equalsIgnoreCase("query")) {

            if (args.length < 2) {
                sender.replyText(label, "用法：/permission query <unionOpenId>");
                return true;
            }

            String userOpenId = args[1];

            PermissionGroup.PermissionData data = PermissionGroup.getData(userOpenId);

            sender.replyMarkdown(label,
                    """
                            ### 权限信息

                            ---
                            
                            ### 👤 用户 OpenId
                            `%s`
                            
                            ### 🛡️ 角色组
                            `%s`
                            
                            ### 📋 权限节点
                            ```text
                            %s
                            ```
                            """.formatted(
                            data.userOpenId(),
                            data.role().name(),
                            data.permissions().isEmpty() ? "暂无权限节点" : String.join("\n", data.permissions())
                    ));

            return true;
        }

        return true;
    }
}