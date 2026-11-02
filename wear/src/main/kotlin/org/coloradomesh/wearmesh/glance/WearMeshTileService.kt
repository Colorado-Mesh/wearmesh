package org.coloradomesh.wearmesh.glance

import android.text.format.DateFormat
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import org.coloradomesh.wearmesh.Mode
import org.coloradomesh.wearmesh.Prefs
import org.coloradomesh.wearmesh.SpikeLog
import org.coloradomesh.wearmesh.data.Storage
import org.coloradomesh.wearmesh.glance.TileParts.ACCENT
import org.coloradomesh.wearmesh.glance.TileParts.MUTED
import org.coloradomesh.wearmesh.glance.TileParts.SURFACE
import org.coloradomesh.wearmesh.glance.TileParts.TEXT
import java.util.Date

/**
 * The WearMesh Messages tile (M4; one task per tile, decided 2 November 2026):
 * - at the top, the unread count in conversations that notify, and under the messages how
 *   current the tile is: "Check-in · synced 2:14 PM", or "Live: Connected";
 * - the last messages heard, in any conversation, read or not (two, or three on a watch at least
 *   [Glance.LARGE_SCREEN_DP] wide), each opening its conversation; unread ones bright with a dot,
 *   read ones dimmed;
 * - one action along the bottom: Reply, to the newest unread message that counts, or Check now
 *   when nothing is unread. Tapping a message opens the app, so there is no Open button.
 * Times are clock times, so the tile never needs redrawing on a timer. It updates only when the
 * app asks ([Glances.refresh]) and never wakes the radio. The radio's state has its own tile
 * ([WearMeshStatusTileService]).
 */
class WearMeshTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            SpikeLog.init(this)
            val device = requestParams.deviceConfiguration
            // The store is read on its own thread; the tile is built there too, off the main thread.
            Storage.later {
                val glance = try {
                    Glances.read(applicationContext)
                } catch (e: Exception) {
                    SpikeLog.log("Tile: could not read the store (${e.message})")
                    Glance(0, 0, null)
                }
                try {
                    completer.set(tile(glance, device))
                } catch (e: Exception) {
                    completer.setException(e)
                }
            }
            "WearMesh Tile"
        }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
            "WearMesh Tile resources"
        }

    private fun tile(glance: Glance, device: DeviceParameters): TileBuilders.Tile {
        val radio = Prefs.of(this).getString(Prefs.RADIO_ADDRESS, null)
        val heading = if (glance.unread > 0) "${Glance.countText(glance.unread)} unread" else "No new messages"
        val shown = if (device.screenWidthDp >= Glance.LARGE_SCREEN_DP) Glance.TILE_MESSAGES_LARGE else Glance.TILE_MESSAGES
        val content = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        if (radio != null) {
            glance.heard.take(shown).forEachIndexed { index, message ->
                if (index > 0) content.addContent(TileParts.spacer(4f))
                content.addContent(messageRow(radio, message))
            }
            if (glance.heard.isEmpty()) content.addContent(TileParts.text(this, "No messages heard yet", Typography.TYPOGRAPHY_CAPTION2, MUTED))
        } else {
            content.addContent(TileParts.text(this, "Choose a radio in WearMesh", Typography.TYPOGRAPHY_CAPTION2, MUTED, 2))
        }

        val reply = glance.replyTo
        val action = if (radio != null && reply != null) {
            TileParts.action(this, "reply", "Reply", TileParts.open(this, radio, reply.conversation, reply.title, quick = true), device)
        } else {
            TileParts.action(this, "check", "Check now", TileParts.checkNow(this), device)
        }
        val layout = PrimaryLayout.Builder(device)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(TileParts.text(this, heading, Typography.TYPOGRAPHY_CAPTION1, ACCENT))
            .setContent(content.build())
            .setSecondaryLabelTextContent(TileParts.text(this, status(glance), Typography.TYPOGRAPHY_CAPTION2, MUTED))
            .setPrimaryChipContent(action)
            .build()
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    /** How current the tile is: the mode, and when the radio was last reached. */
    private fun status(glance: Glance): String = when {
        glance.liveLink != null -> "Live: ${glance.liveLink}"
        Mode.hasQuit(this) -> "Quit"
        glance.lastSyncAt <= 0 -> "Not synced yet"
        else -> {
            val time = DateFormat.getTimeFormat(this).format(Date(glance.lastSyncAt))
            if (Mode.get(this) == Mode.Kind.CHECK_IN) "Check-in · synced $time" else "Synced $time"
        }
    }

    /**
     * One message: who from on the first line, the text on the second; opens its conversation.
     * An unread one is bright, with a dot before the sender; a read one is dimmed.
     */
    private fun messageRow(radio: String, message: GlanceMessage): LayoutElementBuilders.LayoutElement {
        val from = if (message.unread) "● ${message.from}" else message.from
        val lines = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .addContent(TileParts.text(this, from, Typography.TYPOGRAPHY_CAPTION1, if (message.unread) ACCENT else MUTED))
            .addContent(TileParts.text(this, message.body, Typography.TYPOGRAPHY_CAPTION2, if (message.unread) TEXT else MUTED))
            .build()
        val open = TileParts.clickable("open:${message.conversation}", TileParts.open(this, radio, message.conversation, message.title, quick = false))
        return LayoutElementBuilders.Box.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(open)
                    .setBackground(
                        ModifiersBuilders.Background.Builder()
                            .setColor(argb(SURFACE))
                            .setCorner(ModifiersBuilders.Corner.Builder().setRadius(dp(14f)).build())
                            .build(),
                    )
                    .setPadding(
                        ModifiersBuilders.Padding.Builder()
                            .setStart(dp(12f)).setEnd(dp(12f)).setTop(dp(4f)).setBottom(dp(4f))
                            .build(),
                    )
                    .setSemantics(
                        ModifiersBuilders.Semantics.Builder()
                            .setContentDescription("${if (message.unread) "Unread, " else ""}${message.from}: ${message.body}")
                            .build(),
                    )
                    .build(),
            )
            .addContent(lines)
            .build()
    }

    companion object {
        /** With no conversation: sync on opening even if one ended in the last two minutes. */
        const val EXTRA_CHECK_NOW = "org.coloradomesh.wearmesh.CHECK_NOW"

        // Raised from 1: the tile no longer has image resources.
        private const val RESOURCES_VERSION = "2"
    }
}
