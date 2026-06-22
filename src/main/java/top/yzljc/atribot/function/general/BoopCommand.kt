package top.yzljc.atribot.function.general

import top.yzljc.atribot.command.Command
import top.yzljc.atribot.command.CommandExecutor
import top.yzljc.atribot.command.CommandSender
import java.time.LocalDate

object BoopCommand : CommandExecutor {
    override fun onCommand(
        sender: CommandSender?,
        command: Command?,
        label: String?,
        args: Array<out String?>?
    ): Boolean {
        val now = LocalDate.now()
        val halloween = LocalDate.of(now.year, 10, 31)
        var tip = "Boop!"
        sender?.let {
            if (now == halloween) {
                tip = "Boo!"
            }
            it.sendMessage(tip)
        }
        return true
    }
}