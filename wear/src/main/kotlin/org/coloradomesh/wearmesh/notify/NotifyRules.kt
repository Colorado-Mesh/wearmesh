package org.coloradomesh.wearmesh.notify

import org.coloradomesh.wearmesh.ui.Mentions

/**
 * Which stored messages notify, decided for M2 step 4:
 * - conversations notify by default when they are direct messages or private channels, and not
 *   when they are Public or hashtag channels; each can be turned on or off;
 * - a message naming this radio notifies even when its conversation is off;
 * - nothing notifies for the conversation open on screen, or for the backlog that the first
 *   sync after choosing a radio brings.
 */
object NotifyRules {
    enum class Kind { DIRECT, PUBLIC, HASHTAG, PRIVATE }

    /** [channelKind] is the stored [org.coloradomesh.meshcore.protocol.Channel.Kind] name for a channel. */
    fun kindOf(conversation: String, channelKind: String?): Kind = when {
        conversation.startsWith("d:") -> Kind.DIRECT
        channelKind == "PUBLIC" -> Kind.PUBLIC
        channelKind == "HASHTAG" -> Kind.HASHTAG
        // A channel whose kind is not stored yet is treated as private: better one buzz too many.
        else -> Kind.PRIVATE
    }

    fun onByDefault(kind: Kind): Boolean = kind == Kind.DIRECT || kind == Kind.PRIVATE

    /** Whether [conversation] notifies, by the user's choice for it or else by default. */
    fun isOn(conversation: String, kind: Kind, choices: Map<String, Boolean>): Boolean = choices[conversation] ?: onByDefault(kind)

    fun shouldNotify(conversationOn: Boolean, body: String, selfName: String?, openOnScreen: Boolean, backlog: Boolean): Boolean =
        !openOnScreen && !backlog && (conversationOn || Mentions.names(body, selfName))

    /** Notification choices per conversation, kept in settings as lines of `conversation=1` or `=0`. */
    fun parseChoices(saved: String?): Map<String, Boolean> = saved.orEmpty().lines().mapNotNull { line ->
        val at = line.lastIndexOf('=')
        if (at <= 0) return@mapNotNull null
        when (line.substring(at + 1)) {
            "1" -> line.substring(0, at) to true
            "0" -> line.substring(0, at) to false
            else -> null
        }
    }.toMap()

    fun formatChoices(choices: Map<String, Boolean>): String =
        choices.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${if (it.value) 1 else 0}" }

    /** Sets [conversation] to [on]; a choice equal to the default is dropped, so a later default change applies. */
    fun choose(choices: Map<String, Boolean>, conversation: String, kind: Kind, on: Boolean): Map<String, Boolean> =
        if (on == onByDefault(kind)) choices - conversation else choices + (conversation to on)
}

/**
 * When to buzz, so a busy conversation does not buzz for every message: at most once per
 * conversation within [windowMs], unless it has been opened since. A check-in brings its messages
 * together and buzzes once for the lot; Live, whose messages come one at a time, buzzes for a
 * conversation's first new message and then updates it quietly.
 */
class AlertGate(private val windowMs: Long = 5 * 60_000L) {
    private val lastAlert = HashMap<String, Long>()

    /** Whether a new message in [conversation] at [now] should buzz; records it when it should. */
    @Synchronized
    fun shouldAlert(conversation: String, now: Long): Boolean {
        val last = lastAlert[conversation]
        if (last != null && now - last in 0 until windowMs) return false
        lastAlert[conversation] = now
        return true
    }

    /** The conversation was opened: the next new message buzzes again. */
    @Synchronized
    fun opened(conversation: String) {
        lastAlert.remove(conversation)
    }
}
