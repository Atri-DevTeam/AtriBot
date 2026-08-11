package top.yzljc.atribot.function.official.minecraft;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.QQCommandSender;

public final class PackMcmetaGenerator {

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

    public static void handle(QQCommandSender sender, String[] args) {
        if (args.length < 2) {
            sender.sendMessage("用法: /mc pack <版本>\n示例: /mc pack 1.21.2");
            return;
        }

        String versionInput = args[1].replace("v", "").toLowerCase();
        PackVersion version = PackVersion.getPackVersion(versionInput);

        if (version == null) {
            sender.sendMessage("未找到版本 " + args[1] + " 的格式信息");
            return;
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
                    Markdown.img(top.yzljc.atribot.configuration.ResourcesProperties.GRASS_BLOCK_IMG, 24, 24),
                    args[1],
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
                    Markdown.img(top.yzljc.atribot.configuration.ResourcesProperties.GRASS_BLOCK_IMG, 24, 24),
                    args[1],
                    resourcePack, dataPack,
                    rpJson, dpJson
            );
        }

        sender.sendMessage(TC.md(result));
    }

    private static String formatJson(String description, int minFormat, int maxFormat, boolean isNew) {
        if (isNew) {
            return NEW_FORMAT_TEMPLATE.formatted(description, minFormat, maxFormat);
        }
        return OLD_FORMAT_TEMPLATE.formatted(description, minFormat, minFormat, maxFormat);
    }
}
