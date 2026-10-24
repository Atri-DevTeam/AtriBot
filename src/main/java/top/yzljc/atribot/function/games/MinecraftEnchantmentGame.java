package top.yzljc.atribot.function.games;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.button.Button;
import top.yzljc.atricore.chat.official.button.ButtonStyle;
import top.yzljc.atricore.chat.official.button.ButtonType;
import top.yzljc.atricore.chat.official.button.Keyboard;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.service.runtime.ThreadManager;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.function.impl.ImageDTO;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.function.minecraft.MinecraftEnchantmentTable;
import top.yzljc.atribot.function.minecraft.MinecraftEnchantmentTable.Result;
import top.yzljc.atribot.function.minecraft.MinecraftEnchantmentTable.Preset;
import top.yzljc.atribot.service.request.OpenApi;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @Author YZ_Ljc_
 * @ClassName MinecraftEnchantmentGame
 * @Created_at 2026/10/23
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.games
 */
@Slf4j
public final class MinecraftEnchantmentGame {
    private static final MinecraftEnchantmentTable TABLE = MinecraftEnchantmentTable.load();

    private MinecraftEnchantmentGame() {
    }

    public static boolean onCommand(QQCommandSender sender, String[] args) {
        if (args.length == 0) {
            sendPresets(sender);
            return true;
        }
        Preset preset = args.length == 1 ? TABLE.findPreset(args[0]) : null;
        if (preset == null) {
            sender.sendMessage("仅支持原版附魔台可接受的物品，使用 /ec 选择，或发送 /ec 钻石镐 指定物品");
            return true;
        }
        ThreadManager.execute(() -> {
            Result result = TABLE.roll(preset, new Random(ThreadLocalRandom.current().nextInt()));
            ImageDTO image = null;
            try {
                image = PreImageGenerate.dumpMarkdown(OpenApi.get("bot.minecraft.enchantment"),
                        request(result), sender.getPlatform());
                if (image.isError()) {
                    log.warn("随机附魔图片生成失败，用户: {}，原因: {}", sender.getUserId(), image.errorMessage());
                }
            } catch (Exception e) {
                log.warn("随机附魔图片暂不可用，用户: {}", sender.getUserId(), e);
            }
            var keyboard = new Keyboard(List.of(List.of(
                    new Button("enchant_again", "再次附魔", "/ec " + preset.key(), ButtonStyle.BLUE, ButtonType.COMMAND),
                    new Button("enchant_presets", "选择物品", "/ec", ButtonStyle.GRAY, ButtonType.COMMAND))));
            sender.sendMessage(render(result, image).setKeyboard(keyboard, OfficialUsers.isUserUnsupportedKeyboard(sender.getUserId())));
        });
        return true;
    }

    private static void sendPresets(QQCommandSender sender) {
        var presets = TABLE.presets().stream()
                .sorted(Comparator.comparingInt(preset -> preset.name().codePointCount(0, preset.name().length())))
                .toList();
        var rows = new ArrayList<List<Button>>();
        for (int i = 0; i < presets.size();) {
            int end = Math.min(i + (i == 0 ? 4 : 3), presets.size());
            var row = new ArrayList<Button>();
            for (var preset : presets.subList(i, end)) {
                row.add(new Button("enchant_" + preset.key(), preset.name(), "/ec " + preset.key(), ButtonStyle.BLUE, ButtonType.COMMAND));
            }
            rows.add(row);
            i = end;
        }
        String text = "**Minecraft附魔模拟**\n\n请选择附魔物品";
        sender.sendMessage(new Markdown(text).setKeyboard(new Keyboard(rows), OfficialUsers.isUserUnsupportedKeyboard(sender.getUserId())));
    }

    private static Map<String, Object> request(Result result) {
        var enchantments = result.enchantments().stream().map(enchantment -> Map.<String, Object>of(
                "id", enchantment.id(), "name", enchantment.name(), "level", enchantment.level(),
                "maxLevel", enchantment.maxLevel(), "treasure", enchantment.treasure(), "curse", enchantment.curse())).toList();
        return Map.of("version", result.version(), "itemId", result.item().id(), "itemName", result.item().name(), "enchantments", enchantments);
    }

    private static Markdown render(Result result, ImageDTO image) {
        if (image != null && !image.isError() && image.width() > 0 && image.height() > 0) {
            return new Markdown(Markdown.img(image.url(), image.width(), image.height()) + "\n\n> ✨快来试试你的附魔运气吧！");
        }
        String text = "**随机附魔**\n\n";
        text += result.item().name() + "\n\n";
        for (var enchantment : result.enchantments()) {
            String suffix = enchantment.curse() ? "（诅咒）" : enchantment.treasure() ? "（宝藏）" : "";
            text += "> " + enchantment.displayName() + suffix + "\n";
        }
        return new Markdown(text);
    }
}
