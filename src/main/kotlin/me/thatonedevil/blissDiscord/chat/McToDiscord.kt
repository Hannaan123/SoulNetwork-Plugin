package me.thatonedevil.blissDiscord.chat

import io.papermc.paper.event.player.AsyncChatEvent
import me.thatonedevil.blissDiscord.BlissDiscord.Companion.chatFilter
import me.thatonedevil.blissDiscord.BlissDiscord.Companion.lpApi
import me.thatonedevil.blissDiscord.BlissDiscord.Companion.settings
import me.thatonedevil.blissDiscord.Utils.convertLegacyToMiniMessage
import me.thatonedevil.blissDiscord.Utils.playerAvatarUrl
import me.thatonedevil.blissDiscord.discord.DiscordBot
import me.thatonedevil.blissDiscord.discord.ChatOutbox
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

/** Chat filter + relaying in-game chat to Discord through the webhook. */
class McToDiscord : Listener {

    companion object {
        private val COLOR_CODES = Regex("(?i)[&§]x|[&§][0-9a-fk-or]")
        // Pings are also disabled on the webhook; this just keeps them from showing up as text.
        private val MENTIONS = Regex("@everyone|@here|<@")
    }

    @EventHandler(ignoreCancelled = true)
    fun onChat(event: AsyncChatEvent) {
        val player = event.player
        val rawMessage = PlainTextComponentSerializer.plainText().serialize(event.message())

        if (blockBadWords(rawMessage, player, event)) return
        if (!settings.jdaEnabled || !settings.serverChatToggle) return
        if (MENTIONS.containsMatchIn(rawMessage)) return
        // Messages starting with "!" would trigger Discord bot commands; ops are allowed.
        if (rawMessage.startsWith("!") && !player.isOp) return

        val prefix = getPlayerPrefix(player).replace(COLOR_CODES, "")
        val content = settings.mcToDiscordMessage
            .replace("<player>", player.name)
            .replace("<prefix>", prefix)
            .replace("<message>", LegacyComponentSerializer.legacyAmpersand().serialize(event.message()))

        if (DiscordBot.webhookClient == null) return
        DiscordBot.chatMessages.offer(ChatOutbox.Line("$prefix${player.name}", playerAvatarUrl(player), content))
    }

    private fun blockBadWords(rawMessage: String, player: Player, event: AsyncChatEvent): Boolean {
        val badWord = chatFilter?.findBadWord(rawMessage) ?: return false
        player.sendMessage(convertLegacyToMiniMessage(settings.blockedMessage.replace("<badWord>", badWord)))
        event.isCancelled = true
        return true
    }

    private fun getPlayerPrefix(player: Player): String {
        val user = lpApi?.userManager?.getUser(player.uniqueId) ?: return ""
        return user.primaryGroup.let { lpApi?.groupManager?.getGroup(it)?.cachedData?.metaData?.prefix }
            ?: user.cachedData.metaData.prefix ?: ""
    }
}
