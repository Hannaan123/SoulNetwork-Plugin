package me.thatonedevil.blissDiscord.minecraft.commands

import me.thatonedevil.blissDiscord.BlissDiscord.Companion.settings
import me.thatonedevil.blissDiscord.Utils.sendConfigMessage
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.PluginCommand
import org.bukkit.command.TabCompleter
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandSendEvent

/**
 * Applies commands.minecraft.<name>.enabled. Disabled commands answer with commands.disabledMessage
 * and are hidden from players' command lists (tab-complete).
 */
object CommandToggles : Listener {

    private val commands = mutableMapOf<String, PluginCommand>()

    /** Wraps [executor] so it only runs while the command is enabled. */
    fun register(command: PluginCommand, executor: CommandExecutor) {
        commands[command.name] = command
        val name = command.name
        command.setExecutor { sender, cmd, label, args ->
            if (enabled(name)) executor.onCommand(sender, cmd, label, args)
            else { sender.sendDisabled(); true }
        }
        if (executor is TabCompleter) {
            command.tabCompleter = TabCompleter { sender: CommandSender, cmd: Command, label: String, args: Array<out String> ->
                if (enabled(name)) executor.onTabComplete(sender, cmd, label, args) else emptyList()
            }
        }
    }

    private fun enabled(name: String) = settings.mcCommand(name).enabled

    private fun CommandSender.sendDisabled() = sendConfigMessage(settings.commandDisabledMessage)

    @EventHandler
    fun onCommandSend(event: PlayerCommandSendEvent) {
        val hidden = commands.values.filter { !enabled(it.name) }.flatMap { cmd ->
            val labels = listOf(cmd.name) + cmd.aliases
            val namespace = cmd.plugin.name.lowercase()
            labels.flatMap { listOf(it.lowercase(), "$namespace:${it.lowercase()}") }
        }.toSet()
        if (hidden.isNotEmpty()) event.commands.removeAll { it.lowercase() in hidden }
    }

    /** After a reload, resend command lists so newly (dis)abled commands show up or disappear. */
    fun refreshPlayers() = Bukkit.getOnlinePlayers().forEach { it.updateCommands() }
}
