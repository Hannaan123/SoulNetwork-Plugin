package me.thatonedevil.soulNetworkPlugin.config

import org.bukkit.Bukkit
import org.bukkit.configuration.file.FileConfiguration
import java.awt.Color

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
    val linkRewards: List<String> = c.getStringList("linking.rewards").filter { it.isNotBlank() }

    // Bot status
    val botStatusEnabled: Boolean = c.getBoolean("botStatus.enabled", true)
    val botStatusType: String = c.getString("botStatus.type").orEmpty().lowercase().ifBlank { "watching" }
    val botStatusText: String = c.getString("botStatus.text").orEmpty().ifBlank { "<online> players on <ip>" }

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
