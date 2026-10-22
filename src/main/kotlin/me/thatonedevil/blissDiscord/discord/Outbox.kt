package me.thatonedevil.blissDiscord.discord

import me.thatonedevil.blissDiscord.BlissDiscord.Companion.instance
import me.thatonedevil.blissDiscord.BlissDiscord.Companion.settings
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel
import net.dv8tion.jda.api.requests.RestAction
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Bundles outgoing Discord messages so bursts don't hit Discord's rate limits.
 *
 * The first message after a quiet period goes out immediately. Anything that arrives within
 * [Settings.bundleWindowMs] of the last send is held and sent together as one message on the
 * next tick, so each destination gets at most one request per window no matter how busy it is.
 */
abstract class Outbox<T>(protected val name: String) {

    companion object {
        private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { r ->
            Thread(r, "BlissDiscord-Outbox").apply { isDaemon = true }
        }
        private val all = mutableListOf<Outbox<*>>()

        /** Sends everything still queued, waiting for each request. Used on shutdown. */
        fun flushAllBlocking() = synchronized(all) { all.toList() }.forEach { it.flushBlocking() }
    }

    init { synchronized(all) { all += this } }

    protected val queue = ConcurrentLinkedDeque<T>()
    private val scheduled = AtomicBoolean(false)
    @Volatile private var lastSend = 0L

    fun offer(item: T) {
        queue.add(item)
        schedule()
    }

    private fun schedule() {
        if (!scheduled.compareAndSet(false, true)) return
        val wait = (lastSend + settings.bundleWindowMs - System.currentTimeMillis()).coerceAtLeast(0)
        scheduler.schedule({
            try {
                takeBatch()?.let { batch ->
                    lastSend = System.currentTimeMillis()
                    send(batch)?.queue(null) { instance.logger.warning("Couldn't send $name: ${it.message}") }
                }
            } catch (e: Exception) {
                instance.logger.warning("Couldn't send $name: ${e.message}")
            } finally {
                scheduled.set(false)
                if (queue.isNotEmpty()) schedule()
            }
        }, wait, TimeUnit.MILLISECONDS)
    }

    private fun flushBlocking() {
        while (true) {
            val batch = takeBatch() ?: return
            runCatching { send(batch)?.submit()?.get(3, TimeUnit.SECONDS) }
        }
    }

    /** Removes as many queued items as fit in one Discord message, or null if empty. */
    protected abstract fun takeBatch(): List<T>?

    /** Builds the request for one batch, or null if the destination isn't available. */
    protected abstract fun send(batch: List<T>): RestAction<*>?

    /** Takes up to [maxItems] items from the front while their total [size] stays within [maxSize]. */
    protected fun takeWhileFits(maxSize: Int, maxItems: Int = Int.MAX_VALUE, size: (T) -> Int): List<T>? {
        val first = queue.poll() ?: return null
        val batch = mutableListOf(first)
        var total = size(first)
        while (batch.size < maxItems) {
            val next = queue.peek() ?: break
            val nextSize = size(next)
            if (total + nextSize > maxSize) break
            batch += queue.poll() ?: break
            total += nextSize
        }
        return batch
    }
}

/** Plain text lines, merged into messages of up to 2000 characters. */
class TextOutbox(name: String, private val channel: () -> GuildMessageChannel?) : Outbox<String>(name) {
    // +1 per line for the newline; the last line has none, hence 2001.
    override fun takeBatch() = takeWhileFits(2001) { it.length + 1 }

    override fun send(batch: List<String>): RestAction<*>? {
        val target = channel() ?: run { DiscordBot.warnMissing(name); return null }
        return target.sendMessage(batch.joinToString("\n").take(2000)).setAllowedMentions(emptyList())
    }
}

/** Embeds, sent up to 10 per message (Discord's limit). */
class EmbedOutbox(private val label: String, private val channel: () -> GuildMessageChannel?) : Outbox<MessageEmbed>(label) {
    override fun takeBatch() = takeWhileFits(6000, maxItems = 10) { it.length }

    override fun send(batch: List<MessageEmbed>): RestAction<*>? {
        val target = channel() ?: run { DiscordBot.warnMissing(label); return null }
        return target.sendMessageEmbeds(batch).setAllowedMentions(emptyList())
    }
}

/**
 * In-game chat through the webhook. A single message (or a run from one player) keeps that
 * player's name and skin; a burst from several players becomes one "Name: message" list.
 */
class ChatOutbox : Outbox<ChatOutbox.Line>("chat message") {
    data class Line(val username: String, val avatarUrl: String, val content: String)

    private fun render(batch: List<Line>): String =
        if (batch.all { it.username == batch[0].username }) batch.joinToString("\n") { it.content }
        else batch.joinToString("\n") { "**${it.username}**: ${it.content}" }

    // Sized as the longer mixed-sender form ("**name**: msg" + newline), so the batch always fits.
    override fun takeBatch() = takeWhileFits(2001) { it.username.length + it.content.length + 7 }

    override fun send(batch: List<Line>): RestAction<*>? {
        val webhook = DiscordBot.webhookClient ?: return null
        val single = batch.all { it.username == batch[0].username }
        return webhook.sendMessage(render(batch).take(2000))
            .setUsername((if (single) batch[0].username else settings.serverName).take(80))
            .setAvatarUrl(if (single) batch[0].avatarUrl else settings.logoUrl)
            .setAllowedMentions(emptyList())
    }
}
