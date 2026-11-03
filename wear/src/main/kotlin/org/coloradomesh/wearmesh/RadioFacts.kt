package org.coloradomesh.wearmesh

import android.content.Context
import org.coloradomesh.meshcore.protocol.DeviceInfo

/** The radio's model and firmware, kept from its device answer in Live and Check-in, for Settings › About. */
object RadioFacts {
    /** The firmware as one line: its version and build, or the protocol version from older firmware. */
    fun firmware(info: DeviceInfo): String =
        listOf(info.version, info.build).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { "protocol ${info.firmwareVersion}" }

    /** Saves them for [radio], only while it is the radio in use. */
    fun save(context: Context, radio: String, info: DeviceInfo) {
        Prefs.writeForRadio(context, radio) { _, edit ->
            edit.putString(Prefs.RADIO_MODEL, info.model.ifEmpty { null }).putString(Prefs.RADIO_FIRMWARE, firmware(info))
        }
    }
}
