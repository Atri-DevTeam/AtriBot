package top.yzljc.atribot.function.napcat

import top.yzljc.atribot.chat.napcat.impl.MessageUtils
import top.yzljc.atribot.command.Command
import top.yzljc.atribot.command.CommandExecutor
import top.yzljc.atribot.command.CommandSender
import top.yzljc.atribot.configuration.Config
import top.yzljc.atribot.configuration.LoadIllegalWords
import top.yzljc.atribot.configuration.ResourcesProperties
import top.yzljc.atribot.function.general.impl.PreImageGenerate
import top.yzljc.atribot.platform.Platform
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager

/**
 * @Author YZ_Ljc_
 * @ClassName CucumberGirl
 * @Created_at 2026/06/30
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.napcat
 */
object CucumberGirl : CommandExecutor {

    override fun onCommand(
        sender: CommandSender?,
        command: Command?,
        label: String?,
        args: Array<out String?>?
    ): Boolean {
        if (sender?.platform != Platform.NAPCAT_GROUP) return true
        if (!GroupConfigManager.isFeatureEnabled(sender.groupId, "private_func")) {
            if (!sender.hasPermission()) {
                return true
            }
        }
        if (args.isNullOrEmpty()) {
            sender.sendMessage("无效文字内容，请输入文字参数！")
            return true
        }
        val text = args.filterNotNull().joinToString(" ")
        if (text.isBlank()) {
            sender.sendMessage("无效文字内容，请输入文字参数！")
            return true
        }
        if (LoadIllegalWords.containsSensitiveWord(text)) {
            sender.sendMessage("文字内容包含敏感内容，无法生图！")
            return true
        }
        val req = mapOf("text" to text)
        val data = PreImageGenerate.dump(
            ResourcesProperties.GIRL_TEXT_IMG + "?key=" + Config.getInstance().atribotKeySecret,
            req
        )
        data?.url?.let {
            sender.sendMessage(it, MessageUtils.ImageType.URL)
        }
        return true
    }
}