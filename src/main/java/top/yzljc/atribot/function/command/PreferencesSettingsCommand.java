package top.yzljc.atribot.function.command;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atribot.function.minecraft.HypixelRewardAutoClaim;
import top.yzljc.atribot.function.skyblockprice.SkyblockPriceAlertService;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atricore.service.runtime.ThreadManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * @Author YZ_Ljc_
 * @ClassName PreferencesSettingsCommand
 * @Created_at 2026/08/29
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
@Slf4j
public class PreferencesSettingsCommand implements CommandExecutor {
    private static final String HYPIXEL_REWARD_PREFIX = "/preferences hypixel_reward";
    private static final String UNSUPPORT_MARKDOWN_PREFIX = "/preferences unsupport_keyboard";
    private static final String SKYBLOCK_PRICE_PREFIX = "/preferences skyblock_price";

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender user)) return true;
        if (args.length == 0) {
            sender.sendMessage("未指定设置参数。");
            return true;
        }

        if ("unsupport_keyboard".equalsIgnoreCase(args[0])) {
            return handleUnsupportedKeyboard(user, args);
        }
        if ("skyblock_price".equalsIgnoreCase(args[0])) {
            return handleSkyblockPrice(user, args);
        }
        if (!"hypixel_reward".equalsIgnoreCase(args[0])) {
            sender.sendMessage("无效的设置参数。");
            return true;
        }
        if (args.length == 1) {
            sendPanel(user);
            return true;
        }

        String action = args[1].toLowerCase(Locale.ROOT);
        String userId = user.getUserId();
        switch (action) {
            case "enable", "on" -> HypixelRewardAutoClaim.setEnabled(userId, true);
            case "disable", "off" -> HypixelRewardAutoClaim.setEnabled(userId, false);
            case "mode" -> {
                if (args.length < 3 || !HypixelRewardAutoClaim.setFirstClaim(userId, args[2].toLowerCase(Locale.ROOT))) {
                    user.sendMessage("模式只能设置为 rarity（稀有度优先）或 priority（物品优先级优先）。");
                    return true;
                }
            }
            case "priority" -> {
                if (args.length < 4) {
                    user.sendMessage("用法：/preferences hypixel_reward priority <物品键> <1-30>");
                    return true;
                }
                String key = canonicalKey(args[2]);
                int priority;
                try {
                    priority = Integer.parseInt(args[3]);
                } catch (NumberFormatException e) {
                    user.sendMessage("优先级必须是 1-30 的整数。");
                    return true;
                }
                if (key == null || !HypixelRewardAutoClaim.setItemPriority(userId, key, priority)) {
                    user.sendMessage("物品键不存在，或优先级不在 1-30 范围内。");
                    return true;
                }
            }
            case "remove", "clear" -> {
                if (args.length < 3) {
                    user.sendMessage("用法：/preferences hypixel_reward remove <物品键>");
                    return true;
                }
                String key = canonicalKey(args[2]);
                if (key == null || !HypixelRewardAutoClaim.removeItemPriority(userId, key)) {
                    user.sendMessage("物品键不存在。");
                    return true;
                }
            }
            default -> {
                user.sendMessage("未知设置项，请使用下方按钮操作。");
                sendPanel(user);
                return true;
            }
        }
        sendPanel(user);
        return true;
    }

    private static boolean handleSkyblockPrice(QQCommandSender user, String[] args) {
        if (user.getPlatform() != Platform.OFFICIAL_C2C || user.isBot()) {
            user.sendMessage("[!] 该指令仅可在私聊中使用！");
            return true;
        }
        if (args.length != 3 || !("disable".equalsIgnoreCase(args[1]) || "off".equalsIgnoreCase(args[1]))) {
            user.sendMessage("用法：" + SKYBLOCK_PRICE_PREFIX + " disable <规则ID>");
            return true;
        }
        try {
            ThreadManager.execute(() -> {
                try {
                    SkyblockPriceAlertService.INSTANCE.stop(user.getUserId(), args[2]);
                    user.sendMessage("当前价格提醒规则已停用");
                } catch (SkyblockPriceAlertService.Problem e) {
                    user.sendMessage(switch (e.code) {
                        case "PRICE_ALERT_NOT_FOUND" -> "未找到你的这条价格提醒规则";
                        case "PRICE_PERMISSION_REQUIRED" -> "你没有价格提醒权限";
                        case "PRICE_ALERT_FRIEND_REQUIRED" -> "请先添加机器人为好友";
                        default -> "规则参数无效，请从提醒消息中点击停用";
                    });
                } catch (Exception e) {
                    log.warn("价格提醒停用失败: user={}, rule={}", user.getUserId(), args[2], e);
                    user.sendMessage("价格提醒停用失败，请稍后重试");
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            user.sendMessage("当前操作繁忙，请稍后重试");
        }
        return true;
    }

    private static boolean handleUnsupportedKeyboard(QQCommandSender user, String[] args) {
        if (args.length == 1) {
            sendUnsupportedKeyboardPanel(user);
            return true;
        }
        if (args.length != 2) {
            user.sendMessage("用法：" + UNSUPPORT_MARKDOWN_PREFIX + " <enable|disable>");
            return true;
        }

        boolean unsupported;
        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "enable", "on" -> unsupported = true;
            case "disable", "off" -> unsupported = false;
            default -> {
                user.sendMessage("用法：" + UNSUPPORT_MARKDOWN_PREFIX + " <enable|disable>");
                return true;
            }
        }
        if (!OfficialUsers.setUserUnsupportedKeyboard(user.getUserId(), unsupported)) {
            user.sendMessage("键盘偏好保存失败，请稍后重试。");
            return true;
        }
        sendUnsupportedKeyboardPanel(user);
        return true;
    }

    private static void sendUnsupportedKeyboardPanel(QQCommandSender user) {
        boolean unsupported = OfficialUsers.isUserUnsupportedKeyboard(user.getUserId());
        user.sendMessage(TC.md("**个人键盘偏好**\n\n"
                + "当设备不支持按钮消息时可启用此选项，以兼容部分操作\n\n"
                + "按钮兼容模式：" + (unsupported ? "已启用" : "已禁用") + "\n\n"
                + Markdown.enterCommand(UNSUPPORT_MARKDOWN_PREFIX + " enable", "启用") + " "
                + Markdown.enterCommand(UNSUPPORT_MARKDOWN_PREFIX + " disable", "禁用")));
    }

    private static String canonicalKey(String input) {
        if (input == null) return null;
        return HypixelRewardAutoClaim.knownItemKeys().stream()
                .filter(key -> key.equalsIgnoreCase(input))
                .findFirst()
                .orElse(null);
    }

    private static void sendPanel(QQCommandSender user) {
        HypixelRewardAutoClaim.Settings settings = HypixelRewardAutoClaim.getSettings(user.getUserId());
        StringBuilder message = new StringBuilder("**Hypixel 奖励自动领取设置**\n\n");
        message.append("自动领取：")
                .append(settings.enabled() ? "已开启 " : "已关闭 ")
                .append(Markdown.enterCommand(HYPIXEL_REWARD_PREFIX + (settings.enabled() ? " disable" : " enable"), settings.enabled() ? "关闭" : "开启"))
                .append("\n");

        message.append("优先模式：")
                .append(HypixelRewardAutoClaim.MODE_PRIORITY.equals(settings.firstClaim()) ? "物品优先级" : "稀有度优先")
                .append(" ")
                .append(Markdown.enterCommand(HYPIXEL_REWARD_PREFIX + " mode rarity", "稀有度优先"))
                .append(" ")
                .append(Markdown.enterCommand(HYPIXEL_REWARD_PREFIX + " mode priority", "物品优先级优先"))
                .append("\n\n");

        message.append("**游戏模式优先级**（点击后输入 1-30）\n");
        List<String> keys = new ArrayList<>(HypixelRewardAutoClaim.knownItemKeys());
        keys.sort(Comparator.comparing(key -> HypixelRewardCommand.itemNamespace.getOrDefault(key, key)));
        for (String key : keys) {
            String display = HypixelRewardCommand.itemNamespace.getOrDefault(key, key);
            if (!(key.equalsIgnoreCase("dust") || key.equalsIgnoreCase("souls") || key.equalsIgnoreCase("experience") ||
                    key.equalsIgnoreCase("adsense_token") || key.equalsIgnoreCase("housing_package"))) {
                display = display + "硬币";
            }
            int priority = settings.priorityOf(key);
            message.append("> ").append(display).append(" ").append(priority).append(" ")
                    .append(Markdown.enterCommand(HYPIXEL_REWARD_PREFIX + " priority " + key + " ", "设置"));
            if (priority > 0) {
                message.append(" ").append(Markdown.enterCommand(HYPIXEL_REWARD_PREFIX + " remove " + key, "清除"));
            }
            message.append("\n");
        }
        user.sendMessage(TC.md(message.toString()));
    }
}
