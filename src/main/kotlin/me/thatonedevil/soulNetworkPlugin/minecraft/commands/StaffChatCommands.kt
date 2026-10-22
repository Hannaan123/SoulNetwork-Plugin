package me.thatonedevil.soulNetworkPlugin.minecraft.commands

import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.dataStore
import me.thatonedevil.soulNetworkPlugin.chat.StaffChat
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

private val mm = MiniMessage.miniMessage()
private const val TAG = "<#FFA500><bold>STAFFCHAT</bold> <dark_gray>» "

/** /sc <message> */
class StaffChatCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!StaffChat.canUse(sender)) {
            sender.sendMessage(mm.deserialize("$TAG<red>Insufficient permissions!"))
            return true
        }
        if (args.isEmpty()) {
            sender.sendMessage(mm.deserialize("$TAG<red>Usage: /$label <message>"))
            return true
        }
        StaffChat.sendFromGame(sender.name, args.joinToString(" "))
        return true
    }
}

/** /sctoggle - sends all of your chat to staff chat until toggled off. */
class StaffChatToggleCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val player = sender as? Player ?: run { sender.sendMessage("Only players can toggle staff chat."); return true }
        if (!StaffChat.canUse(player)) {
            player.sendMessage(mm.deserialize("$TAG<red>Insufficient permissions!"))
            return true
        }
        val enabled = dataStore.toggleStaffChat(player.uniqueId)
        player.sendMessage(mm.deserialize(if (enabled) "$TAG<green>Staff chat enabled" else "$TAG<red>Staff chat disabled"))
        return true
    }
}
