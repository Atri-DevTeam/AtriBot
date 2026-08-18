package top.yzljc.atribot.function.official.tufe;

import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.database.repo.TufeElecRepository;
import top.yzljc.atribot.service.runtime.ThreadManager;

/**
 * @Author YZ_Ljc_
 * @ClassName TufeElectricQuery
 * @Created_at 2026/08/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.tufe
 */
public class TufeElectricQuery implements CommandExecutor {
    private final int type;
    private final String commandName;
    private final String meterName;

    public TufeElectricQuery(int type, String commandName, String meterName) {
        this.type = type;
        this.commandName = commandName;
        this.meterName = meterName;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender user)) return true;

        ThreadManager.execute(() -> handleQuery(user, args));
        return true;
    }

    private void handleQuery(QQCommandSender sender, String[] args) {
        if (args.length > 2) {
            sender.sendMessage("用法：/" + commandName + " [门牌号] [校区]");
            return;
        }

        try {
            long roomNum;
            int schoolRegion;
            String feedback;

            if (args.length == 0) {
                ElecDTO bind = TufeElecRepository.getDataByOpenIdAndType(sender.getUserId(), type);
                if (bind == null) {
                    sender.sendMessage("当前未绑定" + meterName + "信息，请使用/绑定 <宿舍号> <校区> <类型>进行绑定");
                    return;
                }
                roomNum = bind.roomId();
                schoolRegion = bind.schoolRegion();
                feedback = "当前已绑定" + meterName + "信息，默认查询" + meterName + "信息";
            } else {
                roomNum = Long.parseLong(args[0]);
                schoolRegion = args.length == 2 ? Integer.parseInt(args[1]) : 1;
                feedback = "查询成功";
            }

            if (schoolRegion != 1 && schoolRegion != 2) {
                sender.sendMessage("未知的校区类型");
                return;
            }

            TufeElectricService.CheckData checkData = TufeElectricService.query(roomNum, schoolRegion);
            String lastCommand = "/" + commandName + (args.length == 0 ? "" : " " + roomNum + " " + schoolRegion);
            sender.sendMessage(
                    TC.md(TufeElectricService.formatInfo(feedback, TufeElectricService.formatQueryResult(checkData, type))),
                    TufeElectricService.getKeyboard(sender.getUserId())
            );
        } catch (NumberFormatException e) {
            sender.sendMessage("参数格式错误，请使用 /查询帮助 查看帮助");
        }
    }
}
