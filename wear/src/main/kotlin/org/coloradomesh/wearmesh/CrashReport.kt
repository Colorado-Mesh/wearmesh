package org.coloradomesh.wearmesh

/**
 * What the log records of a crash (M5 step 2): the error and where it happened, enough to find
 * it in the code, but not every frame. Each cause follows, as Java prints them.
 */
object CrashReport {
    fun lines(threadName: String, error: Throwable): List<String> {
        val lines = mutableListOf("Crash on thread $threadName: $error")
        var cause: Throwable? = error
        var first = true
        val seen = HashSet<Throwable>()
        while (cause != null && seen.add(cause) && seen.size <= MAX_CAUSES) {
            if (!first) lines.add("Caused by: $cause")
            val frames = cause.stackTrace
            frames.take(FRAMES).forEach { lines.add("    at $it") }
            if (frames.size > FRAMES) lines.add("    … ${frames.size - FRAMES} more")
            first = false
            cause = cause.cause
        }
        return lines
    }

    private const val FRAMES = 15
    private const val MAX_CAUSES = 5
}
