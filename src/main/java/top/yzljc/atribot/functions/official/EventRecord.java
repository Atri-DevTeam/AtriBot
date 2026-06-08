package top.yzljc.atribot.functions.official;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.Atri;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.onebot.GroupMessage;
import top.yzljc.atribot.config.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.impl.*;
import top.yzljc.atribot.functions.official.permission.GroupList;
import top.yzljc.atribot.functions.official.permission.C2CList;
import top.yzljc.atribot.functions.official.permission.PermissionRole;
import top.yzljc.atribot.service.official.CommandButton;
import top.yzljc.atribot.utils.Alert;

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
    public void onGroupJoin(OfficialGroupJoinEvent event) {
        boolean result = GroupList.registerGroup(event.getGroupOpenId(), event.getOpMemberOpenId(), event.getTimestamp());
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
        boolean result = GroupList.removeGroup(event.getGroupOpenId());
        if (!result) {
            log.error("Failed to remove group: {}", event.getGroupOpenId());
            Alert.notify("Failed to remove group: " + event.getGroupOpenId());
        } else {
            log.info("Removed group: {}", event.getGroupOpenId());
            Alert.notify(event.getOpMemberOpenId() + "将亚托莉喵移出群聊" + event.getGroupOpenId());
        }
    }

    @EventHandler
    public void onGroupAtMessageCreate(OfficialGroupAtMessageCreateEvent event) {
        if (!event.getContent().trim().startsWith(Config.getInstance().getCommandPrefix())) {
            event.sendMessage("你好！我是亚托莉喵，感谢你在群里@我！由于官方限制，我暂时不能主动聊天哦，您可以通过 /help 查看所有可用指令，也可以通过 /feedback <内容> 向开发者提交反馈，感谢您的支持喵~");
        }
    }

    @EventHandler
    public void onC2CMessage(OfficialC2CMessageEvent event) {
        if (!event.getContent().trim().startsWith(Config.getInstance().getCommandPrefix())) {
            event.sendMessage("你好！我是亚托莉喵，感谢你私聊我！由于官方限制，我暂时不能主动聊天哦，您可以通过 /help 查看所有可用指令，也可以通过 /feedback <内容> 向开发者提交反馈，感谢您的支持喵~");
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
        GroupMessage.chatMessage(Config.getInstance().getDebugGroupId(), eventInfo);
    }

    @EventHandler
    public void onRunCommand(UserRunCommandEvent event) {
        if (event.getLabel().equals("2") && GroupList.isGroupBlacklisted(event.getSender().groupOpenId())) {
            if (!event.getCommandHeader().equalsIgnoreCase("feedback")) {
                Object key = Atri.getInstance().getChatService().buildCmdKeyboard(List.of(
                        List.of(new CommandButton("c1", "联系开发者", "/feedback", true, 1, 2))
                ));
                event.getSender().replyMarkdown(event.getLabel(), TC.md("> 该群聊因违反指令使用规则已被禁止使用指令，如有任何疑问请联系开发者处理！"), key);
                log.warn("Command from blacklisted group {}: {} {}", event.getSender().groupOpenId(), event.getCommandHeader(), String.join(" ", event.getArgs()));
                event.setCancelled(true);
                return;
            }
        }

        if (event.getLabel().equals("1") && C2CList.hasRole(event.getSender().unionOpenId(), PermissionRole.BLACKLIST)) {
            if (!event.getCommandHeader().equalsIgnoreCase("feedback")) {
                Object key = Atri.getInstance().getChatService().buildCmdKeyboard(List.of(
                        List.of(new CommandButton("c1", "联系开发者", "/feedback", true, 1, 2))
                ));
                event.getSender().replyMarkdown(event.getLabel(), TC.md("> 你已被禁止使用指令，如有任何疑问请联系开发者处理！"), key);
                log.warn("Command from blacklisted user {}: {} {}", event.getSender().unionOpenId(), event.getCommandHeader(), String.join(" ", event.getArgs()));
                event.setCancelled(true);
                return;
            }
        }

        if (event.getLabel().equals("2") && C2CList.hasRole(event.getSender().unionOpenId(), PermissionRole.BLACKLIST)) {
            if (!event.getCommandHeader().equalsIgnoreCase("feedback")) {
                Object key = Atri.getInstance().getChatService().buildCmdKeyboard(List.of(
                        List.of(new CommandButton("c1", "联系开发者", "/feedback", true, 1, 2))
                ));
                event.getSender().replyMarkdown(event.getLabel(), TC.md("> 你已被禁止在群聊中使用指令，如有任何疑问请联系开发者处理！"), key);
                log.warn("Command from group-blacklisted user {}: {} {}", event.getSender().unionOpenId(), event.getCommandHeader(), String.join(" ", event.getArgs()));
                event.setCancelled(true);
                return;
            }
        }

        if ((event.getLabel().equals("1") || event.getLabel().equals("2")) && !Config.getInstance().isDebugMode()) {
            if (event.getLabel().equals("2") && event.getSender().groupOpenId().equalsIgnoreCase(Config.getInstance().getDebugGroupOpenId()))
                return;

            String info;
            if (event.getLabel().equals("2")) {
                info = "[官机] 用户 %s (%s) 执行了命令: /%s %s (群: %s)".formatted(
                        event.getSender().author().getUsername(),
                        event.getSender().author().getUnionOpenId(),
                        event.getCommandHeader(),
                        String.join(" ", event.getArgs()),
                        event.getSender().groupOpenId()
                );
            } else {
                info = "[官机] 用户 %s (%s) 执行了命令: /%s %s (私聊)".formatted(
                        event.getSender().author().getUsername(),
                        event.getSender().author().getUnionOpenId(),
                        event.getCommandHeader(),
                        String.join(" ", event.getArgs())
                );
            }
            GroupMessage.chatMessage(Config.getInstance().getDebugGroupId(), info);
            log.info(info);
        }
    }
}