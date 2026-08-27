package top.yzljc.atribot.function.impl.tufe;

import top.yzljc.atribot.chat.official.TC;
import top.yzljc.atribot.chat.official.button.Button;
import top.yzljc.atribot.chat.official.button.ButtonStyle;
import top.yzljc.atribot.chat.official.button.ButtonType;
import top.yzljc.atribot.command.Command;
import top.yzljc.atribot.command.CommandExecutor;
import top.yzljc.atribot.command.CommandSender;
import top.yzljc.atribot.command.QQCommandSender;
import top.yzljc.atribot.configuration.ResourcesProperties;

import java.util.List;

/**
 * @Author YZ_Ljc_
 * @ClassName TufeCheckHelp
 * @Created_at 2026/08/18
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.official.tufe
 */
public class TufeCheckHelp implements CommandExecutor {

    private static final String HELP_INFO = """
            ![tufe #35px #34px](%s) **天津财经大学电表查询**

            **参数说明：**

            校区：`1` - 大学生宿舍 `2` - 校内宿舍

            类型：`0` - 宿舍电表 `1` - 空调电表

            **同一账号仅允许绑定一个宿舍电表和一个空调电表**

            **常用指令：**

            > /绑定 <宿舍号> <校区> <类型> - 信息查询绑定（再次绑定会覆盖先前的绑定数据）
            > /宿舍电表 [门牌号] [校区] - 查询宿舍电表信息（若已绑定，参数不用输入）
            > /空调电表 [门牌号] [校区] - 查询空调电表信息（若已绑定，参数不用输入）
            > /反馈 <内容> - 反馈问题或建议

            **在不输入校区和类型的情况下，默认查询宿舍电表并且校区为大学生宿舍**
            """.formatted(ResourcesProperties.TUFE_LOGO_IMG);

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof QQCommandSender user)) return true;
        Object keyboard = TC.keyboard(
                List.of(List.of(
                                new Button("c1", "绑定", "/绑定 ", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c2", "宿舍电表", "/宿舍电表 ", true, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("c3", "空调电表", "/空调电表 ", true, ButtonStyle.BLUE, ButtonType.COMMAND)
                        ),
                        List.of(
                                new Button("c3", "向开发者反馈", "/feedback <反馈内容>", true, ButtonStyle.BLUE, ButtonType.COMMAND)
                        ))
        );
        user.sendMessage(TC.md(HELP_INFO), keyboard);
        return true;
    }
}