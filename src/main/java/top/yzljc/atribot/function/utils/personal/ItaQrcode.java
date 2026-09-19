package top.yzljc.atribot.function.utils.personal;

import io.javalin.Javalin;
import io.javalin.http.Context;
import lombok.extern.slf4j.Slf4j;
import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.chat.official.button.PermissionType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialButtonInteractionEvent;
import top.yzljc.atribot.event.impl.AnswerCode;
import top.yzljc.atribot.platform.Identifier;
import top.yzljc.atribot.platform.Platform;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@Slf4j
public final class ItaQrcode implements CommandExecutor, Listener {
    public static final String PUBLIC_PATH = "/ita-qrcode";
    static final String GROUP_IMAGE_PATH = PUBLIC_PATH + "/group.png";
    private static final String CALLBACK = "ita_qrcode_switch";
    private static final String USAGE = "用法：/ita-qrcode 或 /ita-qrcode set <1|2> <url>\n1 为群状态，2 为问卷状态。";
    private final ItaQrcodeStore store;

    public ItaQrcode() {
        this(Path.of("data", "ita-qrcode.json"));
    }

    ItaQrcode(Path file) {
        this(new ItaQrcodeStore(file));
    }

    ItaQrcode(ItaQrcodeStore store) {
        this.store = store;
    }

    /** 独立于 WebUI 登录和启停开关的公开入口。 */
    public void registerRoutes(Javalin server) {
        server.get(PUBLIC_PATH, ctx -> {
            prepareResponse(ctx);
            ctx.contentType("text/plain; charset=utf-8");
            try {
                var state = store.snapshot();
                String target = state.target();
                if (target.isEmpty()) {
                    ctx.status(503).result("入口暂未配置，请稍后再试。");
                } else if (state.active() == 1) {
                    ctx.html(ItaQrcodePage.html());
                } else {
                    ctx.status(302).header("Location", target).result("");
                }
            } catch (IOException e) {
                log.warn("读取 ITA 二维码配置失败", e);
                ctx.status(503).result("入口暂不可用，请稍后再试。");
            }
        });
        // 使用普通 PNG 图片，便于微信长按识别；不使用 canvas 或外部图片服务。
        server.get(GROUP_IMAGE_PATH, ctx -> {
            prepareResponse(ctx);
            try {
                var state = store.snapshot();
                if (state.active() != 1) {
                    ctx.contentType("text/plain; charset=utf-8").status(404).result("当前入口已切换，请重新打开入口。");
                } else if (state.groupUrl().isEmpty()) {
                    ctx.contentType("text/plain; charset=utf-8").status(503).result("群二维码暂未配置，请稍后再试。");
                } else {
                    ctx.contentType("image/png").result(ItaQrcodePage.png(state.groupUrl()));
                }
            } catch (IOException e) {
                log.warn("生成 ITA 群二维码失败", e);
                ctx.contentType("text/plain; charset=utf-8").status(503).result("群二维码暂不可用，请稍后再试。");
            }
        });
    }

    private static void prepareResponse(Context ctx) {
        // 页面、二维码和跳转都随状态变化，避免浏览器或代理继续使用旧内容。
        ctx.header("Cache-Control", "no-store, max-age=0");
        ctx.header("Pragma", "no-cache");
        ctx.header("X-Content-Type-Options", "nosniff");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!(sender instanceof QQCommandSender)) return true;
        if (!((QQCommandSender) sender).getPlatform().equals(Platform.OFFICIAL_C2C)) return true;

        if (!sender.hasPermission("webui.ita")) {
            sender.sendMessage(Identifier.NO_PERMISSION);
            return true;
        }
        try {
            String notice = "";
            ItaQrcodeStore.State state;
            if (args.length == 0) {
                state = store.snapshot();
            } else if (args.length == 3 && "set".equalsIgnoreCase(args[0])
                    && ("1".equals(args[1]) || "2".equals(args[1]))) {
                int slot = Integer.parseInt(args[1]);
                state = store.setUrl(slot, args[2]);
                notice = ItaQrcodeStore.name(slot) + "地址已更新。\n";
            } else {
                sender.sendMessage(USAGE);
                return true;
            }
            String text = notice + state.statusText();
            if (sender instanceof QQCommandSender qq) {
                qq.sendMessage(TC.md(text), keyboard(state, sender.getUserId()));
            } else {
                sender.sendMessage(text);
            }
        } catch (IllegalArgumentException e) {
            sender.sendMessage(e.getMessage());
        } catch (IOException e) {
            log.warn("读写 ITA 二维码配置失败", e);
            sender.sendMessage("配置读写失败，请检查数据文件后重试。");
        }
        return true;
    }

    @EventHandler
    public void onCallback(OfficialButtonInteractionEvent event) {
        if (!CALLBACK.equals(event.getButtonValue())) return;
        if (event.shouldIgnore()) return;
        if (!OfficialUsers.isAdmin(event.getUserOpenId())) {
            event.answer(AnswerCode.NO_PERMISSION);
            return;
        }
        int slot = switch (event.getButtonId()) {
            case "ita_qrcode_1" -> 1;
            case "ita_qrcode_2" -> 2;
            default -> 0;
        };
        if (slot == 0) {
            event.answer(AnswerCode.FAIL);
            return;
        }
        ItaQrcodeStore.State state;
        try {
            state = store.activate(slot);
        } catch (IllegalArgumentException e) {
            event.answer(AnswerCode.FAIL);
            event.replyMessage(TC.md(e.getMessage()));
            return;
        } catch (IOException e) {
            log.warn("切换 ITA 二维码状态失败", e);
            event.answer(AnswerCode.FAIL);
            event.replyMessage(TC.md("配置读写失败，状态未切换，请稍后重试。"));
            return;
        }
        event.answer(AnswerCode.SUCCESS);
        event.replyMessage(TC.md(state.statusText()), keyboard(state, event.getUserOpenId()));
    }

    private static Object keyboard(ItaQrcodeStore.State state, String userId) {
        return TC.keyboard(List.of(List.of(button(1, state.active(), userId), button(2, state.active(), userId))));
    }

    private static Button button(int slot, int active, String userId) {
        return new Button("ita_qrcode_" + slot, ItaQrcodeStore.name(slot), CALLBACK,
                active == slot ? ButtonStyle.BLUE : ButtonStyle.GRAY, ButtonType.CALLBACK);
    }
}
