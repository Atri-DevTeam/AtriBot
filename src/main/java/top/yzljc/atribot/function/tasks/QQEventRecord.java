package top.yzljc.atribot.function.tasks;

import top.yzljc.atribot.Atri;
import top.yzljc.atribot.auth.official.UnifiedRole;
import top.yzljc.atribot.chat.official.button.ButtonSize;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.QQGuildCommandSender;
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
import top.yzljc.atribot.database.repo.CoinGainLogRepository;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.EventPriority;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.*;
import top.yzljc.atribot.function.command.PushTaskCommand;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.platform.qq.QQBot;
import top.yzljc.atribot.utils.tools.Alert;
import top.yzljc.atribot.webui.SseBroadcaster;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @Author YZ_Ljc_
 * @ClassName EventRecord
 * @Created_at 2026/05/21
 * @Project AtriBot
 * @Package top.yzljc.qqbot.functions.official.permission
 */
@Slf4j
public class QQEventRecord implements Listener {

    private static final List<String> c2cNotifiedUsers = new CopyOnWriteArrayList<>();

    @EventHandler
    public void onMemberJoin(OfficialGroupMemberAddEvent event) {
        // 成员入群后刷新一次所在群资料（成员数等）
        Atri.getInstance().getScheduler().runTaskAsynchronously(() -> fetchAndSaveGroupProfile(event.getGroupOpenId()));
        log.info("[!] 成员入群，群资料刷新，群ID {}, 用户ID {}", event.getGroupOpenId(), event.getMemberOpenId());
        Atri.getInstance().getScheduler().runTaskAsynchronously(() -> {
            if (event.getGroupOpenId().equals("8B4709F81FE02E5E64AC31B2F910793A")) {
                if (CoinGainLogRepository.countCoinGains(event.getMemberOpenId(), "join_my_group") < 1) {
                    LootRepository.addCoins(event.getMemberOpenId(), 100, "join_my_group");
                    log.info("Added coins to new member {} for joining group {} (join_my_group)", event.getMemberOpenId(), event.getGroupOpenId());
                }
            }
        });
        if (!OfficialGroups.isFunctionEnabled(event.getGroupOpenId(), "member_add_welcome")) {
            return;
        }
        // Guided by GordonHim
        String url = ResourcesProperties.WELCOME_IMG;
        String welStr = "欢迎新人喵~";
        int width = 1238;
        int height = 564;
        if (OfficialUsers.getRole(event.getMemberOpenId()) == UnifiedRole.OWNER) {
            welStr = "欢迎" + QQBot.BOT_NAME + "开发者YZ_Ljc_加入本群，有关机器人的问题可以随时与我联系，感谢各位支持喵~";
            url = ResourcesProperties.WELCOME_DEV_IMG;
            width = 850;
            height = 479;
        } else if (OfficialUsers.getRole(event.getMemberOpenId()) == UnifiedRole.ADMIN) {
            welStr = "欢迎" + QQBot.BOT_NAME + "管理员加入本群，有关机器人的问题可以随时与我联系，感谢各位支持喵~";
        }
        Markdown md = TC.md(
                Markdown.at(event.getMemberOpenId()) + " " + welStr + "\n\n" +
                        Markdown.img(url, width, height) + "\n\n" +
                        "> " + Markdown.enterCommand("/推送任务 关闭 member_add_welcome", "关闭欢迎提示")
        );
        Object buttons = TC.keyboard(
                List.of(
                        List.of(new Button("c1", "打卡", "/打卡", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c2", "帮助", "/help", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c3", "提建议", "/feedback ", false, ButtonStyle.BLUE, ButtonType.COMMAND))
                ), ButtonSize.SMALL
        );
        event.sendMessage(md, buttons);
    }

    @EventHandler
    public void onMemberRemove(OfficialGroupMemberRemoveEvent event) {
        Atri.getInstance().getScheduler().runTaskAsynchronously(() -> fetchAndSaveGroupProfile(event.getGroupOpenId()));
        log.info("[!] 成员退群，群资料刷新，群ID {}, 用户ID {}", event.getGroupOpenId(), event.getMemberOpenId());
    }

    @EventHandler
    public void onGroupJoin(OfficialGroupAddRobotEvent event) {
        boolean result = OfficialGroups.registerGroup(event.getGroupOpenId(), event.getOpMemberOpenId(), event.getTimestamp());
        if (!result) {
            log.error("Failed to register group: {}", event.getGroupOpenId());
            Alert.notify("Failed to register group: " + event.getGroupOpenId());
        } else {
            log.info("Registered group: {}", event.getGroupOpenId());
            Alert.notify(event.getOpMemberOpenId() + "将亚托莉喵添加到群聊" + event.getGroupOpenId() + "中");
            Atri.getInstance().getScheduler().runTaskAsynchronously(() -> fetchAndSaveGroupProfile(event.getGroupOpenId()));
        }
        if (CoinGainLogRepository.countCoinGains(event.getOpMemberOpenId(), "group_invite") < 5) {
            LootRepository.addCoins(event.getOpMemberOpenId(), 200, "group_invite");
            log.info("Added 200 coins to user {} for inviting bot to group {} ({}/5)", event.getOpMemberOpenId(), event.getGroupOpenId(), CoinGainLogRepository.countCoinGains(event.getOpMemberOpenId(), "group_invite"));
        }
    }

    private static void fetchAndSaveGroupProfile(String groupOpenId) {
        var profile = QQBot.fetchGroupProfile(groupOpenId);
        if (profile == null) {
            log.warn("新加群后获取群资料失败: {}", groupOpenId);
            return;
        }
        if (!OfficialGroups.saveGroupProfile(profile)) {
            log.warn("新加群后保存群资料失败: {}", groupOpenId);
            return;
        }
        log.info("[!] 已加载新进群的相关资料，群ID: {}, 群名称: {}", profile.groupId(), profile.groupName());
    }

    @EventHandler
    public void onGroupDel(OfficialGroupDelRobotEvent event) {
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
            if (event.getMessage().getContent().contains("签到") || event.getMessage().getContent().contains("hypixel.net")) return;
            if (!c2cNotifiedUsers.contains(userId)) {
                c2cNotifiedUsers.add(userId);
                event.sendMessage(TC.md("你好喵~\n\n你发送的消息不是指令，亚托莉喵无法理解喵，我们暂且不支持角色扮演等聊天功能，请您理解~\n\n> 您可以发送" + Markdown.enterCommand("/help") + " 来查看可用的指令列表喵~"));
            }
        }
    }

    @EventHandler
    public void onFriendAdd(OfficialFriendAddEvent event) {
        log.info("New friend added: {}", event.getUserOpenId());
        OfficialUsers.registerUser(event.getUserOpenId());
        log.info("Registered official user data for new friend: {}", event.getUserOpenId());
        if (CoinGainLogRepository.countCoinGains(event.getUserOpenId(), "friend_add") < 1) {
            LootRepository.addCoins(event.getUserOpenId(), 100, "friend_add");
            log.info("Added 100 coins to new friend {} for first friend_add event", event.getUserOpenId());
        }
        Alert.notify("新的好友添加了亚托莉喵，OpenID: " + event.getUserOpenId() + " （场景: " + event.getScene().getTip() + ")");
    }

    @EventHandler
    public void onFriendDel(OfficialFriendDelEvent event) {
        log.info("Friend removed: {}", event.getUserOpenId());
        if (OfficialUsers.removeUser(event.getUserOpenId())) {
            log.info("Removed official user data for deleted friend: {}", event.getUserOpenId());
        } else {
            log.warn("Failed to remove official user data for deleted friend: {}", event.getUserOpenId());
        }
        Alert.notify("有好友删除了亚托莉喵，OpenID: " + event.getUserOpenId());
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
            if (!c2cPushStatus) {
                for (var task : PushTaskCommand.getTasks()) {
                    if (task.isUserEnabled(userOpenId)) {
                        if (!task.isNeedActiveMessage()) continue;
                        OfficialUsers.setFunctionEnabled(userOpenId, task.getFunctionId(), false, "system_c2c_push_fail");
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void callback(OfficialButtonInteractionEvent event) {
        if (event.getGroupOpenId() != null && event.getGroupOpenId().equals(Config.getInstance().getDebugGroupOpenId()))
            return;
        if (OfficialUsers.isIgnored(event.getUserOpenId()) || OfficialUsers.isBlocked(event.getUserOpenId())) {
            event.setCancelled(true);
            return;
        }
        if (event.getGroupOpenId() != null && OfficialGroups.isGroupBlacklisted(event.getGroupOpenId())) {
            event.setCancelled(true);
            return;
        }
        String eventInfo = "[官机] 收到来自用户 %s (场景%s: %s) 的交互事件: %s (类型: %d)".formatted(
                event.getUserOpenId(),
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
        if (event.getSender() instanceof QQGuildCommandSender guildSender) {
            if (!Config.getInstance().isNapcatEnabled()) {
                return;
            }

            String scene = guildSender.getPlatform() == Platform.OFFICIAL_GUILD_CHANNEL
                    ? "频道: %s, 子频道: %s".formatted(guildSender.getGuildId(), guildSender.getChannelId())
                    : "频道私信: %s".formatted(guildSender.getGuildId());
            String info = "[官机] 用户 %s (%s) 执行了命令: /%s %s (%s)".formatted(
                    guildSender.getUsername(),
                    guildSender.getUserId(),
                    event.getCommandHeader(),
                    String.join(" ", event.getArgs()),
                    scene
            );
            GroupMessage.chatMessage(Config.getInstance().getNapcatDebugGroupUin(), info);
            log.info(info);
            return;
        }

        if (!(event.getSender() instanceof QQCommandSender qq)) return;

        if (qq.getPlatform() == Platform.OFFICIAL_GROUP && OfficialGroups.isGroupBlacklisted(qq.getGroupId())) {
            if (!event.getCommandHeader().equalsIgnoreCase("feedback")) {
                Object key = TC.keyboard(List.of(
                        List.of(new Button("c1", "联系开发者", "/feedback ", true, ButtonStyle.BLUE, ButtonType.COMMAND))
                ));
                qq.sendMessage(TC.md("> 该群聊因违反指令使用规则已被禁止使用指令，如有任何疑问请联系开发者处理！"), key);
                log.warn("Command from blacklisted group {}: {} {}", qq.getGroupId(), event.getCommandHeader(), String.join(" ", event.getArgs()));
                event.setCancelled(true);
                return;
            }
        }

        if (qq.getPlatform() == Platform.OFFICIAL_C2C && OfficialUsers.isBlocked(qq.getUserId())) {
            if (!OfficialUsers.isIgnored(qq.getUserId())) {
                if (!event.getCommandHeader().equalsIgnoreCase("feedback")) {
                    Object key = TC.keyboard(List.of(
                            List.of(new Button("c1", "联系开发者", "/feedback ", true, ButtonStyle.BLUE, ButtonType.COMMAND))
                    ));
                    qq.sendMessage(TC.md("> 你已被禁止使用指令，如有任何疑问请联系开发者处理！"), key);
                    log.warn("Command from blacklisted user {}: {} {}", qq.getUserId(), event.getCommandHeader(), String.join(" ", event.getArgs()));
                    event.setCancelled(true);
                    return;
                }
            } else {
                log.warn("Command from ignored user {}: {} {}", qq.getUserId(), event.getCommandHeader(), String.join(" ", event.getArgs()));
                event.setCancelled(true);
                return;
            }
        }

        if (qq.getPlatform() == Platform.OFFICIAL_GROUP && OfficialUsers.isBlocked(qq.getUserId())) {
            if (!OfficialUsers.isIgnored(qq.getUserId())) {
                if (!event.getCommandHeader().equalsIgnoreCase("feedback")) {
                    Object key = TC.keyboard(List.of(
                            List.of(new Button("c1", "联系开发者", "/feedback ", true, ButtonStyle.BLUE, ButtonType.COMMAND))
                    ));
                    qq.sendMessage(TC.md("> 你已被禁止在群聊中使用指令，如有任何疑问请联系开发者处理！"), key);
                    log.warn("Command from group-blacklisted user {}: {} {}", qq.getUserId(), event.getCommandHeader(), String.join(" ", event.getArgs()));
                    event.setCancelled(true);
                    return;
                }
            } else {
                log.warn("Command from group-ignored user {}: {} {}", qq.getUserId(), event.getCommandHeader(), String.join(" ", event.getArgs()));
                event.setCancelled(true);
                return;
            }
        }

        if ((qq.getPlatform() == Platform.OFFICIAL_C2C || qq.getPlatform() == Platform.OFFICIAL_GROUP) && Config.getInstance().isNapcatEnabled()) {
            if (qq.getPlatform() == Platform.OFFICIAL_GROUP && qq.getUserId().equalsIgnoreCase(Config.getInstance().getDebugGroupOpenId()))
                return;

            String info;
            if (qq.getPlatform() == Platform.OFFICIAL_GROUP) {
                info = "[官机] 用户 %s (%s) 执行了命令: /%s %s (群: %s)".formatted(
                        qq.getUsername(),
                        qq.getUserId(),
                        event.getCommandHeader(),
                        String.join(" ", event.getArgs()),
                        qq.getGroupId()
                );
            } else {
                info = "[官机] 用户 %s (%s) 执行了命令: /%s %s (私聊)".formatted(
                        qq.getUsername(),
                        qq.getUserId(),
                        event.getCommandHeader(),
                        String.join(" ", event.getArgs())
                );
            }
            GroupMessage.chatMessage(Config.getInstance().getNapcatDebugGroupUin(), info);
            log.info(info);
        }
    }
}
