package top.yzljc.atricore.core;

import java.util.Optional;

/**
 * 运行服务提供的指令资源与业务策略，默认不限制指令执行
 *
 * @Author YZ_Ljc_
 * @ClassName BotCommand
 * @Created_at 2026/10/15
 * @Project AtriMeow
 * @Package top.yzljc.atricore.core
 */
public class BotCommand {
    /**
     * 指定指令声明资源
     *
     * @return 由 CommandManager 的类加载器读取的资源路径，默认是 atribot.yml
     */
    public String commandResource() { return "atribot.yml"; }
    /**
     * 提供需要回复未知指令时使用的提示
     *
     * @return 未知指令提示文本
     */
    public String unknownCommandMessage() { return "未知指令"; }
    /**
     * 补充 QQMessage.isCommand 的业务判断，不参与指令注册或分发
     *
     * @param content 去除首尾空白后的消息文本
     * @return 应作为业务指令识别时返回 true
     */
    public boolean isAdditionalCommand(String content) { return false; }
    /**
     * 判断 QQ 群内指定指令是否被业务策略禁用
     *
     * @param commandName 主指令名，插件指令使用限定名称
     * @param groupId QQ 群 OpenID
     * @return 禁用时返回向用户发送的原因，允许执行时返回空值
     */
    public Optional<String> disabledReason(String commandName, String groupId) { return Optional.empty(); }
}
