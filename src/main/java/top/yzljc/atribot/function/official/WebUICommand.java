package top.yzljc.atribot.function.official;

import top.yzljc.atribot.auth.official.OfficialUsers;
import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialInteractionEvent;
import top.yzljc.atribot.event.impl.AnswerCode;
import top.yzljc.atribot.platform.Platform;
import top.yzljc.atribot.webui.official.WebUISessionManager;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName WebUICommand
 * @Created_at 2026/06/12
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official
 */
public class WebUICommand implements CommandExecutor, Listener {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (sender.getPlatform() != Platform.OFFICIAL_GROUP && sender.getPlatform() != Platform.OFFICIAL_C2C) {
            return true;
        }

        Object keyboard = TC.keyboard(
                List.of(
                        List.of(new Button("start", "启用", "webui_session", true, ButtonStyle.BLUE, ButtonType.CALLBACK),
                        new Button("stop", "关闭", "webui_session", true, ButtonStyle.GRAY, ButtonType.CALLBACK)
                )
        ));

        Markdown md = TC.md("**启用WebUI**\n\n> 请在使用后自行关闭\n\n> 当前状态: " + (WebUISessionManager.isActive() ? "已启用" : "未启用"));

        sender.sendMessage(md, keyboard);

        return true;
    }

    @EventHandler
    public void onCallback(OfficialInteractionEvent event) {
        if (!"webui_session".equals(event.getButtonValue())) return;

        AnswerCode code = AnswerCode.SUCCESS;
        if (!OfficialUsers.isAdmin(event.getUnionOpenId())) code = AnswerCode.NO_PERMISSION;

        if (event.getButtonId().equals("start")) {
            WebUISessionManager.start();
            event.answer(code);
            return;
        }
        if (event.getButtonId().equals("stop")) {
            WebUISessionManager.stop();
            event.answer(code);
            return;
        }
        event.answer(AnswerCode.FAIL);
    }
}