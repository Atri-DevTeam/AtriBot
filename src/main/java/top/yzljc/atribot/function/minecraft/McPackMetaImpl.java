package top.yzljc.atribot.function.minecraft;

import top.yzljc.atribot.configuration.RS;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.CommandSender;

public final class McPackMetaImpl {

    private static final String NEW_FORMAT_TEMPLATE = """
            {
              "pack": {
                "description": "%s",
                "min_format": %d,
                "max_format": %d
              }
            }""";

    private static final String OLD_FORMAT_TEMPLATE = """
            {
              "pack": {
                "description": "%s",
                "pack_format": %d,
                "supported_formats": [%d, %d]
              }
            }""";

    private static final String RESOURCE_ONLY_TEMPLATE = """
            {
              "pack": {
                "description": "%s",
                "pack_format": %d
              }
            }""";

    public static void handle(CommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("用法: /mctool pack <版本>\n示例: /mctool pack 1.21.2");
            return;
        }

        String result = describe(args[1], sender instanceof QQCommandSender);
        if (sender instanceof QQCommandSender qq) qq.sendMessage(TC.md(result));
    }

    /**
     * @param versionName Minecraft 版本名称
     * @param withIcon 是否包含 QQ Markdown 草方块图标
     * @return 资源包格式说明，未找到版本时返回错误提示
     */
    public static String describe(String versionName, boolean withIcon) {
        String versionInput = versionName.replace("v", "").toLowerCase();
        PackVersion version = PackVersion.getPackVersion(versionInput);

        if (version == null) {
            return "未找到版本相关版本的的格式信息，请尝试重新输入！";
        }

        int resourcePack = version.resourcePackVersion();
        int dataPack = version.dataPackVersion();

        String rpJson;
        if (dataPack == 0) {
            rpJson = RESOURCE_ONLY_TEMPLATE.formatted("My Resource Pack", resourcePack);
        } else {
            rpJson = formatJson("My Resource Pack", resourcePack, resourcePack, true);
        }

        String result;
        if (dataPack == 0) {
            // 1.12 及以下：仅资源包
            result = """
                    **%s Minecraft %s 格式版本**

                    资源包格式: `%d`（该版本无数据包）

                    **资源包 pack.mcmeta**
                    ```json
                    %s
                    ```""".formatted(
                    withIcon ? Markdown.img(RS.get("resource.image.grass-block"), 24, 24) : "",
                    versionName,
                    resourcePack,
                    rpJson
            );
        } else {
            String dpJson = formatJson("My Data Pack", dataPack, dataPack, false);
            result = """
                    **%s Minecraft %s 格式版本**

                    资源包格式: `%d` | 数据包格式: `%d`

                    **资源包**
                    ```json
                    %s
                    ```

                    **数据包**
                    ```json
                    %s
                    ```""".formatted(
                    withIcon ? Markdown.img(RS.get("resource.image.grass-block"), 24, 24) : "",
                    versionName,
                    resourcePack, dataPack,
                    rpJson, dpJson
            );
        }

        return result;
    }

    private static String formatJson(String description, int minFormat, int maxFormat, boolean isNew) {
        if (isNew) {
            return NEW_FORMAT_TEMPLATE.formatted(description, minFormat, maxFormat);
        }
        return OLD_FORMAT_TEMPLATE.formatted(description, minFormat, minFormat, maxFormat);
    }
}
