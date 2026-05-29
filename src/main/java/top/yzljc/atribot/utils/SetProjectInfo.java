package top.yzljc.atribot.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.atribot.chat.onebot.FriendList;
import top.yzljc.atribot.chat.onebot.GroupInformation;
import top.yzljc.atribot.service.request.PostRequest;
import top.yzljc.atribot.service.request.RequestType;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

public class SetProjectInfo {
    private static final Logger log = LoggerFactory.getLogger(SetProjectInfo.class);
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    public static void setInfo(){
        String commitId = GetProjectInfo.getCommitId();
        String branch = GetProjectInfo.getBranch();
        String buildTime = GetProjectInfo.getBuildTime();
        String version = GetProjectInfo.getVersion();
        int friendCount = FriendList.getFriendCount();
        int groupCount = GroupInformation.fetchAllGroupIds().size();
        Map<String, Object> projectInfo = new HashMap<>();
        projectInfo.put("nickname","ATRI - " + commitId + " - /atrihelp查看帮助");
        projectInfo.put("personal_note","Build Time: " + buildTime + " | " + commitId + "/" + branch + " " + version);
        projectInfo.put("sex","female");
        PostRequest.sendPost(RequestType.SET_PROFILE,projectInfo);
        writeProjectInfoJson(commitId, branch, buildTime, version, friendCount, groupCount);
        log.info("已更新项目标签信息: {} / {} / {} / {}", commitId, branch, buildTime, version);
    }

    private static void writeProjectInfoJson(String commitId, String branch, String buildTime, String version, int friendCount, int groupCount) {
        try {
            Path outputPath = Paths.get("").toAbsolutePath().resolve("project-info.json");
            Map<String, Object> jsonInfo = new LinkedHashMap<>();
            jsonInfo.put("commitId", commitId);
            jsonInfo.put("branch", branch);
            jsonInfo.put("buildTime", buildTime);
            jsonInfo.put("version", version);
            jsonInfo.put("friendCount", friendCount);
            jsonInfo.put("groupCount", groupCount);

            OBJECT_MAPPER.writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), jsonInfo);
            log.info("已生成项目版本信息文件: {}", outputPath);
        } catch (Exception e) {
            log.error("生成 project-info.json 失败: {}", e.getMessage(), e);
        }
    }
}
