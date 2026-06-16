package top.yzljc.atribot.function.official.minecraft;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.napcat.GroupInformation;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager;
import top.yzljc.atribot.service.request.HttpService;
import top.yzljc.atribot.utils.FormatTools;

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
public final class VersionCheckImpl {

    private static final String VERSION_API = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final File RECORD_FILE = new File("minecraft_version.json");

    private VersionCheckImpl() {
    }

    public static Map<String, VersionInfo> checkCurrentVersion() {
        LatestVersions latest = getLatestVersion();

        Map<String, VersionInfo> result = new HashMap<>();
        result.put("release", latest.release());
        result.put("snapshot", latest.snapshot());

        return result;
    }

    public static void checkVersion() {
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

    public static void onCommand(CommandSender sender) {
        Map<String, VersionInfo> versions = checkCurrentVersion();
        VersionInfo release = versions.get("release");
        VersionInfo snapshot = versions.get("snapshot");

        switch (sender.getPlatform()) {
            case NAPCAT_GROUP -> {

                String versionInfo = """
                        当前Minecraft最新版本信息如下:
                        正式版: %s, 发布于 %s
                        快照版: %s, 发布于 %s
                        """.formatted(
                        release != null ? release.id() : "未知",
                        release != null ? FormatTools.formatIsoTime(release.releaseTime()) : "未知",
                        snapshot != null ? snapshot.id() : "未知",
                        snapshot != null ? FormatTools.formatIsoTime(snapshot.releaseTime()) : "未知"
                );

                sender.sendMessage(versionInfo);
            }
            case OFFICIAL_GROUP, OFFICIAL_C2C -> {
                String versionInfo = """
                        %s **Minecraft最新版本信息**
                        
                        正式版: %s
                        
                        > 发布于 %s
                        
                        快照版: %s
                        
                        > 发布于 %s
                        """.formatted(
                        Markdown.img("https://www.yzljc.top/img/grass-block-img.png", 24, 24),
                        release != null ? release.id() : "未知",
                        release != null ? FormatTools.formatIsoTime(release.releaseTime()) : "未知",
                        snapshot != null ? snapshot.id() : "未知",
                        snapshot != null ? FormatTools.formatIsoTime(snapshot.releaseTime()) : "未知"
                );
                sender.sendMessage(TC.md(versionInfo));
            }
        }
    }

    private static void pushUpdateInfo(VersionType type, VersionInfo versionInfo) {

        Set<String> groups = GroupInformation.fetchAllGroupIds();
        List<String> officialGroups = OfficialGroups.enabledGroups("mc_news");

        String verId = type.getDisplayName();

        String textInfo = """
                Minecraft发布了新的%s
                版本号: %s
                发布时间: %s
                """.formatted(
                verId,
                versionInfo.id(),
                FormatTools.formatIsoTime(versionInfo.releaseTime())
        );

        String markdownInfo = """
                **Minecraft发布了新的%s**
                
                >版本号: %s
                
                > 发布时间: %s
                """.formatted(
                verId,
                versionInfo.id(),
                FormatTools.formatIsoTime(versionInfo.releaseTime())
        );

        groups.stream().filter(group -> GroupConfigManager.isFeatureEnabled(group, "mc_news"))
                .forEach(group -> GroupMessage.chatMessage(group, textInfo));

        officialGroups.forEach(group -> Atri.getInstance().getChatService().sendActiveGroupMarkdownMessage(group, TC.md(markdownInfo)));

        log.info("Pushed {} update info to {} groups, including {} official groups", verId, groups.size(), officialGroups.size());
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
}
