package org.coloradomesh.wearmesh.ui

import java.util.Locale

/**
 * How a received message reached the radio, for the message screen (decided 1 November 2026):
 * "SNR 6.5 dB · 3 hops". The SNR is of the last hop the radio heard it on, from the sender or
 * from the repeater that passed it on. A flooded message gives its hops ("no repeaters" when the
 * radio heard the sender itself); one that came along a known route has no count, and none is
 * shown (decided). Older firmware gives neither, and then there is no line.
 */
object Signal {
    fun describe(snr: Double?, hops: Int?): String? {
        val parts = listOfNotNull(
            snr?.let { "SNR ${String.format(Locale.ROOT, "%.1f", it)} dB" },
            hops?.let {
                when (it) {
                    0 -> "no repeaters"
                    1 -> "1 hop"
                    else -> "$it hops"
                }
            },
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }
}
