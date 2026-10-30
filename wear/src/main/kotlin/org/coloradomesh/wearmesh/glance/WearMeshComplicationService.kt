package org.coloradomesh.wearmesh.glance

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.CountUpTimeReference
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.data.SmallImage
import androidx.wear.watchface.complications.data.SmallImageComplicationData
import androidx.wear.watchface.complications.data.SmallImageType
import androidx.wear.watchface.complications.data.TimeDifferenceComplicationText
import androidx.wear.watchface.complications.data.TimeDifferenceStyle
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.coloradomesh.wearmesh.MainActivity
import org.coloradomesh.wearmesh.R
import org.coloradomesh.wearmesh.SpikeLog
import org.coloradomesh.wearmesh.data.Storage
import java.time.Instant

/**
 * The WearMesh complication (M4 step 1), as decided for M4:
 * - short text: the unread count with the app's icon, or the icon alone when nothing is unread;
 * - long text: "Synced 4 min ago" (Wear OS keeps the time current), or "Live: Connected", with
 *   the unread count as its title;
 * - an icon alone, for faces with only an image slot.
 * Tapping it opens WearMesh, which syncs on opening. It updates only when the app asks
 * ([Glances.refresh]), after each sync and whenever messages are read.
 */
class WearMeshComplicationService : SuspendingComplicationDataSourceService() {
    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        SpikeLog.init(this)
        val glance = try {
            withContext(Dispatchers.IO) { Storage.call { Glances.read(applicationContext) } }
        } catch (e: Exception) {
            SpikeLog.log("Glance: could not read the store (${e.message})")
            Glance(0, 0, null)
        }
        return data(request.complicationType, glance)
    }

    override fun getPreviewData(type: ComplicationType): ComplicationData? = data(type, Glance(3, System.currentTimeMillis() - 4 * 60_000, null))

    private fun data(type: ComplicationType, glance: Glance): ComplicationData? {
        val icon = MonochromaticImage.Builder(Icon.createWithResource(this, R.drawable.ic_notification)).build()
        val description = PlainComplicationText.Builder(
            if (glance.unread > 0) "WearMesh, ${glance.unread} unread" else "WearMesh, nothing unread",
        ).build()
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return when (type) {
            ComplicationType.SHORT_TEXT -> {
                val count = Glance.countText(glance.unread)
                ShortTextComplicationData.Builder(
                    if (count.isEmpty()) ComplicationText.EMPTY else PlainComplicationText.Builder(count).build(),
                    description,
                ).setMonochromaticImage(icon).setTapAction(open).build()
            }
            ComplicationType.LONG_TEXT -> {
                val text = when {
                    glance.liveLink != null -> PlainComplicationText.Builder("Live: ${glance.liveLink}").build()
                    glance.lastSyncAt > 0 -> TimeDifferenceComplicationText.Builder(
                        TimeDifferenceStyle.SHORT_SINGLE_UNIT,
                        CountUpTimeReference(Instant.ofEpochMilli(glance.lastSyncAt)),
                    ).setText("Synced ^1 ago").build()
                    else -> PlainComplicationText.Builder("Not synced yet").build()
                }
                val title = if (glance.unread > 0) "${Glance.countText(glance.unread)} unread" else "WearMesh"
                LongTextComplicationData.Builder(text, description)
                    .setTitle(PlainComplicationText.Builder(title).build())
                    .setMonochromaticImage(icon)
                    .setTapAction(open)
                    .build()
            }
            ComplicationType.MONOCHROMATIC_IMAGE -> MonochromaticImageComplicationData.Builder(icon, description).setTapAction(open).build()
            ComplicationType.SMALL_IMAGE -> SmallImageComplicationData.Builder(
                SmallImage.Builder(Icon.createWithResource(this, R.drawable.ic_notification), SmallImageType.ICON).build(),
                description,
            ).setTapAction(open).build()
            else -> null
        }
    }
}
