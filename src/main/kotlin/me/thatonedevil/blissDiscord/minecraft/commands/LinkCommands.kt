package me.thatonedevil.blissDiscord.minecraft.commands

import me.thatonedevil.blissDiscord.BlissDiscord.Companion.dataStore
import me.thatonedevil.blissDiscord.BlissDiscord.Companion.settings
import me.thatonedevil.blissDiscord.Utils.linkMessage
import me.thatonedevil.blissDiscord.Utils.sendConfigMessage
import me.thatonedevil.blissDiscord.linking.LinkManager
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
        val player = sender as? Player ?: run { sender.sendConfigMessage(settings.playersOnlyMessage); return true }

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
        val player = sender as? Player ?: run { sender.sendConfigMessage(settings.playersOnlyMessage); return true }
        if (LinkManager.unlink(player.uniqueId) == null) {
            player.sendMessage(linkMessage("notLinkedError"))
        } else {
            player.sendMessage(linkMessage("unlinked"))
        }
        return true
    }
}

/** /linkclaim - one-time reward for linking (linking.rewards in config.yml). */
class LinkClaimCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run { sender.sendConfigMessage(settings.playersOnlyMessage); return true }

        if (dataStore.discordIdOf(player.uniqueId) == null) {
            player.sendMessage(linkMessage("notLinkedError"))
            return true
        }
        if (!LinkManager.claimReward(player)) player.sendMessage(linkMessage("alreadyClaimed"))
        return true
    }
}
