package top.yzljc.atribot.function.official;

import top.yzljc.atribot.configuration.ResourcesProperties;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialGroups;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.auth.official.PermissionRole;
import top.yzljc.atribot.chat.napcat.GroupMessage;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.*;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.utils.tools.Alert;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName EventRecord
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions.official.permission
 */
@Slf4j
public class EventRecord implements Listener {

    @EventHandler
    public void onMemberJoin(OfficialGroupMemberAddEvent event) {
        if (!OfficialGroups.isFunctionEnabled(event.getGroupOpenId(), "member_add_welcome")) {
            return;
        }
        // Guided by GordonHim
        String url = ResourcesProperties.WELCOME_IMG;
        Markdown md = TC.md(
                Markdown.at(event.getMemberOpenId()) + " 欢迎新人喵~\n\n" +
                Markdown.img(url, 1238 ,564)
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
    public void onFriendAdd(OfficialFriendAddEvent event) {
        log.info("New friend added: {}", event.getUnionOpenId());
        Alert.notify("新的好友添加了亚托莉喵，OpenID: " + event.getUnionOpenId());
    }

    @EventHandler
    public void onFriendDel(OfficialFriendDelEvent event) {
        log.info("Friend removed: {}", event.getUnionOpenId());
        Alert.notify("有好友删除了亚托莉喵，OpenID: " + event.getUnionOpenId());
    }

    @EventHandler
    public void callback(OfficialInteractionEvent event) {
        if (event.getGroupOpenId() != null && event.getGroupOpenId().equals(Config.getInstance().getDebugGroupOpenId())) return;
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

        if (event.getSender().getPlatform() == Platform.OFFICIAL_C2C && OfficialUsers.hasRole(event.getSender().getUserId(), PermissionRole.BLACKLIST)) {
            if (!event.getCommandHeader().equalsIgnoreCase("feedback")) {
                Object key = TC.keyboard(List.of(
                        List.of(new Button("c1", "联系开发者", "/feedback ", true, ButtonStyle.BLUE, ButtonType.COMMAND))
                ));
                event.getSender().sendMessage(TC.md("> 你已被禁止使用指令，如有任何疑问请联系开发者处理！"), key);
                log.warn("Command from blacklisted user {}: {} {}", event.getSender().getUserId(), event.getCommandHeader(), String.join(" ", event.getArgs()));
                event.setCancelled(true);
                return;
            }
        }

        if (event.getSender().getPlatform() == Platform.OFFICIAL_GROUP && OfficialUsers.hasRole(event.getSender().getUserId(), PermissionRole.BLACKLIST)) {
            if (!event.getCommandHeader().equalsIgnoreCase("feedback")) {
                Object key = TC.keyboard(List.of(
                        List.of(new Button("c1", "联系开发者", "/feedback ", true, ButtonStyle.BLUE, ButtonType.COMMAND))
                ));
                event.getSender().sendMessage(TC.md("> 你已被禁止在群聊中使用指令，如有任何疑问请联系开发者处理！"), key);
                log.warn("Command from group-blacklisted user {}: {} {}", event.getSender().getUserId(), event.getCommandHeader(), String.join(" ", event.getArgs()));
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