package top.yzljc.atribot.function.official.minecraft;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.platform.Identifier;

import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName MinecraftCapes
 * @Created_at 2026/07/09
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.minecraft
 */
public final class MinecraftCapes {

    public static boolean handleCapesCommand(QQCommandSender sender) {

        var data = PreImageGenerate.dump(ResourcesProperties.MINECRAFT_CAPES_API, Map.of());
        if (data == null) {
            sender.sendMessage(Identifier.HANDLER_ERROR);
            return true;
        }
        if (data.isError()) {
            sender.sendMessage(data.errorMessage());
            return true;
        }
        if (data.url() == null) {
            sender.sendMessage(Identifier.HANDLER_ERROR);
            return true;
        }

        sender.sendMessage(ImageComponent.imageOf(data.url()));
        return true;
    }
}
