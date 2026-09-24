package top.yzljc.atribot.function.command;

import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.platform.Identifier;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.PlatformRole;
import top.yzljc.atribot.miniapp.service.MiniappGroupService;

/**
 * @Author YZ_Ljc_
 * @ClassName GroupSystemCommand
 * @Created_at 2026/09/20
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
public class GroupSystemCommand implements CommandExecutor {

    private final MiniappGroupService groups;
    public GroupSystemCommand() { this(MiniappGroupService.INSTANCE); }
    public GroupSystemCommand(MiniappGroupService groups) { this.groups = groups; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof QQCommandSender user) || user.isBot()) return true;
        if (!user.getPlatform().equals(Platform.OFFICIAL_GROUP)) {
            user.sendMessage(Identifier.UNSUPPORTED_SCENE);
            return true;
        }

        if (user.getRole() != PlatformRole.OWNER) {
            user.sendMessage("您不是所在群群主，无法绑定！");
            return true;
        }

        if (args.length != 1) {
            user.sendMessage("请发送网页上显示的完整群绑定指令。");
            return true;
        }

        var result = groups.verify(user.getUserId(), user.getGroupId(), args[0], user.getRole());
        user.sendMessage(switch (result) {
            case SUCCESS -> "验证完成，群组绑定成功！可在个人档案的群管理中查看。";
            case NO_PENDING -> "您当前没有需要验证的信息，请先在个人档案的群管理中发起绑定。";
            case EXPIRED -> "验证码已过期，请在群管理中重新生成。";
            case NOT_OWNER -> "您不是所在群群主，无法绑定！";
            case WRONG_GROUP -> "请在发起绑定时填写的目标群中发送验证指令。";
            case WRONG_CODE -> "验证码不正确，请复制网页上的完整指令。";
            case TOO_MANY_ATTEMPTS -> "验证码错误次数过多，请过期后重新申请。";
            case SAVE_FAILED -> "绑定暂未保存，请稍后重发验证指令。";
        });

        return true;
    }
}
