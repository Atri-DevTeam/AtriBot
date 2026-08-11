package top.yzljc.atribot.test;

import top.yzljc.atribot.chat.official.Markdown;
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
 * @ClassName MarkdownDisplayTest
 * @Created_at 2026/08/11
 * @Project AtriMeow
 * @Package top.yzljc.atribot.test
 */
public class MarkdownDisplayTest implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (!sender.hasPermission()) return true;
        if (!(sender instanceof QQCommandSender qq)) return true;

        Markdown md = TC.md("# 一级标题\n" +
                "## 二级标题\n" +
                "### 三级标题\n" +
                "#### 四级标题\n" +
                "##### 五级标题\n" +
                "###### 六级标题\n" +
                " \n" +
                "**加粗文本**\n" +
                "__下划线加粗__\n" +
                "_斜体文本_\n" +
                "*星号斜体*\n" +
                "***加粗斜体***\n" +
                "~~删除线文本~~\n" +
                " \n" +
                "链接\n" +
                "直接显示地址：<https://qun.qq.com>\n" +
                Markdown.link("https://qun.qq.com/qunpro/robot/qunshare?robot_uin=3889798968&robot_appid=102808581&sceneData=Y2m6DyvYd2SX5MyQfppq4axrIRdiOobQ6HDJim4ofdHS4e7PXVyNveeS8neRVhk4WdeLSBVcwJqpoXQTamuFFFC", "外显链接") + "\n" +
                " \n" +
                "图片\n" +
                Markdown.img("test", ResourcesProperties.SKB_BANK_LOGO_IMG, 20, 20) + "\n" +
                " \n" +
                "有序列表\n" +
                "1. 第一项\n" +
                "2. 第二项\n" +
                "    - 嵌套无序列表\n" +
                "    - 继续嵌套\n" +
                "3. 第三项\n" +
                " \n" +
                "## 无序列表\n" +
                "- 列表项一\n" +
                "- 列表项二\n" +
                "    - 嵌套有序列表\n" +
                "        1. 二级列表\n" +
                "        2. 继续列表\n" +
                "- 列表项三\n" +
                " \n" +
                "## 块引用\n" +
                "> 青青子衿，悠悠我心，但为君故，沉吟至今\n" +
                "> 四月维夏，六月徂暑。先祖匪人，胡宁忍予\n" +
                " \n" +
                "## 水平分割线\n" +
                " \n" +
                "----\n" +
                "分割线，三个及以上-\n" +
                " \n" +
                "***\n" +
                "星号分割线\n" +
                " \n" +
                "## 表格\n" +
                "| 列1 | 列2 | 列3 |\n" +
                "| --- | --- | --- |\n" +
                "| 行1 | 单元格1 | 单元格2 |\n" +
                "| 行2 | 单元格3 | 单元格4 |\n" +
                " \n" +
                "## 代码块\n" +
                "```python\n" +
                "print(\"Hello, World!\")\n" +
                "```\n" +
                " \n" +
                "## 数学公式\n" +
                "$$\n" +
                "E=mc^2\n" +
                "$$\n" +
                "$$\n" +
                "E=mc^2\n" +
                "$$\n" +
                " \n" +
                "## 交互文本\n" +
                "<qqbot-cmd-input text=\"交互文本数据\" show=\"交互文本\" reference=\"false\"/>\n" +
                "<qqbot-cmd-input text=\"引用交互文本数据\" show=\"引用交互文本\" reference=\"true\"/>\n" +
                "## 特殊文本\n" +
                "'''哈哈哈哈'''\n$\\Huge{示例文字}$\n$\\small{小}$\n$\\tiny{极小}$\n$\\boxed{重点内容}$\n" +
                "## 颜色代码\n" +
                "$\\textcolor{#27AE60}{\\text{我}}\\textcolor{#27AE60}{\\text{真}}\\textcolor{#E74C3C}{\\text{厉}}\\textcolor{#FF8C00}{\\text{害}}$");

        Object keyboard = TC.keyboard(
                List.of(
                        List.of(
                                new Button("t0", "样式0", "这里什么都没有", false, ButtonStyle.GRAY, ButtonType.COMMAND),
                                new Button("t1", "样式1", "这里什么都没有", false, ButtonStyle.BLUE, ButtonType.COMMAND),
                                new Button("t2", "样式2", "这里什么都没有", false, ButtonStyle.ICON_BUTTON, ButtonType.COMMAND)
                        ),
                        List.of(
                                new Button("t3", "样式3", "这里什么都没有", false, ButtonStyle.RED, ButtonType.COMMAND),
                                new Button("t4", "样式4", "这里什么都没有", false, ButtonStyle.BLUE_WITH_BACKGROUND, ButtonType.COMMAND),
                                new Button("t5", "链接样式", "https://qun.qq.com/qunpro/robot/qunshare?robot_uin=3889798968&robot_appid=102808581&sceneData=Y2m6DyvYd2SX5MyQfppq4axrIRdiOobQ6HDJim4ofdHS4e7PXVyNveeS8neRVhk4WdeLSBVcwJqpoXQTamuFFFC", false, ButtonStyle.ICON_BUTTON, ButtonType.LINK
                        )
                )
        ));

        qq.sendMessage(md, keyboard, false);
        return true;
    }
}