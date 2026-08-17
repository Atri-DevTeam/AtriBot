package top.yzljc.atribot.function.official;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.impl.PreImageGenerate;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName EarthOnline
 * @Created_at 2026/07/30
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official
 */
public class EarthOnline implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender instanceof QQCommandSender qq) {
            var d = PreImageGenerate.dump(ResourcesProperties.EARTH_ONLINE_API, Map.of());
            if (!d.isError()) {
                qq.sendMessage(ImageComponent.imageOf(d.url()).setText("欢迎来到地球ONLINE!"));
            } else {
                qq.sendMessage(d.errorMessage());
            }
        }

        return true;
    }
}
