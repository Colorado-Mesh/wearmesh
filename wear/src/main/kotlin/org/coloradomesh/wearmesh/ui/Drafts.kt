package org.coloradomesh.wearmesh.ui

import org.coloradomesh.meshcore.protocol.CompanionFrames

/**
 * The text a reply sends, and whether it fits (M3 step 2). A reply to one person on a channel
 * starts with `@[their name]`, as the MeshCore apps write it (decided for M3). MeshCore limits a
 * message to 160 bytes of UTF-8; on a channel the radio puts its node name and ": " in front,
 * inside the same limit.
 */
object Drafts {
    /** What is sent for [text] in reply to [to], if anyone. */
    fun body(text: String, to: String?): String {
        val trimmed = text.trim()
        return if (to.isNullOrBlank()) trimmed else "@[$to] $trimmed"
    }

    /**
     * The most bytes a message in [conversation] can take. Before the radio's name is known (no
     * sync yet), a channel allows for the longest name a node can have.
     */
    fun limit(conversation: String, selfName: String?): Int =
        if (conversation.startsWith("d:")) {
            CompanionFrames.MAX_DIRECT_TEXT_BYTES
        } else {
            val name = selfName?.takeIf { it.isNotEmpty() }?.toByteArray(Charsets.UTF_8)?.size ?: MAX_NAME_BYTES
            CompanionFrames.MAX_CHANNEL_TEXT_BYTES - name - 2
        }

    /** The longest node name MeshCore keeps. */
    private const val MAX_NAME_BYTES = 32

    fun bytes(body: String): Int = body.toByteArray(Charsets.UTF_8).size

    /** Why [body] cannot be sent in [conversation], or null when it can. */
    fun problem(conversation: String, body: String, selfName: String?): String? {
        val over = bytes(body) - limit(conversation, selfName)
        return when {
            body.isBlank() -> "Nothing to send"
            over > 0 -> "Too long by $over bytes"
            else -> null
        }
    }

    /** For the preview, such as "92 of 148 bytes". */
    fun size(conversation: String, body: String, selfName: String?): String = "${bytes(body)} of ${limit(conversation, selfName)} bytes"
}
