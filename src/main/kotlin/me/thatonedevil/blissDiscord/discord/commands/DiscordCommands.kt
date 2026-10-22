package me.thatonedevil.blissDiscord.discord.commands

import me.thatonedevil.blissDiscord.Hooks
import me.thatonedevil.blissDiscord.BlissDiscord.Companion.dataStore
import me.thatonedevil.blissDiscord.BlissDiscord.Companion.settings
import me.thatonedevil.blissDiscord.Utils.fill
import me.thatonedevil.blissDiscord.Utils.onMainThread
import me.thatonedevil.blissDiscord.Utils.playerBustUrl
import me.thatonedevil.blissDiscord.Utils.playerAvatarUrl
import me.thatonedevil.blissDiscord.discord.DiscordBot
import me.thatonedevil.blissDiscord.discord.Embeds
import me.thatonedevil.blissDiscord.linking.LinkManager
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.Statistic
import java.util.concurrent.CompletableFuture

/** What a Discord command answers with. Used by both slash and prefix commands. */
sealed interface Reply {
    data class Text(val text: String) : Reply
    data class Embed(val embed: MessageEmbed) : Reply
}

private fun text(s: String): CompletableFuture<Reply> = CompletableFuture.completedFuture(Reply.Text(s))

/**
 * Discord command logic. Anything touching Bukkit runs on the main thread via [onMainThread];
 * the result is then turned into an embed on the calling (JDA) thread.
 */
object DiscordCommands {

    /** commands.discord.<command>.<key> with placeholders filled in. */
    private fun msg(command: String, key: String, vararg placeholders: Pair<String, String>): String =
        fill(settings.discordCommand(command).string(key), *placeholders)

    fun ip(): CompletableFuture<Reply> =
        text(if (settings.ip.isBlank()) msg("ip", "notSet") else msg("ip", "reply", "ip" to settings.ip))

    fun store(): CompletableFuture<Reply> =
        text(if (settings.storeUrl.isBlank()) msg("store", "notSet") else msg("store", "reply", "url" to settings.storeUrl))

    fun stats(requester: User?): CompletableFuture<Reply> = onMainThread {
        Embeds.Stats(
            uptimeMillis = DiscordBot.uptimeMillis,
            tps = Bukkit.getTPS().firstOrNull() ?: 20.0,
            voteParty = Hooks.placeholder(settings.votePartyPlaceholder),
            linkedUsers = dataStore.linkCount,
            lastContributor = dataStore.lastContributor,
            uniquePlayers = Bukkit.getOfflinePlayers().size,
            onlineNames = Bukkit.getOnlinePlayers().map { it.name }.sorted(),
            maxPlayers = settings.maxPlayers,
        )
    }.thenApply { Reply.Embed(Embeds.stats(it, requester)) }

    fun online(requester: User?): CompletableFuture<Reply> = onMainThread {
        val players = Bukkit.getOnlinePlayers()
        val groups = players
            .map { Hooks.onlineGroup(it.uniqueId) to it.name }
            .groupBy({ it.first }, { it.second })
            .entries
            .sortedByDescending { it.key.second }
            .map { (group, names) -> group.first to names.sorted() }
        Triple(groups, players.size, settings.maxPlayers)
    }.thenApply { (groups, count, max) -> Reply.Embed(Embeds.online(groups, count, max, requester)) }

    /** /player <name> */
    fun playerByName(name: String, requester: User?): CompletableFuture<Reply> = onMainThread {
        // getOfflinePlayerIfCached never makes a blocking Mojang lookup.
        Bukkit.getPlayerExact(name) ?: Bukkit.getOfflinePlayerIfCached(name)
    }.thenCompose { player ->
        if (player == null || (!player.isOnline && !player.hasPlayedBefore())) text(msg("player", "notFound", "name" to name))
        else profile(player, requester)
    }

    /** /profile [user] */
    fun playerByDiscord(user: User, requester: User?): CompletableFuture<Reply> {
        val uuid = dataStore.uuidOf(user.id)
            ?: return text(msg("profile", "notLinked", "user" to user.asMention))
        return onMainThread { Bukkit.getOfflinePlayer(uuid) }.thenCompose { profile(it, requester) }
    }

    private fun profile(player: OfflinePlayer, requester: User?): CompletableFuture<Reply> {
        val base = onMainThread {
            Embeds.Profile(
                name = player.name ?: player.uniqueId.toString(),
                online = player.isOnline,
                rank = null,
                playTimeMillis = runCatching { player.getStatistic(Statistic.PLAY_ONE_MINUTE) * 50L }.getOrDefault(0L), // ticks -> ms
                kills = runCatching { player.getStatistic(Statistic.PLAYER_KILLS) }.getOrDefault(0),
                deaths = runCatching { player.getStatistic(Statistic.DEATHS) }.getOrDefault(0),
                firstJoined = player.firstPlayed,
                lastSeen = player.lastSeen,
                balance = Hooks.balance(player),
                discordId = dataStore.discordIdOf(player.uniqueId),
                bustUrl = playerBustUrl(player),
            )
        }
        return base.thenCombine(Hooks.rank(player.uniqueId)) { profile, rank ->
            Reply.Embed(Embeds.profile(profile.copy(rank = rank), requester))
        }
    }

    /** Used by /link <code> and the link panel's button. */
    fun link(code: String, user: User): CompletableFuture<Reply> =
        when (val result = LinkManager.redeem(code, user.id)) {
            is LinkManager.Result.Linked -> onMainThread { playerAvatarUrl(Bukkit.getOfflinePlayer(result.uuid)) }
                .thenApply { Reply.Embed(Embeds.linkSuccess(user.name, result.name, it)) }
            is LinkManager.Result.AlreadyLinked ->
                text(msg("link", "alreadyLinked", "player" to result.name))
            LinkManager.Result.InvalidCode ->
                text(msg("link", "invalidCode"))
        }

    fun unlink(user: User): CompletableFuture<Reply> {
        val uuid = LinkManager.unlinkDiscord(user.id) ?: return text(msg("unlink", "notLinked"))
        return text(msg("unlink", "success", "player" to (Bukkit.getOfflinePlayer(uuid).name ?: uuid.toString())))
    }
}
