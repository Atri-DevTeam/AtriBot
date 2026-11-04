package top.yzljc.atribot.function.command;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.function.astronomy.AstronomyData;
import top.yzljc.atribot.function.astronomy.AstronomyRegion;
import top.yzljc.atribot.function.astronomy.AstronomyService;
import top.yzljc.atribot.function.impl.ImageDTO;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atricore.chat.official.Markdown;
import top.yzljc.atricore.chat.official.TC;
import top.yzljc.atricore.chat.official.button.Button;
import top.yzljc.atricore.chat.official.button.ButtonStyle;
import top.yzljc.atricore.chat.official.button.ButtonType;
import top.yzljc.atricore.chat.official.button.Keyboard;
import top.yzljc.atricore.command.Command;
import top.yzljc.atricore.command.CommandExecutor;
import top.yzljc.atricore.command.CommandSender;
import top.yzljc.atricore.command.QQCommandSender;
import top.yzljc.atricore.platform.Platform;
import top.yzljc.atricore.service.runtime.ThreadManager;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * @Author YZ_Ljc_
 * @ClassName AstronomyCommand
 * @Created_at 2026/10/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
public final class AstronomyCommand implements CommandExecutor {
    private static final Logger LOG = LoggerFactory.getLogger(AstronomyCommand.class);
    private static final String SETTING = "astronomy_region";
    private static final String REGIONS = "东北 / 华北 / 华东 / 华中 / 华南 / 西南 / 西北 / 新疆";
    private final AstronomyService service = new AstronomyService();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender qq)
                || (qq.getPlatform() != Platform.OFFICIAL_GROUP && qq.getPlatform() != Platform.OFFICIAL_C2C))
            return true;
        ThreadManager.execute(() -> execute(qq, args));
        return true;
    }

    private void execute(QQCommandSender sender, String[] args) {
        if (args.length == 1 && args[0].equals("区域")) {
            send(sender, "请选择要查询的区域", null, false);
            return;
        }
        if (args.length == 1 && args[0].equals("设置")) {
            send(sender, "请选择你的默认区域", null, true);
            return;
        }
        boolean setting = args.length == 2 && args[0].equals("设置");
        AstronomyRegion region = null;
        if (args.length == 0) {
            var saved = OfficialUsers.getUserSetting(sender.getUserId(), SETTING);
            if (saved != null && saved.isTextual()) {
                try {
                    region = AstronomyRegion.valueOf(saved.textValue());
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (region == null) {
                send(sender, "请选择要查询的区域", null, false);
                return;
            }
        } else if (setting || args.length == 1) {
            region = AstronomyRegion.parse(args[setting ? 1 : 0]);
        }
        if (region == null) {
            send(sender, "仅支持固定区域，不支持具体省市、地址或经纬度\n\n" + REGIONS
                    + "\n\n查询：/astro 华东\n设置默认区域：/astro 设置 华东", null, false);
            return;
        }
        if (setting) {
            boolean saved = OfficialUsers.setUserSetting(sender.getUserId(), SETTING, region.name());
            send(sender, saved ? "默认区域已设为 **" + region.getDisplayName() + "**\n\n发送 /astro 查询今日参考天象"
                    : "默认区域保存失败，请稍后重试", region, false);
            return;
        }
        AstronomyData data;
        try {
            data = service.today(region);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return;
        } catch (Exception failure) {
            LOG.warn("区域天象查询失败，区域: {}，原因: {}", region.getDisplayName(), failure.getMessage());
            send(sender, "天象数据暂时获取失败，请稍后再试", region, false);
            return;
        }
        ImageDTO image = null;
        try {
            image = PreImageGenerate.dumpMarkdown(OpenApi.get("bot.astronomy"), data.renderRequest(region), sender.getPlatform());
            if (image.isError()) LOG.warn("天象卡片生成失败: {}", image.errorMessage());
        } catch (Exception failure) {
            LOG.warn("天象卡片暂不可用: {}", failure.getMessage());
        }
        String body;
        if (image != null && !image.isError() && image.width() > 0 && image.height() > 0) {
            body = Markdown.img(image.url(), image.width(), image.height()) + "\n\n";
        } else {
            body = fallback(data, region);
        }
        body += "> 数据所显示的地区信息仅供参考";
        send(sender, body, region, false);
    }

    private static String fallback(AstronomyData d, AstronomyRegion region) {
        String illumination = d.moonIllumination() == null ? "暂无" : String.format(Locale.ROOT, "%.2f%%", d.moonIllumination());
        return "代表位置：" + region.getReferenceName() + "\n\n"
                + "**太阳**\n日出 " + AstronomyData.time(d.sunrise()) + " · 日落 " + AstronomyData.time(d.sunset())
                + "\n太阳中天 " + AstronomyData.time(d.solarNoon()) + " · 昼长 " + d.daylight() + "\n\n"
                + "**月亮**\n月升 " + AstronomyData.time(d.moonrise()) + " · 月落 " + AstronomyData.time(d.moonset())
                + "\n月相 " + d.phaseName() + " · 月面照明 " + illumination + "\n\n"
                + "**黄金时刻**\n晨间 " + d.morningGoldenHour().display() + " · 傍晚 " + d.eveningGoldenHour().display() + "\n\n"
                + "**蓝调时刻**\n晨间 " + d.morningBlueHour().display() + " · 傍晚 " + d.eveningBlueHour().display() + "\n\n";
    }

    private static void send(QQCommandSender sender, String text, AstronomyRegion selected, boolean chooseDefault) {
        var rows = new ArrayList<List<Button>>();
        var regions = AstronomyRegion.values();
        for (int i = 0; i < regions.length; i += 4) {
            var row = new ArrayList<Button>();
            for (int j = i; j < Math.min(i + 4, regions.length); j++) {
                var region = regions[j];
                var button = new Button("astro_" + region.name(), region.getDisplayName(),
                        "/astro " + (chooseDefault ? "设置 " : "") + region.getDisplayName(),
                        region == selected ? ButtonStyle.BLUE : ButtonStyle.GRAY, ButtonType.COMMAND);
                if (chooseDefault) button.setAllowedOpenIds(List.of(sender.getUserId()));
                row.add(button);
            }
            rows.add(row);
        }
        if (!chooseDefault) {
            rows.add(List.of(new Button("astro_default", "设置我的位置", "/astro 设置",
                    ButtonStyle.GRAY, ButtonType.COMMAND)));
        }
        sender.sendMessage(TC.md(text).setKeyboard(new Keyboard(rows)));
    }
}
