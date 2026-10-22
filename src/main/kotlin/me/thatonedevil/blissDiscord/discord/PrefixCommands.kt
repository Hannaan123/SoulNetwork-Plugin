package me.thatonedevil.blissDiscord.discord

import me.thatonedevil.blissDiscord.BlissDiscord.Companion.instance
import me.thatonedevil.blissDiscord.BlissDiscord.Companion.settings
import me.thatonedevil.blissDiscord.discord.commands.DiscordCommands
import me.thatonedevil.blissDiscord.discord.commands.Reply
import net.dv8tion.jda.api.events.message.MessageReceivedEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter

/**
 * Text versions of the info commands (!ip, ?store, !stats, !online). Prefixes come from prefixCommands
 * and the names from commands.discord.<command>.prefixAliases.
 */
class PrefixCommands : ListenerAdapter() {

    private companion object {
        val COMMANDS = listOf("ip", "store", "stats", "online")
    }

    override fun onMessageReceived(event: MessageReceivedEvent) {
        if (event.author.isBot || event.isWebhookMessage || !event.isFromGuild) return

        val content = event.message.contentRaw.trim()
        val prefix = settings.prefixes.firstOrNull { content.startsWith(it) } ?: return
        val name = content.removePrefix(prefix).substringBefore(' ').lowercase()
        if (name.isEmpty()) return

        val command = COMMANDS.firstOrNull { cmd ->
            val cfg = settings.discordCommand(cmd)
            cfg.enabled && name in cfg.list("prefixAliases")
        } ?: return

        val result = when (command) {
            "ip" -> DiscordCommands.ip()
            "store" -> DiscordCommands.store()
            "stats" -> DiscordCommands.stats(event.author)
            "online" -> DiscordCommands.online(event.author)
            else -> return
        }

        result.whenComplete { reply, error ->
            val action = when {
                error != null -> {
                    instance.logger.warning("Discord $prefix$name failed: ${error.message}")
                    return@whenComplete
                }
                reply is Reply.Embed -> event.message.replyEmbeds(reply.embed)
                reply is Reply.Text && reply.text.isNotBlank() -> event.message.reply(reply.text)
                else -> return@whenComplete
            }
            action.mentionRepliedUser(false).setAllowedMentions(emptyList()).queue()
        }
    }
}
