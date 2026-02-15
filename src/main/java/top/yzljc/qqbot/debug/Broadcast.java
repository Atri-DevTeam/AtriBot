package top.yzljc.qqbot.debug;

import top.yzljc.qqbot.botkits.findinfo.GetGroupList;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.botkits.thread.ThreadManager;
import top.yzljc.qqbot.command.CommandContext;
import top.yzljc.qqbot.command.ExecuteCommand;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.botkits.tools.FT;

import java.util.Set;

public class Broadcast implements ExecuteCommand {
    private static final Set<Long> GroupList = GetGroupList.fetchAllGroupIds();
    static Settings settings = Config.getInstance();
    private static final long DebugGroupId = settings.getDebugGroupId();

    @Override
    public void execute(CommandContext ct) {
        if (!ct.getIsAdmin()) return;
        if (ct.getIsDebug()){
            debugBroadcastRequest(ct.getRawMsg().substring(3).trim());
        }else{
            fecthToGroups(ct.getRawMsg().substring(3).trim());
        }
    }

    private static void fecthToGroups(String message){
        for (long gid : GroupList) {
            ThreadManager.execute(() -> MessageSender.sendGroupMessage(gid, FT.unescape(message)));
        }
    }

    private static void debugBroadcastRequest(String message){
        MessageSender.sendGroupMessage(DebugGroupId, FT.unescape(message));
    }
}
