package top.yzljc.atribot.function.command;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.Config;
import top.yzljc.atribot.configuration.ResourcesProperties;
import top.yzljc.atribot.database.repo.C2CRepository;
import top.yzljc.atribot.database.repo.CoinGainLogRepository;
import top.yzljc.atribot.database.repo.LootRepository;
import top.yzljc.atribot.function.impl.drawitem.LootService;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.service.request.HttpService;

import java.util.List;
import java.util.Map;

/**
 * @Author YZ_Ljc_
 * @ClassName BilibiliBindCommand
 * @Created_at 2026/09/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.command
 */
public class BilibiliBindCommand implements CommandExecutor {
    private static final ObjectMapper JSON = new ObjectMapper();
    private final BindingStore store;
    private final FollowChecker checker;

    public BilibiliBindCommand() {
        this(new BindingStore() {
            public JsonNode get(String user) {
                return OfficialUsers.getUserSetting(user, OfficialUsers.BILIBILI_UID_SETTING);
            }

            public C2CRepository.SettingWriteResult bind(String user, long uid) {
                return OfficialUsers.bindBilibiliUid(user, uid);
            }
        }, uid -> HttpService.postJsonDetailed(ResourcesProperties.BILIBILI_BIND_API, Map.of("uid", uid),
                "Authorization", "Bearer " + Config.getInstance().getAtribotKeySecret()));
    }

    BilibiliBindCommand(BindingStore store, FollowChecker checker) {
        this.store = store;
        this.checker = checker;
    }

    interface BindingStore {
        JsonNode get(String user);

        C2CRepository.SettingWriteResult bind(String user, long uid);
    }

    @FunctionalInterface
    interface FollowChecker {
        HttpService.PostResult check(long uid);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender user)) {
            return true;
        }

        if (args.length == 1 && args[0].equals("help")) {
            Markdown md = TC.md("关注开发者哔哩哔哩\n\n> 点击第一个按钮前往关注\n> 关注成功后，将会发放1200金粒奖励\n> 点击第二个按钮，输入你的B站UID验证关注状态");
            var keyboard = TC.keyboard(
                    List.of(
                            List.of(
                                    new Button("c1", "关注B站", "https://space.bilibili.com/592616376", ButtonStyle.BLUE_WITH_BACKGROUND, ButtonType.LINK),
                                    new Button("c2", "验证关注", "/bvbind ", false, ButtonStyle.BLUE, ButtonType.COMMAND)
                            )
                    )
            );
            user.sendMessage(md, keyboard);
            return true;
        }

        long uid;
        try {
            if (args.length != 1) throw new IllegalArgumentException();
            String uidStr = args[0].trim();
            if (!uidStr.replace("UID:", "").matches("[0-9]+")) throw new IllegalArgumentException();
            uid = Long.parseLong(args[0]);
            if (uid <= 0) throw new IllegalArgumentException();
            if (uid == 592616376) throw new IllegalArgumentException();
        } catch (IllegalArgumentException e) {
            user.sendMessage("输入的UID无效哦！");
            return true;
        }
        JsonNode existing = store.get(user.getUserId());
        if ((existing != null && !existing.isNull()) || CoinGainLogRepository.countCoinGains(user.getUserId(), "bv_follow") > 0) {
            user.sendMessage("你已经领取过关注奖励啦！");
            return true;
        }
        FollowStatus status = parseFollowStatus(checker.check(uid), uid);
        if (status == FollowStatus.ERROR) {
            user.sendMessage("关注状态查询失败，本次未绑定，请稍后重试。");
        } else if (status == FollowStatus.NOT_FOLLOWING) {
            user.sendMessage("你还没有关注我哦，点个关注再来领取把！");
        } else {
            switch (store.bind(user.getUserId(), uid)) {
                case SAVED -> {
                    user.sendMessage("感谢关注，金粒奖励已发放！");
                    LootRepository.addCoins(user.getUserId(), 1200, "bv_follow");
                }
                case ALREADY_EXISTS -> user.sendMessage("你已经领取过关注奖励啦！");
                case UID_IN_USE -> user.sendMessage("这个 B站 UID 已被其他用户绑定，不能重复绑定或领取奖励。");
                case FAILED -> user.sendMessage("绑定记录保存失败，请稍后重试。");
            }
        }
        return true;
    }

    enum FollowStatus {FOLLOWING, NOT_FOLLOWING, ERROR}

    static FollowStatus parseFollowStatus(HttpService.PostResult response, long uid) {
        if (response == null || response.status() < 200 || response.status() >= 300 || response.body() == null)
            return FollowStatus.ERROR;
        try {
            JsonNode root = JSON.readTree(response.body());
            if (root == null || !root.path("status").isIntegralNumber() || root.path("status").intValue() != 200)
                return FollowStatus.ERROR;
            JsonNode data = root.path("data");
            JsonNode returnedUid = data.path("uid");
            JsonNode follows = data.path("followsMe");
            if (!returnedUid.isIntegralNumber() || !returnedUid.canConvertToLong() || returnedUid.longValue() != uid
                    || !follows.isBoolean()) return FollowStatus.ERROR;
            return follows.booleanValue() ? FollowStatus.FOLLOWING : FollowStatus.NOT_FOLLOWING;
        } catch (Exception e) {
            return FollowStatus.ERROR;
        }
    }
}
