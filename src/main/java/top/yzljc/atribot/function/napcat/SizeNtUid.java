package top.yzljc.atribot.function.napcat;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.UserInformation;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.webui.Result;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName SizeNtUid
 * @Created_at 2026/06/08
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.napcat
 */
public class SizeNtUid implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender.getPlatform() != Platform.NAPCAT_GROUP) return true;
        if (sender.getMentions().isEmpty()) {
            sender.sendMessage("请@目标账号！");
            return true;
        }
        String info = "Target Account Info\n" +
                "Uin: " + sender.getMentions().getFirst().getUserId() + "\n" +
                "GroupId: " + sender.getGroupId() + "\n" +
                "Uid: " + sender.getMentions().getFirst().getData().path("ntUid").asText();
        var msgId = sender.sendMessage(info);
        Atri.getInstance().getScheduler().runTaskLater(() -> GroupMessage.recallMessage(msgId), 30 * 1000);
        return true;
    }

    public static void ntUidController(Context ctx) {
        var checked = UserInformation.getUserNtUid(ctx.bodyAsClass(JsonNode.class).path("uin").asText(null));
        switch (checked) {
            case "-1" -> ctx.json(Result.custom(201, "非法号段", null));
            case "-2" -> ctx.json(Result.custom(201, "无结果", null));
            case "-3" -> ctx.json(Result.custom(201, "获取失败，不是好友关系，请添加好友970717559", null));
            default -> ctx.json(Result.success(Map.of("ntUid", checked)));
        }
    }
}
