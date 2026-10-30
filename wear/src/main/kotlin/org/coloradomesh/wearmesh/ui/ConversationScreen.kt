package org.coloradomesh.wearmesh.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import org.coloradomesh.wearmesh.data.MessageRow

/**
 * One conversation, oldest message at the top and newest at the bottom, opened at the newest.
 * The crown scrolls. [onSeen] runs when it opens and again as each new message arrives, to mark
 * them read; keyed on the newest message, since a full conversation stays at 100 messages.
 * Reply comes after the newest message; tapping a message replies to its sender (M3 step 2).
 */
@Composable
fun ConversationScreen(
    title: String,
    messages: List<MessageRow>,
    selfName: String?,
    now: Long,
    notifyOn: Boolean,
    onSeen: () -> Unit,
    onToggleNotify: () -> Unit,
    /** For a direct conversation whose contact is not a favourite on the radio (step 4). */
    favouriteWarning: String?,
    onReply: () -> Unit,
    /** A tapped message: to reply to its sender, or retry one of the watch's that failed. */
    onOpenMessage: (MessageRow) -> Unit,
) {
    val state = rememberScalingLazyListState()
    var placed by remember { mutableStateOf(false) }
    LaunchedEffect(messages.lastOrNull()?.id) {
        onSeen()
        // The first time messages are there, start at the newest (item 0 is the title).
        if (!placed && messages.isNotEmpty()) {
            state.scrollToItem(messages.size)
            placed = true
        }
    }
    ScreenList(state) {
        item { ListHeader { Text(title, maxLines = 1) } }
        if (messages.isEmpty()) item { Note("No messages yet") }
        items(messages, key = { it.id }) { message -> MessageItem(message, selfName, now) { onOpenMessage(message) } }
        item { WideChip("Reply") { onReply() } }
        favouriteWarning?.let { item { Note(it) } }
        item {
            WideChip(
                label = if (notifyOn) "Notifications: on" else "Notifications: off",
                detail = "Tap to turn ${if (notifyOn) "off" else "on"}",
                secondary = true,
            ) { onToggleNotify() }
        }
    }
}

@Composable
private fun MessageItem(message: MessageRow, selfName: String?, now: Long, onClick: () -> Unit) {
    val accent = MaterialTheme.colors.primary
    Column(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 4.dp)) {
        val sender = if (message.outgoing) "You" else message.sender
        if (sender.isNotEmpty()) {
            Text(sender, style = MaterialTheme.typography.caption1, fontWeight = FontWeight.Bold, maxLines = 1)
        }
        Text(withMentions(message.body, selfName, accent), style = MaterialTheme.typography.body2)
        val time = TimeAgo.format(now, message.receivedAt)
        Text(SentStatus.label(message.status)?.let { "$time · $it" } ?: time, style = MaterialTheme.typography.caption3)
    }
}

/** Mentions as `@name`; those naming this radio bold and in the accent colour. */
private fun withMentions(body: String, selfName: String?, accent: androidx.compose.ui.graphics.Color): AnnotatedString =
    buildAnnotatedString {
        for (part in Mentions.parse(body)) {
            when (part) {
                is Mentions.Part.Plain -> append(part.text)
                is Mentions.Part.Mention -> if (!selfName.isNullOrBlank() && part.name == selfName) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = accent)) { append("@${part.name}") }
                } else {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append("@${part.name}") }
                }
            }
        }
    }
