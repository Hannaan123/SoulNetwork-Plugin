package me.thatonedevil.soulNetworkPlugin.minecraft.commands

import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.dataStore
import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.instance
import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.settings
import me.thatonedevil.soulNetworkPlugin.Utils.linkMessage
import me.thatonedevil.soulNetworkPlugin.linking.LinkManager
import me.thatonedevil.soulNetworkPlugin.linking.PlayerLinkedEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** /link - gives a one-time code to enter in Discord (link panel button or /link <code>). */
class LinkCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run { sender.sendMessage("Only players can use this command."); return true }

        if (dataStore.discordIdOf(player.uniqueId) != null) {
            player.sendMessage(linkMessage("linkedError"))
            return true
        }

        val code = LinkManager.createCode(player.uniqueId, player.name)
        player.sendMessage(
            linkMessage("linkCodeMessage", "code" to code)
                .clickEvent(ClickEvent.copyToClipboard(code))
                .hoverEvent(HoverEvent.showText(Component.text("Click to copy")))
        )
        return true
    }
}

/** /unlink */
class UnlinkCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run { sender.sendMessage("Only players can use this command."); return true }
        if (LinkManager.unlink(player.uniqueId) == null) {
            player.sendMessage(linkMessage("notLinkedError"))
        } else {
            player.sendMessage(linkMessage("unlinked"))
        }
        return true
    }
}

/**
 * /linkclaim - one-time reward for linking (from SoulzProxy). Runs the configured reward commands
 * and fires PlayerLinkedEvent so existing Skript reward hooks keep working.
 */
class LinkClaimCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run { sender.sendMessage("Only players can use this command."); return true }

        if (dataStore.discordIdOf(player.uniqueId) == null) {
            player.sendMessage(linkMessage("notLinkedError"))
            return true
        }
        if (!dataStore.claimLinkReward(player.uniqueId)) {
            player.sendMessage(linkMessage("alreadyClaimed"))
            return true
        }

        val console = Bukkit.getConsoleSender()
        for (reward in settings.linkRewards) {
            val cmd = reward.removePrefix("/").replace("<player>", player.name)
            runCatching { Bukkit.dispatchCommand(console, cmd) }
                .onFailure { instance.logger.warning("Link reward command failed ($cmd): ${it.message}") }
        }
        Bukkit.getPluginManager().callEvent(PlayerLinkedEvent(player, true, player.uniqueId.toString()))
        player.sendMessage(linkMessage("claimSuccess"))
        return true
    }
}
