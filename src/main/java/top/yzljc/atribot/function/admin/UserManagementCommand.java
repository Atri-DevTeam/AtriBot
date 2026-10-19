package top.yzljc.atribot.function.admin;

import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atricore.auth.official.UnifiedRole;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.platform.Identifier;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName PermissionCommand
 * @Created_at 2026/05/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official
 */
public class UserManagementCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof QQCommandSender qq)) return true;

        if (!qq.hasPermission()) {
            qq.sendMessage(Identifier.NO_PERMISSION);
            return true;
        }

        if (args.length < 1) {
            qq.sendMessage("""
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
            args[1] = qq.getUserId();
        }

        if (action.equalsIgnoreCase("setrole")) {
            if (args.length < 3) {
                qq.sendMessage("用法：/permission setrole <unionOpenId> <role>");
                return true;
            }
            String userOpenId = args[1];
            UnifiedRole role = UnifiedRole.fromString(args[2]);
            OfficialUsers.UserData data = OfficialUsers.getData(userOpenId);
            boolean success = OfficialUsers.setPermissionGroup(userOpenId, role, data.permissions());
            qq.sendMessage(success ? "权限组设置成功" : "权限组设置失败");

            return true;
        }

        /*
         * add
         */

        if (action.equalsIgnoreCase("add")) {
            if (args.length < 3) {
                qq.sendMessage("用法：/permission add <unionOpenId> <permission1,permission2>");
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
                if (!OfficialUsers.addPermission(userOpenId, permission)) {
                    success = false;
                }
            }
            qq.sendMessage(success ? "权限节点添加成功" : "部分权限节点添加失败");
            return true;
        }

        if (action.equalsIgnoreCase("remove")) {
            if (args.length < 3) {
                qq.sendMessage("用法：/permission remove <unionOpenId> <permission1,permission2>");
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
                if (!OfficialUsers.removePermission(userOpenId, permission)) {
                    success = false;
                }
            }
            qq.sendMessage(success ? "权限节点删除成功" : "部分权限节点删除失败");
            return true;
        }

        if (action.equalsIgnoreCase("block")) {
            if (args.length < 2) {
                qq.sendMessage("用法：/perm block <add | remove> <unionOpenId>");
                return true;
            }
            if (args.length < 3) {
                qq.sendMessage("用法：/perm block <add | remove> <unionOpenId>");
                return true;
            }
            String userid = args[2];
            if (args[1].equalsIgnoreCase("add")) {
                qq.sendMessage(OfficialUsers.setBlocked(userid, true) ? "已拉黑用户 " + userid : "保存账号状态失败");
            } else if (args[1].equalsIgnoreCase("remove")) {
                qq.sendMessage(OfficialUsers.setBlocked(userid, false) ? "已解除拉黑用户 " + userid : "保存账号状态失败");
            } else {
                qq.sendMessage("用法：/perm block <add | remove> <unionOpenId>");
            }
            return true;
        }

        if (action.equalsIgnoreCase("ignore")) {

            if (args.length < 2) {
                qq.sendMessage("用法：/perm ignore <add | remove> <unionOpenId>");
                return true;
            }
            if (args.length < 3) {
                qq.sendMessage("用法：/perm ignore <add | remove> <unionOpenId>");
                return true;
            }
            String userid = args[2];
            if (args[1].equalsIgnoreCase("add")) {
                qq.sendMessage(OfficialUsers.setIgnored(userid, true) ? "已屏蔽用户 " + userid : "保存账号状态失败");
            } else if (args[1].equalsIgnoreCase("remove")) {
                qq.sendMessage(OfficialUsers.setIgnored(userid, false) ? "已解除屏蔽用户 " + userid : "保存账号状态失败");
            } else {
                qq.sendMessage("用法：/perm ignore <add | remove> <unionOpenId>");
            }
            return true;
        }

        if (action.equalsIgnoreCase("query")) {
            if (args.length < 2) {
                qq.sendMessage("用法：/permission query <unionOpenId>");
                return true;
            }
            String userOpenId = args[1];
            OfficialUsers.UserData data = OfficialUsers.getData(userOpenId);
            qq.sendMessage(TC.md(
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
                    )));
            return true;
        }
        return true;
    }
}
