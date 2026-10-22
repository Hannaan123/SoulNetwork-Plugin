package me.thatonedevil.soulNetworkPlugin.storage

import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Small persistent store kept in data.yml: Discord links, staff chat toggles and the
 * last store contributor. Everything lives in memory; changes are written off the main thread.
 */
class DataStore(private val plugin: JavaPlugin) {

    private val file = File(plugin.dataFolder, "data.yml")

    private val links = ConcurrentHashMap<UUID, String>() // minecraft uuid -> discord user id
    private val staffChatToggled: MutableSet<UUID> = ConcurrentHashMap.newKeySet()
    private val rewardClaimed: MutableSet<UUID> = ConcurrentHashMap.newKeySet()

    @Volatile
    var lastContributor: String? = null
        set(value) { field = value; saveAsync() }

    val linkCount: Int get() = links.size

    fun load() {
        if (!file.exists()) return
        val yaml = YamlConfiguration.loadConfiguration(file)
        yaml.getConfigurationSection("links")?.let { section ->
            for (key in section.getKeys(false)) {
                val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                section.getString(key)?.let { links[uuid] = it }
            }
        }
        yaml.getStringList("staffChatToggled").mapNotNullTo(staffChatToggled) {
            runCatching { UUID.fromString(it) }.getOrNull()
        }
        yaml.getStringList("linkRewardClaimed").mapNotNullTo(rewardClaimed) {
            runCatching { UUID.fromString(it) }.getOrNull()
        }
        lastContributor = yaml.getString("lastContributor")
    }

    fun hasClaimedLinkReward(uuid: UUID): Boolean = uuid in rewardClaimed

    /** Marks the reward claimed. Returns false if it already was. Survives unlink/relink so it can't be farmed. */
    fun claimLinkReward(uuid: UUID): Boolean = rewardClaimed.add(uuid).also { if (it) saveAsync() }

    fun discordIdOf(uuid: UUID): String? = links[uuid]

    fun uuidOf(discordId: String): UUID? = links.entries.firstOrNull { it.value == discordId }?.key

    /** Links the accounts, replacing any previous link of either side. */
    fun link(uuid: UUID, discordId: String) {
        links.entries.removeIf { it.value == discordId }
        links[uuid] = discordId
        saveAsync()
    }

    /** Returns the discord id that was unlinked, if any. */
    fun unlink(uuid: UUID): String? = links.remove(uuid).also { if (it != null) saveAsync() }

    /** Returns the minecraft uuid that was unlinked, if any. */
    fun unlinkDiscord(discordId: String): UUID? {
        val uuid = uuidOf(discordId) ?: return null
        links.remove(uuid)
        saveAsync()
        return uuid
    }

    fun isStaffChatToggled(uuid: UUID): Boolean = uuid in staffChatToggled

    /** Flips the toggle and returns the new state. */
    fun toggleStaffChat(uuid: UUID): Boolean {
        val enabled = if (staffChatToggled.remove(uuid)) false else staffChatToggled.add(uuid)
        saveAsync()
        return enabled
    }

    private fun saveAsync() {
        if (!plugin.isEnabled) return
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { save() })
    }

    @Synchronized
    fun save() {
        val yaml = YamlConfiguration()
        links.forEach { (uuid, id) -> yaml.set("links.$uuid", id) }
        yaml.set("staffChatToggled", staffChatToggled.map { it.toString() })
        yaml.set("linkRewardClaimed", rewardClaimed.map { it.toString() })
        yaml.set("lastContributor", lastContributor)
        runCatching {
            plugin.dataFolder.mkdirs()
            val tmp = File(plugin.dataFolder, "data.yml.tmp")
            yaml.save(tmp)
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }.onFailure { plugin.logger.warning("Couldn't save data.yml: ${it.message}") }
    }
}
