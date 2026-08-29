package top.yzljc.atribot.function.tasks.pushtask;

import lombok.Getter;
import lombok.Setter;
import top.yzljc.atribot.auth.official.FullMessageAuth;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.C2CChat;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.QQMessageSendException;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.UnsupportedPlatform;

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
    @Getter
    private final boolean groupEnable;
    @Getter
    private final boolean c2cEnable;
    @Getter @Setter
    private boolean defaultEnabled = false;

    public PushTask(String functionId, String displayName, boolean needActiveMessage) {
        this.functionId = functionId;
        this.displayName = displayName;
        this.needActiveMessage = needActiveMessage;
        this.groupEnable = true;
        this.c2cEnable = true;
    }

    public PushTask(String functionId, String displayName, boolean needActiveMessage, boolean groupEnable, boolean c2cEnable) {
        this.functionId = functionId;
        this.displayName = displayName;
        this.needActiveMessage = needActiveMessage;
        this.groupEnable = groupEnable;
        this.c2cEnable = c2cEnable;
    }

    public PushTask(String functionId, String displayName, boolean needActiveMessage, boolean groupEnable, boolean c2cEnable, boolean defaultEnabled) {
        this.functionId = functionId;
        this.displayName = displayName;
        this.needActiveMessage = needActiveMessage;
        this.groupEnable = groupEnable;
        this.c2cEnable = c2cEnable;
        this.defaultEnabled = defaultEnabled;
        if (this.needActiveMessage) this.defaultEnabled = false;
    }

    public List<String> getEnabledGroupOpenIds() {
        return OfficialGroups.enabledGroups(this.functionId);
    }

    public List<String> getEnabledUserOpenIds() {
        return OfficialUsers.enabledUsers(this.functionId);
    }

    public boolean isGroupEnabled(String groupOpenId) {
        return OfficialGroups.isFunctionEnabled(groupOpenId, this.functionId);
    }

    public boolean isUserEnabled(String userOpenId) {
        return OfficialUsers.isFunctionEnabled(userOpenId, this.functionId);
    }

    protected String getStatus(Platform platform, String platformIdentifyId) {
        String statusLine;
        if (platform.equals(Platform.OFFICIAL_GROUP)) {
            var statusInfo = OfficialGroups.getFunctionInfo(platformIdentifyId, this.functionId);
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
                if (this.defaultEnabled) {
                    statusLine += "（默认启用）";
                }
            }
        } else if (platform.equals(Platform.OFFICIAL_C2C)) {
            var statusInfo = OfficialUsers.getFunctionInfo(platformIdentifyId, this.functionId);
            if (statusInfo.operator() != null) {
                if (!statusInfo.operator().equals("webui") && !statusInfo.operator().equals("system_c2c_push_fail")) {
                    statusLine = statusInfo.enabled()
                            ? "> 当前状态：已由 " + "你" + " 于 " + statusInfo.time() + " 开启"
                            : "> 当前状态：已由 " + "你" + " 于 " + statusInfo.time() + " 关闭";
                } else {
                    statusLine = statusInfo.enabled()
                            ? "> 当前状态：已由 " + statusInfo.operator() + " 于 " + statusInfo.time() + " 开启"
                            : "> 当前状态：已由 " + statusInfo.operator() + " 于 " + statusInfo.time() + " 关闭";
                }
            } else {
                statusLine = "> 当前状态：未配置";
            }
        } else {
            throw new UnsupportedPlatform(platform, "推送任务在该平台不支持");
        }

        return statusLine;
    }

    public abstract Markdown getDescription(Platform platform, String platformIdentifyId);

    public void enable(Platform platform, String groupOpenId, String operatorOpenId, String commandMessageId) {
        Markdown md = TC.md( Markdown.at(operatorOpenId) + "\n\n" + "✅ 已启用**" + this.getDisplayName() + "**");
        Object keys = TC.keyboard(
                List.of(
                        List.of(new Button("c1", "关闭", "/tasks disable " + this.getFunctionId(), true, ButtonStyle.RED, ButtonType.COMMAND),
                                new Button("c2", "返回列表", "/tasks", true, ButtonStyle.BLUE, ButtonType.COMMAND))
                )
        );
        if (platform.equals(Platform.OFFICIAL_GROUP)) {
            if (this.needActiveMessage) {
                // 探测主动消息权限：发送失败以异常形式抛出，需映射回失败分支引导授权
                String messageId;
                try {
                    messageId = GroupChat.sendMessage(groupOpenId, md, keys);
                } catch (QQMessageSendException e) {
                    messageId = null;
                }
                if (messageId == null) {
                    GroupChat.replyMessage(groupOpenId, operatorOpenId, commandMessageId, FullMessageAuth.a());
                    return;
                }
            } else {
                GroupChat.replyMessage(groupOpenId, operatorOpenId, commandMessageId, md, keys);
            }
            OfficialGroups.setFunctionEnabled(groupOpenId, this.getFunctionId(), true, operatorOpenId);
        } else if (platform.equals(Platform.OFFICIAL_C2C)) {
            if (this.needActiveMessage) {
                // 探测主动消息权限：发送失败以异常形式抛出，需映射回失败分支引导授权
                String messageId;
                try {
                    messageId = C2CChat.sendMessage(operatorOpenId, md, keys);
                } catch (QQMessageSendException e) {
                    messageId = null;
                }
                if (messageId == null) {
                    C2CChat.replyMessage(operatorOpenId, commandMessageId, FullMessageAuth.a());
                    return;
                }
            } else {
                C2CChat.replyMessage(operatorOpenId, commandMessageId, md, keys);
            }
            OfficialUsers.setFunctionEnabled(operatorOpenId, this.getFunctionId(), true, operatorOpenId);
        } else {
            throw new UnsupportedPlatform(platform, "推送任务在该平台不支持");
        }
    }

    public void disable(Platform platform, String groupOpenId, String operatorOpenId, String commandMessageId) {
        Markdown md = TC.md("❌ 已关闭**" + this.getDisplayName() + "**");
        Object keys = TC.keyboard(
                List.of(
                        List.of(new Button("c1", "启用", "/tasks enable " + this.getFunctionId(), true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c2", "返回列表", "/tasks", true, ButtonStyle.BLUE, ButtonType.COMMAND))
                )
        );
        if (platform.equals(Platform.OFFICIAL_GROUP)) {
            OfficialGroups.setFunctionEnabled(groupOpenId, this.getFunctionId(), false, operatorOpenId);
            GroupChat.replyMessage(groupOpenId, operatorOpenId, commandMessageId, md, keys);
        } else if (platform.equals(Platform.OFFICIAL_C2C)) {
            OfficialUsers.setFunctionEnabled(operatorOpenId, this.getFunctionId(), false, operatorOpenId);
            C2CChat.replyMessage(operatorOpenId, commandMessageId, md, keys);
        } else {
            throw new UnsupportedPlatform(platform, "推送任务在该平台不支持");
        }
    }
}