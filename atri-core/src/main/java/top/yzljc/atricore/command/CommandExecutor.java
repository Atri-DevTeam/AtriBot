package top.yzljc.atricore.command;

/**
 * 文本指令执行接口，参数由指令分发器按空白字符拆分
 *
 * @Author YZ_Ljc_
 * @ClassName CommandExecutor
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.command
 */
public interface CommandExecutor {
    /**
     * 处理已匹配的文本指令
     *
     * @param sender  指令发送者
     * @param command 匹配到的主指令
     * @param label   本次分发使用的标签，可能是别名或无前缀映射后的名称
     * @param args    不包含标签的参数数组，按空白拆分，不解析引号
     * @return 已处理时返回 true；返回 false 时 CommandFeature 发送非空的用法提示
     */
    boolean onCommand(CommandSender sender, Command command, String label, String[] args);
}