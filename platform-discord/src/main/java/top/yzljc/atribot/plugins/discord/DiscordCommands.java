package top.yzljc.atribot.plugins.discord;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.chat.discord.DiscordEmbed;
import top.yzljc.atribot.function.command.BanTrackCommand;
import top.yzljc.atribot.function.command.FeedbackCommand;
import top.yzljc.atribot.function.command.TimezoneCommand;
import top.yzljc.atribot.function.impl.ImageDTO;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.function.minecraft.McVersionImpl;
import top.yzljc.atribot.function.minecraft.SkyblockPackCheckImpl;
import top.yzljc.atribot.platform.Identifier;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atribot.utils.FormatTools;
import top.yzljc.atribot.utils.tools.Alert;

/**
 * @Author YZ_Ljc_
 * @ClassName DiscordCommands
 * @Created_at 2026/10/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.plugins.discord
 */
@Slf4j
public final class DiscordCommands implements SlashCommandExecutor {
    @Override
    public boolean onCommand(SlashCommandSender sender, Command command, String label, SlashCommandArguments args) {
        return switch (command.getName()) {
            case "help" -> handleHelp(sender, command, label, args);
            case "hitokoto" -> handleHitokoto(sender, command, label, args);
            case "today" -> handleCalendar(sender, command, label, args);
            case "newyear" -> handleNewYear(sender, command, label, args);
            case "mojang" -> handleMojang(sender, command, label, args);
            case "mcv" -> handleVersion(sender, command, label, args);
            case "mccape" -> handleCape(sender, command, label, args);
            case "skbpack" -> handlePack(sender, command, label, args);
            case "whoami" -> handleWhoAmI(sender, command, label, args);
            case "feedback" -> handleFeedback(sender, command, label, args);
            case "hypstatus" -> handleHypStatus(sender, command, label, args);
            case "wz" -> handleWizards(sender, command, label, args);
            case "zs" -> handleZombies(sender, command, label, args);
            case "bantrack" -> handleBanTrack(sender, command, label, args);
            case "time" -> new TimezoneCommand().onCommand(sender, command, label, args);
            default -> false;
        };
    }

    private boolean handleHelp(SlashCommandSender slashSender, Command command, String label,
                                  SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;
        sender.sendEmbed(new DiscordEmbed()
                .title("Discord 指令帮助")
                .description("""
                        `/zs <player>` Hypixel 僵尸末日数据
                        `/wz <player>` Hypixel 法师掘战数据
                        `/hypstatus` Hypixel 服务状态
                        `/mojang` Mojang 服务状态
                        `/mcv` Minecraft 最新版本
                        `/mccape` Minecraft 披风状态
                        `/skbpack` Skyblock 资源包版本
                        `/today` 查看今日日历
                        `/newyear` 新年倒计时
                        `/time [zone]` 地区时间
                        `/bantrack [window]` Hypixel 封禁统计
                        `/whoami` 当前 Discord 场景信息
                        `/feedback <content>` 提交反馈
                        """.trim())
                .footer("Discord 查询指令不会读取 QQ 绑定信息"));
        return true;
    }

    private boolean handleHitokoto(SlashCommandSender slashSender, Command command, String label, SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;

        var d = PreImageGenerate.dump(OpenApi.get("bot.hitokoto"), Map.of(), sender.getPlatform());
        if (d.isError()) {
            sender.sendMessage(d.errorMessage());
            return true;
        }
        sender.sendEmbed(new DiscordEmbed().image(d.url()));
        return true;
    }

    private boolean handleCalendar(SlashCommandSender slashSender, Command command, String label, SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;

        ImageDTO data = PreImageGenerate.dump(OpenApi.get("bot.calendar"), Map.of("system", false), sender.getPlatform());
        if (data.isError()) {
            String errMsg = data.errorMessage();
            sender.sendMessage("获取日历图片失败: " + errMsg);
            Alert.notify("日历图片获取失败: " + errMsg);
            log.warn("获取日历图片失败: {}", errMsg);
            return true;
        }

        String today = "> 现在是北京时间" + FormatTools.formatTimestampMilli(System.currentTimeMillis());
        sender.sendEmbed(new DiscordEmbed().title(today).image(data.url()));
        return true;
    }

    private boolean handleNewYear(SlashCommandSender slashSender, Command command, String label,
                                  SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;
        var data = PreImageGenerate.dump(OpenApi.get("bot.happy-new-year"), Map.of(), sender.getPlatform());
        if (data.isError() || data.url() == null) {
            sender.sendMessage(data.isError() ? data.errorMessage() : "新年倒计时数据获取失败，请稍后重试。");
            return true;
        }
        sender.sendEmbed(new DiscordEmbed().title("新年倒计时").image(data.url()));
        return true;
    }

    private boolean handleMojang(SlashCommandSender slashSender, Command command, String label, SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;

        sender.sendMessage("正在查询Minecraft验证服务器服务状态，请稍候...");
        var data = PreImageGenerate.dump(OpenApi.get("bot.minecraft.status"), Map.of(), sender.getPlatform());

        if (data.isError()) {
            String errMsg = data.errorMessage();
            sender.sendMessage("在查询Minecraft验证服务器服务状态时出现错误: " + errMsg);
            return true;
        }

        sender.sendEmbed(new DiscordEmbed().image(data.url()));

        return true;
    }

    private boolean handleVersion(SlashCommandSender slashSender, Command command, String label,
                                  SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;
        Map<String, McVersionImpl.VersionInfo> versions = McVersionImpl.checkCurrentVersion();
        McVersionImpl.VersionInfo release = versions.get("release");
        McVersionImpl.VersionInfo snapshot = versions.get("snapshot");

        DiscordEmbed embed = new DiscordEmbed()
                .title("Minecraft 最新版本信息")
                .field("正式版", formatVersion(release), false)
                .field("快照版", formatVersion(snapshot), false);
        sender.sendEmbed(embed);
        return true;
    }

    private boolean handleCape(SlashCommandSender slashSender, Command command, String label,
                                  SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;
        var data = PreImageGenerate.dump(OpenApi.get("bot.minecraft.capes"), Map.of(), sender.getPlatform());
        if (data.isError() || data.url() == null) {
            sender.sendMessage(data.isError() ? data.errorMessage() : "Minecraft 披风数据获取失败，请稍后重试。");
            return true;
        }
        sender.sendEmbed(new DiscordEmbed().title("Minecraft 披风状态").image(data.url()));
        return true;
    }

    private boolean handlePack(SlashCommandSender slashSender, Command command, String label,
                                  SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;
        var result = SkyblockPackCheckImpl.query(sender.getPlatform());
        if (result.image().isError()) sender.sendMessage(result.image().errorMessage());
        else sender.sendEmbed(new DiscordEmbed().title("Hypixel Skyblock 资源包")
                .description("上一次的更新时间为 " + FormatTools.formatTimestampMilli(result.lastUpdated()))
                .image(result.image().url()));
        return true;
    }

    private boolean handleWhoAmI(SlashCommandSender slashSender, Command command, String label,
                                  SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;
        DiscordEmbed embed = new DiscordEmbed()
                .title("当前场景下用户信息")
                .field("Discord 用户", sender.getUsername(), true)
                .field("Discord 用户 ID", sender.getUserId(), true)
                .field("文字频道 ID", sender.getChannelId(), false)
                .field("用户身份", String.valueOf(sender.getRole()), true)
                .footer("相关信息仅供问题排查和鉴权使用");
        sender.sendEmbed(embed);
        return true;
    }

    private boolean handleFeedback(SlashCommandSender slashSender, Command command, String label,
                                  SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;
        String content = args.getString("content", "").trim();
        if (content.isBlank()) {
            sender.sendEphemeralMessage("请提供反馈或建议的内容。");
            return true;
        }
        return FeedbackCommand.submitFeedback(sender, sender.getChannelId(), content);
    }

    private boolean handleHypStatus(SlashCommandSender slashSender, Command command, String label, SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;
        sender.sendMessage("正在检查Hypixel服务器状态，请稍候...");
        var data = PreImageGenerate.dump(OpenApi.get("bot.hypixel.status"), Map.of(), sender.getPlatform());
        if (data.isError()) {
            sender.sendMessage(data.errorMessage());
            return true;
        }
        if (data.url() == null) {
            sender.sendMessage(Identifier.HANDLER_ERROR);
            return true;
        }
        sender.sendEmbed(new DiscordEmbed().image(data.url()));
        return true;
    }

    private boolean handleWizards(SlashCommandSender slashSender, Command command, String label,
                                  SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;
        String player = args.getString("player", "").trim();
        if (player.isBlank()) {
            sender.sendEphemeralMessage("请指定要查询的 Minecraft 玩家名或 UUID。");
            return true;
        }

        sender.sendMessage("正在查询相关数据，请稍等片刻...");
        var data = PreImageGenerate.dump(OpenApi.get("bot.hypixel.tnt-wizards"), Map.of("player", player), sender.getPlatform());
        if (data.isError() || data.url() == null) {
            sender.sendMessage(data.isError() ? data.errorMessage() : "在执行操作时出现错误，请稍后重试。");
            return true;
        }
        sender.sendEmbed(new DiscordEmbed().title("Hypixel 法师决战数据").image(data.url()));
        return true;
    }

    private boolean handleZombies(SlashCommandSender slashSender, Command command, String label,
                                  SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;
        String player = args.getString("player", "").trim();
        if (player.isBlank()) {
            sender.sendEphemeralMessage("请指定要查询的 Minecraft 玩家名或 UUID。");
            return true;
        }

        sender.sendMessage("正在查询相关数据，请稍等片刻...");
        var data = PreImageGenerate.dump(OpenApi.get("bot.hypixel.zombies"), Map.of("player", player), sender.getPlatform());
        if (data.isError() || data.url() == null) {
            sender.sendMessage(data.isError() ? data.errorMessage() : "在执行操作时出现错误，请稍后重试。");
            return true;
        }
        sender.sendEmbed(new DiscordEmbed().title("Hypixel 僵尸末日数据").image(data.url()));
        return true;
    }

    private boolean handleBanTrack(SlashCommandSender slashSender, Command command, String label, SlashCommandArguments args) {
        if (!(slashSender instanceof DiscordCommandSender sender)) return true;
        return handleBanQuery(sender, args);
    }

    private static boolean handleBanQuery(DiscordCommandSender sender, SlashCommandArguments args) {
        if (BanTrackCommand.isCoolingDown(sender)) {
            return true;
        }

        String window = "30min";
        if (args.getOption("window") != null) {
            window = args.getString("window");
        }

        if (!BanTrackCommand.isValidType(window)) {
            sender.sendMessage("参数错误: 查询时长必须为 10 分钟至 3 个月之间的时间段，支持的单位包括分钟 (min/m)、小时 (h)、天 (d)、周 (w) 和月 (mo)");
            return true;
        }

        ImageDTO data = PreImageGenerate.dump(OpenApi.get("bot.hypixel.bantracker") + "?" + System.currentTimeMillis(), Map.of("window", window), sender.getPlatform());
        if (data.isError()) {
            sender.sendMessage(data.errorMessage());
            return true;
        }
        if (data.url() == null) {
            sender.sendMessage("数据获取失败: 发生未知错误!");
            return true;
        }

        String tip = "当前查询范围: " + window
                + "\n调用方式: /bantracker <30min|1h|3h|24h|7d|30d>";
        sender.sendEmbed(new DiscordEmbed().image(data.url()).title(tip));
        return true;
    }

    private static String formatVersion(McVersionImpl.VersionInfo version) {
        if (version == null) return "未知";
        return version.id() + "\n发布于 " + FormatTools.formatIsoTime(version.releaseTime()).trim();
    }
}
