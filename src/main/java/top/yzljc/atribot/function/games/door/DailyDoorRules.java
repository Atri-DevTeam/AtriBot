package top.yzljc.atribot.function.games.door;

import java.util.List;
import java.util.random.RandomGenerator;

/**
 * @Author YZ_Ljc_
 * @ClassName DailyDoorRules
 * @Created_at 2026/10/28
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.games.door
 */
public final class DailyDoorRules {
    public static final double INITIAL_REFERENCE_VISITORS = 100;
    private static final double MINIMUM_ATTEMPT_RATIO = .3;
    private static final List<String> FAILURES = List.of(
            "你握住门把手，推了一下。\n\n门没有打开。",
            "门把手纹丝不动。", "你轻轻敲了敲门。\n\n没有回应。",
            "门依旧紧闭。", "你试着拉了一下。\n\n什么都没有发生。",
            "你把耳朵贴上门板。\n\n只听见自己的呼吸。",
            "你敲了三下，等了一会儿。\n\n安静仍然是安静。",
            "你向后退了一步，再上前推门。\n\n门没有改变主意。",
            "把手有些凉。\n\n你松开手，门还是原来的样子。",
            "你试着从门缝往里看。\n\n里面没有光。",
            "你找了找钥匙孔。\n\n这扇门似乎没有留钥匙孔。",
            "你很有礼貌地敲了门。\n\n门很有礼貌地保持了沉默。",
            "你推门的时候，鞋底在地上滑了一下。\n\n门倒是站得很稳。",
            "你等到周围安静下来，才转动把手。\n\n周围一直很安静。",
            "你伸出手，又停了一下。\n\n最后还是试了。没有打开。",
            "你沿着门框摸了一圈。\n\n没有开关，也没有纸条。",
            "你轻声说了一句打扰了。\n\n门没有回应。",
            "门上没有写推，也没有写拉。\n\n你都试了。",
            "你把力气用在了把手上。\n\n把手把力气还了回来。",
            "你退远了一点，重新看了看这扇门。\n\n它仍然只是一扇关着的门。");
    private static final List<String> SOUNDS = List.of(
            "你听见门后传来一声轻响。像有人把杯子放回桌面。",
            "里面有纸张翻动的声音。你停下来听，它也停了。",
            "门后响起了两下敲门声。比你少一下。",
            "很远的地方，好像有一串钥匙落在地上。",
            "你听见一小段钢琴声。弹到第三个音，就没有了下文。",
            "门后似乎有人挪了一下椅子。随后又安静下来。");
    private static final List<String> MOVEMENTS = List.of(
            "门把手似乎转动了一点。你松开手，它又回到了原位。",
            "门板轻轻震了一下，像是另一边也有人试着推门。",
            "门底吹出一阵风，把你脚边的一粒灰尘送远了。",
            "你敲门时，把手跟着晃了一下。不是你碰的那一下。",
            "门向里让了很小的一点，又轻轻抵住了。",
            "一小片纸屑从门缝里滑出来。上面什么也没有。");
    private static final List<String> LIGHTS = List.of(
            "门缝里闪过一点光，像有人划亮了一根火柴。",
            "门底亮起一道细线。你眨了眨眼，它就消失了。",
            "把手上掠过一道光。你回过头，身后仍然很暗。",
            "门框边缘泛起一点暖色，很快又暗下去。",
            "门缝里透出一瞬蓝光，像一台刚刚关掉的电视。",
            "门底漏出几块摇晃的光斑。也许另一边有一棵树。");
    private static final List<String> ENDINGS = List.of(
            "里面只有一把椅子，正对着窗外的落日。",
            "一只猫走了出来，伸了个懒腰，又慢悠悠地走远了。",
            "一张空白明信片躺在地上，背面画着另一扇门。",
            "门后是一片草地，风刚好吹过。",
            "桌上摆着一杯还冒着热气的茶。没有人坐在那里。",
            "一架纸飞机从门里飞了出来，落在你的脚边。",
            "里面是一间空房间，阳光在地板上画了一个方块。",
            "门后传来收音机的沙沙声，随后是一段很轻的音乐。",
            "一颗玻璃弹珠滚了出来，停在门槛旁边。",
            "门后是一条通向海边的小路。今天的海很平静。",
            "一盏台灯亮着，桌上的书刚好翻到最后一页。",
            "里面什么也没有，只有一阵带着雨后气味的风。",
            "门后是一间打烊的面包店。柜台上留着最后一个可颂。",
            "一张餐桌摆在房间中央，椅子都收好了，只有一把稍稍拉开。",
            "里面正在下雪。雪落在门槛前，便不再往外飘。",
            "门后有一座小小的站台。时刻表上只写着：还来得及。",
            "地板上画着跳房子的格子。最后一格，画了一轮歪歪的月亮。",
            "一只橘猫睡在键盘上。屏幕里只有一长串还没打完的字母。",
            "门后是一间洗衣房。唯一转着的滚筒里，有一只红袜子。",
            "窗边放着两杯水。一杯盛着夕阳，另一杯已经凉了。",
            "里面是刚下过雨的操场。球停在球门前，没人急着踢最后一下。",
            "一辆自行车靠着墙，车筐里放着一束用报纸包好的花。",
            "门后是一片麦田。风走过去的时候，田里留下了一条路。",
            "一只小狗叼来一只拖鞋，放在门口，认真地看着你。",
            "房间里挂着许多钟。只有最小的那一只，还在慢慢走。",
            "门后有一台自动售货机。最下面的取物口里，躺着一颗橘子。",
            "里面是一座空剧场。幕布没有拉开，观众席上亮着一盏阅读灯。",
            "一根羽毛从上方飘下来。你抬头时，天花板已经变成了天空。",
            "门后是一条深夜的小巷。尽头的早餐店，刚把灯打开。",
            "桌上放着一副拼图。最后一块不见了，但画里的海依然完整。",
            "一条很长的走廊铺着柔软的地毯。尽头的门上写着：今天休息。",
            "里面是一间旧教室。黑板擦得很干净，角落还留着半朵粉笔画的花。",
            "门后有一棵树，叶子都是小小的纸船。风来的时候，没有一只掉下来。",
            "一盏路灯立在房间中央。灯下，是一小块刚好够两个人站的雪地。",
            "里面放着一台老游戏机。画面停在存档成功，没有催你继续。",
            "一只乌龟正在慢慢跨过门槛。看起来，它比你更早开始这趟旅程。",
            "门后是一家没有店员的花店。每个花盆旁边，都放着一把小伞。",
            "窗台上的风铃没有响。倒是旁边那只猫，打了一个很大的哈欠。",
            "一张唱片转到末尾，还在轻轻沙沙作响。有人把针放得很小心。",
            "门后是一片浅浅的水。水里倒映着白天，门外却已经是晚上。",
            "里面有一间小厨房。锅盖轻轻跳着，空气里是米饭的味道。",
            "一张纸条压在杯底：门有点难开，谢谢你来。",
            "门后是一座图书馆。书脊上没有名字，夹着的书签倒是五颜六色。",
            "地上放着一个空纸箱。一只猫走过去，把空这个字取消了。",
            "里面是一片沙滩。潮水刚退，留下一个很小、很完整的贝壳。",
            "一把伞撑在房间里。伞面上落着雨，伞外却很晴朗。",
            "门后有一张办公桌。待办事项全被划掉，最后一行是：早点回家。",
            "一只气球系在椅背上，已经有点瘪了，仍然努力朝上飘着。",
            "房间中央摆着一盘切好的西瓜。窗外，蝉刚好停了一会儿。",
            "门后有一条小河。对岸挂着一串灯，每盏都比水里的倒影亮一点。",
            "里面是一间玩具店。小火车绕了一圈，准时停在你的脚边。",
            "一张空白画布对着窗。今天的晚霞，已经替它想好了颜色。",
            "门后是一间修表铺。老师傅不在，门边留着一碗给猫的水。",
            "桌上摊着一封没写完的信。只写了开头：今天发生了一件小事。",
            "里面有一架钢琴。琴凳上坐着一只玩具熊，似乎等了很久的掌声。",
            "门后是一片竹林。风吹过来时，整间屋子都轻轻点了点头。",
            "一双雨靴整齐地放在门边。里面各长出了一朵小花。",
            "房间里摆着一张折叠床。被子晒过，旁边留着一盏可以伸手关掉的灯。",
            "门后没有房间，只有傍晚的天空。一群鸟刚好飞过。",
            "里面还有一扇门，不过是画在墙上的。画旁边写着：这次真的到底了。");

    private DailyDoorRules() {}

    public record Feedback(String type, String text) {}

    /**
     * 按近期参与规模调整参考人数，每次调整限制在上一记录的 75%～125%
     *
     * @param averageVisitors 最近七天日均独立访问人数
     * @param previousReference 上一次保存的参考人数，无记录时使用冷启动值
     * @return 当天固定使用的参考人数，至少为 1
     */
    public static double referenceVisitors(double averageVisitors, double previousReference) {
        double previous = previousReference > 0 ? previousReference : INITIAL_REFERENCE_VISITORS;
        return Math.max(1, Math.max(previous * .75, Math.min(previous * 1.25, averageVisitors)));
    }

    /**
     * 最低尝试人数为参考人数的 30%，向上取整；达到门槛的当次开始随机判定
     * <p>设门槛为 M，k = attemptNumber - M + 1，S = max(1, N - M + 1)
     * 本次条件概率为 1-exp(-(2k-1)/S²)，达到门槛后的累计开启概率为 1-exp(-(k/S)²)
     *
     * @param attemptNumber 当天第几次有效尝试，从 1 开始
     * @param referenceVisitors 当天固定的参考人数 N
     * @return 本次尝试的开门概率
     */
    public static double openingChance(long attemptNumber, double referenceVisitors) {
        if (attemptNumber < 1 || !Double.isFinite(referenceVisitors) || referenceVisitors < 1)
            throw new IllegalArgumentException("Invalid door probability parameters");
        long minimumAttempts = (long) Math.ceil(referenceVisitors * MINIMUM_ATTEMPT_RATIO);
        if (attemptNumber < minimumAttempts) return 0;
        long eligibleAttempt = attemptNumber - minimumAttempts + 1;
        double scale = Math.max(1, referenceVisitors - minimumAttempts + 1);
        return Math.min(Math.nextDown(1.0), -Math.expm1(-(2.0 * eligibleAttempt - 1) / scale / scale));
    }

    public static boolean opens(RandomGenerator random, long attemptNumber, double referenceVisitors) {
        return random.nextDouble() < openingChance(attemptNumber, referenceVisitors);
    }

    public static String ending(RandomGenerator random) {
        return ENDINGS.get(random.nextInt(ENDINGS.size()));
    }

    public static Feedback failure(RandomGenerator random) {
        if (random.nextInt(100) < 5) {
            int type = random.nextInt(3);
            var texts = switch (type) {
                case 0 -> SOUNDS;
                case 1 -> MOVEMENTS;
                default -> LIGHTS;
            };
            String result = switch (type) {
                case 0 -> "RARE_SOUND";
                case 1 -> "RARE_MOVEMENT";
                default -> "RARE_LIGHT";
            };
            return new Feedback(result, texts.get(random.nextInt(texts.size())) + "\n\n门依然没有打开。");
        }
        return new Feedback("NORMAL_FAIL", FAILURES.get(random.nextInt(FAILURES.size())));
    }
}
