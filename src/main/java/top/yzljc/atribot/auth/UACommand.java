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
        if (!user.getPlatform().isOfficialQQPlatform()) {
            user.sendMessage("[!] /ua 仅支持 QQ 官方机器人平台。");
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("qq")) {
            if (args.length == 2) {
                var qq = args[1];
                if (qq.matches("\\d+") && qq.length() >= 5 && qq.length() <= 10) {
                    var account = UnifiedAuthentication.ensureByQqUserOpenId(user.getUserId(), user.getUsername());
                    if (account == null) {
                        user.sendMessage("[!] 统一身份认证账号创建失败，请稍后再试！");
                        return true;
                    }
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
            user.sendMessage("[!] 用法：/ua qq <QQ号>，QQ号必须是 5-10 位数字！");
            return true;
        }

        if (!user.hasPermission()) {
            user.sendMessage("[!] 只有管理员可以查询统一身份认证信息！");
            return true;
        }

        if (args.length != 1 || args[0].isBlank()) {
            user.sendMessage("[!] 用法：/ua <UUID / OpenID / UIN / Minecraft UUID / 用户名>");
            return true;
        }

        var matches = UnifiedAuthentication.findMatching(args[0]);
        if (matches.isEmpty()) {
            user.sendMessage("[!] 未找到匹配的统一身份认证账号！");
            return true;
        }

        StringBuilder message = new StringBuilder("**统一身份认证查询**\n\n");
        for (int i = 0; i < matches.size(); i++) {
            var account = matches.get(i);
            if (i > 0) message.append("\n---\n\n");
            message.append("**账号 ").append(i + 1).append("**\n")
                    .append("UUID: `").append(account.uuid()).append("`\n")
                    .append("用户名: `").append(display(account.username())).append("`\n")
                    .append("QQ OpenID: `").append(display(account.qqUserOpenId())).append("`\n")
                    .append("QQ UIN: `").append(display(account.qqUserUin())).append("`\n")
                    .append("Minecraft UUID: `").append(display(account.minecraftUuid())).append("`\n")
                    .append("角色: `").append(account.role().getDisplayName()).append("`\n")
                    .append("状态: `").append(account.status()).append("`\n")
                    .append("权限: `").append(account.permissions().isEmpty() ? "无" : String.join(", ", account.permissions())).append("`\n")
                    .append("创建时间: `").append(display(account.createTime())).append("`\n")
                    .append("最后更新: `").append(display(account.lastUpdateTime())).append("`\n");
        }
        user.sendMessage(TC.md(message.toString()));

        return true;
    }

    private static String display(String value) {
        return value == null || value.isBlank() ? "未绑定" : value;
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
