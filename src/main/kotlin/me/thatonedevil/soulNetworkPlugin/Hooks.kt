package me.thatonedevil.soulNetworkPlugin

import me.clip.placeholderapi.PlaceholderAPI
import me.thatonedevil.soulNetworkPlugin.SoulNetworkPlugin.Companion.lpApi
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import java.util.UUID
import java.util.concurrent.CompletableFuture

/**
 * Optional integrations. Each one returns null when the plugin isn't installed,
 * and the classes are only touched after that check so nothing breaks without them.
 */
object Hooks {

    private fun enabled(name: String) = Bukkit.getPluginManager().isPluginEnabled(name)

    /** Server-wide placeholder value, or null if PlaceholderAPI or the expansion is missing. Main thread. */
    fun placeholder(text: String): String? {
        if (text.isBlank() || !enabled("PlaceholderAPI")) return null
        val result = PlaceholderAPI.setPlaceholders(null as OfflinePlayer?, text)
        return result.takeUnless { it == text || it.isBlank() }
    }

    /** Formatted balance, or null without Vault + an economy plugin. Main thread. */
    fun balance(player: OfflinePlayer): String? {
        if (!enabled("Vault")) return null
        val economy = Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider ?: return null
        return runCatching { economy.format(economy.getBalance(player)) }.getOrNull()
    }

    /** Display name of the player's primary LuckPerms group (loads offline users). Any thread. */
    fun rank(uuid: UUID): CompletableFuture<String?> {
        val api = lpApi ?: return CompletableFuture.completedFuture(null)
        return api.userManager.loadUser(uuid).thenApply { user ->
            val groupName = user.primaryGroup
            val group = api.groupManager.getGroup(groupName)
            (group?.displayName ?: groupName).replaceFirstChar { it.uppercase() }
        }.exceptionally { null }
    }

    /** Primary group of an online player, plus its weight for sorting. Main thread. */
    fun onlineGroup(uuid: UUID): Pair<String, Int> {
        val api = lpApi ?: return "Players" to 0
        val user = api.userManager.getUser(uuid) ?: return "Players" to 0
        val group = api.groupManager.getGroup(user.primaryGroup)
        val name = (group?.displayName ?: user.primaryGroup).replaceFirstChar { it.uppercase() }
        return name to (group?.weight?.orElse(0) ?: 0)
    }
}
