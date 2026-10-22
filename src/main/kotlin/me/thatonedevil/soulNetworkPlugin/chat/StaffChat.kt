package me.thatonedevil.soulNetworkPlugin.chat

import io.papermc.paper.event.player.AsyncChatEvent
import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.dataStore
import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.settings
import me.thatonedevil.soulNetworkPlugin.Utils.convertLegacyToMiniMessage
import me.thatonedevil.soulNetworkPlugin.Utils.escapeTags
import me.thatonedevil.soulNetworkPlugin.discord.DiscordBot
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener

/** Two-way staff chat between in-game staff and the Discord staff channel. */
object StaffChat : Listener {

    /** Old Skript permission (staffchat.use) still works. */
    fun canUse(sender: CommandSender): Boolean =
        sender.hasPermission("bliss.staffchat") || sender.hasPermission("staffchat.use")

    private fun broadcastToStaff(message: Component) {
        Bukkit.getOnlinePlayers().filter(::canUse).forEach { it.sendMessage(message) }
        Bukkit.getConsoleSender().sendMessage(message)
    }

    fun sendFromGame(senderName: String, message: String) {
        broadcastToStaff(convertLegacyToMiniMessage(
            settings.staffChatFormat.replace("<player>", senderName).replace("<message>", escapeTags(message))
        ))
        DiscordBot.staffChatMessages.offer("**STAFFCHAT** » $senderName: $message")
    }

    fun sendFromDiscord(discordName: String, message: String) {
        broadcastToStaff(convertLegacyToMiniMessage(
            settings.staffChatFromDiscordFormat
                .replace("<discordName>", escapeTags(discordName))
                .replace("<message>", escapeTags(message))
        ))
    }

    /** With /sctoggle on, normal chat goes to staff chat instead. Runs first so nothing else sees it. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        if (!dataStore.isStaffChatToggled(player.uniqueId) || !canUse(player)) return
        event.isCancelled = true
        sendFromGame(player.name, PlainTextComponentSerializer.plainText().serialize(event.message()))
    }
}
