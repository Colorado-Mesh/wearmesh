package org.coloradomesh.wearmesh.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.PositionIndicator
import androidx.wear.compose.material.Scaffold
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.TimeText

/** A screen that is one scrolling list, with the time at the top and a scroll indicator. */
@Composable
fun ScreenList(
    state: ScalingLazyListState = rememberScalingLazyListState(),
    content: ScalingLazyListScope.() -> Unit,
) {
    Scaffold(
        timeText = { TimeText() },
        positionIndicator = { PositionIndicator(scalingLazyListState = state) },
    ) {
        ScalingLazyColumn(
            state = state,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 8.dp, top = 32.dp, end = 8.dp, bottom = 48.dp),
            content = content,
        )
    }
}

/** A full-width chip: primary for the main action or something new, secondary otherwise. */
@Composable
fun WideChip(
    label: String,
    detail: String? = null,
    secondary: Boolean = false,
    enabled: Boolean = true,
    detailLines: Int = 1,
    onClick: () -> Unit,
) {
    Chip(
        onClick = onClick,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        secondaryLabel = if (detail != null) {
            { Text(detail, maxLines = detailLines, overflow = TextOverflow.Ellipsis) }
        } else {
            null
        },
        colors = if (secondary) ChipDefaults.secondaryChipColors() else ChipDefaults.primaryChipColors(),
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
    )
}

/** A short centred line of explanation. */
@Composable
fun Note(text: String) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.caption2,
        modifier = Modifier.fillMaxWidth(),
    )
}
