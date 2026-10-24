package top.yzljc.atribot.bootstrap;

import top.yzljc.atricore.core.BotCommand;
import top.yzljc.atribot.configuration.Properties;
import top.yzljc.atribot.command.CommandSettingsService;
import top.yzljc.atribot.function.command.SignCommand;
import top.yzljc.atribot.i18n.I18N;
import java.util.Optional;

/**
 * @Author YZ_Ljc_
 * @ClassName ApplicationBotCommand
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atribot.bootstrap
 */
public final class ApplicationBotCommand extends BotCommand {
    @Override
    public String commandResource() {
        return Properties.ATRIBOT;
    }

    @Override
    public String unknownCommandMessage() {
        return I18N.text("command.unknown");
    }

    @Override
    public boolean isAdditionalCommand(String content) {
        return SignCommand.isMatch(content);
    }

    @Override
    public Optional<String> disabledReason(String commandName, String groupId) {
        return CommandSettingsService.INSTANCE.resolve(commandName, groupId).map(CommandSettingsService.DisableDecision::reason);
    }

}
