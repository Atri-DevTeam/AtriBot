package top.yzljc.qqbot.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.findinfo.GetProjectInfo;
import top.yzljc.qqbot.botkits.request.PostRequest;
import top.yzljc.qqbot.botkits.request.RequestType;

import java.util.HashMap;
import java.util.Map;

public class SetProjectInfo {
    private static final Logger log = LoggerFactory.getLogger(SetProjectInfo.class);
    public static void setInfo(){
        String commitId = GetProjectInfo.getCommitId();
        String branch = GetProjectInfo.getBranch();
        String buildTime = GetProjectInfo.getBuildTime();
        String version = GetProjectInfo.getVersion();
        Map<String, Object> projectInfo = new HashMap<>();
        projectInfo.put("nickname","ATRI - " + commitId);
        projectInfo.put("personal_note","Build Time: " + buildTime + " | " + commitId + "/" + branch + " " + version);
        projectInfo.put("sex","female");
        PostRequest.sendPost(RequestType.SET_PROFILE,projectInfo);
        log.info("已更新项目标签信息: {} / {} / {} / {}", commitId, branch, buildTime, version);
    }
}
