package top.yzljc.atribot.function.command;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.*;
import top.yzljc.atribot.platform.Identifier;
import top.yzljc.atribot.service.ai.AiProvider;
import top.yzljc.atribot.service.ai.AiService;
import top.yzljc.atribot.service.runtime.ThreadManager;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName TimezoneCommand
 * @Created_at 2026/08/21
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official
 */
@Slf4j
public class TimezoneCommand implements CommandExecutor, SlashCommandExecutor {

    /** 默认时区，北京时间 */
    private static final String DEFAULT_ZONE_ID = "Asia/Shanghai";
    private static final String DEFAULT_DISPLAY_NAME = "北京时间";

    /** 常见国家/地区简写与 IANA 时区的直接映射，避免无意义的 AI 调用 */
    private static final Map<String, String> QUICK_ALIASES = buildQuickAliases();

    private static final String SYSTEM_PROMPT =
            "你是亚托莉，一个高性能机器人。" +
            "你的任务是把用户输入的国家、城市或时区描述转换成 Java IANA 时区 ID（例如 Asia/Shanghai、Europe/London、America/New_York、Asia/Tokyo）。" +
            "规则：\n" +
            "1. 只输出一行结果：IANA 时区 ID 与显示名称用英文竖线 '|' 分隔，左侧是 IANA 时区 ID，右侧是用户视角下的友好中文显示名（例如：北京|Asia/Shanghai 这种格式是错误的，正确输出形如 Asia/Shanghai|中国北京 或 Europe/London|英国伦敦）。\n" +
            "2. 优先取该国/该地区的代表性时区，例如美国输出 America/New_York，俄罗斯输出 Europe/Moscow。\n" +
            "3. 如果用户输入已经是合法 IANA 时区 ID（例如 Asia/Tokyo、UTC、Europe/Paris），右侧显示名尽量按原意翻译为中文。\n" +
            "4. 如果无法识别任何合理的时区，仅输出 UNKNOWN。\n" +
            "5. 禁止输出任何解释、代码块、标点前缀、空格或换行。";

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss EEE", Locale.SIMPLIFIED_CHINESE);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String query = String.join(" ", args).trim();

        ThreadManager.execute(() -> {
            try {
                ResolvedZone zone = resolveZone(query);
                if (zone == null) {
                    reply(sender, "亚托莉没能理解你输入的时区，请换个写法再试试～");
                    return;
                }

                ZonedDateTime now = ZonedDateTime.now(zone.zoneId);
                String text = "**" + "目标地区时间" + "**\n" +
                        "> 当前时间：" + now.format(DATE_FORMATTER) + "\n" +
                        "> 时区偏移：UTC" + now.getOffset().getId() + "\n\n" +
                        "小提示: " + Markdown.enterCommand("/time ", "/time [位置]") + "可以指定时区查询哦";

                switch (sender) {
                    case QQCommandSender qq -> qq.sendMessage(TC.md(text), false);
                    case QQGuildCommandSender guild -> guild.sendMessage(text);
//                    case NapcatCommandSender nc -> nc.sendMessage(text);
//                    case null, default -> sender.sendMessage(Identifier.UNSUPPORTED_PLATFORM);
                    default -> {}
                }
            } catch (Exception e) {
                log.error("TimezoneCommand 执行失败, query='{}'", query, e);
                sender.sendMessage(Identifier.HANDLER_ERROR);
            }
        });

        return true;
    }


    @Override
    public boolean onSlashCommand(DiscordCommandSender sender, Command command, String label, SlashCommandArguments args) {
        String query = args.getString("zone", "zh").trim();

        ThreadManager.execute(() -> {
            try {
                ResolvedZone zone = resolveZone(query);
                if (zone == null) {
                    sender.sendMessage("亚托莉没能理解你输入的时区，请换个写法再试试～");
                    return;
                }

                ZonedDateTime now = ZonedDateTime.now(zone.zoneId);
                String text = "**" + "目标地区时间" + "**\n" +
                        "> 当前时间：" + now.format(DATE_FORMATTER) + "\n" +
                        "> 时区偏移：UTC" + now.getOffset().getId() + "\n\n" +
                        "小提示: " + Markdown.enterCommand("/time ", "/time [位置]") + "可以指定时区查询哦";

                sender.sendMessage(text);

            } catch (Exception e) {
                log.error("TimezoneCommand 执行失败, query='{}'", query, e);
                sender.sendMessage(Identifier.HANDLER_ERROR);
            }
        });
        return true;
    }

    private static ResolvedZone resolveZone(String query) {
        if (query == null || query.isBlank()) {
            return ResolvedZone.of(DEFAULT_ZONE_ID, DEFAULT_DISPLAY_NAME);
        }

        String normalized = query.trim();
        String quick = QUICK_ALIASES.get(normalized.toLowerCase(Locale.ROOT));
        if (quick != null) {
            String[] parts = splitQuick(quick);
            return ResolvedZone.of(parts[0], parts[1]);
        }

        try {
            ZoneId zoneId = ZoneId.of(normalized);
            return ResolvedZone.of(zoneId.getId(), zoneId.getId());
        } catch (Exception ignored) {
            // 不是合法的 IANA ID，走 AI 兜底
        }

        AiService aiService = Atri.getInstance().getAiService();
        if (aiService == null) {
            return null;
        }

        String aiResponse = aiService.askWithSystemPrompt(AiProvider.PLAN_2,
                "用户输入：" + normalized,
                SYSTEM_PROMPT);
        return parseAiResponse(aiResponse);
    }

    private static ResolvedZone parseAiResponse(String response) {
        if (response == null || response.isBlank()) {
            return null;
        }
        if (response.contains("UNKNOWN")) {
            return null;
        }

        String cleaned = response.trim();
        int newline = cleaned.indexOf('\n');
        if (newline >= 0) {
            cleaned = cleaned.substring(0, newline).trim();
        }

        String zoneIdStr;
        String displayName;
        int sep = cleaned.indexOf('|');
        if (sep >= 0) {
            zoneIdStr = cleaned.substring(0, sep).trim();
            displayName = cleaned.substring(sep + 1).trim();
        } else {
            zoneIdStr = cleaned.trim();
            displayName = zoneIdStr;
        }

        if (zoneIdStr.isBlank()) {
            return null;
        }

        try {
            ZoneId zoneId = ZoneId.of(zoneIdStr);
            return ResolvedZone.of(zoneId.getId(),
                    displayName.isBlank() ? zoneId.getId() : displayName);
        } catch (Exception e) {
            log.warn("AI 返回的时区无法解析: '{}'", response);
            return null;
        }
    }

    private static String[] splitQuick(String value) {
        int sep = value.indexOf('|');
        if (sep < 0) {
            return new String[]{value, value};
        }
        return new String[]{value.substring(0, sep), value.substring(sep + 1)};
    }

    private static String safeQuery(String query) {
        return query == null ? "" : query;
    }

    private static void reply(CommandSender sender, String text) {
        switch (sender) {
            case QQCommandSender qq -> qq.sendMessage(TC.md(text), false);
            case QQGuildCommandSender guild -> guild.sendMessage(text);
//            case NapcatCommandSender nc -> nc.sendMessage(text);
//            case null, default -> sender.sendMessage(text);
            default -> {}
        }
    }

    private static Map<String, String> buildQuickAliases() {
        Map<String, String> map = new HashMap<>();
        map.put("cn", "Asia/Shanghai|中国北京");
        map.put("chinese", "Asia/Shanghai|中国北京");
        map.put("zh", "Asia/Shanghai|中国北京");
        map.put("china", "Asia/Shanghai|中国北京");
        map.put("中国", "Asia/Shanghai|中国北京");
        map.put("北京", "Asia/Shanghai|中国北京");
        map.put("中国大陆", "Asia/Shanghai|中国北京");
        map.put("hk", "Asia/Hong_Kong|中国香港");
        map.put("hongkong", "Asia/Hong_Kong|中国香港");
        map.put("香港", "Asia/Hong_Kong|中国香港");
        map.put("mo", "Asia/Macau|中国澳门");
        map.put("macau", "Asia/Macau|中国澳门");
        map.put("澳门", "Asia/Macau|中国澳门");
        map.put("tw", "Asia/Taipei|中国台北");
        map.put("taiwan", "Asia/Taipei|中国台北");
        map.put("台北", "Asia/Taipei|中国台北");
        map.put("台湾", "Asia/Taipei|中国台北");
        map.put("jp", "Asia/Tokyo|日本东京");
        map.put("japan", "Asia/Tokyo|日本东京");
        map.put("日本", "Asia/Tokyo|日本东京");
        map.put("东京", "Asia/Tokyo|日本东京");
        map.put("kr", "Asia/Seoul|韩国首尔");
        map.put("korea", "Asia/Seoul|韩国首尔");
        map.put("韩国", "Asia/Seoul|韩国首尔");
        map.put("首尔", "Asia/Seoul|韩国首尔");
        map.put("sg", "Asia/Singapore|新加坡");
        map.put("singapore", "Asia/Singapore|新加坡");
        map.put("新加坡", "Asia/Singapore|新加坡");
        map.put("my", "Asia/Kuala_Lumpur|马来西亚");
        map.put("malaysia", "Asia/Kuala_Lumpur|马来西亚");
        map.put("马来西亚", "Asia/Kuala_Lumpur|马来西亚");
        map.put("th", "Asia/Bangkok|泰国曼谷");
        map.put("thailand", "Asia/Bangkok|泰国曼谷");
        map.put("泰国", "Asia/Bangkok|泰国曼谷");
        map.put("vn", "Asia/Ho_Chi_Minh|越南胡志明市");
        map.put("vietnam", "Asia/Ho_Chi_Minh|越南胡志明市");
        map.put("越南", "Asia/Ho_Chi_Minh|越南胡志明市");
        map.put("id", "Asia/Jakarta|印度尼西亚雅加达");
        map.put("indonesia", "Asia/Jakarta|印度尼西亚雅加达");
        map.put("印尼", "Asia/Jakarta|印度尼西亚雅加达");
        map.put("ph", "Asia/Manila|菲律宾马尼拉");
        map.put("philippines", "Asia/Manila|菲律宾马尼拉");
        map.put("菲律宾", "Asia/Manila|菲律宾马尼拉");
        map.put("in", "Asia/Kolkata|印度加尔各答");
        map.put("india", "Asia/Kolkata|印度加尔各答");
        map.put("印度", "Asia/Kolkata|印度加尔各答");
        map.put("ae", "Asia/Dubai|阿联酋迪拜");
        map.put("uae", "Asia/Dubai|阿联酋迪拜");
        map.put("迪拜", "Asia/Dubai|阿联酋迪拜");
        map.put("sa", "Asia/Riyadh|沙特利雅得");
        map.put("saudi", "Asia/Riyadh|沙特利雅得");
        map.put("tr", "Europe/Istanbul|土耳其伊斯坦布尔");
        map.put("turkey", "Europe/Istanbul|土耳其伊斯坦布尔");
        map.put("土耳其", "Europe/Istanbul|土耳其伊斯坦布尔");
        map.put("ru", "Europe/Moscow|俄罗斯莫斯科");
        map.put("russia", "Europe/Moscow|俄罗斯莫斯科");
        map.put("俄罗斯", "Europe/Moscow|俄罗斯莫斯科");
        map.put("uk", "Europe/London|英国伦敦");
        map.put("gb", "Europe/London|英国伦敦");
        map.put("britain", "Europe/London|英国伦敦");
        map.put("英国", "Europe/London|英国伦敦");
        map.put("伦敦", "Europe/London|英国伦敦");
        map.put("fr", "Europe/Paris|法国巴黎");
        map.put("france", "Europe/Paris|法国巴黎");
        map.put("法国", "Europe/Paris|法国巴黎");
        map.put("巴黎", "Europe/Paris|法国巴黎");
        map.put("de", "Europe/Berlin|德国柏林");
        map.put("germany", "Europe/Berlin|德国柏林");
        map.put("德国", "Europe/Berlin|德国柏林");
        map.put("柏林", "Europe/Berlin|德国柏林");
        map.put("es", "Europe/Madrid|西班牙马德里");
        map.put("spain", "Europe/Madrid|西班牙马德里");
        map.put("西班牙", "Europe/Madrid|西班牙马德里");
        map.put("it", "Europe/Rome|意大利罗马");
        map.put("italy", "Europe/Rome|意大利罗马");
        map.put("意大利", "Europe/Rome|意大利罗马");
        map.put("nl", "Europe/Amsterdam|荷兰阿姆斯特丹");
        map.put("netherlands", "Europe/Amsterdam|荷兰阿姆斯特丹");
        map.put("荷兰", "Europe/Amsterdam|荷兰阿姆斯特丹");
        map.put("se", "Europe/Stockholm|瑞典斯德哥尔摩");
        map.put("sweden", "Europe/Stockholm|瑞典斯德哥尔摩");
        map.put("瑞典", "Europe/Stockholm|瑞典斯德哥尔摩");
        map.put("ch", "Europe/Zurich|瑞士苏黎世");
        map.put("switzerland", "Europe/Zurich|瑞士苏黎世");
        map.put("瑞士", "Europe/Zurich|瑞士苏黎世");
        map.put("pl", "Europe/Warsaw|波兰华沙");
        map.put("poland", "Europe/Warsaw|波兰华沙");
        map.put("波兰", "Europe/Warsaw|波兰华沙");
        map.put("ua", "Europe/Kyiv|乌克兰基辅");
        map.put("ukraine", "Europe/Kyiv|乌克兰基辅");
        map.put("乌克兰", "Europe/Kyiv|乌克兰基辅");
        map.put("gr", "Europe/Athens|希腊雅典");
        map.put("greece", "Europe/Athens|希腊雅典");
        map.put("希腊", "Europe/Athens|希腊雅典");
        map.put("us", "America/New_York|美国纽约");
        map.put("usa", "America/New_York|美国纽约");
        map.put("america", "America/New_York|美国纽约");
        map.put("united states", "America/New_York|美国纽约");
        map.put("美国", "America/New_York|美国纽约");
        map.put("纽约", "America/New_York|美国纽约");
        map.put("la", "America/Los_Angeles|美国洛杉矶");
        map.put("losangeles", "America/Los_Angeles|美国洛杉矶");
        map.put("洛杉矶", "America/Los_Angeles|美国洛杉矶");
        map.put("ny", "America/New_York|美国纽约");
        map.put("newyork", "America/New_York|美国纽约");
        map.put("sf", "America/Los_Angeles|美国旧金山");
        map.put("san", "America/Los_Angeles|美国旧金山");
        map.put("sanfrancisco", "America/Los_Angeles|美国旧金山");
        map.put("旧金山", "America/Los_Angeles|美国旧金山");
        map.put("芝加哥", "America/Chicago|美国芝加哥");
        map.put("chicago", "America/Chicago|美国芝加哥");
        map.put("ca", "America/Los_Angeles|美国洛杉矶");
        map.put("canada", "America/Toronto|加拿大多伦多");
        map.put("加拿大", "America/Toronto|加拿大多伦多");
        map.put("多伦多", "America/Toronto|加拿大多伦多");
        map.put("墨西哥", "America/Mexico_City|墨西哥墨西哥城");
        map.put("mx", "America/Mexico_City|墨西哥墨西哥城");
        map.put("br", "America/Sao_Paulo|巴西圣保罗");
        map.put("brazil", "America/Sao_Paulo|巴西圣保罗");
        map.put("巴西", "America/Sao_Paulo|巴西圣保罗");
        map.put("ar", "America/Argentina/Buenos_Aires|阿根廷布宜诺斯艾利斯");
        map.put("argentina", "America/Argentina/Buenos_Aires|阿根廷布宜诺斯艾利斯");
        map.put("阿根廷", "America/Argentina/Buenos_Aires|阿根廷布宜诺斯艾利斯");
        map.put("au", "Australia/Sydney|澳大利亚悉尼");
        map.put("australia", "Australia/Sydney|澳大利亚悉尼");
        map.put("澳大利亚", "Australia/Sydney|澳大利亚悉尼");
        map.put("澳洲", "Australia/Sydney|澳大利亚悉尼");
        map.put("悉尼", "Australia/Sydney|澳大利亚悉尼");
        map.put("nz", "Pacific/Auckland|新西兰奥克兰");
        map.put("newzealand", "Pacific/Auckland|新西兰奥克兰");
        map.put("新西兰", "Pacific/Auckland|新西兰奥克兰");
        map.put("za", "Africa/Johannesburg|南非约翰内斯堡");
        map.put("southafrica", "Africa/Johannesburg|南非约翰内斯堡");
        map.put("南非", "Africa/Johannesburg|南非约翰内斯堡");
        map.put("eg", "Africa/Cairo|埃及开罗");
        map.put("egypt", "Africa/Cairo|埃及开罗");
        map.put("埃及", "Africa/Cairo|埃及开罗");
        map.put("ng", "Africa/Lagos|尼日利亚拉各斯");
        map.put("nigeria", "Africa/Lagos|尼日利亚拉各斯");
        map.put("utc", "UTC|协调世界时");
        map.put("gmt", "GMT|格林威治标准时");
        map.put("零时区", "UTC|协调世界时");
        map.put("东八区", "Asia/Shanghai|东八区北京时间");
        map.put("东九区", "Asia/Tokyo|东九区东京时间");
        map.put("seattle", "America/Los_Angeles|美国洛杉矶");
        map.put("andy", "America/Los_Angeles|美国洛杉矶");
        return map;
    }

    private record ResolvedZone(ZoneId zoneId, String displayName) {
        static ResolvedZone of(String zoneId, String displayName) {
            return new ResolvedZone(ZoneId.of(zoneId), displayName);
        }
    }
}