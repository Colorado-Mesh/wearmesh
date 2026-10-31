package org.coloradomesh.wearmesh.glance

import android.content.Context
import android.text.format.DateFormat
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Button
import androidx.wear.protolayout.material.ButtonDefaults
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.ListenableFuture
import org.coloradomesh.wearmesh.MainActivity
import org.coloradomesh.wearmesh.Mode
import org.coloradomesh.wearmesh.Prefs
import org.coloradomesh.wearmesh.R
import org.coloradomesh.wearmesh.SpikeLog
import org.coloradomesh.wearmesh.data.Storage
import org.coloradomesh.wearmesh.notify.Notifier
import java.util.Date

/**
 * The WearMesh Tile (M4 step 2), as decided for M4:
 * - the unread count at the top, counted as the complication counts it;
 * - the last two messages heard, in any conversation, read or not (decided 31 October 2026), sender
 *   and the start of the text, each opening its conversation; unread ones bright, read ones dimmed;
 * - Check now (opens WearMesh, which checks at once), Quick reply (the newest unread message's
 *   conversation, on its quick replies) and Open WearMesh;
 * - a status line with the mode: "Live: Connected", or when Check-in last synced.
 * The sync time is the clock time, not "4 min ago", so the Tile never needs redrawing on a timer.
 * Like the complication it updates only when the app asks ([Glances.refresh]) and never wakes the
 * radio.
 */
class WearMeshTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            SpikeLog.init(this)
            val device = requestParams.deviceConfiguration
            // The store is read on its own thread; the Tile is built there too, off the main thread.
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
            completer.set(
                ResourceBuilders.Resources.Builder()
                    .setVersion(RESOURCES_VERSION)
                    .addIdToImageMapping(ICON_CHECK, image(R.drawable.ic_tile_check))
                    .addIdToImageMapping(ICON_REPLY, image(R.drawable.ic_tile_reply))
                    .addIdToImageMapping(ICON_OPEN, image(R.drawable.ic_notification))
                    .build(),
            )
            "WearMesh Tile resources"
        }

    private fun image(id: Int) = ResourceBuilders.ImageResource.Builder()
        .setAndroidResourceByResId(ResourceBuilders.AndroidImageResourceByResId.Builder().setResourceId(id).build())
        .build()

    private fun tile(glance: Glance, device: DeviceParameters): TileBuilders.Tile {
        val radio = Prefs.of(this).getString(Prefs.RADIO_ADDRESS, null)
        val heading = if (glance.unread > 0) "${Glance.countText(glance.unread)} unread" else "Nothing unread"
        val content = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
        if (radio != null) {
            glance.heard.forEachIndexed { index, message ->
                if (index > 0) content.addContent(spacer(4f))
                content.addContent(messageRow(radio, message))
            }
            if (glance.heard.isEmpty()) content.addContent(text("No messages heard yet", Typography.TYPOGRAPHY_CAPTION2, MUTED, 1))
        } else {
            content.addContent(text("Choose a radio in WearMesh", Typography.TYPOGRAPHY_CAPTION2, MUTED, 2))
        }
        content.addContent(spacer(6f))
        content.addContent(buttons(radio, glance.replyTo))

        val layout = PrimaryLayout.Builder(device)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(text(heading, Typography.TYPOGRAPHY_CAPTION1, ACCENT, 1))
            .setContent(content.build())
            .setSecondaryLabelTextContent(text(status(glance), Typography.TYPOGRAPHY_CAPTION2, MUTED, 1))
            .build()
        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout))
            .build()
    }

    /** The status line: the mode, and how current the Tile is. */
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
        val from = if (message.unread) "\u25CF ${message.from}" else message.from
        val lines = LayoutElementBuilders.Column.Builder()
            .setWidth(expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_START)
            .addContent(text(from, Typography.TYPOGRAPHY_CAPTION1, if (message.unread) ACCENT else MUTED, 1))
            .addContent(text(message.body, Typography.TYPOGRAPHY_CAPTION2, if (message.unread) TEXT else MUTED, 1))
            .build()
        val open = clickable("open:${message.conversation}", open(this, radio, message.conversation, message.title, quick = false))
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

    /** Check now, Quick reply (to the newest unread message that counts, when there is one) and Open WearMesh. */
    private fun buttons(radio: String?, newest: GlanceMessage?): LayoutElementBuilders.LayoutElement {
        val row = LayoutElementBuilders.Row.Builder().setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
        val check = home(this).addKeyToExtraMapping(
            EXTRA_CHECK_NOW,
            ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build(),
        ).build()
        row.addContent(button("check", ICON_CHECK, "Check now", check, primary = true))
        if (radio != null && newest != null) {
            row.addContent(spacerWide(8f))
            row.addContent(
                button("quick", ICON_REPLY, "Quick reply to ${newest.title}", open(this, radio, newest.conversation, newest.title, quick = true)),
            )
        }
        row.addContent(spacerWide(8f))
        row.addContent(button("open", ICON_OPEN, "Open WearMesh", home(this).build()))
        return row.build()
    }

    private fun button(id: String, icon: String, description: String, activity: ActionBuilders.AndroidActivity, primary: Boolean = false) =
        Button.Builder(this, clickable(id, activity))
            .setIconContent(icon)
            .setContentDescription(description)
            .setSize(dp(BUTTON_DP))
            .setButtonColors(if (primary) ButtonDefaults.PRIMARY_COLORS else ButtonDefaults.SECONDARY_COLORS)
            .build()

    private fun text(value: String, typography: Int, color: Int, maxLines: Int) =
        Text.Builder(this, value)
            .setTypography(typography)
            .setColor(argb(color))
            .setMaxLines(maxLines)
            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
            .build()

    private fun spacer(heightDp: Float) = LayoutElementBuilders.Spacer.Builder().setHeight(dp(heightDp)).build()

    private fun spacerWide(widthDp: Float) = LayoutElementBuilders.Spacer.Builder().setWidth(dp(widthDp)).build()

    private fun clickable(id: String, activity: ActionBuilders.AndroidActivity) =
        ModifiersBuilders.Clickable.Builder()
            .setId(id)
            .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity).build())
            .build()

    companion object {
        /** With no conversation: sync on opening even if one ended in the last two minutes. */
        const val EXTRA_CHECK_NOW = "org.coloradomesh.wearmesh.CHECK_NOW"

        private const val RESOURCES_VERSION = "1"
        private const val ICON_CHECK = "check"
        private const val ICON_REPLY = "reply"
        private const val ICON_OPEN = "open"
        private const val BUTTON_DP = 40f

        private const val ACCENT = 0xFFAECBFA.toInt()
        private const val TEXT = 0xFFFFFFFF.toInt()
        private const val MUTED = 0xFFBDC1C6.toInt()
        private const val SURFACE = 0xFF303134.toInt()

        private fun home(context: Context) = ActionBuilders.AndroidActivity.Builder()
            .setPackageName(context.packageName)
            .setClassName(MainActivity::class.java.name)

        /** A conversation, or its quick replies, with the same extras a notification's tap carries. */
        private fun open(context: Context, radio: String, conversation: String, title: String, quick: Boolean) =
            home(context)
                .addKeyToExtraMapping(Notifier.EXTRA_RADIO, ActionBuilders.AndroidStringExtra.Builder().setValue(radio).build())
                .addKeyToExtraMapping(Notifier.EXTRA_CONVERSATION, ActionBuilders.AndroidStringExtra.Builder().setValue(conversation).build())
                .addKeyToExtraMapping(Notifier.EXTRA_TITLE, ActionBuilders.AndroidStringExtra.Builder().setValue(title).build())
                .addKeyToExtraMapping(Notifier.EXTRA_QUICK_REPLY, ActionBuilders.AndroidBooleanExtra.Builder().setValue(quick).build())
                .build()
    }
}
