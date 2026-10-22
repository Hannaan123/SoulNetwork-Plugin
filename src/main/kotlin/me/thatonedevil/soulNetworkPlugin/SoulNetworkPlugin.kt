package me.thatonedevil.soulNetworkPlugin

import me.thatonedevil.soulNetworkPlugin.chat.CommandLogger
import me.thatonedevil.soulNetworkPlugin.chat.McToDiscord
import me.thatonedevil.soulNetworkPlugin.chat.StaffChat
import me.thatonedevil.soulNetworkPlugin.chatfilter.ChatFilter
import me.thatonedevil.soulNetworkPlugin.config.PluginConfig
import me.thatonedevil.soulNetworkPlugin.discord.DiscordBot
import me.thatonedevil.soulNetworkPlugin.linking.PlayerJoinEvents
import me.thatonedevil.soulNetworkPlugin.minecraft.commands.AnnouncePurchaseCommand
import me.thatonedevil.soulNetworkPlugin.minecraft.commands.BlissCommand
import me.thatonedevil.soulNetworkPlugin.minecraft.commands.LinkCommand
import me.thatonedevil.soulNetworkPlugin.minecraft.commands.LinkClaimCommand
import me.thatonedevil.soulNetworkPlugin.minecraft.commands.StaffChatCommand
import me.thatonedevil.soulNetworkPlugin.minecraft.commands.StaffChatToggleCommand
import me.thatonedevil.soulNetworkPlugin.minecraft.commands.UnlinkCommand
import me.thatonedevil.soulNetworkPlugin.minecraft.commands.UuidCommand
import me.thatonedevil.soulNetworkPlugin.storage.DataStore
import net.luckperms.api.LuckPerms
import org.bukkit.Bukkit
import org.bukkit.command.CommandExecutor
import org.bukkit.plugin.java.JavaPlugin
import java.util.logging.Logger

class SoulNetworkPlugin : JavaPlugin() {

    companion object {
        lateinit var instance: SoulNetworkPlugin
        lateinit var settings: PluginConfig
        lateinit var dataStore: DataStore
        var lpApi: LuckPerms? = null
        var soulLogger: Logger? = null
        var chatFilter: ChatFilter? = null

        val isDataStoreLoaded: Boolean get() = ::dataStore.isInitialized
    }

    override fun onEnable() {
        instance = this
        soulLogger = logger

        saveDefaultConfig()
        if (!dataFolder.resolve("badwords.txt").exists()) saveResource("badwords.txt", false)
        reloadConfig()
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
        cmd.setExecutor(executor)
        if (executor is org.bukkit.command.TabCompleter) cmd.tabCompleter = executor
    }

    fun reloadSettings() {
        reloadConfig()
        settings = PluginConfig(config)
        chatFilter?.reloadBadWords()
        DiscordBot.onConfigReload()
    }

    override fun onDisable() {
        DiscordBot.shutdown()
        if (isDataStoreLoaded) dataStore.save()
    }
}
