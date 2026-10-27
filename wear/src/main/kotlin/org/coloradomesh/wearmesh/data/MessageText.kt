package org.coloradomesh.wearmesh.data

/**
 * Splits a channel message into sender and text.
 *
 * MeshCore sends channel text as `Sender name: message`, with the sender's node name in front,
 * since a channel packet carries no other sign of who sent it. A node name is at most 31 bytes,
 * so a longer prefix, or one with a line break, is taken as part of the message instead, and the
 * sender is left empty.
 */
object MessageText {
    data class Split(
        /** The sender's node name, or empty when the text does not start with one. */
        val sender: String,
        val body: String,
    )

    /** MeshCore keeps node names to 32 bytes, the last a terminating zero. */
    const val MAX_NAME_BYTES = 31

    private const val SEPARATOR = ": "

    fun split(text: String): Split {
        val at = text.indexOf(SEPARATOR)
        if (at <= 0) return Split("", text)
        val name = text.substring(0, at)
        if (name.isBlank() || '\n' in name || name.toByteArray(Charsets.UTF_8).size > MAX_NAME_BYTES) {
            return Split("", text)
        }
        return Split(name, text.substring(at + SEPARATOR.length))
    }
}
