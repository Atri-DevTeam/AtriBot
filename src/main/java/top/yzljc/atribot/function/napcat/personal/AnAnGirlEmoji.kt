package top.yzljc.atribot.function.napcat.personal

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
        val nc = sender as? NapcatCommandSender ?: return true
        if (!GroupConfigManager.isFeatureEnabled(nc.groupId, "private_func")) {
            if (!nc.hasPermission()) {
                return true
            }
        }
        if (args.isNullOrEmpty()) {
            nc.sendMessage("无效文字内容，请输入文字参数！用法: /anan <文本> [参数]，可用参数: [-base, -病娇, -开心, -脸红, -生气, -无语]")
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
            nc.sendMessage("无效文字内容，请输入文字参数！")
            return true
        }

        if (LoadIllegalWords.containsSensitiveWord(text)) {
            nc.sendMessage("文字内容包含敏感内容，无法生图！")
            return true
        }

        val req = mutableMapOf("text" to text)
        modeParam.let {
            req["mode"] = it
        }

        val data = PreImageGenerate.dump(
            ResourcesProperties.ANAN_TEXT_IMG,
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