package me.thatonedevil.soulNetworkPlugin.discord

import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.settings
import me.thatonedevil.soulNetworkPlugin.Utils.escapeCodeBlock
import me.thatonedevil.soulNetworkPlugin.Utils.formatDuration
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.EmbedBuilder.ZERO_WIDTH_SPACE
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import java.awt.Color
import java.time.Instant

/** All embeds the plugin sends, styled with the branding from config.yml. */
object Embeds {

    /** Shades of the brand color, so every embed stays on theme. */
    private object Shade {
        val main get() = settings.color
        val light get() = mix(settings.color, Color.WHITE, 0.35)   // joins, starts, success
        val glow get() = mix(settings.color, Color.WHITE, 0.6)     // first join, challenges
        val dark get() = mix(settings.color, Color.BLACK, 0.45)    // leaves, stops
        val darkest get() = mix(settings.color, Color.BLACK, 0.75) // deaths

        private fun mix(a: Color, b: Color, t: Double) = Color(
            (a.red + (b.red - a.red) * t).toInt(),
            (a.green + (b.green - a.green) * t).toInt(),
            (a.blue + (b.blue - a.blue) * t).toInt(),
        )
    }

    private fun base(color: Color = Shade.main): EmbedBuilder = EmbedBuilder().setColor(color)

    /** Footer with the server icon, e.g. "Bliss • bliss.minehut.gg". */
    private fun EmbedBuilder.brandFooter(text: String = settings.ip): EmbedBuilder = apply {
        val footer = if (text.isBlank()) settings.serverName else "${settings.serverName} • $text"
        setFooter(footer, settings.logoUrl)
    }

    private fun EmbedBuilder.requestedBy(user: User?): EmbedBuilder = apply {
        if (user != null) setFooter("Requested by ${user.name} • ID: ${user.id}", user.effectiveAvatarUrl)
        else brandFooter()
        setTimestamp(Instant.now())
    }

    /** Discord field values are capped at 1024 characters. */
    private fun fit(text: String, max: Int = 1024): String =
        if (text.length <= max) text else text.take(max - 1) + "…"

    fun serverStarted(): MessageEmbed = base(Shade.light)
        .setAuthor("${settings.serverName} is online ✅", null, settings.logoUrl)
        .apply { if (settings.ip.isNotBlank()) setDescription("Join now at **${settings.ip}**!") }
        .setImage(settings.bannerUrl)
        .setTimestamp(Instant.now())
        .build()

    fun serverStopped(): MessageEmbed = base(Shade.dark)
        .setAuthor("${settings.serverName} is offline ❌", null, settings.logoUrl)
        .setTimestamp(Instant.now())
        .build()

    fun playerJoined(name: String, avatarUrl: String): MessageEmbed = base(Shade.light)
        .setAuthor("$name joined the server!", null, avatarUrl)
        .brandFooter()
        .setTimestamp(Instant.now())
        .build()

    fun playerFirstJoin(name: String, avatarUrl: String): MessageEmbed = base(Shade.glow)
        .setAuthor("$name joined for the first time! 🎉", null, avatarUrl)
        .setDescription("Welcome to **${settings.serverName}**! 💎")
        .setThumbnail(settings.thumbnailUrl)
        .brandFooter()
        .setTimestamp(Instant.now())
        .build()

    fun playerDied(deathMessage: String, avatarUrl: String): MessageEmbed = base(Shade.darkest)
        .setAuthor(fit("☠ $deathMessage", 256), null, avatarUrl)
        .build()

    /** frame: "task", "goal" or "challenge", worded like the vanilla chat message. */
    fun advancement(name: String, title: String, description: String, frame: String, avatarUrl: String): MessageEmbed {
        val (verb, color, icon) = when (frame) {
            "challenge" -> Triple("has completed the challenge", Shade.glow, "🏆")
            "goal" -> Triple("has reached the goal", Shade.main, "🎯")
            else -> Triple("has made the advancement", Shade.main, "⭐")
        }
        return base(color)
            .setAuthor(fit("$icon $name $verb [$title]", 256), null, avatarUrl)
            .apply { if (description.isNotBlank()) setDescription("*$description*") }
            .build()
    }

    fun playerLeft(name: String, avatarUrl: String, playTime: String): MessageEmbed = base(Shade.dark)
        .setAuthor("$name left the server!", null, avatarUrl)
        .setDescription("**Time played:** `$playTime`")
        .brandFooter()
        .setTimestamp(Instant.now())
        .build()

    fun storePurchase(player: String, item: String, price: String, bustUrl: String): MessageEmbed {
        val store = if (settings.storeUrl.isNotBlank()) "\n\n🛒 You can also buy something at ${settings.storeUrl}" else ""
        return base()
            .setAuthor("${settings.serverName} Store", settings.storeUrl.ifBlank { null }, settings.logoUrl)
            .setThumbnail(bustUrl)
            .setTitle("💜 $player bought an item from our store!")
            .setDescription("$player bought **$item** for **$$price**!$store")
            .setImage(settings.bannerUrl)
            .brandFooter("Community Store")
            .setTimestamp(Instant.now())
            .build()
    }

    /** The /linkembed panel (posted with a "Click to link" button), from SoulzProxy. */
    fun linkPanel(): MessageEmbed = base()
        .setAuthor("${settings.serverName} Linking System", null, settings.logoUrl)
        .setTitle("🔗 Link Your Minecraft Account")
        .setThumbnail(settings.thumbnailUrl)
        .setDescription("⚡ **Easily connect your Discord & Minecraft accounts!**\n\nFollow the steps below to complete the linking process.")
        .addField("💎 **Step 1:**", "Use `/link` in **Minecraft** to generate a unique code.", false)
        .addField("💎 **Step 2:**", "Click the button below and enter your **6-character code**.", false)
        .addField("💎 **Step 3:**", "If the code is correct, your accounts will be linked **automatically**!", false)
        .addField("💎 **Step 4:**", "Claim your reward using `/linkclaim` in **Minecraft**", false)
        .setImage(settings.bannerUrl)
        .brandFooter("Linking System")
        .build()

    fun linkSuccess(discordName: String, minecraftName: String, avatarUrl: String): MessageEmbed = base(Shade.light)
        .setAuthor("${settings.serverName} Linking System", null, settings.logoUrl)
        .setTitle("✅ Account Linked Successfully!")
        .setDescription("Your Discord account has been linked to your Minecraft profile.\nUse `/linkclaim` in-game to claim your reward!")
        .addField("🔗 Discord:", "**$discordName**", false)
        .addField("🎮 Minecraft:", "**$minecraftName**", false)
        .setThumbnail(avatarUrl)
        .brandFooter("Linking System")
        .setTimestamp(Instant.now())
        .build()

    data class Stats(
        val uptimeMillis: Long,
        val tps: Double,
        val voteParty: String?,
        val linkedUsers: Int,
        val lastContributor: String?,
        val uniquePlayers: Int,
        val onlineNames: List<String>,
        val maxPlayers: Int,
    )

    fun stats(s: Stats, requester: User?): MessageEmbed {
        val online = if (s.onlineNames.isEmpty()) "Nobody online" else s.onlineNames.joinToString(", ")
        val community = buildString {
            if (s.voteParty != null) append("• **Voteparty**: `${s.voteParty}`\n")
            append("• **Linked Users**: `${s.linkedUsers}`\n")
            append("• **Last Contributor**: `${s.lastContributor ?: "None yet"}`")
        }
        val players = "• **Unique Players**: `${s.uniquePlayers}`\n" +
            "• **Online Players (${s.onlineNames.size}/${s.maxPlayers})**:\n" +
            "```${escapeCodeBlock(online)}```"

        return base()
            .setAuthor("📊 ${settings.serverName} Statistics", null, settings.logoUrl)
            .setThumbnail(settings.thumbnailUrl)
            .setDescription(
                "• **Bot Uptime**: `${formatDuration(s.uptimeMillis)}`\n" +
                    "• **Server TPS**: `${"%.2f".format(s.tps.coerceAtMost(20.0))}`"
            )
            .addField(ZERO_WIDTH_SPACE, community, false)
            .addField(ZERO_WIDTH_SPACE, fit(players), false)
            .requestedBy(requester)
            .build()
    }

    /** groups: rank name -> player names, already sorted. */
    fun online(groups: List<Pair<String, List<String>>>, count: Int, max: Int, requester: User?): MessageEmbed {
        val builder = base()
            .setAuthor("🟣 Online Players ($count/$max)", null, settings.logoUrl)
            .setThumbnail(settings.thumbnailUrl)
            .requestedBy(requester)
        if (groups.isEmpty()) {
            builder.setDescription("Nobody is online right now. Join at **${settings.ip}**!")
        } else {
            // Discord allows 25 fields per embed.
            groups.take(25).forEach { (rank, names) ->
                builder.addField("$rank (${names.size})", fit("```${escapeCodeBlock(names.joinToString(", "))}```"), false)
            }
        }
        return builder.build()
    }

    data class Profile(
        val name: String,
        val online: Boolean,
        val rank: String?,
        val playTimeMillis: Long,
        val kills: Int,
        val deaths: Int,
        val firstJoined: Long,
        val lastSeen: Long,
        val balance: String?,
        val discordId: String?,
        val bustUrl: String,
    )

    fun profile(p: Profile, requester: User?): MessageEmbed {
        val kd = if (p.deaths == 0) p.kills.toString() else "%.2f".format(p.kills.toDouble() / p.deaths)
        val builder = base()
            .setAuthor("👤 ${p.name}", null, settings.logoUrl)
            .setThumbnail(p.bustUrl)
            .setDescription(if (p.online) "🟣 **Online now**" else "⚫ Last seen <t:${p.lastSeen / 1000}:R>")
            .addField("Rank", p.rank ?: "N/A", true)
            .addField("Playtime", formatDuration(p.playTimeMillis), true)
            .addField("Balance", p.balance ?: "N/A", true)
            .addField("Kills", p.kills.toString(), true)
            .addField("Deaths", p.deaths.toString(), true)
            .addField("K/D", kd, true)
            .addField("First joined", if (p.firstJoined > 0) "<t:${p.firstJoined / 1000}:D>" else "N/A", true)
            .addField("Discord", p.discordId?.let { "<@$it>" } ?: "Not linked", true)
        return builder.requestedBy(requester).build()
    }
}
