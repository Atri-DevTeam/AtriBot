package top.yzljc.atribot.function.official.pushtask;

import lombok.Getter;
import top.yzljc.atribot.auth.official.FullMessageAuth;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;

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
    @Getter
    private final boolean needActiveMessage;

    public PushTask(String functionId, String displayName, boolean needActiveMessage) {
        this.functionId = functionId;
        this.displayName = displayName;
        this.needActiveMessage = needActiveMessage;
    }

    public List<String> getEnabledGroupOpenIds() {
        return OfficialGroups.enabledGroups(this.functionId);
    }

    public boolean isGroupEnabled(String groupOpenId) {
        return OfficialGroups.isFunctionEnabled(groupOpenId, this.functionId);
    }

    protected OfficialGroups.FunctionInfo getFunctionInfo(String groupOpenId) {
        return OfficialGroups.getFunctionInfo(groupOpenId, this.functionId);
    }

    protected String getStatus(String groupOpenId) {
        var statusInfo = getFunctionInfo(groupOpenId);
        String statusLine;
        if (statusInfo.operator() != null) {
            if (!statusInfo.operator().equals("webui") && !statusInfo.operator().equals("system_active_message_fail")) {
                statusLine = statusInfo.enabled()
                        ? "> 当前状态：已由 " + Markdown.at(statusInfo.operator()) + " 于 " + statusInfo.time() + " 开启"
                        : "> 当前状态：已由 " + Markdown.at(statusInfo.operator()) + " 于 " + statusInfo.time() + " 关闭";
            } else {
                statusLine = statusInfo.enabled()
                        ? "> 当前状态：已由 " + statusInfo.operator() + " 于 " + statusInfo.time() + " 开启"
                        : "> 当前状态：已由 " + statusInfo.operator() + " 于 " + statusInfo.time() + " 关闭";
            }
        } else {
            statusLine = "> 当前状态：未配置";
        }

        return statusLine;
    }

    public abstract Markdown getDescription(String groupOpenId);

    public void enable(String groupOpenId, String operatorOpenId, String commandMessageId) {
        Markdown md = TC.md("✅ 已启用**" + this.getDisplayName() + "**");
        Object keys = TC.keyboard(
                List.of(
                        List.of(new Button("c1", "关闭", "/推送任务 关闭 " + this.getFunctionId(), true, ButtonStyle.RED, ButtonType.COMMAND),
                                new Button("c2", "返回列表", "/推送任务", true, ButtonStyle.BLUE, ButtonType.COMMAND))
                )
        );
        if (this.needActiveMessage) {
            String messageId = GroupChat.sendMessage(groupOpenId, md, keys);
            if (messageId == null) {
                GroupChat.replyMessage(groupOpenId, operatorOpenId, commandMessageId, FullMessageAuth.a());
                return;
            }
        } else {
            GroupChat.replyMessage(groupOpenId, operatorOpenId, commandMessageId, md, keys);
        }
        OfficialGroups.setFunctionEnabled(groupOpenId, this.getFunctionId(), true, operatorOpenId);
    }

    public void disable(String groupOpenId, String operatorOpenId, String commandMessageId) {
        Markdown md = TC.md("❌ 已关闭**" + this.getDisplayName() + "**");
        Object keys = TC.keyboard(
                List.of(
                        List.of(new Button("c1", "启用", "/推送任务 开启 " + this.getFunctionId(), true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c2", "返回列表", "/推送任务", true, ButtonStyle.BLUE, ButtonType.COMMAND))
                )
        );
        OfficialGroups.setFunctionEnabled(groupOpenId, this.getFunctionId(), false, operatorOpenId);
        GroupChat.replyMessage(groupOpenId, operatorOpenId, commandMessageId, md, keys);
    }
}