package top.yzljc.qqbot.command;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.findinfo.GetBotInfo;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.config.groups.GroupConfigInfo;
import top.yzljc.qqbot.config.groups.GroupConfigManager;
import top.yzljc.qqbot.debug.Broadcast;
import top.yzljc.qqbot.debug.RecallLastMsg;
import top.yzljc.qqbot.feature.github.WebhookServer;
import top.yzljc.qqbot.feature.minecraft.MojangStatus;
import top.yzljc.qqbot.feature.minecraft.Motd;
import top.yzljc.qqbot.feature.minecraft.ServerStatus;
import top.yzljc.qqbot.feature.news.HypixelNews;
import top.yzljc.qqbot.feature.news.MinecraftNews;
import top.yzljc.qqbot.feature.schedule.*;
import top.yzljc.qqbot.utils.CommandHelp;

import java.util.HashMap;
import java.util.Map;

public class CommandManager {
    private static final Logger log = LoggerFactory.getLogger(CommandManager.class);
    private static final Settings settings = Config.getInstance();
    private static final String COMMAND_PREFIX = settings.getCommandPrefix();
    private static final long BOT_QQ = GetBotInfo.getBotId();
    private static final String DEBUG_SUFFIX = "--debug";

    private static final Map<String, ExecuteCommand> commands = new HashMap<>();
    private static final Map<String, String> commandFeatures = new HashMap<>();

    static {
        registerCommand("happynewyear", new HappyNewYear(), "new_year");
        registerCommand("bc", new Broadcast(),"broadcast");
        registerCommand("wakeup", new WakeUp(), "wakeup_send");
        registerCommand("reboot", new Reboot(), null);
        registerCommand("manodate", new ManosabaDate(), null);
        registerCommand("github", new WebhookServer(), "github_info");
        registerCommand("recall", new RecallLastMsg(), null);
        registerCommand("motd", new Motd(), "motd");
        registerCommand("mojang", new MojangStatus(), "mojang_status");
        registerCommand("checkmcnews", new MinecraftNews(), "mc_news");
        registerCommand("checkhypnews", new HypixelNews(), "hyp_news");
        registerCommand("signall", new AutoSign(), "auto_sign");
        registerCommand("rollback", new RollbackMessages(), null);
        registerCommand("stats", new MessageStats(), null);
        registerCommand("serverstatus", new ServerStatus(), null);
        registerCommand("groupinfo", new GroupConfigInfo(), null);
        registerCommand("help", new CommandHelp(), null);
        registerCommand("update",new WebhookServer(),"github_info");
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
        boolean isAdmin = settings.getAdminUids().contains(userId);
        boolean isAtBot = rawMessage.contains("[CQ:at,qq=" + BOT_QQ + "]");
        boolean isDebug = false;
        String finalRawMsg = rawMessage;

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