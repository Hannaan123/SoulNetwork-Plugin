package me.thatonedevil.blissDiscord

import me.thatonedevil.blissDiscord.chat.CommandLogger
import me.thatonedevil.blissDiscord.chat.McToDiscord
import me.thatonedevil.blissDiscord.chat.StaffChat
import me.thatonedevil.blissDiscord.chatfilter.ChatFilter
import me.thatonedevil.blissDiscord.config.PluginConfig
import me.thatonedevil.blissDiscord.discord.DiscordBot
import me.thatonedevil.blissDiscord.linking.PlayerJoinEvents
import me.thatonedevil.blissDiscord.minecraft.commands.AnnouncePurchaseCommand
import me.thatonedevil.blissDiscord.minecraft.commands.BlissCommand
import me.thatonedevil.blissDiscord.minecraft.commands.CommandToggles
import me.thatonedevil.blissDiscord.minecraft.commands.LinkCommand
import me.thatonedevil.blissDiscord.minecraft.commands.LinkClaimCommand
import me.thatonedevil.blissDiscord.minecraft.commands.StaffChatCommand
import me.thatonedevil.blissDiscord.minecraft.commands.StaffChatToggleCommand
import me.thatonedevil.blissDiscord.minecraft.commands.UnlinkCommand
import me.thatonedevil.blissDiscord.minecraft.commands.UuidCommand
import me.thatonedevil.blissDiscord.storage.DataStore
import net.luckperms.api.LuckPerms
import org.bukkit.Bukkit
import org.bukkit.command.CommandExecutor
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Logger

class BlissDiscord : JavaPlugin() {

    companion object {
        lateinit var instance: BlissDiscord
        lateinit var settings: PluginConfig
        lateinit var dataStore: DataStore
        var lpApi: LuckPerms? = null
        var soulLogger: Logger? = null
        var chatFilter: ChatFilter? = null

        val isDataStoreLoaded: Boolean get() = ::dataStore.isInitialized
    }

    /** The plugin used to be called SoulNetworkPlugin; carry its folder (config, links, badwords) over once. */
    override fun onLoad() {
        val old = dataFolder.resolveSibling("SoulNetworkPlugin")
        if (old.isDirectory && !dataFolder.exists()) {
            if (old.renameTo(dataFolder)) logger.info("Moved plugins/${old.name} to plugins/${dataFolder.name}")
            else logger.warning("Couldn't move plugins/${old.name} to plugins/${dataFolder.name}. Move it by hand to keep your config and links.")
        }
    }

    override fun onEnable() {
        instance = this
        soulLogger = logger

        saveDefaultConfig()
        if (!dataFolder.resolve("badwords.txt").exists()) saveResource("badwords.txt", false)
        reloadConfig()
        migrateConfig()
        // Write any new keys from this version into the existing config.yml.
        config.options().copyDefaults(true)
        saveConfig()
        settings = PluginConfig(config)

        dataStore = DataStore(this).also { it.load() }
        chatFilter = ChatFilter().also { it.reloadBadWords() }
        lpApi = Bukkit.getServicesManager().getRegistration(LuckPerms::class.java)?.provider

        if (!DiscordBot.start()) return

        val pm = server.pluginManager
        pm.registerEvents(StaffChat, this)
        pm.registerEvents(McToDiscord(), this)
        pm.registerEvents(PlayerJoinEvents(), this)
        pm.registerEvents(CommandLogger(), this)
        pm.registerEvents(CommandToggles, this)

        command("bliss", BlissCommand(this))
        command("link", LinkCommand())
        command("unlink", UnlinkCommand())
        command("linkclaim", LinkClaimCommand())
        command("sc", StaffChatCommand())
        command("sctoggle", StaffChatToggleCommand())
        command("announcepurchase", AnnouncePurchaseCommand())
        command("uuid", UuidCommand())
    }

    private fun command(name: String, executor: CommandExecutor) {
        val cmd = getCommand(name) ?: return logger.warning("Command /$name is missing from plugin.yml")
        CommandToggles.register(cmd, executor)
    }

    /** Moves settings from older config layouts to the current one. */
    private fun migrateConfig() {
        val c = config
        // linking.rewards used to be a plain list of commands
        if (c.isList("linking.rewards")) {
            val commands = c.getStringList("linking.rewards")
            c.set("linking.rewards", null)
            c.set("linking.rewards.commands", commands)
        }
        // botStatus used to have a single type/text instead of the activities list
        if (c.contains("botStatus.type", true) && !c.contains("botStatus.activities", true)) {
            c.set("botStatus.activities", listOf(mapOf(
                "type" to c.getString("botStatus.type", "watching"),
                "text" to c.getString("botStatus.text", "<online> players on <ip>"),
            )))
            c.set("botStatus.type", null)
            c.set("botStatus.text", null)
        }
    }

    fun reloadSettings() {
        reloadConfig()
        settings = PluginConfig(config)
        chatFilter?.reloadBadWords()
        DiscordBot.onConfigReload()
        CommandToggles.refreshPlayers()
    }

    override fun onDisable() {
        DiscordBot.shutdown()
        if (isDataStoreLoaded) dataStore.save()
    }
}
