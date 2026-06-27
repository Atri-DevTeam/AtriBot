package top.yzljc.atribot.function.general

import top.yzljc.atribot.command.Command
import top.yzljc.atribot.command.CommandExecutor
import top.yzljc.atribot.command.CommandSender

object PingCommand : CommandExecutor {
    override fun onCommand(
        sender: CommandSender?,
        command: Command?,
        label: String?,
        args: Array<out String?>?
    ): Boolean {
        sender?.sendMessage("Pong!")
        return true
    }
}