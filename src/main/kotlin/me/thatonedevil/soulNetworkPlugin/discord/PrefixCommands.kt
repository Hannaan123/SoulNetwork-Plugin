package me.thatonedevil.soulNetworkPlugin.discord

import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.instance
import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.settings
import me.thatonedevil.soulNetworkPlugin.discord.commands.DiscordCommands
import me.thatonedevil.soulNetworkPlugin.discord.commands.Reply
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

/** Text versions of the info commands: !ip, ?store, !stats, !online (prefixes from config). */
class PrefixCommands : ListenerAdapter() {

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot || event.isWebhookMessage || !event.isFromGuild) return

        val content = event.message.contentRaw.trim()
        val prefix = settings.prefixes.firstOrNull { content.startsWith(it) } ?: return
        val name = content.removePrefix(prefix).substringBefore(' ').lowercase()

        val result = when (name) {
            "ip" -> DiscordCommands.ip()
            "store" -> DiscordCommands.store()
            "stats" -> DiscordCommands.stats(event.author)
            "online", "players" -> DiscordCommands.online(event.author)
            else -> return
        }

        result.whenComplete { reply, error ->
            val action = when {
                error != null -> {
                    instance.logger.warning("Discord $prefix$name failed: ${error.message}")
                    return@whenComplete
                }
                reply is Reply.Embed -> event.message.replyEmbeds(reply.embed)
                reply is Reply.Text -> event.message.reply(reply.text)
                else -> return@whenComplete
            }
            action.mentionRepliedUser(false).setAllowedMentions(emptyList()).queue()
        }
    }
}
