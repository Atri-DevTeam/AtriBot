package top.yzljc.qqbot.utils.draft;

import top.yzljc.qqbot.botservice.userinfo.GetUserInfo;
import top.yzljc.qqbot.command.impl.Command;
import top.yzljc.qqbot.command.impl.CommandExecutor;
import top.yzljc.qqbot.command.impl.CommandSender;
import top.yzljc.qqbot.feature.LikeUser;

import java.util.List;
import java.util.stream.Collectors;

public class AutoLikeCommand implements CommandExecutor {

    private static final long AUTOLIKE_GROUP_ID = 818804507L;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getGroupId() != AUTOLIKE_GROUP_ID) {
            sender.reply("该命令仅在指定群开放", false);
            return true;
        }
        if (args == null || args.length < 1) {
            return false;
        }
        String sub = args[0].trim().toLowerCase();
        if ("add".equals(sub)) {
            long targetUid;
            if (sender.isAdmin() && args.length >= 2) {
                try {
                    targetUid = Long.parseLong(args[1].trim());
                } catch (NumberFormatException e) {
                    sender.reply("请填写有效的 QQ 号", false);
                    return true;
                }
            } else {
                targetUid = sender.getUserId();
            }
            LikeUser.addToAutoLikeList(targetUid);
            sender.reply("已加入自动点赞列表", false);
            return true;
        }
        if ("remove".equals(sub)) {
            long targetUid;
            if (sender.isAdmin() && args.length >= 2) {
                try {
                    targetUid = Long.parseLong(args[1].trim());
                } catch (NumberFormatException e) {
                    sender.reply("请填写有效的 QQ 号", false);
                    return true;
                }
            } else {
                targetUid = sender.getUserId();
            }
            LikeUser.removeFromAutoLikeList(targetUid);
            sender.reply("已移出自动点赞列表", false);
            return true;
        }
        if ("list".equals(sub)) {
            List<Long> list = LikeUser.getAutoLikeList();
            if (list.isEmpty()) {
                sender.reply("自动点赞列表为空", false);
                return true;
            }
            String oneLine = list.stream()
                    .map(uid -> {
                        String name = GetUserInfo.getUserName(uid);
                        return name != null ? name + "(" + uid + ")" : String.valueOf(uid);
                    })
                    .collect(Collectors.joining(", "));
            sender.reply("自动点赞列表：" + oneLine, false);
            return true;
        }
        if (sender.isAdmin() && "test".equals(sub)){
            LikeUser.likeAllinList();
            return true;
        }
        return false;
    }
}
