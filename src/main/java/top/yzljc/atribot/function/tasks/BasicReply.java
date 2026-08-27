package top.yzljc.atribot.function.tasks;

import top.yzljc.atribot.event.EventHandler;
import top.yzljc.atribot.event.Listener;
import top.yzljc.atribot.event.events.OfficialC2CMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupAtMessageCreateEvent;
import top.yzljc.atribot.event.events.OfficialGroupMessageCreateEvent;

import java.util.Set;

/**
 * @Author YZ_Ljc_
 * @ClassName BasicReply
 * @Created_at 2026/08/08
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official
 */
public class BasicReply implements Listener {

    private static final String CIALLO = "Ciallo～ (∠・ω< )⌒★";
    public static final Set<String> REPLIES = Set.of(
            "嘻嘻",
            "嘿嘿",
            "好哦",
            "好哒",
            "知道啦",
            "收到收到",
            "你也是呢",
            "你最好啦",
            "你最可爱",
            "乖啦乖啦",
            "好啦好啦",
            "没毛病",
            "原来如此",
            "可不是嘛",
            "已读乱回",
            "明白了",
            "懂了懂了",
            "没问题",
            "那必须的",
            "真的假的",
            "学到了",
            "你开心就好啦",
            "反弹",
            "醒醒",
            "你猜",
            "我就不",
            "偏不",
            "下次一定",
            "溜了溜了",
            "我在听",
            "你说你说",
            "吃瓜",
            "吃瓜中",
            "我只是个机器人",
            "电量不足",
            "信号不好",
            "听不懂",
            "这题超纲了",
            "这题我不会",
            "下一题",
            "换一个",
            "聊点别的",
            "今天天气不错",
            "吃了吗",
            "多喝热水",
            "早点睡",
            "晚安",
            "好梦"
    );
    public static final Set<String> TRIGGER_WORDS = Set.of(
            "我喜欢你", "爱你", "爱死你", "l love you", "love you", "hello world",
            "亲亲", "mua", "啵一个", "亲一下", "kiss", "喜欢",
            "摸摸", "摸头", "抱抱", "揉揉", "蹭蹭", "贴贴", "摸", "亚托莉",
            "想你了", "在干嘛", "出来玩", "陪我", "rua", "晚安", "可爱", "聊天"
    );

    @EventHandler
    public void onC2CMessage(OfficialC2CMessageCreateEvent event) {
        if (event.getMessage().isCommand()) return;
        if (event.shouldIgnore()) return;
        String content = event.getMessage().getContent().trim();

        if (content.contains("你好") || content.toLowerCase().contains("hello") || content.toLowerCase().contains("ciallo") || content.toLowerCase().contains("hi")) {
            event.sendMessage(CIALLO);
            return;
        }

        for (var w : TRIGGER_WORDS) {
            if (content.contains(w)) {
                event.sendMessage(getRandomReply());
                return;
            }
        }
    }

    @EventHandler
    public void onGroupAtMessage(OfficialGroupAtMessageCreateEvent event) {
        if (event.getMessage().isCommand()) return;
        if (event.shouldIgnore()) return;
        String content = event.getMessage().getContent().trim();

        if (content.contains("你好") || content.toLowerCase().contains("hello") || content.contains("ciallo")) {
            event.sendMessage(CIALLO);
            return;
        }

        for (var w : TRIGGER_WORDS) {
            if (content.contains(w)) {
                event.sendMessage(getRandomReply());
                return;
            }
        }
    }

    @EventHandler
    public void onGroupMessage(OfficialGroupMessageCreateEvent event) {
        if (event.getMessage().isCommand()) return;
        if (event.shouldIgnore()) return;
        if (!event.isAtBot()) return;
        String content = event.getMessage().getContent().trim();

        if (content.contains("你好") || content.toLowerCase().contains("hello") || content.contains("ciallo")) {
            event.sendMessage(CIALLO);
            return;
        }

        for (var w : TRIGGER_WORDS) {
            if (content.contains(w)) {
                event.sendMessage(getRandomReply());
                return;
            }
        }
    }

    public static String getRandomReply() {
        int index = (int) (Math.random() * REPLIES.size());
        return REPLIES.stream().skip(index).findFirst().orElse("嘻嘻");
    }
}