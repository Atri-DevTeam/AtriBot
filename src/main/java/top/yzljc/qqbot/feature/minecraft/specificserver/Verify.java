package top.yzljc.qqbot.feature.minecraft.specificserver;

import top.yzljc.qqbot.AtriBot;
import top.yzljc.qqbot.command.Command;
import top.yzljc.qqbot.command.CommandExecutor;
import top.yzljc.qqbot.command.CommandSender;

/**
 * @Author YZ_Ljc_
 * @ClassName Variety
 * @Created_at 2026/03/30
 * @Project Yzljc-QQ-Bot
 * @Package top.yzljc.qqbot.feature.minecraft.specificserver
 */
public class Verify implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        var uid = sender.userId();
        if (args.length < 1) {
            return false;
        }
        String code = args[0].trim();
        var result = AtriBot.getMinecraftVerify().sendRequest(uid, code);
        switch (result) {
            case 200 -> sender.reply("✅ 绑定成功!", false);
            case 100 -> sender.reply("⚠️ 绑定失败：你游戏内的账号已经绑定过其他 QQ 了！", false);
            case 400 -> sender.reply("❌ 验证码错误或已过期(有效时间5分钟)，请在游戏内重新生成", false);
            case 500 -> sender.reply("🔧 服务器未开启或网络异常，请稍后再试!", false);
            default -> sender.reply("❓ 未知错误代码: " + result, false);
        }
        return true;
    }
}