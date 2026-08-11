package top.yzljc.atribot.auth;

import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.chat.official.GroupChat;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.management.JoinApprovalStrategy;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialGroupJoinRequestEvent;
import top.yzljc.atribot.function.official.minecraft.MinecraftBind;
import top.yzljc.atribot.webui.repo.JoinApprovalWhitelistRepo;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName UACommand
 * @Created_at 2026/08/13
 * @Project AtriMeow
 * @Package top.yzljc.atribot.auth
 */
@Slf4j
public class UACommand implements CommandExecutor, Listener {

    private static final String strategyId = Config.getInstance().getVerifyStrategyId();
    private static final Map<String, String> pendingBindQq = new HashMap<>();

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof QQCommandSender user)) {
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("bindqq")) {
            if (UnifiedAuthentication.findByQqUserOpenId(user.getUserId()) == null) {
                user.sendMessage("[!] 当前身份用户未完成统一身份认证注册，请先执行 /ua 注册！");
                return true;
            }
            if (args.length > 1) {
                var qq = args[1];
                if (qq.matches("\\d+") && qq.length() >= 5 && qq.length() <= 10) {
                    pendingBindQq.put(user.getUserId(), qq);
                    boolean success = JoinApprovalStrategy.updateWhitelist(strategyId, JoinApprovalStrategy.WhitelistOp.ADD, List.of(qq));
                    JoinApprovalWhitelistRepo.addUsers(strategyId, List.of(qq));
                    if (success) {
                        user.sendMessage(TC.md("**统一身份认证绑定**\n\n" +
                                "请尽快申请加入下方验证群聊以完成验证，验证消息可任意填写 " + Markdown.link("https://qm.qq.com/q/VTbnlwtQA2", "前往验证")));
                    } else {
                        user.sendMessage("[!] 绑定失败，请联系开发者处理！");
                    }
                    return true;
                }
            }
            user.sendMessage("[!] 无效的号码标识，请重新输入！");
            return true;
        }

        if (UnifiedAuthentication.findByQqUserOpenId(user.getUserId()) != null) {
            user.sendMessage("[!] 当前身份用户已完成统一身份认证注册！");
        }

        var d = UnifiedAuthentication.register(user.getUserId(), user.getUsername().isBlank() ? null : user.getUsername());
        // 查询: 是否绑定过Minecraft账号
        var mc = MinecraftBind.getDataByOpenId(user.getUserId());
        if (mc != null && mc.uuid() != null && UnifiedAuthentication.updateMinecraftUuid(d.uuid(), mc.uuid())) {
            log.info("[!] 用户 {} 已绑定过 Minecraft 账号，已同步 UUID: {}", user.getUserId(), mc.uuid());
        }

        if (d.uuid() != null) {
            log.info("[!] 用户 {} 统一身份认证注册成功，UUID: {}", user.getUserId(), d.uuid());
            user.sendMessage(
                    TC.md("**统一身份认证成功**\n\n" +
                            "UUID: `" + d.uuid() + "`\n\n" +
                            "用户名: " + Markdown.at(user.getUserId()) + "\n\n" +
                            "OpenID: `" + user.getUserId() + "`\n\n" +
                            "MinecraftUUID: `" + d.minecraftUuid() + "`\n\n" +
                            "QQ: `" + "未绑定" + "`")
            );
        } else {
            user.sendMessage("[!] 统一身份认证注册失败，请联系开发者处理！");
        }

        return true;
    }

    @EventHandler
    public void onJoinRequestStrategy(OfficialGroupJoinRequestEvent event) {
        if (event.getStrategyId() != null && event.getStrategyId().equals(strategyId)) {
            var userOpenId = event.getMemberOpenId();
            if (pendingBindQq.containsKey(userOpenId)) {
                var profile = UnifiedAuthentication.findByQqUserOpenId(userOpenId);
                if (profile != null) {
                    var qqUin = pendingBindQq.get(userOpenId);
                    if (UnifiedAuthentication.updateQqUserUin(profile.uuid(), qqUin)) {
                        GroupChat.sendMessage(event.getGroupOpenId(),
                                TC.md("**统一身份认证绑定成功**\n\n" +
                                        "UUID: `" + profile.uuid() + "`\n\n" +
                                        "用户名: " + Markdown.at(userOpenId) + "\n\n" +
                                        "OpenID: `" + userOpenId + "`\n\n" +
                                        "UIN: `" + qqUin + "`\n\n" + "您现在可以退出本群了！"));
                        log.info("[!] 用户 {} 统一身份认证绑定 QQ 成功，QQ: {}", userOpenId, qqUin);
                        pendingBindQq.remove(userOpenId);
                        return;
                    }
                }
            }
            GroupChat.sendMessage(event.getGroupOpenId(), Markdown.at(userOpenId) + "\n[!] 在绑定时出现错误，请联系开发者处理！");
            log.warn("[!] 用户 {} 统一身份认证绑定 QQ 失败", userOpenId);
            return;
        }
        if (event.getGroupOpenId().equals("8FBAEDF67ECBE99BAA1118F402DEE743")) {
            event.deny("认证失败: 不符合身份认证条件！");
        }
    }
}