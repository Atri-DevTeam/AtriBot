package top.yzljc.atribot.function.official.minecraft;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.service.request.HttpService;

import java.util.HashMap;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName PackVersion
 * @Created_at 2026/07/26
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.minecraft
 */
@Slf4j
public record PackVersion(String name, int dataPackVersion, int resourcePackVersion) {

    private static final Map<String, PackVersion> VERSIONS = new HashMap<>();

    public static void init() {
        var d = HttpService.sendGetRequest(ResourcesProperties.PACK_VERSION_API);
        if (d == null || d.isEmpty()) {
            log.error("Failed to fetch Minecraft pack versions.");
            return;
        }
        for (var node : d) {
            String id = node.path("id").asText("unknown");
            String name = node.path("name").asText(null);
            int dataPackVersion = node.path("data_pack_version").asInt(-1);
            int resPackVersion = node.path("resource_pack_version").asInt(-1);

            VERSIONS.put(id, new PackVersion(name, dataPackVersion, resPackVersion));
        }
    }

    public static String getVersionNameByDataPackVersion(int dataPackVersion) {
        for (var version : VERSIONS.values()) {
            if (version.dataPackVersion() == dataPackVersion) {
                return version.name();
            }
        }
        init();
        for (var version : VERSIONS.values()) {
            if (version.dataPackVersion() == dataPackVersion) {
                return version.name();
            }
        }
        return null;
    }

    public static String getVersionNameByResourcePackVersion(int resourcePackVersion, boolean fillerTestVersion) {
        for (var version : VERSIONS.values()) {
            if (version.resourcePackVersion() == resourcePackVersion) {
                if (fillerTestVersion) {
                    if (version.name().contains("Snapshot") || version.name().contains("Release")) {
                        continue;
                    }
                }
                return version.name();
            }
        }
        init();
        for (var version : VERSIONS.values()) {
            if (version.resourcePackVersion() == resourcePackVersion) {
                if (fillerTestVersion) {
                    if (version.name().contains("Snapshot") || version.name().contains("Release")) {
                        continue;
                    }
                }
                return version.name();
            }
        }
        return null;
    }

    public static PackVersion getPackVersion(String versionId) {
        var d = VERSIONS.get(versionId);
        if (d != null) return d;
        init();
        return VERSIONS.get(versionId);
    }
}