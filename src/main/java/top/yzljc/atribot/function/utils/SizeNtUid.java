package top.yzljc.atribot.function.utils;

import com.fasterxml.jackson.databind.JsonNode;
import io.javalin.http.Context;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.napcat.UserInformation;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.NapcatCommandSender;
import top.yzljc.atribot.platform.napcat.NapcatUser;
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
        if (!(sender instanceof NapcatCommandSender nc)) return true;
        if (nc.getMessage().getMentionedUsers().isEmpty()) {
            nc.sendMessage("请@目标账号！");
            return true;
        }
        if (!(nc.getMessage().getMentionedUsers().getFirst() instanceof NapcatUser mentioned)) return true;
        String info = "Target Account Info\n" +
                "Uin: " + mentioned.getUserId() + "\n" +
                "GroupId: " + nc.getGroupId() + "\n" +
                "Uid: " + mentioned.getData().path("ntUid").asText();
        var msgId = nc.sendMessage(info);
        Atri.getInstance().getScheduler().runTaskLater(() -> GroupMessage.recallMessage(msgId), 30 * 1000);
        return true;
    }

    public static void ntUidController(Context ctx) {
        var checked = UserInformation.getUserNtUid(ctx.bodyAsClass(JsonNode.class).path("uin").asText(null));
        switch (checked) {
            case "-1" -> ctx.json(Result.fail(400, "QQ 号码号段无效，请检查后重试。"));
            case "-2" -> ctx.json(Result.fail(404, "未查询到对应的 NT UID。"));
            case "-3" -> ctx.json(Result.fail(403, "查询 NT UID 失败，请先添加 970717559 为好友后重试。"));
            default -> ctx.json(Result.success(Map.of("ntUid", checked)));
        }
    }
}
