package me.thatonedevil.blissDiscord.minecraft.commands

import me.thatonedevil.blissDiscord.BlissDiscord.Companion.dataStore
import me.thatonedevil.blissDiscord.BlissDiscord.Companion.settings
import me.thatonedevil.blissDiscord.Utils.sendConfigMessage
import me.thatonedevil.blissDiscord.chat.StaffChat
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/** /sc <message> */
class StaffChatCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val cfg = settings.mcCommand("sc")
        if (!StaffChat.canUse(sender)) {
            sender.sendConfigMessage(cfg.string("noPermission"))
            return true
        }
        if (args.isEmpty()) {
            sender.sendConfigMessage(cfg.string("usage"), "label" to label)
            return true
        }
        StaffChat.sendFromGame(sender.name, args.joinToString(" "))
        return true
    }
}

/** /sctoggle - sends all of your chat to staff chat until toggled off. */
class StaffChatToggleCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val cfg = settings.mcCommand("sctoggle")
        val player = sender as? Player ?: run { sender.sendConfigMessage(settings.playersOnlyMessage); return true }
        if (!StaffChat.canUse(player)) {
            player.sendConfigMessage(cfg.string("noPermission"))
            return true
        }
        val enabled = dataStore.toggleStaffChat(player.uniqueId)
        player.sendConfigMessage(cfg.string(if (enabled) "toggledOn" else "toggledOff"))
        return true
    }
}
