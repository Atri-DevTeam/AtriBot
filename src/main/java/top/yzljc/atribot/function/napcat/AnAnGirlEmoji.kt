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
 * @ClassName AnAnGirlEmoji
 * @Created_at 2026/07/01
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.napcat
 */
object AnAnGirlEmoji : CommandExecutor {

    val MODE = listOf(
        "-base",
        "-病娇",
        "-开心",
        "-脸红",
        "-生气",
        "-无语"
    )

    override fun onCommand(
        sender: CommandSender?,
        command: Command?,
        label: String?,
        args: Array<out String?>?
    ): Boolean {
        if (sender?.platform != Platform.NAPCAT_GROUP) return true
        if (!GroupConfigManager.isFeatureEnabled(sender.groupId, "girl_text")) {
            if (!sender.hasPermission()) {
                return true
            }
        }
        if (args.isNullOrEmpty()) {
            sender.sendMessage("无效文字内容，请输入文字参数！用法: /anan <文本> [参数]，可用参数: [-base, -病娇, -开心, -脸红, -生气, -无语]")
            return true
        }

        val argsList = args.filterNotNull().toMutableList()

        var modeParam: String?
        val lastArg = argsList.lastOrNull()
        if (lastArg != null && MODE.contains(lastArg)) {
            modeParam = lastArg.removePrefix("-")
            argsList.removeAt(argsList.size - 1)
        } else {
            modeParam = "base"
        }

        val text = argsList.joinToString(" ")
        if (text.isBlank()) {
            sender.sendMessage("无效文字内容，请输入文字参数！")
            return true
        }

        if (LoadIllegalWords.containsSensitiveWord(text)) {
            sender.sendMessage("文字内容包含敏感内容，无法生图！")
            return true
        }

        val req = mutableMapOf("text" to text)
        modeParam.let {
            req["mode"] = it
        }

        val data = PreImageGenerate.dump(
            ResourcesProperties.ANAN_TEXT_IMG + "?key=" + Config.getInstance().atribotKeySecret,
            req
        )
        data?.url?.let {
            sender.sendMessage(it, MessageUtils.ImageType.URL)
        }
        return true
    }
}