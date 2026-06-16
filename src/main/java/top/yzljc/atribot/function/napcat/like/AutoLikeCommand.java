package top.yzljc.atribot.function.napcat.like;

import top.yzljc.atribot.chat.napcat.UserInformation;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.platform.Platform;

import java.util.List;
import java.util.stream.Collectors;

public class AutoLikeCommand implements CommandExecutor {

    private static final String AUTOLIKE_GROUP_ID = "818804507";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.NAPCAT_GROUP) return true;
        if (!sender.getGroupId().equals(AUTOLIKE_GROUP_ID)) {
            sender.sendMessage("该命令仅在指定群开放");
            return true;
        }
        if (args == null || args.length < 1) {
            return false;
        }
        String sub = args[0].trim().toLowerCase();
        switch (sub) {
            case "add" -> {
                long targetUid;
                if (sender.hasPermission() && args.length >= 2) {
                    try { targetUid = Long.parseLong(args[1].trim()); }
                    catch (NumberFormatException e) { sender.sendMessage("请填写有效的 QQ 号"); return true; }
                } else {
                    targetUid = Long.parseLong(sender.getUserId());
                }
                LikeUser.addToAutoLikeList(targetUid);
                sender.sendMessage("已将 " + UserInformation.getUserName(String.valueOf(targetUid)) + "(" + targetUid + ") 加入自动点赞列表");
                return true;
            }
            case "remove" -> {
                long targetUid;
                if (sender.hasPermission() && args.length >= 2) {
                    try { targetUid = Long.parseLong(args[1].trim()); }
                    catch (NumberFormatException e) { sender.sendMessage("请填写有效的 QQ 号"); return true; }
                } else {
                    targetUid = Long.parseLong(sender.getUserId());
                }
                LikeUser.removeFromAutoLikeList(targetUid);
                sender.sendMessage("已将 " + UserInformation.getUserName(String.valueOf(targetUid)) + "(" + targetUid + ") 从自动点赞列表移除");
                return true;
            }
            case "list" -> {
                List<Long> list = LikeUser.getAutoLikeList();
                if (list.isEmpty()) { sender.sendMessage("自动点赞列表为空"); return true; }
                String oneLine = list.stream().map(uid -> {
                    String name = UserInformation.getUserName(String.valueOf(uid));
                    return name != null ? name + "(" + uid + ")" : String.valueOf(uid);
                }).collect(Collectors.joining(", "));
                sender.sendMessage("自动点赞列表：" + oneLine);
                return true;
            }
        }
        if (sender.hasPermission() && "test".equals(sub)) {
            LikeUser.likeAllinList();
            return true;
        }
        return false;
    }
}
