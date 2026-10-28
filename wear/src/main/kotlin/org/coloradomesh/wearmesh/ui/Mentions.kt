package org.coloradomesh.wearmesh.ui

/**
 * MeshCore writes a mention as `@[Node name]`. A message is split into plain text and mentions,
 * so a mention can be shown as `@Node name` and one naming this radio can stand out.
 */
object Mentions {
    sealed interface Part {
        data class Plain(val text: String) : Part

        data class Mention(val name: String) : Part
    }

    private val pattern = Regex("""@\[([^\[\]\n]+)]""")

    fun parse(text: String): List<Part> {
        val parts = ArrayList<Part>()
        var at = 0
        for (match in pattern.findAll(text)) {
            if (match.range.first > at) parts.add(Part.Plain(text.substring(at, match.range.first)))
            parts.add(Part.Mention(match.groupValues[1]))
            at = match.range.last + 1
        }
        if (at < text.length) parts.add(Part.Plain(text.substring(at)))
        return parts
    }

    /** Whether [text] mentions [name] (this radio's own name); false for a blank name. */
    fun names(text: String, name: String?): Boolean =
        !name.isNullOrBlank() && parse(text).any { it is Part.Mention && it.name == name }
}
