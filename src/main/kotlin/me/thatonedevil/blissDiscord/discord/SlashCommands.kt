package me.thatonedevil.blissDiscord.discord

import me.thatonedevil.blissDiscord.BlissDiscord.Companion.instance
import me.thatonedevil.blissDiscord.BlissDiscord.Companion.settings
import me.thatonedevil.blissDiscord.Utils.fill
import me.thatonedevil.blissDiscord.discord.commands.DiscordCommands
import me.thatonedevil.blissDiscord.discord.commands.Reply
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.hooks.ListenerAdapter
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.interactions.commands.OptionType
import net.dv8tion.jda.api.interactions.commands.build.Commands
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.text.TextInput
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle
import net.dv8tion.jda.api.interactions.modals.Modal
import java.util.concurrent.CompletableFuture

class SlashCommands : ListenerAdapter() {

    companion object {
        private const val LINK_BUTTON = "link"
        private const val LINK_MODAL = "link_modal"
        private const val LINK_INPUT = "code_input"

        /** Every slash command, with the description used when the config's is blank. */
        private val DEFAULT_DESCRIPTIONS = linkedMapOf(
            "stats" to "Server statistics",
            "online" to "Who's online right now",
            "player" to "Look up a player's stats",
            "profile" to "Stats of a Discord user's linked Minecraft account",
            "ip" to "Server IP",
            "store" to "Server store link",
            "link" to "Link your Minecraft account",
            "unlink" to "Unlink your Minecraft account",
            "linkembed" to "Post the account linking panel in this channel",
        )

        /** The enabled commands with their configured descriptions (Discord allows 1-100 characters). */
        private fun definitions(): List<SlashCommandData> = DEFAULT_DESCRIPTIONS
            .filterKeys { settings.discordCommand(it).enabled }
            .map { (name, default) ->
                val description = settings.discordCommand(name).string("description").ifBlank { default }.take(100)
                val data = Commands.slash(name, description)
                when (name) {
                    "player" -> data.addOption(OptionType.STRING, "name", "Minecraft username", true)
                    "profile" -> data.addOption(OptionType.USER, "user", "Discord user (defaults to you)", false)
                    "link" -> data.addOption(OptionType.STRING, "code", "Code from running /link in-game", true)
                    "linkembed" -> data.setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR))
                    else -> data
                }
            }

        /** Guild commands show up instantly; global ones are the fallback if the guild isn't found. */
        fun register(jda: JDA, guild: Guild?) {
            val action = guild?.updateCommands() ?: jda.updateCommands()
            action.addCommands(definitions()).queue(
                { instance.logger.info("Registered ${it.size} Discord slash commands${if (guild == null) " (global)" else ""}") },
                { instance.logger.warning("Couldn't register slash commands: ${it.message}") }
            )
        }

        /** Defers (so slow lookups don't hit Discord's 3 second limit), then sends the reply. */
        private fun respond(event: IReplyCallback, label: String, ephemeral: Boolean, result: CompletableFuture<Reply>) {
            event.deferReply(ephemeral).queue { hook -> result.whenComplete { reply, error -> send(hook, label, reply, error) } }
        }

        private fun send(hook: InteractionHook, label: String, reply: Reply?, error: Throwable?) {
            val action = when {
                error != null -> {
                    instance.logger.warning("Discord $label failed: ${error.message}")
                    hook.sendMessage(settings.discordCommandError)
                }
                reply is Reply.Embed -> hook.sendMessageEmbeds(reply.embed)
                // A deferred reply must be answered, so a blanked-out message becomes an invisible character.
                reply is Reply.Text -> hook.sendMessage(reply.text.ifBlank { "​" })
                else -> return
            }
            action.setAllowedMentions(emptyList()).queue()
        }

        /** commands.discord.linkembed.<key>, falling back to the built-in text if blank. */
        private fun panelText(key: String, fallback: String): String =
            settings.discordCommand("linkembed").string(key).ifBlank { fallback }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val user = event.user
        val cfg = settings.discordCommand(event.name)

        // Can still be used if Discord hasn't caught up with a reload that turned it off
        if (!cfg.enabled) {
            event.reply(settings.discordCommandDisabled).setEphemeral(true).queue()
            return
        }
        if (event.name == "linkembed") return postLinkPanel(event)

        val result: CompletableFuture<Reply> = when (event.name) {
            "stats" -> DiscordCommands.stats(user)
            "online" -> DiscordCommands.online(user)
            "player" -> DiscordCommands.playerByName(event.getOption("name")!!.asString, user)
            "profile" -> DiscordCommands.playerByDiscord(event.getOption("user")?.asUser ?: user, user)
            "ip" -> DiscordCommands.ip()
            "store" -> DiscordCommands.store()
            "link" -> DiscordCommands.link(event.getOption("code")!!.asString, user)
            "unlink" -> DiscordCommands.unlink(user)
            else -> return
        }
        val ephemeral = cfg.bool("ephemeral", event.name == "link" || event.name == "unlink")
        respond(event, "/${event.name}", ephemeral, result)
    }

    private fun postLinkPanel(event: SlashCommandInteractionEvent) {
        if (event.member?.hasPermission(Permission.ADMINISTRATOR) != true) {
            event.reply(panelText("noPermission", "❌ You do not have permission to use this command!"))
                .setEphemeral(true).queue()
            return
        }
        event.channel.sendMessageEmbeds(Embeds.linkPanel())
            .setComponents(ActionRow.of(Button.primary(LINK_BUTTON, panelText("button", "Click to link").take(80))))
            .queue(
                { event.reply(panelText("posted", "✅ Link panel posted.")).setEphemeral(true).queue() },
                {
                    val text = fill(panelText("failed", "❌ Couldn't post here: <error>"), "error" to (it.message ?: "unknown error"))
                    event.reply(text).setEphemeral(true).queue()
                }
            )
    }

    /** "Click to link" on the panel opens a box to type the code into. */
    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        if (event.componentId != LINK_BUTTON) return
        if (!settings.discordCommand("link").enabled) {
            event.reply(settings.discordCommandDisabled).setEphemeral(true).queue()
            return
        }

        val input = TextInput.create(LINK_INPUT, panelText("inputLabel", "Put code here").take(45), TextInputStyle.SHORT)
            .setPlaceholder(panelText("inputPlaceholder", "Enter your 6-character code from /link").take(100))
            .setRequired(true)
            .setMinLength(6)
            .setMaxLength(6)
            .build()
        event.replyModal(
            Modal.create(LINK_MODAL, panelText("modalTitle", "Link Your Minecraft Account").take(45))
                .addComponents(ActionRow.of(input)).build()
        ).queue()
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        if (event.modalId != LINK_MODAL) return
        val code = event.getValue(LINK_INPUT)?.asString ?: return
        respond(event, "link panel", ephemeral = true, DiscordCommands.link(code, event.user))
    }
}
