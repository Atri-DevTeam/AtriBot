package top.yzljc.atribot.function.official;

import top.yzljc.atribot.configuration.ResourcesProperties;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;

import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.EventPriority;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.*;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.utils.tools.Alert;
import top.yzljc.atribot.webui.impl.SseBroadcaster;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName EventRecord
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions.official.permission
 */
@Slf4j
public class EventRecord implements Listener {

    private static final Set<String> c2cNotifiedUsers = new HashSet<>();

    @EventHandler
    public void onMemberJoin(OfficialGroupMemberAddEvent event) {
        if (!OfficialGroups.isFunctionEnabled(event.getGroupOpenId(), "member_add_welcome")) {
            return;
        }
        // Guided by GordonHim
        String url = ResourcesProperties.WELCOME_IMG;
        Markdown md = TC.md(
                Markdown.at(event.getMemberOpenId()) + " 欢迎新人喵~\n\n" +
                        Markdown.img(url, 1238, 564) + "\n\n" +
                        "> " + Markdown.enterCommand("/推送任务 关闭 member_add_welcome", "关闭欢迎提示")
        );
        Object buttons = TC.keyboard(
                List.of(
                        List.of(new Button("c1", "打卡", "/打卡", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c2", "功能", "/help", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c3", "提建议", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
                )
        );
        event.sendMessage(md, buttons);
    }

    @EventHandler
    public void onGroupJoin(OfficialGroupJoinEvent event) {
        boolean result = OfficialGroups.registerGroup(event.getGroupOpenId(), event.getOpMemberOpenId(), event.getTimestamp());
        if (!result) {
            log.error("Failed to register group: {}", event.getGroupOpenId());
            Alert.notify("Failed to register group: " + event.getGroupOpenId());
        } else {
            log.info("Registered group: {}", event.getGroupOpenId());
            Alert.notify(event.getOpMemberOpenId() + "将亚托莉喵添加到群聊" + event.getGroupOpenId() + "中");
        }
    }

    @EventHandler
    public void onGroupDel(OfficialGroupDelEvent event) {
        boolean result = OfficialGroups.removeGroup(event.getGroupOpenId());
        if (!result) {
            log.error("Failed to remove group: {}", event.getGroupOpenId());
            Alert.notify("Failed to remove group: " + event.getGroupOpenId());
        } else {
            log.info("Removed group: {}", event.getGroupOpenId());
            Alert.notify(event.getOpMemberOpenId() + "将亚托莉喵移出群聊" + event.getGroupOpenId());
        }
    }

    @EventHandler
    public void onC2CMessageButNotCommand(OfficialC2CMessageCreateEvent event) {
        String userId = event.getUser().getUserId();
        if (!event.getMessage().isCommand()) {
            if (OfficialUsers.isC2CPushEnabled(userId)) {
                if (c2cNotifiedUsers.add(userId)) {
                    event.getUser().sendMessage(event.getMessage().getMessageId(), TC.md("你好喵~\n\n" +
                            Config.getInstance().getOfficialUsername() + "为兼顾安全问题，未接入AI主动聊天，因此我暂时不能与你聊天。您可以使用 " +
                            Markdown.enterCommand("/help") + "查看指令帮助，或通过 " + Markdown.enterCommand("/feedback 私聊对话") + "呼叫开发者与您对话喵~"));
                }
                return;
            }
            var md = TC.md("你好喵~\n\n由于您未允许" + Config.getInstance().getOfficialUsername() +
                    "主动聊天，因此我暂时不能与你聊天，您可以在机器人权限设置中允许我主动聊天。同时，您也可以使用 " +
                    Markdown.enterCommand("/help") + "查看指令帮助，或通过 " + Markdown.enterCommand("/feedback") + "与开发者取得联系喵~");
            event.getUser().sendMessage(event.getMessage().getMessageId(), md);
        }
    }

    @EventHandler
    public void onFriendAdd(OfficialFriendAddEvent event) {
        log.info("New friend added: {}", event.getUnionOpenId());
        OfficialUsers.registerUser(event.getUnionOpenId());
        log.info("Registered official user data for new friend: {}", event.getUnionOpenId());
        Alert.notify("新的好友添加了亚托莉喵，OpenID: " + event.getUnionOpenId());
    }

    @EventHandler
    public void onFriendDel(OfficialFriendDelEvent event) {
        log.info("Friend removed: {}", event.getUnionOpenId());
        if (OfficialUsers.removeUser(event.getUnionOpenId())) {
            log.info("Removed official user data for deleted friend: {}", event.getUnionOpenId());
        } else {
            log.warn("Failed to remove official user data for deleted friend: {}", event.getUnionOpenId());
        }
        Alert.notify("有好友删除了亚托莉喵，OpenID: " + event.getUnionOpenId());
    }

    @EventHandler
    public void onC2CAuthorizeModified(OfficialC2CAuthorizeModifyEvent event) {
        if (event.getOptScene() == OfficialC2CAuthorizeModifyEvent.OptScene.SETTING
                && event.getScope() == OfficialC2CAuthorizeModifyEvent.Scope.C2C_PUSH) {
            boolean c2cPushStatus = event.getAuthorizeData().isAllowedC2CPush();
            String userOpenId = event.getUserOpenId();
            if (userOpenId == null || userOpenId.isBlank()) {
                log.warn("收到 C2C 主动消息授权变更，但缺少用户 OpenID，eventId: {}", event.getEventId());
                return;
            }
            OfficialUsers.setC2CPush(userOpenId, c2cPushStatus);
            SseBroadcaster.broadcastC2CPushStatus(userOpenId, c2cPushStatus);
            log.info("C2C 主动消息授权变更: userOpenId={}, enabled={}", userOpenId, c2cPushStatus);
            Alert.notify("C2C 主动消息授权变更: " + userOpenId + " -> " + (c2cPushStatus ? "开启" : "关闭"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void callback(OfficialButtonInteractionEvent event) {
        if (event.getGroupOpenId() != null && event.getGroupOpenId().equals(Config.getInstance().getDebugGroupOpenId()))
            return;
        if (OfficialUsers.isIgnored(event.getUnionOpenId()) || OfficialUsers.isBlocked(event.getUnionOpenId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getGroupOpenId() != null && OfficialGroups.isGroupBlacklisted(event.getGroupOpenId())) {
            event.setCancelled(true);
            return;
        }
        String eventInfo = "[官机] 收到来自用户 %s (场景%s: %s) 的交互事件: %s (类型: %d)".formatted(
                event.getUnionOpenId(),
                event.getScene(),
                event.getGroupOpenId() == null ? "x" : event.getGroupOpenId(),
                event.getData().getResolved(),
                event.getData().getType()
        );
        log.info(eventInfo);
        GroupMessage.chatMessage(Config.getInstance().getNapcatDebugGroupUin(), eventInfo);
    }

    @EventHandler
    public void onRunCommand(UserRunCommandEvent event) {
        if (event.getSender().getPlatform() == Platform.OFFICIAL_GROUP && OfficialGroups.isGroupBlacklisted(event.getSender().getGroupId())) {
            if (!event.getCommandHeader().equalsIgnoreCase("feedback")) {
                Object key = TC.keyboard(List.of(
                        List.of(new Button("c1", "联系开发者", "/feedback ", true, ButtonStyle.BLUE, ButtonType.COMMAND))
                ));
                event.getSender().sendMessage(TC.md("> 该群聊因违反指令使用规则已被禁止使用指令，如有任何疑问请联系开发者处理！"), key);
                log.warn("Command from blacklisted group {}: {} {}", event.getSender().getGroupId(), event.getCommandHeader(), String.join(" ", event.getArgs()));
                event.setCancelled(true);
                return;
            }
        }

        if (event.getSender().getPlatform() == Platform.OFFICIAL_C2C && OfficialUsers.isBlocked(event.getSender().getUserId())) {
            if (!OfficialUsers.isIgnored(event.getSender().getUserId())) {
                if (!event.getCommandHeader().equalsIgnoreCase("feedback")) {
                    Object key = TC.keyboard(List.of(
                            List.of(new Button("c1", "联系开发者", "/feedback ", true, ButtonStyle.BLUE, ButtonType.COMMAND))
                    ));
                    event.getSender().sendMessage(TC.md("> 你已被禁止使用指令，如有任何疑问请联系开发者处理！"), key);
                    log.warn("Command from blacklisted user {}: {} {}", event.getSender().getUserId(), event.getCommandHeader(), String.join(" ", event.getArgs()));
                    event.setCancelled(true);
                    return;
                }
            } else {
                log.warn("Command from ignored user {}: {} {}", event.getSender().getUserId(), event.getCommandHeader(), String.join(" ", event.getArgs()));
                event.setCancelled(true);
                return;
            }
        }

        if (event.getSender().getPlatform() == Platform.OFFICIAL_GROUP && OfficialUsers.isBlocked(event.getSender().getUserId())) {
            if (!OfficialUsers.isIgnored(event.getSender().getUserId())) {
                if (!event.getCommandHeader().equalsIgnoreCase("feedback")) {
                    Object key = TC.keyboard(List.of(
                            List.of(new Button("c1", "联系开发者", "/feedback ", true, ButtonStyle.BLUE, ButtonType.COMMAND))
                    ));
                    event.getSender().sendMessage(TC.md("> 你已被禁止在群聊中使用指令，如有任何疑问请联系开发者处理！"), key);
                    log.warn("Command from group-blacklisted user {}: {} {}", event.getSender().getUserId(), event.getCommandHeader(), String.join(" ", event.getArgs()));
                    event.setCancelled(true);
                    return;
                }
            } else {
                log.warn("Command from group-ignored user {}: {} {}", event.getSender().getUserId(), event.getCommandHeader(), String.join(" ", event.getArgs()));
                event.setCancelled(true);
                return;
            }
        }

        if ((event.getSender().getPlatform() == Platform.OFFICIAL_C2C || event.getSender().getPlatform() == Platform.OFFICIAL_GROUP) && Config.getInstance().isNapcatEnabled()) {
            if (event.getSender().getPlatform() == Platform.OFFICIAL_GROUP && event.getSender().getUserId().equalsIgnoreCase(Config.getInstance().getDebugGroupOpenId()))
                return;

            String info;
            if (event.getSender().getPlatform() == Platform.OFFICIAL_GROUP) {
                info = "[官机] 用户 %s (%s) 执行了命令: /%s %s (群: %s)".formatted(
                        event.getSender().getUsername(),
                        event.getSender().getUserId(),
                        event.getCommandHeader(),
                        String.join(" ", event.getArgs()),
                        event.getSender().getGroupId()
                );
            } else {
                info = "[官机] 用户 %s (%s) 执行了命令: /%s %s (私聊)".formatted(
                        event.getSender().getUsername(),
                        event.getSender().getUserId(),
                        event.getCommandHeader(),
                        String.join(" ", event.getArgs())
                );
            }
            GroupMessage.chatMessage(Config.getInstance().getNapcatDebugGroupUin(), info);
            log.info(info);
        }
    }
}