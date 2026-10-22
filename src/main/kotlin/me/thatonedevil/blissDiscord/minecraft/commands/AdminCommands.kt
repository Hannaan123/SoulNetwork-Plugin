package me.thatonedevil.blissDiscord.minecraft.commands

import me.thatonedevil.blissDiscord.BlissDiscord
import me.thatonedevil.blissDiscord.BlissDiscord.Companion.dataStore
import me.thatonedevil.blissDiscord.BlissDiscord.Companion.settings
import me.thatonedevil.blissDiscord.Utils.playerBustUrl
import me.thatonedevil.blissDiscord.Utils.sendConfigMessage
import me.thatonedevil.blissDiscord.discord.DiscordBot
import me.thatonedevil.blissDiscord.discord.Embeds
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

/**
 * /announcepurchase <player> <price> <item...>
 * Add as a Tebex package command: announcepurchase {username} {price} {packageName}
 */
class AnnouncePurchaseCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val cfg = settings.mcCommand("announcepurchase")
        if (args.size < 3) {
            sender.sendConfigMessage(cfg.string("usage"), "label" to label)
            return true
        }
        val name = args[0]
        val price = args[1].removePrefix("$")
        val item = args.drop(2).joinToString(" ")

        // By UUID if we know the player (correct skin), otherwise by name. Never a blocking lookup.
        val player = Bukkit.getPlayerExact(name) ?: Bukkit.getOfflinePlayerIfCached(name)
        val bust = player?.let { playerBustUrl(it) } ?: "https://mineskin.eu/armor/bust/$name/100"

        if (DiscordBot.store == null) {
            sender.sendConfigMessage(cfg.string("channelMissing"))
            return true
        }
        DiscordBot.storeEmbeds.offer(Embeds.storePurchase(name, item, price, bust))
        dataStore.lastContributor = name
        sender.sendConfigMessage(cfg.string("sent"))
        return true
    }
}

/** /uuid [player] */
class UuidCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val cfg = settings.mcCommand("uuid")
        val target = when {
            args.isNotEmpty() -> Bukkit.getPlayerExact(args[0]) ?: Bukkit.getOfflinePlayerIfCached(args[0])
            sender is Player -> sender
            else -> { sender.sendConfigMessage(cfg.string("usage"), "label" to label); return true }
        }
        if (target == null) {
            sender.sendConfigMessage(cfg.string("notFound"))
            return true
        }
        sender.sendConfigMessage(
            cfg.string("result"),
            "player" to (target.name ?: args.firstOrNull().orEmpty()),
            "uuid" to target.uniqueId.toString(),
        )
        return true
    }
}

/** /bliss reload (alias /configReload) */
class BlissCommand(private val plugin: BlissDiscord) : CommandExecutor, TabCompleter {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        val cfg = settings.mcCommand("bliss")
        if (!sender.hasPermission("bliss.admin")) {
            sender.sendConfigMessage(cfg.string("noPermission"))
            return true
        }
        // The old /configReload alias reloads straight away.
        if (args.firstOrNull()?.equals("reload", true) == true || label.equals("configreload", true)) {
            plugin.reloadSettings()
            // Read again so an edited "reloaded" message shows straight away.
            sender.sendConfigMessage(settings.mcCommand("bliss").string("reloaded"))
        } else {
            sender.sendConfigMessage(cfg.string("usage"), "label" to label)
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, label: String, args: Array<out String>): List<String> =
        if (args.size == 1 && sender.hasPermission("bliss.admin")) listOf("reload").filter { it.startsWith(args[0], true) }
        else emptyList()
}
