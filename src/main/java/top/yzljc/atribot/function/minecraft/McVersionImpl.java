package top.yzljc.atribot.function.minecraft;

import top.yzljc.atribot.configuration.ResourcesProperties;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.napcat.GroupInformation;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.QQGuildCommandSender;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName VersionInfoImpl
 * @Created_at 2026/06/04
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official.minecraft
 */
@Slf4j
public final class McVersionImpl implements ScheduledTask {

    private static final String VERSION_API = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final File RECORD_FILE = new File("minecraft_version.json");

    public static Map<String, VersionInfo> checkCurrentVersion() {
        LatestVersions latest = getLatestVersion();

        Map<String, VersionInfo> result = new HashMap<>();
        result.put("release", latest.release());
        result.put("snapshot", latest.snapshot());

        return result;
    }

    public static LatestVersions getLatestVersion() {
        JsonNode response = HttpService.sendGetRequest(VERSION_API);

        if (response == null) {
            throw new IllegalStateException("Failed to fetch version information");
        }

        JsonNode latest = response.get("latest");

        String releaseId = latest.get("release").asText();

        String snapshotId = latest.get("snapshot").asText();

        VersionInfo releaseInfo = null;
        VersionInfo snapshotInfo = null;

        for (JsonNode node : response.get("versions")) {

            String id = node.get("id").asText();

            if (releaseId.equals(id)) {
                releaseInfo = toVersionInfo(node);
            }

            if (snapshotId.equals(id)) {
                snapshotInfo = toVersionInfo(node);
            }

            if (releaseInfo != null && snapshotInfo != null) {
                break;
            }
        }

        if (releaseInfo == null || snapshotInfo == null) {
            throw new IllegalStateException("Failed to get latest versions");
        }

        return new LatestVersions(
                releaseInfo,
                snapshotInfo
        );
    }

    private static VersionInfo toVersionInfo(JsonNode node) {
        return new VersionInfo(
                node.get("id").asText(),
                node.get("type").asText(),
                node.get("url").asText(),
                node.get("time").asText(),
                node.get("releaseTime").asText(),
                node.get("sha1").asText(),
                node.get("complianceLevel").asInt()
        );
    }

    private static void saveVersions(LatestVersions versions) {
        try {
            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(RECORD_FILE, versions);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static LatestVersions loadVersions() {
        if (!RECORD_FILE.exists()) {
            return null;
        }

        try {
            return OBJECT_MAPPER.readValue(RECORD_FILE, LatestVersions.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static boolean onCommand(QQCommandSender sender) {
        Map<String, VersionInfo> versions = checkCurrentVersion();
        VersionInfo release = versions.get("release");
        VersionInfo snapshot = versions.get("snapshot");

        String versionInfo = """
                %s **Minecraft最新版本信息**

                正式版: %s

                > 发布于 %s

                快照版: %s

                > 发布于 %s

                > %s
                """.formatted(
                Markdown.img(ResourcesProperties.GRASS_BLOCK_IMG, 24, 24),
                release != null ? release.id() : "未知",
                release != null ? FormatTools.formatIsoTime(release.releaseTime()) : "未知",
                snapshot != null ? snapshot.id() : "未知",
                snapshot != null ? FormatTools.formatIsoTime(snapshot.releaseTime()) : "未知",
                Markdown.enterCommand("/推送任务 开启 mc_news", "订阅更新动态")
        );
        return sender.sendMessage(TC.md(versionInfo)) != null;
    }

    public static void onCommand(QQGuildCommandSender sender) {
        Map<String, VersionInfo> versions = checkCurrentVersion();
        VersionInfo release = versions.get("release");
        VersionInfo snapshot = versions.get("snapshot");

        String versionInfo = """
                Minecraft 最新版本信息

                正式版: %s
                发布于: %s

                快照版: %s
                发布于: %s
                """.formatted(
                release != null ? release.id() : "未知",
                release != null ? FormatTools.formatIsoTime(release.releaseTime()).trim() : "未知",
                snapshot != null ? snapshot.id() : "未知",
                snapshot != null ? FormatTools.formatIsoTime(snapshot.releaseTime()).trim() : "未知"
        );
        sender.sendMessage(versionInfo.trim());
    }

    private static void pushUpdateInfo(VersionType type, VersionInfo versionInfo) {

        Set<String> groups = GroupInformation.fetchAllGroupIds();
        List<String> officialGroups = OfficialGroups.enabledGroups("mc_news");

        String verId = type.getDisplayName();

        String textInfo = """
                Minecraft更新了新的%s
                版本号: %s
                发布时间: %s
                """.formatted(
                verId,
                versionInfo.id(),
                FormatTools.formatIsoTime(versionInfo.releaseTime()).trim()
        );

        String markdownInfo = """
                **Minecraft更新了新的%s**
                
                > 版本号: %s
                > 发布时间: %s
                """.formatted(
                verId,
                versionInfo.id(),
                FormatTools.formatIsoTime(versionInfo.releaseTime())
        );

        groups.stream().filter(group -> GroupConfigManager.isFeatureEnabled(group, "mc_news"))
                .forEach(group -> GroupMessage.chatMessage(group, textInfo));

        officialGroups.forEach(group -> GroupChat.sendMessage(group, TC.md(markdownInfo)));

        ChannelPosts.sendMessage(ForumCode.GUILD_ID, ForumCode.MINECRAFT_NEWS.getChannelId(), "[版本更新] Minecraft发布了新的版本", TC.md(markdownInfo));

        log.info("Pushed {} update info to {} groups, including {} official groups", verId, groups.size(), officialGroups.size());
    }

    @Override
    public TaskSchedule schedule() {
        return new TaskPlan().setMode(ScheduleMode.hourly);
    }

    @Override
    public void run() {
        LatestVersions latest = getLatestVersion();
        LatestVersions old = loadVersions();

        if (old == null) {
            saveVersions(latest);
            return;
        }

        if (!old.release().id().equals(latest.release().id())) {
            pushUpdateInfo(VersionType.RELEASE, latest.release());
        }

        if (!old.snapshot().id().equals(latest.snapshot().id())) {
            pushUpdateInfo(VersionType.SNAPSHOT, latest.snapshot());
        }

        saveVersions(latest);
    }

    @Getter
    public enum VersionType {

        RELEASE("正式版"),
        SNAPSHOT("快照版");

        private final String displayName;

        VersionType(String displayName) {
            this.displayName = displayName;
        }

    }

    public record VersionInfo(String id, String type, String url, String time, String releaseTime, String sha1, int complianceLevel) { }

    public record LatestVersions(VersionInfo release, VersionInfo snapshot) {}
}
