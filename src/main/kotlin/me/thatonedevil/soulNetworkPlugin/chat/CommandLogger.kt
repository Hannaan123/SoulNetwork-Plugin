package me.thatonedevil.soulNetworkPlugin.chat

import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.settings
import me.thatonedevil.soulNetworkPlugin.Utils.escapeCodeBlock
import me.thatonedevil.soulNetworkPlugin.discord.DiscordBot
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent

/** Posts commands run by players with bliss.commandlog (or the old command.log) to the log channel. */
class CommandLogger : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onCommand(event: PlayerCommandPreprocessEvent) {
        val player = event.player
        if (!player.hasPermission("bliss.commandlog") && !player.hasPermission("command.log")) return

        val command = event.message.removePrefix("/").trim()
        if (command.isEmpty() || isIgnored(command)) return

        DiscordBot.commandLogMessages.offer("**${player.name}** executed command ```/${escapeCodeBlock(command)}```")
    }

    /** Matches the command name ("home") or the start of the command ("kit celestial"), ignoring namespaces. */
    private fun isIgnored(command: String): Boolean {
        val lower = command.lowercase()
        val label = lower.substringBefore(' ').substringAfter(':') // "essentials:kit" -> "kit"
        val normalized = label + lower.removePrefix(lower.substringBefore(' '))
        val ignore = settings.commandLogIgnore
        if (label in ignore || normalized in ignore) return true
        return ignore.any { normalized.startsWith("$it ") }
    }
}
