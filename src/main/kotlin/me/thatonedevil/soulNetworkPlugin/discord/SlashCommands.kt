package me.thatonedevil.soulNetworkPlugin.discord

import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.instance
import me.thatonedevil.soulNetworkPlugin.discord.commands.DiscordCommands
import me.thatonedevil.soulNetworkPlugin.discord.commands.Reply
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

        private val definitions: List<SlashCommandData> = listOf(
            Commands.slash("stats", "Server statistics"),
            Commands.slash("online", "Who's online right now"),
            Commands.slash("player", "Look up a player's stats")
                .addOption(OptionType.STRING, "name", "Minecraft username", true),
            Commands.slash("profile", "Stats of a Discord user's linked Minecraft account")
                .addOption(OptionType.USER, "user", "Discord user (defaults to you)", false),
            Commands.slash("ip", "Server IP"),
            Commands.slash("store", "Server store link"),
            Commands.slash("link", "Link your Minecraft account")
                .addOption(OptionType.STRING, "code", "Code from running /link in-game", true),
            Commands.slash("unlink", "Unlink your Minecraft account"),
            Commands.slash("linkembed", "Post the account linking panel in this channel")
                .setDefaultPermissions(DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)),
        )

        /** Guild commands show up instantly; global ones are the fallback if the guild isn't found. */
        fun register(jda: JDA, guild: Guild?) {
            val action = guild?.updateCommands() ?: jda.updateCommands()
            action.addCommands(definitions).queue(
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
                    hook.sendMessage("Something went wrong running that command.")
                }
                reply is Reply.Embed -> hook.sendMessageEmbeds(reply.embed)
                reply is Reply.Text -> hook.sendMessage(reply.text)
                else -> return
            }
            action.setAllowedMentions(emptyList()).queue()
        }
    }

    override fun onSlashCommandInteraction(event: SlashCommandInteractionEvent) {
        val user = event.user

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
        respond(event, "/${event.name}", ephemeral = event.name == "link" || event.name == "unlink", result)
    }

    private fun postLinkPanel(event: SlashCommandInteractionEvent) {
        if (event.member?.hasPermission(Permission.ADMINISTRATOR) != true) {
            event.reply("❌ You do not have permission to use this command!").setEphemeral(true).queue()
            return
        }
        event.channel.sendMessageEmbeds(Embeds.linkPanel())
            .setComponents(ActionRow.of(Button.primary(LINK_BUTTON, "Click to link")))
            .queue(
                { event.reply("✅ Link panel posted.").setEphemeral(true).queue() },
                { event.reply("❌ Couldn't post here: ${it.message}").setEphemeral(true).queue() }
            )
    }

    /** "Click to link" on the panel opens a box to type the code into. */
    override fun onButtonInteraction(event: ButtonInteractionEvent) {
        if (event.componentId != LINK_BUTTON) return

        val input = TextInput.create(LINK_INPUT, "Put code here", TextInputStyle.SHORT)
            .setPlaceholder("Enter your 6-character code from /link")
            .setRequired(true)
            .setMinLength(6)
            .setMaxLength(6)
            .build()
        event.replyModal(
            Modal.create(LINK_MODAL, "Link Your Minecraft Account").addComponents(ActionRow.of(input)).build()
        ).queue()
    }

    override fun onModalInteraction(event: ModalInteractionEvent) {
        if (event.modalId != LINK_MODAL) return
        val code = event.getValue(LINK_INPUT)?.asString ?: return
        respond(event, "link panel", ephemeral = true, DiscordCommands.link(code, event.user))
    }
}
