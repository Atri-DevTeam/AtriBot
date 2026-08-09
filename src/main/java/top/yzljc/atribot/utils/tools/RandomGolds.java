package top.yzljc.atribot.utils.tools;

import java.util.random.RandomGenerator;

/**
 * @Author YZ_Ljc_
 * @ClassName RandomGolds
 * @Created_at 2026/08/03
 * @Project AtriMeow
 * @Package top.yzljc.atribot.utils.tools
 */
public final class RandomGolds {

    private static final RandomGenerator RANDOM_GENERATOR = RandomGenerator.getDefault();

    /**
     * 输入区间范围（不区分大小），当输入的两个数值一样时，返回该值，不经过随机计算逻辑
     */
    public static int get(int a, int b) {

        if (a == b) return a;

        if (a > b) {
            int tmp = a;
            a = b;
            b = tmp;
        }
        return RANDOM_GENERATOR.nextInt(a, b + 1);
    }
}