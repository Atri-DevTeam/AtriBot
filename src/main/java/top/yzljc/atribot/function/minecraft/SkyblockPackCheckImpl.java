package top.yzljc.atribot.function.minecraft;

import java.io.File;
import java.io.IOException;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.QQGuildCommandSender;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.configuration.RS;
import top.yzljc.atribot.function.background.BackgroundService;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.function.impl.ImageDTO;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.function.tasks.pushtask.PushTask;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.request.OpenApi;
import top.yzljc.atribot.service.taskscheduler.ScheduleMode;
import top.yzljc.atribot.service.taskscheduler.ScheduledTask;
import top.yzljc.atribot.service.taskscheduler.TaskPlan;
import top.yzljc.atribot.service.taskscheduler.TaskSchedule;
import top.yzljc.atribot.utils.FormatTools;
import top.yzljc.sakuraba_ema.guild.ChannelPosts;
import top.yzljc.sakuraba_ema.utils.ForumCode;

/**
 * @Author YZ_Ljc_
 * @ClassName SkyblockResourcePackChecker
 * @Created_at 2026/07/26
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.minecraft
 */
@Slf4j
public final class SkyblockPackCheckImpl implements ScheduledTask {

    private static final ObjectMapper jsonMapper = new ObjectMapper();
    private static final String CACHE_FILE = Properties.SKYBLOCK_CACHE;

    private static volatile long lastUpdateTime = 0;
    private static volatile String lastDeployId = null;

    static {
        loadCache();
    }

    public boolean onCommand(QQCommandSender sender) {
        check(sender);
        return true;
    }

    public boolean onCommand(QQGuildCommandSender sender) {
        check(sender);
        return true;
    }


    @Override
    public TaskSchedule schedule() {
        return new TaskPlan().setMode(ScheduleMode.hourly);
    }

    /**
     * @param platform 图片接收平台，用于选择图片分发方式
     * @return 资源包成图及更新时间；查询不会更改定时推送的更新游标
     */
    public static PackImage query(Platform platform) {
        var data = HttpService.sendGetRequest(RS.get("request.hypixel.resource-packs"));
        if (data != null) {
            for (var pack : data.path("packs")) {
                if (pack.path("id").asText().equals("SkyBlock")) {
                    return new PackImage(PreImageGenerate.dump(OpenApi.get("bot.hypixel.skyblock.resource-pack"),
                            renderRequest(pack), platform), pack.path("lastUpdated").asLong());
                }
            }
        }
        return new PackImage(new ImageDTO(null, 0, 0, "获取 Hypixel Skyblock 资源包信息失败，请稍后重试。", null), 0);
    }

    public record PackImage(ImageDTO image, long lastUpdated) {}

    private static Map<String, Object> renderRequest(JsonNode pack) {
        List<Pack> versions = new ArrayList<>();
        for (var version : pack.path("versions")) {
            int format = version.path("packFormat").asInt(-1);
            String name = PackVersion.getVersionNameByResourcePackVersion(format, true);
            versions.add(new Pack(name == null ? "资源包版本: " + format : name, version.path("hash").asText("null")));
        }
        return Map.of("lastUpdated", FormatTools.formatTimestampMilli(pack.path("lastUpdated").asLong()),
                "deployId", pack.path("deployId").asText("unknown"), "versions", versions);
    }

    @Override
    public void run() {
        check(null);
    }

    private void check(CommandSender sender) {
        var d = HttpService.sendGetRequest(RS.get("request.hypixel.resource-packs"));
        if (d == null || d.isEmpty() || d.path("packs").isMissingNode()) {
            log.error("获取Hypixel Skyblock资源包信息失败");
            if (sender != null) sender.sendMessage("获取 Hypixel Skyblock 资源包信息失败，请稍后重试。");
            return;
        }
        Map<String, Object> request = new HashMap<>();

        long tmpLastTime = lastUpdateTime;
        for (var node : d.path("packs")) {
            if (node.path("id").asText("default").equals("SkyBlock")) {
                long updateTimeRaw = node.path("lastUpdated").asLong(0);
                String deployId = node.path("deployId").asText("unknown");

                if (updateTimeRaw == lastUpdateTime && Objects.equals(deployId, lastDeployId)) {
                    if (sender == null) {
                        log.info("Skyblock 资源包无更新");
                        return;
                    }
                }

                lastUpdateTime = updateTimeRaw;
                lastDeployId = deployId;
                saveCache();

                request.putAll(renderRequest(node));
                break;
            }
        }

        if (sender instanceof QQCommandSender) BackgroundService.applyApprovedBackground(request, sender.getUserId());
        String renderUrl = OpenApi.get("bot.hypixel.skyblock.resource-pack");
        var r = sender == null
                ? PreImageGenerate.dump(renderUrl, request)
                : PreImageGenerate.dump(renderUrl, request, sender.getPlatform());
        if (sender != null) {
            if (r.url() == null || r.isError()) {
                sender.sendMessage(r.errorMessage());
                return;
            }
            var lastUpdatedTime = "上一次的更新时间为 " + FormatTools.formatTimestampMilli(lastUpdateTime);
            ImageComponent image = ImageComponent.imageOf(r.url()).setText(lastUpdatedTime);
            switch (sender) {
                case QQCommandSender qqSender -> qqSender.sendMessage(image);
                case QQGuildCommandSender guildSender -> guildSender.sendMessage(image);
                default -> {
                }
            }
        } else {
            if (r.url() == null || r.isError()) {
                log.error("Skyblock 资源包信息生成图片失败: {}", r.errorMessage());
                return;
            }

//            var groups = OfficialGroups.enabledGroups("skyblock_resource_pack");
//            var users = OfficialUsers.enabledUsers("skyblock_resource_pack");
            var lastUpdatedTime = "Skyblock资源包已在近期更新，上一次的更新时间为 " + FormatTools.formatTimestampMilli(tmpLastTime);
//            for (var gid : groups) {
//                GroupChat.sendMessage(gid, ImageComponent.imageOf(r.url()).setText(lastUpdatedTime));
//            }
//            for (var uid : users) {
//                C2CChat.sendMessage(uid, ImageComponent.imageOf(r.url()).setText(lastUpdatedTime));
//            }

            PushTask.push("hyp_alpha_news", ImageComponent.imageOf(r.url()).setText(lastUpdatedTime));

            Markdown md = TC.md(
                    lastUpdatedTime + "\n\n" + Markdown.img(r.url(), r.width(), r.height())
            );
            ChannelPosts.sendMessage(ForumCode.GUILD_ID, ForumCode.HYPIXEL_SKYBLOCK_NEWS.getChannelId(), "[资源包更新] Skyblock资源包已更新", md);
        }
    }

    private static void loadCache() {
        File file = new File(CACHE_FILE);
        if (!file.exists()) {
            saveCache();
            log.info("Skyblock 缓存文件不存在，已创建初始缓存文件");
            return;
        }
        try {
            JsonNode root = jsonMapper.readTree(file);
            lastUpdateTime = root.path("updateTime").asLong(0);
            lastDeployId = root.path("deployId").asText(null);
            log.info("Skyblock 缓存加载成功, updateTime={}, deployId={}", lastUpdateTime, lastDeployId);
        } catch (IOException e) {
            log.warn("Skyblock 缓存文件读取失败，将重置缓存: {}", e.getMessage());
            saveCache();
        }
    }

    private static void saveCache() {
        try {
            File file = new File(CACHE_FILE);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) {
                parent.mkdirs();
            }
            Map<String, Object> cache = new LinkedHashMap<>();
            cache.put("updateTime", lastUpdateTime);
            cache.put("deployId", lastDeployId);
            jsonMapper.writerWithDefaultPrettyPrinter().writeValue(file, cache);
            log.info("Skyblock 缓存已保存, updateTime={}, deployId={}", lastUpdateTime, lastDeployId);
        } catch (IOException e) {
            log.warn("Skyblock 缓存保存失败: {}", e.getMessage());
        }
    }

    private record Pack(String versionName, String hash) {
    }
}
