package me.thatonedevil.blissDiscord.config

import me.thatonedevil.blissDiscord.linking.LinkRewards
import org.bukkit.Bukkit
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.configuration.file.FileConfiguration
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

/** Typed snapshot of config.yml. Rebuilt on every reload. */
class PluginConfig(c: FileConfiguration) {

    val token: String = c.getString("token").orEmpty()
    val jdaEnabled: Boolean = c.getBoolean("jdaEnabled", true)
    val serverChatToggle: Boolean = c.getBoolean("serverChatToggle", true)
    val guildId: String = c.getString("guildId").orEmpty()

    // Branding
    val serverName: String = c.getString("branding.name").orEmpty().ifBlank { "Server" }
    val ip: String = c.getString("branding.ip").orEmpty()
    val storeUrl: String = c.getString("branding.storeUrl").orEmpty()
    // Blank image URLs fall back to the Bliss images in the repo's branding/ folder.
    val logoUrl: String = c.getString("branding.logoUrl").orEmpty().ifBlank { "$BRANDING_BASE/icon.png" }
    val thumbnailUrl: String = c.getString("branding.thumbnailUrl").orEmpty().ifBlank { "$BRANDING_BASE/logo.png" }
    val bannerUrl: String = c.getString("branding.bannerUrl").orEmpty().ifBlank { "$BRANDING_BASE/banner.png" }
    val color: Color = runCatching { Color.decode(c.getString("branding.color")) }.getOrDefault(Color(0xB026FF))
    private val configuredMaxPlayers = c.getInt("branding.maxPlayers", 0)
    val maxPlayers: Int get() = if (configuredMaxPlayers > 0) configuredMaxPlayers else Bukkit.getMaxPlayers()

    // Channels. The old top-level serverChatChannel key still works.
    val serverChatChannel: String = c.getString("channels.serverChat").orEmpty()
        .ifBlank { c.getString("serverChatChannel").orEmpty() }
    val staffChatChannel: String = c.getString("channels.staffChat").orEmpty()
    val commandLogChannel: String = c.getString("channels.commandLog").orEmpty()
    val storeChannel: String = c.getString("channels.store").orEmpty()

    val webhookUrl: String = c.getString("webhook.url").orEmpty()
    val webhookId: String = c.getString("webhook.webhookId").orEmpty()

    val prefixes: List<String> = c.getStringList("prefixCommands").filter { it.isNotBlank() }
    val commandLogIgnore: Set<String> = c.getStringList("commandLog.ignore").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()
    val votePartyPlaceholder: String = c.getString("placeholders.voteParty").orEmpty()

    // Which game events are posted to the server chat channel
    val eventJoin: Boolean = c.getBoolean("discordEvents.join", true)
    val eventFirstJoin: Boolean = c.getBoolean("discordEvents.firstJoin", true)
    val eventLeave: Boolean = c.getBoolean("discordEvents.leave", true)
    val eventDeath: Boolean = c.getBoolean("discordEvents.death", true)
    val eventAdvancement: Boolean = c.getBoolean("discordEvents.advancement", true)
    val eventServerStatus: Boolean = c.getBoolean("discordEvents.serverStatus", true)

    /** Messages to the same Discord destination within this window are bundled into one. */
    val bundleWindowMs: Long = c.getLong("bundling.windowMs", 1000L).coerceIn(250L, 10_000L)

    // Linking
    val verifiedRoleId: String = c.getString("linking.verifiedRole").orEmpty()
    val linkCodeExpirySeconds: Long = c.getLong("linking.codeExpirySeconds", 300L).coerceIn(30L, 3600L)
    val linkRewards: LinkRewards = LinkRewards(c.getConfigurationSection("linking.rewards"))
    val linkAutoClaim: Boolean = c.getBoolean("linking.autoClaim", false)

    // Bot status (Discord presence)
    data class BotActivity(val type: String, val text: String)

    val botStatusEnabled: Boolean = c.getBoolean("botStatus.enabled", true)
    val botOnlineStatus: String = c.getString("botStatus.onlineStatus").orEmpty().lowercase().ifBlank { "online" }
    val botStatusUpdateSeconds: Long = c.getLong("botStatus.updateSeconds", 10L).coerceIn(5L, 300L)
    val botStatusRotateSeconds: Long = c.getLong("botStatus.rotateSeconds", 30L).coerceIn(10L, 3600L)
    val botStreamUrl: String = c.getString("botStatus.streamUrl").orEmpty()
    val botActivities: List<BotActivity> = c.getMapList("botStatus.activities").mapNotNull { m ->
        val text = m["text"]?.toString().orEmpty()
        if (text.isBlank()) null
        else BotActivity(m["type"]?.toString().orEmpty().lowercase().ifBlank { "watching" }, text)
    }

    // Commands
    /** One command's section under commands.minecraft or commands.discord. */
    class CommandConfig(private val section: ConfigurationSection?) {
        val enabled: Boolean = bool("enabled", true)
        fun string(key: String): String = section?.getString(key).orEmpty()
        fun bool(key: String, default: Boolean): Boolean =
            if (section?.contains(key) == true) section.getBoolean(key) else default
        fun list(key: String): List<String> =
            section?.getStringList(key).orEmpty().map { it.trim().lowercase() }.filter { it.isNotEmpty() }
    }

    val commandDisabledMessage: String = c.getString("commands.disabledMessage").orEmpty()
    val playersOnlyMessage: String = c.getString("commands.playersOnly").orEmpty()
    val discordCommandError: String = c.getString("commands.discord.error").orEmpty()
        .ifBlank { "Something went wrong running that command." }
    val discordCommandDisabled: String = c.getString("commands.discord.disabled").orEmpty()
        .ifBlank { "That command is disabled." }

    private val commandCache = ConcurrentHashMap<String, CommandConfig>()
    private val config = c
    private fun command(path: String) = commandCache.getOrPut(path) { CommandConfig(config.getConfigurationSection(path)) }

    /** commands.minecraft.<name> */
    fun mcCommand(name: String): CommandConfig = command("commands.minecraft.$name")

    /** commands.discord.<name> */
    fun discordCommand(name: String): CommandConfig = command("commands.discord.$name")

    // Linking messages (support <primary>/<secondary>)
    val primaryColour: String = c.getString("messages.colour.primary").orEmpty()
    val secondaryColour: String = c.getString("messages.colour.secondary").orEmpty()
    private val linkMessages: Map<String, String> =
        c.getConfigurationSection("messages.linking")?.getValues(false)?.mapValues { it.value.toString() }.orEmpty()
    fun linkMessage(key: String): String = linkMessages[key].orEmpty()

    // Messages
    val discordToMcMessage: String = c.getString("messages.discordToMcMessage").orEmpty()
    val mcToDiscordMessage: String = c.getString("messages.mcToDiscordMessage").orEmpty().ifBlank { "<message>" }
    val staffChatFormat: String = c.getString("messages.staffChat").orEmpty()
    val staffChatFromDiscordFormat: String = c.getString("messages.staffChatFromDiscord").orEmpty()
    val blockedMessage: String = c.getString("messages.chatFilter.blockedMessage").orEmpty()

    private companion object {
        const val BRANDING_BASE = "https://raw.githubusercontent.com/Hannaan123/SoulNetwork-Plugin/master/branding"
    }
}
