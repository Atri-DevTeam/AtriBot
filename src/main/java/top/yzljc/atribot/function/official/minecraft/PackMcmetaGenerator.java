package top.yzljc.atribot.function.official.minecraft;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.CommandSender;

import java.util.LinkedHashMap;
import java.util.Map;

public final class PackMcmetaGenerator {

    private static final Map<String, int[]> VERSION_DATA = new LinkedHashMap<>();

    static {
        // [0] = resource pack format, [1] = data pack format

        // 1.21.3+ : 新格式（min_format / max_format）
        VERSION_DATA.put("26.3-snapshot-5", new int[]{93, 112});
        VERSION_DATA.put("26.3-snapshot-4", new int[]{92, 111});
        VERSION_DATA.put("26.3-snapshot-3", new int[]{91, 110});
        VERSION_DATA.put("26.3-snapshot-2", new int[]{90, 109});
        VERSION_DATA.put("26.3-snapshot-1", new int[]{89, 108});
        VERSION_DATA.put("26.2", new int[]{88, 107});
        VERSION_DATA.put("26.2-snapshot-8", new int[]{87, 106});
        VERSION_DATA.put("26.2-snapshot-7", new int[]{87, 105});
        VERSION_DATA.put("26.2-snapshot-6", new int[]{86, 105});
        VERSION_DATA.put("26.2-snapshot-5", new int[]{86, 104});
        VERSION_DATA.put("26.2-snapshot-4", new int[]{86, 103});
        VERSION_DATA.put("26.2-snapshot-3", new int[]{86, 102});
        VERSION_DATA.put("26.2-snapshot-2", new int[]{85, 101});
        VERSION_DATA.put("26.2-snapshot-1", new int[]{85, 101});
        VERSION_DATA.put("26.1.2", new int[]{84, 101});
        VERSION_DATA.put("26.1.1", new int[]{84, 101});
        VERSION_DATA.put("26.1", new int[]{84, 101});
        VERSION_DATA.put("26.1-snapshot-11", new int[]{83, 100});
        VERSION_DATA.put("26.1-snapshot-10", new int[]{82, 99});
        VERSION_DATA.put("26.1-snapshot-9", new int[]{81, 99});
        VERSION_DATA.put("26.1-snapshot-7", new int[]{81, 99});
        VERSION_DATA.put("26.1-snapshot-6", new int[]{80, 99});
        VERSION_DATA.put("26.1-snapshot-5", new int[]{79, 98});
        VERSION_DATA.put("26.1-snapshot-4", new int[]{78, 97});
        VERSION_DATA.put("26.1-snapshot-3", new int[]{78, 97});
        VERSION_DATA.put("26.1-snapshot-2", new int[]{77, 96});
        VERSION_DATA.put("26.1-snapshot-1", new int[]{76, 95});
        VERSION_DATA.put("1.21.11", new int[]{75, 94});
        VERSION_DATA.put("1.21.10", new int[]{69, 88});
        VERSION_DATA.put("1.21.9", new int[]{69, 87});
        VERSION_DATA.put("1.21.8", new int[]{64, 81});
        VERSION_DATA.put("1.21.7", new int[]{63, 80});
        VERSION_DATA.put("1.21.6", new int[]{63, 79});
        VERSION_DATA.put("1.21.5", new int[]{55, 71});
        VERSION_DATA.put("1.21.4", new int[]{46, 61});
        VERSION_DATA.put("1.21.3", new int[]{42, 57});
        VERSION_DATA.put("1.21.2", new int[]{41, 57});

        // 1.18.1 ~ 1.21.1 : 旧格式（pack_format / supported_formats）
        VERSION_DATA.put("1.21.1", new int[]{34, 48});
        VERSION_DATA.put("1.21", new int[]{33, 47});
        VERSION_DATA.put("1.20.6", new int[]{32, 41});
        VERSION_DATA.put("1.20.5", new int[]{31, 41});
        VERSION_DATA.put("1.20.4", new int[]{26, 26});
        VERSION_DATA.put("1.20.3", new int[]{22, 22});
        VERSION_DATA.put("1.20.2", new int[]{18, 18});
        VERSION_DATA.put("1.20.1", new int[]{15, 15});
        VERSION_DATA.put("1.20", new int[]{15, 15});
        VERSION_DATA.put("1.19.4", new int[]{12, 12});
        VERSION_DATA.put("1.19.3", new int[]{10, 10});
        VERSION_DATA.put("1.19.2", new int[]{9, 9});
        VERSION_DATA.put("1.19", new int[]{8, 8});
        VERSION_DATA.put("1.18.2", new int[]{8, 9});
        VERSION_DATA.put("1.18.1", new int[]{7, 7});
        VERSION_DATA.put("1.18", new int[]{7, 7});
        VERSION_DATA.put("1.17.1", new int[]{7, 7});
        VERSION_DATA.put("1.17", new int[]{7, 7});
        VERSION_DATA.put("1.16.5", new int[]{6, 6});
        VERSION_DATA.put("1.16.4", new int[]{6, 6});
        VERSION_DATA.put("1.16.3", new int[]{5, 5});
        VERSION_DATA.put("1.16.2", new int[]{5, 5});
        VERSION_DATA.put("1.16.1", new int[]{5, 5});
        VERSION_DATA.put("1.16", new int[]{5, 5});
        VERSION_DATA.put("1.15.2", new int[]{5, 5});
        VERSION_DATA.put("1.15.1", new int[]{4, 4});
        VERSION_DATA.put("1.15", new int[]{4, 4});
        VERSION_DATA.put("1.14.4", new int[]{4, 4});
        VERSION_DATA.put("1.14.3", new int[]{4, 4});
        VERSION_DATA.put("1.14.2", new int[]{4, 4});
        VERSION_DATA.put("1.14.1", new int[]{4, 4});
        VERSION_DATA.put("1.14", new int[]{4, 4});
        VERSION_DATA.put("1.13.2", new int[]{4, 4});
        VERSION_DATA.put("1.13.1", new int[]{4, 4});
        VERSION_DATA.put("1.13", new int[]{4, 4});

        // 1.12 及以下：仅资源包，无数据包（数据包从 1.14 引入）
        // [0] = resource pack format, [1] = 0 表示无数据包
        VERSION_DATA.put("1.12.2", new int[]{3, 0});
        VERSION_DATA.put("1.12.1", new int[]{3, 0});
        VERSION_DATA.put("1.12", new int[]{3, 0});
        VERSION_DATA.put("1.11.2", new int[]{3, 0});
        VERSION_DATA.put("1.11.1", new int[]{3, 0});
        VERSION_DATA.put("1.11", new int[]{3, 0});
        VERSION_DATA.put("1.10.2", new int[]{2, 0});
        VERSION_DATA.put("1.10", new int[]{2, 0});
        VERSION_DATA.put("1.9.4", new int[]{2, 0});
        VERSION_DATA.put("1.9.2", new int[]{2, 0});
        VERSION_DATA.put("1.9", new int[]{2, 0});
        VERSION_DATA.put("1.8.9", new int[]{1, 0});
        VERSION_DATA.put("1.8.1", new int[]{1, 0});
        VERSION_DATA.put("1.8", new int[]{1, 0});
        VERSION_DATA.put("1.7.10", new int[]{1, 0});
        VERSION_DATA.put("1.7.5", new int[]{1, 0});
        VERSION_DATA.put("1.7.4", new int[]{1, 0});
        VERSION_DATA.put("1.7.2", new int[]{1, 0});
        VERSION_DATA.put("1.6.4", new int[]{1, 0});
        VERSION_DATA.put("1.6.2", new int[]{1, 0});
        VERSION_DATA.put("1.6.1", new int[]{1, 0});
    }

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
            sender.sendMessage("用法: /mc pack <版本>\n示例: /mc pack 1.21.2");
            return;
        }

        String versionInput = args[1].replace("v", "").toLowerCase();
        int[] formats = VERSION_DATA.get(versionInput);

        if (formats == null) {
            sender.sendMessage("未找到版本 " + args[1] + " 的格式信息");
            return;
        }

        int resourcePack = formats[0];
        int dataPack = formats[1];

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
