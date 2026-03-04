package top.yzljc.qqbot.utils.deprecated;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botservice.userinfo.GetUserInfo;
import top.yzljc.qqbot.command.CommandManager;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.config.groups.GroupConfigManager;

import java.util.HashMap;
import java.util.Map;

/**
 * @deprecated 旧版命令管理器，已废弃。请使用 {@link CommandManager} 替代。
 */
@Deprecated(since = "2.6.1")
public class CommandManagerOld {
    private static final Logger log = LoggerFactory.getLogger(CommandManagerOld.class);
    private static final Settings settings = Config.getInstance();
    private static final String COMMAND_PREFIX = settings.getCommandPrefix();
    private static final long BOT_QQ = GetUserInfo.getBotId();
    private static final String DEBUG_SUFFIX = "--debug";

    private static final Map<String, ExecuteCommand> commands = new HashMap<>();
    private static final Map<String, String> commandFeatures = new HashMap<>();

    static {
        // registerCommand("happynewyear", new HappyNewYear(), "new_year");
        // registerCommand("bc", new Broadcast(),"broadcast");
        // registerCommand("wakeup", new WakeUp(), "wakeup_send");
        //registerCommand("reboot", new Reboot(), null);
        // registerCommand("manodate", new ManosabaDate(), null);
        // registerCommand("github", new WebhookServer(), "github_info");
        // registerCommand("recall", new RecallLastMsg(), null);
        // registerCommand("motd", new Motd(), "motd");
        // registerCommand("mojang", new MojangStatus(), "mojang_status");
        // registerCommand("checkmcnews", new MinecraftNews(), "mc_news");
        // registerCommand("checkhypnews", new HypixelNews(), "hyp_news");
        // registerCommand("signall", new AutoSign(), "auto_sign");
        // registerCommand("rollback", new RollbackMessages(), null);
        // registerCommand("stats", new MessageStats(), null);
        // registerCommand("statsy", new MessageStats(), null);
        // registerCommand("statsoverall", new MessageStats(), null);
        // registerCommand("serverstatus", new ServerStatus(), null);
        // registerCommand("groupinfo", new GroupConfigInfo(), null);
        // registerCommand("help", new AtriHelp(), null);
        // registerCommand("update",new WebhookServer(),"github_info");
        // registerCommand("calendar",new Calendar(),"calendar");
    }

    private static void registerCommand(String name, ExecuteCommand cmd, String featureKey) {
        String key = name.toLowerCase();
        commands.put(key, cmd);
        if (featureKey != null) {
            commandFeatures.put(key, featureKey);
        }
    }

    public static void processCommand(JsonNode json) {
        String postType = json.path("post_type").asText("");
        String messageType = json.path("message_type").asText("");
        if (!"message".equals(postType) || !"group".equals(messageType)) {
            return;
        }
        String rawMessage = json.path("raw_message").asText("").trim();
        long groupId = json.path("group_id").asLong();
        long userId = json.path("user_id").asLong();
        if (userId == GetUserInfo.getBotId()) return;
        boolean isAdmin = settings.getAdminUids().contains(userId);
        boolean isAtBot = rawMessage.contains("[CQ:at,qq=" + BOT_QQ + "]");
        boolean isDebug = false;
        String finalRawMsg = rawMessage;
//        Pattern command = Pattern.compile("^[/!?][a-zA-Z]+");
//        Matcher rawCommand = command.matcher(finalRawMsg);

        if (isAdmin && rawMessage.endsWith(DEBUG_SUFFIX)) {
            isDebug = true;
            finalRawMsg = rawMessage.substring(0, rawMessage.length() - DEBUG_SUFFIX.length()).trim();
        }

        CommandContext.Builder ctxBuilder = CommandContext.builder(finalRawMsg)
                .groupId(groupId)
                .userId(userId)
                .rawMsg(finalRawMsg)
                .isAdmin(isAdmin)
                .isAtBot(isAtBot)
                .isDebug(isDebug);

        if (rawMessage.startsWith(COMMAND_PREFIX)) {
            String[] parts = rawMessage.substring(COMMAND_PREFIX.length()).split("\\s+", 2);
            String commandKey = parts[0].toLowerCase();

            ExecuteCommand cmd = commands.get(commandKey);

            if (cmd != null) {
                String featureKey = commandFeatures.get(commandKey);

                boolean isEnabled = true;

                if (featureKey != null) {
                    isEnabled = GroupConfigManager.isFeatureEnabled(groupId, featureKey);
                }

                ctxBuilder.isEnabled(isEnabled);

                try {
                    cmd.execute(ctxBuilder.build());
                } catch (Exception e) {
                    log.error("执行命令 {} 时发生异常", commandKey, e);
                }
            }
        }
    }
}