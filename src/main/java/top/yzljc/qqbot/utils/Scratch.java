package top.yzljc.qqbot.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;

public class Scratch {
    private static final Logger log = LoggerFactory.getLogger(Scratch.class);
    static Settings settings = Config.getInstance();
    private static final long GROUP_ID = settings.getManosabaGroupId();
    private static int huffCounts = 0;

    public static void huffCount(){
        huffCounts++;
    }

    private static void huffResult(int num){
        String result = "喜报！你此次共哈气 " + num + " 次！";
        MessageSender.sendGroupMessage(GROUP_ID, result);
        log.info("用户在群 {} 哈气 {} 次。", GROUP_ID, num);
    }

    public static void stopHuff(){
        if (huffCounts >= 5){
            huffResult(huffCounts);
        }
        huffCounts = 0;
    }
}
