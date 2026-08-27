package top.yzljc.atribot.function.utils.personal

import top.yzljc.atribot.chat.ImageComponent
import top.yzljc.atribot.command.Command
import top.yzljc.atribot.command.CommandExecutor
import top.yzljc.atribot.command.CommandSender
import top.yzljc.atribot.command.NapcatCommandSender
import top.yzljc.atribot.configuration.LoadIllegalWords
import top.yzljc.atribot.configuration.ResourcesProperties
import top.yzljc.atribot.function.impl.PreImageGenerate
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
        val nc = sender as? NapcatCommandSender ?: return true
        if (!GroupConfigManager.isFeatureEnabled(nc.groupId, "private_func")) {
            if (!nc.hasPermission()) {
                return true
            }
        }
        if (args.isNullOrEmpty()) {
            nc.sendMessage("无效文字内容，请输入文字参数！")
            return true
        }
        val text = args.filterNotNull().joinToString(" ")
        if (text.isBlank()) {
            nc.sendMessage("无效文字内容，请输入文字参数！")
            return true
        }
        if (LoadIllegalWords.containsSensitiveWord(text)) {
            nc.sendMessage("文字内容包含敏感内容，无法生图！")
            return true
        }
        val req = mapOf("text" to text)
        val data = PreImageGenerate.dump(
            ResourcesProperties.GIRL_TEXT_IMG,
            req
        )
        if (data == null) {
            nc.sendMessage("图片生成失败，请稍后重试")
            return true
        }
        if (data.isError) {
            nc.sendMessage(data.errorMessage)
            return true
        }
        data.url?.let {
            nc.sendMessage(ImageComponent.imageOf(it))
        }
        return true
    }
}