package org.coloradomesh.wearmesh.glance

import android.content.Context
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.material.CompactChip
import androidx.wear.protolayout.material.Text
import org.coloradomesh.wearmesh.MainActivity
import org.coloradomesh.wearmesh.notify.Notifier

/**
 * What the two WearMesh Tiles share (decided 2 November 2026, after Google's tile guidance: one
 * task per tile, glanceable content, one action along the bottom): text, the action chip, and the
 * screens they open.
 */
internal object TileParts {
    const val ACCENT = 0xFFAECBFA.toInt()
    const val TEXT = 0xFFFFFFFF.toInt()
    const val MUTED = 0xFFBDC1C6.toInt()
    const val SURFACE = 0xFF303134.toInt()
    const val WARNING = 0xFFF6AEA9.toInt()

    fun text(context: Context, value: String, typography: Int, color: Int, maxLines: Int = 1): LayoutElementBuilders.LayoutElement =
        Text.Builder(context, value)
            .setTypography(typography)
            .setColor(argb(color))
            .setMaxLines(maxLines)
            .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE)
            .build()

    fun spacer(heightDp: Float): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder().setHeight(dp(heightDp)).build()

    fun clickable(id: String, activity: ActionBuilders.AndroidActivity): ModifiersBuilders.Clickable =
        ModifiersBuilders.Clickable.Builder()
            .setId(id)
            .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(activity).build())
            .build()

    /** The tile's one action, along its bottom edge. */
    fun action(context: Context, id: String, label: String, activity: ActionBuilders.AndroidActivity, device: DeviceParameters) =
        CompactChip.Builder(context, label, clickable(id, activity), device).build()

    fun home(context: Context): ActionBuilders.AndroidActivity.Builder = ActionBuilders.AndroidActivity.Builder()
        .setPackageName(context.packageName)
        .setClassName(MainActivity::class.java.name)

    /** WearMesh, checking with the radio at once (decided for M4). */
    fun checkNow(context: Context): ActionBuilders.AndroidActivity = home(context)
        .addKeyToExtraMapping(WearMeshTileService.EXTRA_CHECK_NOW, ActionBuilders.AndroidBooleanExtra.Builder().setValue(true).build())
        .build()

    /** A conversation, or its quick replies, with the same extras a notification's tap carries. */
    fun open(context: Context, radio: String, conversation: String, title: String, quick: Boolean): ActionBuilders.AndroidActivity =
        home(context)
            .addKeyToExtraMapping(Notifier.EXTRA_RADIO, ActionBuilders.AndroidStringExtra.Builder().setValue(radio).build())
            .addKeyToExtraMapping(Notifier.EXTRA_CONVERSATION, ActionBuilders.AndroidStringExtra.Builder().setValue(conversation).build())
            .addKeyToExtraMapping(Notifier.EXTRA_TITLE, ActionBuilders.AndroidStringExtra.Builder().setValue(title).build())
            .addKeyToExtraMapping(Notifier.EXTRA_QUICK_REPLY, ActionBuilders.AndroidBooleanExtra.Builder().setValue(quick).build())
            .build()
}
