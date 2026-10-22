package me.thatonedevil.soulNetworkPlugin.discord

import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.instance
import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.settings
import me.thatonedevil.soulNetworkPlugin.chat.DiscordToMc
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Guild
import net.dv8tion.jda.api.entities.IncomingWebhookClient
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.WebhookClient
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.MemberCachePolicy
import org.bukkit.Bukkit
import org.bukkit.scheduler.BukkitTask
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/** Owns the JDA connection, channel lookups and sending helpers. */
object DiscordBot {

    @Volatile
    var jda: JDA? = null
        private set

    @Volatile
    var webhookClient: IncomingWebhookClient? = null
        private set

    @Volatile
    private var readyAt = 0L

    val isReady: Boolean get() = jda?.status == JDA.Status.CONNECTED

    val uptimeMillis: Long get() = if (readyAt == 0L) 0L else System.currentTimeMillis() - readyAt

    val guild: Guild? get() = jda?.let { j -> runCatching { j.getGuildById(settings.guildId) }.getOrNull() }

    // Looked up on every use, so they start working as soon as the bot can see the channel
    // (e.g. after being invited) and pick up config reloads.
    val serverChat: GuildMessageChannel? get() = channel(settings.serverChatChannel)
    val staffChat: GuildMessageChannel? get() = channel(settings.staffChatChannel)
    val commandLog: GuildMessageChannel? get() = channel(settings.commandLogChannel)
    val store: GuildMessageChannel? get() = channel(settings.storeChannel)

    private fun channel(id: String): GuildMessageChannel? {
        if (id.isBlank() || !isReady) return null
        return runCatching { jda?.getChannelById(GuildMessageChannel::class.java, id) }.getOrNull()
    }

    /** Returns false if the plugin was disabled because the config is invalid. */
    fun start(): Boolean {
        if (!settings.jdaEnabled) return true

        val token = settings.token
        if (token.isBlank()) {
            instance.logger.severe("Missing bot token in config.yml")
            instance.server.pluginManager.disablePlugin(instance)
            return false
        }

        CompletableFuture.runAsync {
            runCatching {
                // Light client: the plugin never reads the member/emoji/voice/presence caches
                // (role changes use IDs, message events carry their own member), so skip them.
                val built = JDABuilder.createLight(token, GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .setMemberCachePolicy(MemberCachePolicy.NONE)
                    .setBulkDeleteSplittingEnabled(false)
                    .addEventListeners(DiscordToMc(), PrefixCommands(), SlashCommands())
                    .build()
                jda = built
                built.awaitReady()
                readyAt = System.currentTimeMillis()

                onReady(built)
            }.onFailure {
                instance.logger.severe("Failed to connect to Discord: ${it.message}")
            }
        }
        return true
    }

    private fun onReady(jda: JDA) {
        val guild = guild
        if (guild == null) {
            instance.logger.severe("Guild not found (${settings.guildId})")
            val joined = jda.guilds.joinToString { "${it.name} (${it.id})" }.ifEmpty { "none" }
            instance.logger.severe("Bot is currently in: $joined")
            instance.logger.severe("Invite the bot with: " + jda.getInviteUrl(
                Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY,
                Permission.MESSAGE_EMBED_LINKS, Permission.MANAGE_WEBHOOKS
            ))
        }

        mapOf(
            "Server chat" to settings.serverChatChannel,
            "Staff chat" to settings.staffChatChannel,
            "Command log" to settings.commandLogChannel,
            "Store" to settings.storeChannel,
        ).forEach { (label, id) ->
            if (id.isNotBlank() && channel(id) == null) instance.logger.warning("$label channel not found ($id)")
        }

        webhookClient = settings.webhookUrl.takeIf { it.isNotBlank() }
            ?.let { runCatching { WebhookClient.createClient(jda, it) }.getOrNull() }

        SlashCommands.register(jda, guild)
        startStatusUpdater()
        if (settings.eventServerStatus) serverChatEmbeds.offer(Embeds.serverStarted())
    }

    @Volatile private var lastStatus: String? = null
    private var statusTask: BukkitTask? = null

    /**
     * Shows the player count as the bot's activity (e.g. "Watching 12 players on bliss.minehut.gg"),
     * like SoulzProxy. Checked every 10 seconds; Discord is only updated when the text changes.
     */
    private fun startStatusUpdater() {
        statusTask?.cancel()
        statusTask = Bukkit.getScheduler().runTaskTimer(instance, Runnable {
            val j = jda ?: return@Runnable
            if (!settings.botStatusEnabled) {
                if (lastStatus != null) { j.presence.activity = null; lastStatus = null }
                return@Runnable
            }
            val text = settings.botStatusText
                .replace("<online>", Bukkit.getOnlinePlayers().size.toString())
                .replace("<max>", settings.maxPlayers.toString())
                .replace("<ip>", settings.ip)
                .replace("<server>", settings.serverName)
            val key = "${settings.botStatusType}|$text"
            if (key == lastStatus) return@Runnable
            lastStatus = key
            j.presence.activity = when (settings.botStatusType) {
                "playing" -> Activity.playing(text)
                "listening" -> Activity.listening(text)
                "competing" -> Activity.competing(text)
                "custom" -> Activity.customStatus(text)
                else -> Activity.watching(text)
            }
        }, 20L, 200L)
    }

    /** Re-reads settings that affect the connection (webhook). Channels are looked up live anyway. */
    fun onConfigReload() {
        warnedMissing.clear()
        lastStatus = null // re-apply the status with the new settings
        val j = jda ?: return
        webhookClient = settings.webhookUrl.takeIf { it.isNotBlank() }
            ?.let { runCatching { WebhookClient.createClient(j, it) }.getOrNull() }
    }

    // Each "channel not found" warning is logged once (until the next reload) so busy features
    // like the command log don't flood the console.
    private val warnedMissing: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    fun warnMissing(what: String) {
        if (settings.jdaEnabled && isReady && warnedMissing.add(what)) {
            instance.logger.warning("Can't send $what: channel not found or bot can't see it. Check config.yml (warning shown once)")
        }
    }

    // Everything sent to Discord goes through these, so bursts are bundled into one message
    // per destination per bundling window (see Outbox).
    val serverChatEmbeds = EmbedOutbox("server chat embed") { serverChat }
    val staffChatMessages = TextOutbox("staff chat message") { staffChat }
    val commandLogMessages = TextOutbox("command log") { commandLog }
    val storeEmbeds = EmbedOutbox("store announcement") { store }
    val chatMessages = ChatOutbox()

    fun shutdown() {
        val j = jda ?: return
        if (isReady) {
            // Send whatever is still queued, then the stop embed, before the connection closes.
            Outbox.flushAllBlocking()
            if (settings.eventServerStatus) runCatching {
                serverChat?.sendMessageEmbeds(Embeds.serverStopped())?.submit()?.get(3, TimeUnit.SECONDS)
            }
        }
        statusTask?.cancel()
        statusTask = null
        lastStatus = null
        j.shutdown()
        runCatching { if (!j.awaitShutdown(5, TimeUnit.SECONDS)) j.shutdownNow() }
        jda = null
        webhookClient = null
        readyAt = 0L
    }
}
