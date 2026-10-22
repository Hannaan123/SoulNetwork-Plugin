package me.thatonedevil.soulNetworkPlugin.linking

import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.dataStore
import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.instance
import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.settings
import me.thatonedevil.soulNetworkPlugin.Utils.linkMessage
import me.thatonedevil.soulNetworkPlugin.discord.DiscordBot
import net.dv8tion.jda.api.entities.UserSnowflake
import org.bukkit.Bukkit
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Account linking, ported from SoulzProxy: one-time codes made in-game (/link) and entered in
 * Discord (link panel button or /link <code>). Linking gives the verified role and announces it.
 */
object LinkManager {

    private const val CODE_LENGTH = 6
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // no 0/O/1/I mix-ups

    private data class Pending(val uuid: UUID, val name: String, val expiresAt: Long)

    sealed interface Result {
        data class Linked(val uuid: UUID, val name: String) : Result
        data object InvalidCode : Result
        data class AlreadyLinked(val name: String) : Result
    }

    private val random = SecureRandom()
    private val pending = ConcurrentHashMap<String, Pending>()

    /** Makes a new code for the player (replacing their old one) and tells them when it expires. */
    fun createCode(uuid: UUID, name: String): String {
        val now = System.currentTimeMillis()
        val ttlMs = settings.linkCodeExpirySeconds * 1000
        pending.entries.removeIf { it.value.uuid == uuid || it.value.expiresAt < now }
        var code: String
        do {
            code = (1..CODE_LENGTH).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        } while (pending.containsKey(code))
        pending[code] = Pending(uuid, name, now + ttlMs)

        Bukkit.getScheduler().runTaskLater(instance, Runnable {
            // Only if this exact code is still unused.
            if (pending[code]?.uuid == uuid && pending.remove(code) != null) {
                Bukkit.getPlayer(uuid)?.sendMessage(linkMessage("linkCodeExpired"))
            }
        }, settings.linkCodeExpirySeconds * 20)
        return code
    }

    /** Called from Discord (any thread). Links the accounts if the code is valid. */
    fun redeem(rawCode: String, discordId: String): Result {
        dataStore.uuidOf(discordId)?.let {
            return Result.AlreadyLinked(Bukkit.getOfflinePlayer(it).name ?: it.toString())
        }
        val entry = pending.remove(rawCode.trim().uppercase()) ?: return Result.InvalidCode
        if (entry.expiresAt < System.currentTimeMillis()) return Result.InvalidCode

        dataStore.link(entry.uuid, discordId)
        setVerifiedRole(discordId, give = true)
        Bukkit.getScheduler().runTask(instance, Runnable {
            Bukkit.broadcast(linkMessage("linkedBroadcast", "player" to entry.name))
        })
        fireEvent(entry.uuid, linked = true)
        return Result.Linked(entry.uuid, entry.name)
    }

    /** Unlinks a Minecraft account. Returns the Discord id that was linked, if any. */
    fun unlink(uuid: UUID): String? {
        val discordId = dataStore.unlink(uuid) ?: return null
        setVerifiedRole(discordId, give = false)
        fireEvent(uuid, linked = false)
        return discordId
    }

    /** Unlinks by Discord id. Returns the Minecraft uuid that was linked, if any. */
    fun unlinkDiscord(discordId: String): UUID? {
        val uuid = dataStore.uuidOf(discordId) ?: return null
        unlink(uuid)
        return uuid
    }

    private fun setVerifiedRole(discordId: String, give: Boolean) {
        val roleId = settings.verifiedRoleId.takeIf { it.isNotBlank() } ?: return
        val guild = DiscordBot.guild ?: return
        val role = runCatching { guild.getRoleById(roleId) }.getOrNull()
            ?: return instance.logger.warning("Verified role not found ($roleId)")
        val user = UserSnowflake.fromId(discordId)
        runCatching {
            (if (give) guild.addRoleToMember(user, role) else guild.removeRoleFromMember(user, role))
                .queue(null) { instance.logger.warning("Couldn't ${if (give) "give" else "remove"} verified role: ${it.message}") }
        }.onFailure {
            // e.g. missing Manage Roles, or the role is above the bot's highest role
            instance.logger.warning("Couldn't ${if (give) "give" else "remove"} verified role: ${it.message}")
        }
    }

    /** Fires PlayerLinkedEvent on the main thread if the player is online (for skript-reflect hooks). */
    fun fireEvent(uuid: UUID, linked: Boolean) {
        Bukkit.getScheduler().runTask(instance, Runnable {
            val player = Bukkit.getPlayer(uuid) ?: return@Runnable
            Bukkit.getPluginManager().callEvent(PlayerLinkedEvent(player, linked, uuid.toString()))
        })
    }
}
