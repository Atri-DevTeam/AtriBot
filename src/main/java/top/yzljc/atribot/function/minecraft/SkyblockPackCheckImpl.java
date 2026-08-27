package top.yzljc.atribot.function.minecraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.ImageComponent;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.discord.DiscordEmbed;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.DiscordCommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.QQGuildCommandSender;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.function.impl.PreImageGenerate;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.service.taskscheduler.TaskPlan;
import top.yzljc.atribot.service.taskscheduler.ScheduleMode;
import top.yzljc.atribot.service.taskscheduler.ScheduledTask;
import top.yzljc.atribot.service.taskscheduler.TaskSchedule;
import top.yzljc.atribot.utils.FormatTools;
import top.yzljc.sakuraba_ema.guild.ChannelPosts;
import top.yzljc.sakuraba_ema.utils.ForumCode;

import java.io.File;
import java.io.IOException;
import java.util.*;

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

    public boolean onCommand(DiscordCommandSender sender) {
        check(sender);
        return true;
    }

    @Override
    public TaskSchedule schedule() {
        return new TaskPlan().setMode(ScheduleMode.hourly);
    }

    @Override
    public void run() {
        check(null);
    }

    private void check(CommandSender sender) {
        var d = HttpService.sendGetRequest(ResourcesProperties.SKB_VERSION_CHECK);
        if (d == null || d.isEmpty() || d.path("packs").isMissingNode()) {
            log.error("获取Hypixel Skyblock资源包信息失败");
            return;
        }
        Map<String, Object> request = new HashMap<>();

        int i = 0;
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

                String updateTime = FormatTools.formatTimestampMilli(updateTimeRaw);
                List<Pack> versions = new ArrayList<>();
                for (var t : node.path("versions")) {
                    int packVersionNum = t.path("packFormat").asInt(-1);
                    String hash = t.path("hash").asText("null");
                    String versionName = PackVersion.getVersionNameByResourcePackVersion(packVersionNum, true);
                    if (versionName == null) {
                        versionName = "资源包版本: " + packVersionNum;
                    }
                    versions.add(new Pack(versionName, hash));
                    i++;
                }
                request.put("lastUpdated", updateTime);
                request.put("deployId", deployId);
                request.put("versions", versions);
                break;
            }
        }

        var r = PreImageGenerate.dump(ResourcesProperties.SKB_PACK_VERSION_API, request);
        if (sender != null) {
            if (r.url() == null || r.isError()) {
                sender.sendMessage(r.errorMessage());
                return;
            }
            var lastUpdatedTime = "上一次的更新时间为 " + FormatTools.formatTimestampMilli(lastUpdateTime);
            ImageComponent image = ImageComponent.imageOf(r.url()).setText(lastUpdatedTime);
            if (sender instanceof QQCommandSender qqSender) {
                qqSender.sendMessage(image);
            } else if (sender instanceof QQGuildCommandSender guildSender) {
                guildSender.sendMessage(image);
            } else if (sender instanceof DiscordCommandSender discordSender) {
                discordSender.sendEmbed(new DiscordEmbed()
                        .title("Hypixel Skyblock 资源包")
                        .description(lastUpdatedTime)
                        .image(r.url()));
            }
        } else {
            if (r.url() == null || r.isError()) {
                log.error("Skyblock 资源包信息生成图片失败: {}", r.errorMessage());
                return;
            }

            var groups = OfficialGroups.enabledGroups("skyblock_resource_pack");
            var users = OfficialUsers.enabledUsers("skyblock_resource_pack");
            var lastUpdatedTime = "Skyblock资源包已在近期更新，上一次的更新时间为 " + FormatTools.formatTimestampMilli(tmpLastTime);
            for (var gid : groups) {
                GroupChat.sendMessage(gid, ImageComponent.imageOf(r.url()).setText(lastUpdatedTime));
            }
            for (var uid : users) {
                C2CChat.sendMessage(uid, ImageComponent.imageOf(r.url()).setText(lastUpdatedTime));
            }
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
