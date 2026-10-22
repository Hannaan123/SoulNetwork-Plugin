package me.thatonedevil.blissDiscord.linking

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import me.thatonedevil.blissDiscord.BlissDiscord.Companion.instance
import me.thatonedevil.blissDiscord.Hooks
import me.thatonedevil.blissDiscord.Utils.convertLegacyToMiniMessage
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.configuration.ConfigurationSection
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

/**
 * The one-time reward for linking (linking.rewards in config.yml): money, XP, items,
 * LuckPerms groups/permissions and console commands. Invalid entries are logged and skipped.
 */
class LinkRewards(section: ConfigurationSection?) {

    val money: Double = section?.getDouble("money", 0.0)?.coerceAtLeast(0.0) ?: 0.0
    val xpLevels: Int = section?.getInt("xpLevels", 0)?.coerceAtLeast(0) ?: 0
    val groups: List<String> = section?.getStringList("groups").orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
    val permissions: List<String> = section?.getStringList("permissions").orEmpty().map { it.trim() }.filter { it.isNotEmpty() }
    val commands: List<String> = section?.getStringList("commands").orEmpty().filter { it.isNotBlank() }
    private val items: List<ItemStack> = section?.getList("items").orEmpty().mapNotNull(::parseItem)

    val isEmpty: Boolean
        get() = money <= 0 && xpLevels <= 0 && items.isEmpty() && groups.isEmpty() && permissions.isEmpty() && commands.isEmpty()

    /** Gives everything to [player]. Main thread. */
    fun give(player: Player, discordId: String?) {
        if (money > 0 && !Hooks.deposit(player, money)) {
            instance.logger.warning("Couldn't give ${player.name} their link reward money: Vault or an economy plugin is missing")
        }
        if (xpLevels > 0) player.giveExpLevels(xpLevels)

        // Full inventory: the rest drops at their feet.
        val leftover = player.inventory.addItem(*items.map { it.clone() }.toTypedArray())
        leftover.values.forEach { player.world.dropItemNaturally(player.location, it) }

        if ((groups.isNotEmpty() || permissions.isNotEmpty()) && !Hooks.addNodes(player.uniqueId, groups, permissions)) {
            instance.logger.warning("Couldn't give ${player.name} their link reward groups/permissions: LuckPerms is missing")
        }

        val console = Bukkit.getConsoleSender()
        for (raw in commands) {
            val cmd = raw.removePrefix("/")
                .replace("<player>", player.name)
                .replace("<uuid>", player.uniqueId.toString())
                .replace("<discord>", discordId.orEmpty())
            runCatching { Bukkit.dispatchCommand(console, cmd) }
                .onFailure { instance.logger.warning("Link reward command failed ($cmd): ${it.message}") }
        }
    }

    /**
     * An item is either a string like "diamond 5", or a map with
     * material, amount, name, lore and enchantments ({sharpness: 2}).
     */
    private fun parseItem(entry: Any?): ItemStack? {
        val map: Map<*, *> = when (entry) {
            is Map<*, *> -> entry
            is ConfigurationSection -> entry.getValues(false)
            is String -> entry.trim().split(Regex("\\s+")).let { mapOf("material" to it[0], "amount" to it.getOrNull(1)) }
            else -> return null
        }
        val materialName = map["material"]?.toString().orEmpty()
        val material = Material.matchMaterial(materialName)
        if (material == null || !material.isItem) {
            instance.logger.warning("Link reward item skipped: unknown material '$materialName'")
            return null
        }
        val amount = map["amount"]?.toString()?.toIntOrNull()?.coerceIn(1, 6400) ?: 1
        val item = ItemStack(material, amount)

        item.editMeta { meta ->
            map["name"]?.toString()?.takeIf { it.isNotBlank() }?.let {
                meta.displayName(convertLegacyToMiniMessage(it).decoration(TextDecoration.ITALIC, false))
            }
            (map["lore"] as? List<*>)?.takeIf { it.isNotEmpty() }?.let { lines ->
                meta.lore(lines.map { convertLegacyToMiniMessage(it.toString()).decoration(TextDecoration.ITALIC, false) })
            }
        }

        val enchants = when (val e = map["enchantments"]) {
            is Map<*, *> -> e
            is ConfigurationSection -> e.getValues(false)
            else -> emptyMap<Any, Any>()
        }
        val registry = RegistryAccess.registryAccess().getRegistry(RegistryKey.ENCHANTMENT)
        for ((key, level) in enchants) {
            val enchant = NamespacedKey.fromString(key.toString().lowercase())?.let { registry.get(it) }
            if (enchant == null) {
                instance.logger.warning("Link reward enchantment skipped: unknown enchantment '$key'")
                continue
            }
            item.addUnsafeEnchantment(enchant, level.toString().toIntOrNull()?.coerceIn(1, 255) ?: 1)
        }
        return item
    }
}
