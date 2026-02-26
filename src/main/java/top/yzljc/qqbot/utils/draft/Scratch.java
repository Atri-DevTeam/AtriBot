package top.yzljc.qqbot.utils.draft;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import top.yzljc.qqbot.botkits.userinfo.GetUserInfo;
import top.yzljc.qqbot.botkits.message.MessageSender;
import top.yzljc.qqbot.botkits.thread.ThreadManager;
import top.yzljc.qqbot.config.Config;
import top.yzljc.qqbot.config.Settings;
import top.yzljc.qqbot.feature.LikeUser;

import java.util.List;
import java.util.concurrent.*;

public class Scratch {
    private static final Logger log = LoggerFactory.getLogger(Scratch.class);
    static Settings settings = Config.getInstance();
    private static final long GROUP_ID = settings.getManosabaGroupId();
    private static int huffCounts = 0;
    private static ScheduledFuture<?> autoSettleTask = null;
    private static final long SETTLE_DELAY = 8L;

    public static synchronized void huffCount() {
        huffCounts++;
        resetSettleTimer();
    }

    private static void huffResult(int num){
        String result = "喜报！你此次共哈气 " + num + " 次！";
        MessageSender.sendGroupMessage(GROUP_ID, result);
        log.info("用户在群 {} 哈气 {} 次。", GROUP_ID, num);
    }

    public static synchronized void stopHuff(){
        if (autoSettleTask != null) {
            autoSettleTask.cancel(false);
            autoSettleTask = null;
        }
        if (huffCounts >= 5){
            huffResult(huffCounts);
        }
        huffCounts = 0;
    }

    private static void resetSettleTimer() {
        if (autoSettleTask != null) {
            autoSettleTask.cancel(false);
        }
        autoSettleTask = ThreadManager.setSchedule(() -> {
            synchronized (Scratch.class) {
                stopHuff();
            }
        }, SETTLE_DELAY, TimeUnit.SECONDS);
    }

    public static void shizoukiaGroupNameChange(Long userId, String newName){
        String userName = GetUserInfo.getUserName(userId);
        String resultMsg = userName + "修改了群名称为\"" + newName + "\"";
        MessageSender.sendGroupMessage(820103390L, resultMsg);
        log.info("已向群 820103390 通知用户 {} 修改群名称为 {}", userName, newName);
    }

//    private static final List<Long> beingAutoLikedUser = List.of(3199590352L, 1948308L, 1955248991L,3052381496L,3388215589L,1724175133L, 3414769292L);
//    public static void scheduledAutoLike(){
//        for (Long userId : beingAutoLikedUser) {
//            String userName = GetUserInfo.getUserName(userId);
//            String resultMsg = "自动点赞 " + userName + "！";
//            LikeUser.processCommand(userId, 818804507L);
//            MessageSender.sendGroupMessage(818804507L, resultMsg);
//            log.info("已向群 820103390 自动点赞用户 {}", userName);
//        }
//    }
}