package top.yzljc.atribot.command;

import java.util.Set;

/**
 * 运行服务开放给群主配置的指令及频控计数范围
 *
 * @Author YZ_Ljc_
 * @ClassName GroupCommands
 * @Created_at 2026/10/24
 * @Project AtriMeow
 * @Package top.yzljc.atribot.command
 */
public final class GroupCommands {
    private GroupCommands() {
    }

    public static void register(CommandSettingsService service) {
        if (!service.registrations().isEmpty()) return;
        service.register("minesweeper", "扫雷", true, true,
                (label, args) -> args.length > 0 && "start".equalsIgnoreCase(args[0]));
        service.register("tictactoe", "井字棋", true, true, (label, args) -> args.length == 0);
        service.register(new CommandSettingsService.Registration("connect4", "四子棋", true, true,
                Set.of("connect4", "四子棋"), (label, args) -> args.length == 0));
        service.register("clicktrain", "反应力测试", true, true,
                (label, args) -> args.length == 0 || !"放弃".equals(args[0]));
        service.register("roulette", "幸运轮盘", true, true,
                (label, args) -> args.length > 0 && "start".equalsIgnoreCase(args[0]));
        service.register("rsp", "石头剪刀布", true, true, (label, args) -> true);
        service.register("sound", "听声辨物", true, true, (label, args) -> args.length == 0);
        service.register("fish", "今日钓鱼", true, true, (label, args) -> true);
        service.register("item", "物品卡", true, true, (label, args) -> true);
        service.register("ec", "随机附魔", true, true, (label, args) -> args.length > 0);
    }
}
