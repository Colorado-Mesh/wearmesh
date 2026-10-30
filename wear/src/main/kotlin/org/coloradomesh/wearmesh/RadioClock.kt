package org.coloradomesh.wearmesh

/**
 * The radio's clock (decided for M3): each connection reads it, and the watch sets it from its
 * own when they differ by more than [TOLERANCE_SECONDS]. This is the only thing WearMesh changes
 * on the radio.
 */
object RadioClock {
    const val TOLERANCE_SECONDS = 60L

    /** A log line for a reading, or null when the clocks agree to within a few seconds. */
    fun describe(radioSeconds: Long, watchSeconds: Long, setting: Boolean): String? {
        val behind = watchSeconds - radioSeconds
        val off = if (behind >= 0) "$behind s behind the watch" else "${-behind} s ahead of the watch"
        return when {
            setting -> "Radio clock $off; setting it"
            behind in -CLOSE_SECONDS..CLOSE_SECONDS -> null
            else -> "Radio clock $off; within ${TOLERANCE_SECONDS} s, left as it is"
        }
    }

    private const val CLOSE_SECONDS = 5L
}
