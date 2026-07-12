package top.yzljc.atribot.function.napcat.personal

import com.github.stuxuhai.jpinyin.ChineseHelper
import com.github.stuxuhai.jpinyin.PinyinFormat
import com.github.stuxuhai.jpinyin.PinyinHelper
import top.yzljc.atribot.command.Command
import top.yzljc.atribot.command.CommandExecutor
import top.yzljc.atribot.command.CommandSender
import top.yzljc.atribot.configuration.LoadIllegalWords
import top.yzljc.atribot.platform.Platform
import top.yzljc.atribot.platform.napcat.groupfunction.GroupConfigManager

/**
 * @Author YZ_Ljc_
 * @ClassName PinYin
 * @Created_at 2026/07/06
 * @Project AtriMeow
 * @Package top.yzljc.atribot.function.napcat
 */
object PinYin : CommandExecutor {
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
        val rawText = args?.joinToString(" ") ?: return true
        if (rawText.contains("$")) {
            sender.sendMessage("在转换为拼音时出现问题: 含有无效字符")
            return true
        }

        if (LoadIllegalWords.containsSensitiveWord(rawText)) {
            sender.sendMessage("在转换为拼音时出现问题: 含有违规内容")
            return true
        }

        when (val pinyin = getPinYin(rawText)) {
            "" -> sender.sendMessage("在转换为拼音时出现问题: 无效内容")
            else -> sender.sendMessage(pinyin)
        }

        return true
    }

    fun getPinYin(text: String): String {
        val cRaw = PinyinHelper.convertToPinyinString(text, "$", PinyinFormat.WITH_TONE_MARK)
        if (!cRaw.isNullOrEmpty()) {
            val pinyinArray = cRaw.split("$")
            val wordAndPinYinList = mutableListOf<String>()
            for ((i, c) in text.withIndex()) {
                if (!ChineseHelper.isChinese(c)) {
                    wordAndPinYinList.add(c.toString())
                    continue
                }
                val t = c + "(" + pinyinArray[i] + ")"
                wordAndPinYinList.add(t)
            }
            return wordAndPinYinList.joinToString("")
        }
        return ""
    }
}