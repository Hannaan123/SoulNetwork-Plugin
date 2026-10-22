package me.thatonedevil.soulNetworkPlugin.minecraft.commands

import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin
import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.dataStore
import me.thatonedevil.soulNetworkPlugin.Utils.playerBustUrl
import me.thatonedevil.soulNetworkPlugin.discord.DiscordBot
import me.thatonedevil.soulNetworkPlugin.discord.Embeds
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

private val mm = MiniMessage.miniMessage()

/**
 * /announcepurchase <player> <price> <item...>
 * Add as a Tebex package command: announcepurchase {username} {price} {packageName}
 */
class AnnouncePurchaseCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (args.size < 3) {
            sender.sendMessage(mm.deserialize("<red>Usage: /$label <player> <price> <item...>"))
            return true
        }
        val name = args[0]
        val price = args[1].removePrefix("$")
        val item = args.drop(2).joinToString(" ")

        // By UUID if we know the player (correct skin), otherwise by name. Never a blocking lookup.
        val player = Bukkit.getPlayerExact(name) ?: Bukkit.getOfflinePlayerIfCached(name)
        val bust = player?.let { playerBustUrl(it) } ?: "https://mineskin.eu/armor/bust/$name/100"

        if (DiscordBot.store == null) {
            sender.sendMessage(mm.deserialize("<red>Store channel not found. Check channels.store in config.yml."))
            return true
        }
        DiscordBot.storeEmbeds.offer(Embeds.storePurchase(name, item, price, bust))
        dataStore.lastContributor = name
        sender.sendMessage(mm.deserialize("<dark_green>Successfully sent the message to the discord."))
        return true
    }
}

/** /uuid [player] */
class UuidCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val target = when {
            args.isNotEmpty() -> Bukkit.getPlayerExact(args[0]) ?: Bukkit.getOfflinePlayerIfCached(args[0])
            sender is Player -> sender
            else -> { sender.sendMessage("Usage: /$label <player>"); return true }
        }
        if (target == null) {
            sender.sendMessage(mm.deserialize("<red>That player has never joined the server."))
            return true
        }
        val uuid = target.uniqueId.toString()
        sender.sendMessage(mm.deserialize(
            "<gray>${target.name}'s UUID: <click:copy_to_clipboard:'$uuid'><hover:show_text:'Click to copy'><white>$uuid"
        ))
        return true
    }
}

/** /bliss reload (alias /configReload) */
class BlissCommand(private val plugin: SoulNetworkPlugin) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!sender.hasPermission("bliss.admin")) {
            sender.sendMessage(mm.deserialize("<red>You don't have permission to do that."))
            return true
        }
        // The old /configReload alias reloads straight away.
        if (args.firstOrNull()?.equals("reload", true) == true || label.equals("configreload", true)) {
            plugin.reloadSettings()
            sender.sendMessage(mm.deserialize("<green>Config reloaded!"))
        } else {
            sender.sendMessage(mm.deserialize("<red>Usage: /$label reload"))
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> =
        if (args.size == 1 && sender.hasPermission("bliss.admin")) listOf("reload").filter { it.startsWith(args[0], true) }
        else emptyList()
}
