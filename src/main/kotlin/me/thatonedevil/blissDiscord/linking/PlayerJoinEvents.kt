package me.thatonedevil.blissDiscord.linking

import io.papermc.paper.advancement.AdvancementDisplay
import me.thatonedevil.blissDiscord.BlissDiscord.Companion.settings
import me.thatonedevil.blissDiscord.Utils.formatDuration
import me.thatonedevil.blissDiscord.Utils.playerAvatarUrl
import me.thatonedevil.blissDiscord.discord.DiscordBot
import me.thatonedevil.blissDiscord.discord.Embeds
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import net.kyori.adventure.translation.GlobalTranslator
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerAdvancementDoneEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import java.util.Locale
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/** Join/leave, first join, death and advancement embeds in the server chat channel. */
class PlayerJoinEvents : Listener {

    private val timeJoined = ConcurrentHashMap<UUID, Long>()

    /** Death and advancement messages are translatable, so render them to English first. */
    private fun plain(component: Component): String =
        PlainTextComponentSerializer.plainText().serialize(GlobalTranslator.render(component, Locale.US))

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerJoin(event: PlayerJoinEvent) {
        val player = event.player
        timeJoined[player.uniqueId] = System.currentTimeMillis()
        if (!settings.jdaEnabled) return

        val avatar = playerAvatarUrl(player)
        when {
            !player.hasPlayedBefore() && settings.eventFirstJoin ->
                DiscordBot.serverChatEmbeds.offer(Embeds.playerFirstJoin(player.name, avatar))
            settings.eventJoin ->
                DiscordBot.serverChatEmbeds.offer(Embeds.playerJoined(player.name, avatar))
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event: PlayerQuitEvent) {
        val player = event.player
        val joined = timeJoined.remove(player.uniqueId) ?: System.currentTimeMillis()
        if (!settings.jdaEnabled || !settings.eventLeave) return

        val playTime = formatDuration(System.currentTimeMillis() - joined)
        DiscordBot.serverChatEmbeds.offer(Embeds.playerLeft(player.name, playerAvatarUrl(player), playTime))
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onDeath(event: PlayerDeathEvent) {
        if (!settings.jdaEnabled || !settings.eventDeath) return
        // Null when another plugin or the showDeathMessages gamerule hides it.
        val message = event.deathMessage()?.let(::plain)?.takeIf { it.isNotBlank() } ?: return
        DiscordBot.serverChatEmbeds.offer(Embeds.playerDied(message, playerAvatarUrl(event.player)))
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onAdvancement(event: PlayerAdvancementDoneEvent) {
        if (!settings.jdaEnabled || !settings.eventAdvancement) return
        // message() is null unless the game announces it in chat (skips recipes, hidden ones, gamerule off).
        if (event.message() == null) return
        val display = event.advancement.display ?: return

        val frame = when (display.frame()) {
            AdvancementDisplay.Frame.CHALLENGE -> "challenge"
            AdvancementDisplay.Frame.GOAL -> "goal"
            else -> "task"
        }
        DiscordBot.serverChatEmbeds.offer(Embeds.advancement(
            event.player.name, plain(display.title()), plain(display.description()), frame, playerAvatarUrl(event.player)
        ))
    }
}
