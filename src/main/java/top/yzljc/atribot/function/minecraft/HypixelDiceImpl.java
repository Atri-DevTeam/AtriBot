package top.yzljc.atribot.function.minecraft;

import top.yzljc.atribot.configuration.RS;

import top.yzljc.atribot.chat.official.Markdown;
import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.command.CommandSender;

import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

/**
 * @Author YZ_Ljc_
 * @ClassName DiceLogic
 * @Created_at 2026/06/04
 * @Project AtriBot
 * @Package top.yzljc.atribot.functions.official
 */
public class HypixelDiceImpl {

    private static final double[] PROBABILITIES = {
            0.1872, // 1点
            0.1872, // 2点
            0.1872, // 3点
            0.1872, // 4点
            0.1872, // 5点
            0.0624, // 6点
            0.0015  // 7点
    };

    /** args 只包含投掷参数，不包含 dice 子命令名称。 */
    public static void handle(CommandSender sender, String[] args) {
        long count = 1;
        if (args.length > 0) {
            try {
                count = Long.parseLong(args[0]);
            } catch (NumberFormatException ignored) {
            }
        }

        // 防止负数捣乱
        if (count < 1) count = 1;

        if (count > 1) {
            rollMultiple(sender, count);
        } else {
            rollSingle(sender);
        }
    }

    /**
     * @param count 投掷次数，小于 1 时按一次处理
     * @param mention 单次稀有结果中的用户提及文本，允许为空字符串
     * @return 骰子模拟结果，包含原有图片标记；消息发送由调用方负责
     */
    public static String result(long count, String mention) {
        return count <= 1 ? singleResult(mention) : multipleResult(count);
    }

    private static String singleResult(String mention) {
        String url = RS.get("resource.image.dice-result-template");

        int rolledNumber = getRolledNumber();
        String imageUrl = url.replace("<id>", String.valueOf(rolledNumber));

        String text = switch (rolledNumber) {
            case 7 -> "> Your High Class Archfiend Dice rolled a 7!\n" +
                    "> Wait, a 7? But dice only have 6 sides...\n" +
                    "> Hm...?\n" +
                    "> The Dice broke apart, revealing an Archfiend Dye hidden within!\n" +
                    "> WOW! " + mention + "found Archfiend Dye!\n" +
                    "> An immensely tiny 1/666 (0.1501502%) chance!\n" +
                    "> Talk to Vincent in the Artist's Abode to learn more about this dye!";
            case 1 -> "> Your High Class Archfiend Dice rolled a 1! Bonus: -300❤";
            case 2 -> "> Your High Class Archfiend Dice rolled a 2! Bonus: -200❤";
            case 3 -> "> Your High Class Archfiend Dice rolled a 3! Bonus: -100❤";
            case 4 -> "> Your High Class Archfiend Dice rolled a 4! Bonus: +100❤";
            case 5 -> "> Your High Class Archfiend Dice rolled a 5! Bonus: +200❤";
            case 6 -> "> Your High Class Archfiend Dice rolled a 6! Bonus: +300❤";
            default -> "";
        };

        String markdown;
        if (rolledNumber == 7) {
            markdown = "![dice #80px #80px](" + imageUrl + ")\n\n" + text + "\n\n![skb #24px #24px](" + RS.get("resource.image.skyblock-logo") + ")数据来源于 Hypixel Skyblock, 结果仅供娱乐";
        } else if (rolledNumber == 6) {
            markdown = "![dice #128px #128px](" + imageUrl + ")\n\n" + text + "\n\n" + "![bank #20px #20px](" + RS.get("resource.image.skyblock-bank-logo") + ") Purse: +100M\n\n" + "![skb #24px #24px](" + RS.get("resource.image.skyblock-logo") + ")数据来源于 Hypixel Skyblock, 结果仅供娱乐";
        } else {
            markdown = "![dice #128px #128px](" + imageUrl + ")\n\n" + text + "\n\n![skb #24px #24px](" + RS.get("resource.image.skyblock-logo") + ")数据来源于 Hypixel Skyblock, 结果仅供娱乐";
        }

        return markdown;
    }

    public static void rollSingle(CommandSender sender) {
        String markdown = singleResult(Markdown.at(sender.getUserId()));
        List<List<Button>> buttons = List.of(
                List.of(
                        new Button("c1", "再掷 1 次", "/hyp dice", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                        new Button("c2", "掷 10 次", "/hyp dice 10", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                        new Button("c3", "掷 100 次", "/hyp dice 100", true, ButtonStyle.BLUE, ButtonType.COMMAND)
                )
        );

        Object button = TC.keyboard(buttons);
        if (sender instanceof QQCommandSender qq) qq.sendMessage(TC.md(markdown), button);
    }

    private static String multipleResult(long times) {

        long[] calculatedPulls = simulateMultinomial(times, PROBABILITIES);

        long[] results = new long[8];
        System.arraycopy(calculatedPulls, 0, results, 1, 7);
        StringBuilder sb = new StringBuilder();
        // 2026/9/19 删除数字显示，防止出现奇怪的东西
        sb.append("![skb #20px #20px](").append(RS.get("resource.image.dice-result-seven")).append(") **投掷").append("High Class Archfiend Dice结果：**\n\n");
        sb.append("> ").append(Markdown.img(RS.get("resource.image.dice-result-template").replace("<id>", "1"), 16, 16)).append("**1点** (-300❤)：").append(results[1]).append(" 次\n");
        sb.append("> ").append(Markdown.img(RS.get("resource.image.dice-result-template").replace("<id>", "2"), 16, 16)).append("**2点** (-200❤)：").append(results[2]).append(" 次\n");
        sb.append("> ").append(Markdown.img(RS.get("resource.image.dice-result-template").replace("<id>", "3"), 16, 16)).append("**3点** (-100❤)：").append(results[3]).append(" 次\n");
        sb.append("> ").append(Markdown.img(RS.get("resource.image.dice-result-template").replace("<id>", "4"), 16, 16)).append("**4点** (+100❤)：").append(results[4]).append(" 次\n");
        sb.append("> ").append(Markdown.img(RS.get("resource.image.dice-result-template").replace("<id>", "5"), 16, 16)).append("**5点** (+200❤)：").append(results[5]).append(" 次\n");
        sb.append("> ").append(Markdown.img(RS.get("resource.image.dice-result-template").replace("<id>", "6"), 16, 16)).append("**6点** (+300❤)：").append(results[6]).append(" 次\n");
        sb.append("> ").append(Markdown.img(RS.get("resource.image.dice-result-seven"), 16, 16)).append("**7点** (Archfiend Dye)：").append(results[7]).append(" 次！\n");

        long totalLostHealth = (results[1] * 300L) + (results[2] * 200L) + (results[3] * 100L);
        long totalGainedHealth = (results[4] * 100L) + (results[5] * 200L) + (results[6] * 300L);

        BigDecimal costMoney = BigDecimal.valueOf(times).multiply(BigDecimal.valueOf(6.6));
        BigDecimal gainedMoney = BigDecimal.valueOf(results[6]).multiply(BigDecimal.valueOf(100));
        BigDecimal netMoney = gainedMoney.subtract(costMoney);

        sb.append("---\n\n");
        sb.append("\n> ![bank #20px #20px](").append(RS.get("resource.image.skyblock-bank-logo")).append(") Purse: ").append(netMoney.compareTo(BigDecimal.ZERO) > 0 ? "+" : "")
                .append(netMoney.stripTrailingZeros().toPlainString()).append("M ")
                .append("(- ").append(costMoney.stripTrailingZeros().toPlainString()).append("M | ")
                .append("+ ").append(gainedMoney.stripTrailingZeros().toPlainString()).append("M)\n");
        sb.append("> 当前理论血量：").append(-totalLostHealth + totalGainedHealth).append("❤\n");

        sb.append("\n![skb #24px #24px](").append(RS.get("resource.image.skyblock-logo")).append(")数据来源于 Hypixel Skyblock, 结果仅供娱乐");

        return sb.toString();
    }

    public static void rollMultiple(CommandSender sender, long times) {
        String result = multipleResult(times);

        List<List<Button>> buttons = List.of(
                List.of(
                        new Button("c1", "再掷 1 次", "/hyp dice", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                        new Button("c2", "再掷 10 次", "/hyp dice 10", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                        new Button("c3", "再掷 100 次", "/hyp dice 100", true, ButtonStyle.BLUE, ButtonType.COMMAND)
                )
        );

        if (sender instanceof QQCommandSender qq) qq.sendMessage(TC.md(result), TC.keyboard(buttons));
    }

    static int getRolledNumber() {
        double r = Math.random();
        double cumulative = 0.0;
        for (int i = 0; i < PROBABILITIES.length; i++) {
            cumulative += PROBABILITIES[i];
            if (r <= cumulative) return i + 1;
        }
        return 7;
    }

    static long[] simulateMultinomial(long totalRolls, double[] probabilities) {
        long[] results = new long[probabilities.length];
        long remainingN = totalRolls;
        double remainingP = 1.0;
        Random rand = new Random();

        for (int i = 0; i < probabilities.length - 1; i++) {
            if (remainingN <= 0) break;

            double currentP = probabilities[i] / remainingP;
            if (currentP > 1.0) currentP = 1.0;
            if (currentP < 0.0) currentP = 0.0;

            long count = getFastBinomial(remainingN, currentP, rand);
            results[i] = count;

            remainingN -= count;
            remainingP -= probabilities[i];
        }

        // 最后一个概率项直接拿走剩下的所有次数，保证总和绝对吻合
        if (remainingN > 0) {
            results[probabilities.length - 1] = remainingN;
        }

        return results;
    }

    static long getFastBinomial(long n, double p, Random rand) {
        if (p >= 1.0) return n;
        if (p <= 0.0) return 0;

        if (n < 10000) {
            long count = 0;
            for (long i = 0; i < n; i++) {
                if (rand.nextDouble() < p) count++;
            }
            return count;
        }

        // 正态分布近似二项分布公式：
        // 均值 μ = np，标准差 σ = √(np(1-p))
        double mean = n * p;
        double stdDev = Math.sqrt(n * p * (1.0 - p));

        // rand.nextGaussian() 随机生成标准正态分布变量 (-3 ~ +3)
        double val = mean + rand.nextGaussian() * stdDev;

        long result = Math.round(val);

        if (result < 0) return 0;
        return Math.min(result, n);
    }
}
