package org.coloradomesh.wearmesh

import android.content.Context
import org.coloradomesh.meshcore.protocol.DeviceInfo

/** The radio's model and firmware, kept from its device answer in Live and Check-in, for Settings › About. */
object RadioFacts {
    /** The nonempty version and build joined as one line, or the protocol version if both are empty. */
    fun firmware(info: DeviceInfo): String =
        listOf(info.version, info.build).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { "protocol ${info.firmwareVersion}" }

    /** Saves the model and firmware if [radio] is still the address in use; an empty model is removed. */
    fun save(context: Context, radio: String, info: DeviceInfo) {
        Prefs.writeForRadio(context, radio) { _, edit ->
            edit.putString(Prefs.RADIO_MODEL, info.model.ifEmpty { null }).putString(Prefs.RADIO_FIRMWARE, firmware(info))
        }
    }
}
