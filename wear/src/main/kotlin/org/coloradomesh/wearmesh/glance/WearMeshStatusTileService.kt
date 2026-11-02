package org.coloradomesh.wearmesh.glance

import android.text.format.DateFormat
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import org.coloradomesh.wearmesh.SpikeLog
import org.coloradomesh.wearmesh.glance.TileParts.ACCENT
import org.coloradomesh.wearmesh.glance.TileParts.MUTED
import org.coloradomesh.wearmesh.glance.TileParts.TEXT
import org.coloradomesh.wearmesh.glance.TileParts.WARNING
import java.util.Date

/**
 * The WearMesh Status tile (decided 2 November 2026): the radio and the link, at a glance.
 * - at the top, the mode: "Check-in every 15 min", "Live" or "Quit";
 * - in the middle, large, when the watch last reached the radio ("2:14 PM") or Live's link state,
 *   then when the next check-in is due, the radio's battery and name, and a failed last try;
 * - one action along the bottom: Check now.
 * Clock times rather than "4 min ago", so it needs no timer. Updated with the Messages tile
 * ([Glances.refresh]), and by Wear OS when the battery reading shown expires ([Status.freshForMs]);
 * it reads saved settings only and never wakes the radio.
 */
class WearMeshStatusTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            SpikeLog.init(this)
            try {
                completer.set(tile(Glances.status(applicationContext), requestParams.deviceConfiguration))
            } catch (e: Exception) {
                SpikeLog.log("Status tile: could not build (${e.message})")
                completer.setException(e)
            }
            "WearMesh Status tile"
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
            "WearMesh Status tile resources"
        }

    private fun tile(status: Status, device: DeviceParameters): TileBuilders.Tile {
        val format = DateFormat.getTimeFormat(this)
        val now = System.currentTimeMillis()
        val lines = status.lines(now) { format.format(Date(it)) }
        val content = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .addContent(TileParts.text(this, lines.label, Typography.TYPOGRAPHY_CAPTION2, MUTED))
            .addContent(TileParts.text(this, lines.big, Typography.TYPOGRAPHY_TITLE1, TEXT))
        if (lines.details.isNotEmpty() || lines.warning != null) content.addContent(TileParts.spacer(4f))
        for (line in lines.details) content.addContent(TileParts.text(this, line, Typography.TYPOGRAPHY_CAPTION1, TEXT))
        lines.warning?.let { content.addContent(TileParts.text(this, it, Typography.TYPOGRAPHY_CAPTION1, WARNING)) }

        val layout = PrimaryLayout.Builder(device)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(TileParts.text(this, lines.heading, Typography.TYPOGRAPHY_CAPTION1, ACCENT))
            .setContent(content.build())
            .setPrimaryChipContent(TileParts.action(this, "check", "Check now", TileParts.checkNow(this), device))
            .build()
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            // Drawn again when the battery reading it shows expires, so it does not linger.
            .setFreshnessIntervalMillis(status.freshForMs(now))
            .build()
    }

    private companion object {
        const val RESOURCES_VERSION = "1"
    }
}
