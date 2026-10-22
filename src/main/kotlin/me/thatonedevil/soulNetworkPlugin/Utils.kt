package me.thatonedevil.soulNetworkPlugin

import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.instance
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.util.concurrent.CompletableFuture


object Utils {

    /**
     * Head image for a player's skin. Online-mode (and proxy-forwarded) players have a
     * version 4 UUID, which mc-heads resolves directly. Offline/Bedrock UUIDs don't map
     * to a Mojang account, so fall back to looking the skin up by name.
     */
    fun playerAvatarUrl(player: OfflinePlayer): String = "https://mc-heads.net/avatar/${skinId(player)}/128"

    /** Armored bust render, as used by the old store embed. */
    fun playerBustUrl(player: OfflinePlayer): String = "https://mineskin.eu/armor/bust/${skinId(player)}/100"

    private fun skinId(player: OfflinePlayer): String =
        if (player.uniqueId.version() == 4) player.uniqueId.toString().replace("-", "")
        else player.name ?: player.uniqueId.toString()

    fun formatDuration(milliseconds: Long): String {
        val totalSeconds = milliseconds.coerceAtLeast(0) / 1000
        val days = totalSeconds / (24 * 3600)
        val hours = (totalSeconds % (24 * 3600)) / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        return when {
            days > 0 -> "${days}d, ${hours}h, ${minutes}m and ${seconds}s"
            hours > 0 -> "${hours}h, ${minutes}m and ${seconds}s"
            minutes > 0 -> "${minutes}m and ${seconds}s"
            else -> "${seconds}s"
        }
    }

    /** Escapes MiniMessage tags in user-written text so players can't inject formatting/click events. */
    fun escapeTags(text: String): String = MiniMessage.miniMessage().escapeTags(text)

    /** Stops user text from breaking out of Discord markdown code blocks. */
    fun escapeCodeBlock(text: String): String = text.replace("```", "ʼʼʼ")

    /** Runs [block] on the main server thread and completes with its result. */
    fun <T> onMainThread(block: () -> T): CompletableFuture<T> {
        val future = CompletableFuture<T>()
        if (Bukkit.isPrimaryThread()) {
            runCatching(block).fold(future::complete, future::completeExceptionally)
        } else {
            Bukkit.getScheduler().runTask(instance, Runnable {
                runCatching(block).fold(future::complete, future::completeExceptionally)
            })
        }
        return future
    }

    /**
     * A messages.linking.* message with <primary>/<secondary> colours and the given placeholders
     * filled in. Placeholder values are escaped so player names can't inject tags.
     */
    fun linkMessage(key: String, vararg placeholders: Pair<String, String>): Component {
        val s = SoulNetworkPlugin.settings
        var text = s.linkMessage(key)
            .replace("<primary>", s.primaryColour)
            .replace("<secondary>", s.secondaryColour)
        for ((name, value) in placeholders) text = text.replace("<$name>", escapeTags(value))
        return convertLegacyToMiniMessage(text)
    }

    private val LEGACY_TAGS = mapOf(
            "&0" to "<black>", "&1" to "<dark_blue>", "&2" to "<dark_green>", "&3" to "<dark_aqua>",
            "&4" to "<dark_red>", "&5" to "<dark_purple>", "&6" to "<gold>", "&7" to "<gray>",
            "&8" to "<dark_gray>", "&9" to "<blue>", "&a" to "<green>", "&b" to "<aqua>",
            "&c" to "<red>", "&d" to "<light_purple>", "&e" to "<yellow>", "&f" to "<white>",
            "&l" to "<bold>", "&o" to "<italic>", "&n" to "<underlined>", "&m" to "<strikethrough>",
            "&r" to "<reset>"
        )
    private val LEGACY_CODE = Regex("&[0-9a-fk-or]")

    fun convertLegacyToMiniMessage(input: String): Component {
        val converted = if ('&' !in input) input
            else LEGACY_CODE.replace(input) { LEGACY_TAGS[it.value] ?: it.value }
        return MiniMessage.miniMessage().deserialize(converted)
    }

}
