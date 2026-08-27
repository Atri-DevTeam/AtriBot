package top.yzljc.atribot.function.command;

import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.database.repo.TufeElecRepository;
import top.yzljc.atribot.function.impl.tufe.TufeElectricService;
import top.yzljc.atribot.service.runtime.ThreadManager;

/**
 * @Author YZ_Ljc_
 * @ClassName TufeElectricBind
 * @Created_at 2026/08/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.tufe
 */
public class TufeElectricBindCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender user)) return true;

        ThreadManager.execute(() -> handleBind(user, args));
        return true;
    }

    private void handleBind(QQCommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage("用法：/绑定 <宿舍号> <校区> <类型>");
            return;
        }

        try {
            long roomNum = Long.parseLong(args[0]);
            int schoolRegion = Integer.parseInt(args[1]);
            int type = Integer.parseInt(args[2]);

            if (schoolRegion != 1 && schoolRegion != 2) {
                sender.sendMessage("未知的校区类型");
                return;
            }
            if (type != 0 && type != 1) {
                sender.sendMessage("未知的电表类型");
                return;
            }

            TufeElectricService.CheckData checkData = TufeElectricService.query(roomNum, schoolRegion);
            if ("-".equals(checkData.rec())) {
                sender.sendMessage("无法查询到该电表信息");
                return;
            }

            sender.sendMessage(TufeElecRepository.bind(sender.getUserId(), roomNum, schoolRegion, type) ? "绑定成功" : "绑定失败");
        } catch (NumberFormatException e) {
            sender.sendMessage("参数格式错误");
        }
    }
}
