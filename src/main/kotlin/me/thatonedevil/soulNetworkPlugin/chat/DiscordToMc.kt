package me.thatonedevil.soulNetworkPlugin.chat

import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.settings
import me.thatonedevil.soulNetworkPlugin.Utils.convertLegacyToMiniMessage
import me.thatonedevil.soulNetworkPlugin.Utils.escapeTags
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import org.bukkit.Bukkit
import java.awt.Color

/** Relays Discord messages from the server chat and staff chat channels into the game. */
class DiscordToMc : ListenerAdapter() {

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (!settings.jdaEnabled || !event.isFromGuild) return
        // Ignore our own webhook and other bots/webhooks so messages don't echo back.
        if (event.author.isBot || event.isWebhookMessage || event.author.id == settings.webhookId) return
        val member = event.member ?: return

        val text = buildString {
            append(event.message.contentDisplay)
            if (event.message.attachments.isNotEmpty()) {
                if (isNotEmpty()) append(' ')
                append("[attachment]")
            }
        }.trim()
        if (text.isEmpty()) return

        when (event.channel.id) {
            settings.staffChatChannel -> StaffChat.sendFromDiscord(member.effectiveName, text)
            settings.serverChatChannel -> if (settings.serverChatToggle) sendToServerChat(member, text)
        }
    }

    private fun sendToServerChat(member: Member, text: String) {
        val role = member.roles.firstOrNull()
        val formatted = settings.discordToMcMessage
            .replace("<roleColor>", "<color:${formatRoleColor(role?.color)}>")
            .replace("<roleName>", escapeTags(role?.name ?: ""))
            .replace("<discordName>", escapeTags(member.effectiveName))
            .replace("<text>", escapeTags(text))

        Bukkit.broadcast(convertLegacyToMiniMessage(formatted))
    }

    private fun formatRoleColor(color: Color?): String =
        color?.let { String.format("#%02X%02X%02X", it.red, it.green, it.blue) } ?: "#FFFFFF"
}
