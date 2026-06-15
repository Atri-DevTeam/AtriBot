package top.yzljc.atribot.functions.official.pushtask;

import lombok.Getter;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.functions.official.permission.GroupList;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName AbstractPushTask
 * @Created_at 2026/06/14
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official.pushtask
 */
public abstract class PushTask {

    @Getter
    private final String functionId;
    @Getter
    private final String displayName;

    public PushTask(String functionId, String displayName) {
        this.functionId = functionId;
        this.displayName = displayName;
    }

    public List<String> getEnabledGroupOpenIds() {
        return GroupList.enabledGroups(this.functionId);
    }

    public boolean isGroupEnabled(String groupOpenId) {
        return GroupList.isFunctionEnabled(groupOpenId, this.functionId);
    }

    protected GroupList.FunctionInfo getFunctionInfo(String groupOpenId) {
        return GroupList.getFunctionInfo(groupOpenId, this.functionId);
    }

    protected String getStatus(String groupOpenId) {
        var statusInfo = getFunctionInfo(groupOpenId);
        String statusLine;
        if (statusInfo.operator() != null) {
            statusLine = statusInfo.enabled()
                    ? "> 当前状态：已由 " + Markdown.at(statusInfo.operator()) + " 于 " + statusInfo.time() + " 开启"
                    : "> 当前状态：已由 " + Markdown.at(statusInfo.operator()) + " 于 " + statusInfo.time() + " 关闭";
        } else {
            statusLine = "> 当前状态：未配置";
        }

        return statusLine;
    }

    public abstract Markdown getDescription(String groupOpenId);

    public abstract Markdown enable(String groupOpenId, String operatorOpenId);

    public abstract Markdown disable(String groupOpenId, String operatorOpenId);
}