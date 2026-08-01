package top.yzljc.atribot.function.official.minecraft;

import top.yzljc.atribot.chat.official.media.ImageType;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.general.impl.PreImageGenerate;
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

    public static boolean handleCapesCommand(CommandSender sender) {

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

        sender.sendMessage(data.url(), ImageType.URL);
        return true;
    }
}