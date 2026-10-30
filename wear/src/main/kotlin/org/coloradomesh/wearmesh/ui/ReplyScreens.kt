package org.coloradomesh.wearmesh.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import org.coloradomesh.wearmesh.data.MessageRow

/**
 * Replying in a conversation (M3 step 2, decided for M3): it opens straight into dictation, with
 * the keyboard one tap away. Dictated or typed text is shown first with Send and Cancel; a quick
 * reply sends at once. A reply to one person on a channel ([to]) starts with `@[their name]`.
 * [onSend] gets the text to send; the caller sends it and leaves the screen.
 */
@Composable
fun ReplyScreen(
    conversation: String,
    title: String,
    to: String?,
    selfName: String?,
    quickReplies: List<String>,
    onSend: (String) -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf<String?>(null) }
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    val dictation = rememberDictation(
        prompt = if (to != null) "Reply to $to" else title,
        onText = { draft = it },
        onMissing = { notice = "This watch has no dictation; use the keyboard" },
    )
    val keyboard = rememberKeyboard(
        label = if (to != null) "Reply to $to" else title,
        onText = { draft = it },
        onMissing = { notice = "This watch has no keyboard screen" },
    )
    // Dictation first, once, when the screen opens; backing out of it leaves the choices here.
    var opened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (!opened) {
            opened = true
            dictation.start()
        }
    }

    val text = draft
    if (text == null) {
        ScreenList {
            item { ListHeader { Text(if (to != null) "Reply to $to" else title, maxLines = 2) } }
            item { WideChip("Speak") { dictation.start() } }
            item { WideChip("Type", secondary = true) { keyboard.start() } }
            notice?.let { item { Note(it) } }
            if (quickReplies.isNotEmpty()) item { ListHeader { Text("Quick replies") } }
            items(quickReplies) { reply ->
                val body = Drafts.body(reply, to)
                val problem = Drafts.problem(conversation, body, selfName)
                WideChip(reply, detail = problem, secondary = true, enabled = problem == null) { onSend(body) }
            }
        }
    } else {
        val body = Drafts.body(text, to)
        val problem = Drafts.problem(conversation, body, selfName)
        ScreenList {
            item { ListHeader { Text(title, maxLines = 1) } }
            item {
                Text(body, style = MaterialTheme.typography.body2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            }
            item { Note(problem ?: Drafts.size(conversation, body, selfName)) }
            item { WideChip("Send", enabled = problem == null) { onSend(body) } }
            item { WideChip("Speak again", secondary = true) { dictation.start() } }
            item { WideChip("Type instead", secondary = true) { keyboard.start() } }
            item { WideChip("Cancel", secondary = true) { draft = null } }
        }
    }
}

/**
 * One message, tapped in its conversation: reply to its sender on a channel, reply in a direct
 * conversation, or try a failed message of the watch's again.
 */
@Composable
fun MessageScreen(
    message: MessageRow,
    now: Long,
    onReply: (to: String?) -> Unit,
    onRetry: () -> Unit,
) {
    ScreenList {
        val sender = if (message.outgoing) "You" else message.sender
        if (sender.isNotEmpty()) item { ListHeader { Text(sender, maxLines = 1, fontWeight = FontWeight.Bold) } }
        item {
            Text(message.body, style = MaterialTheme.typography.body2, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
        item {
            val time = TimeAgo.format(now, message.receivedAt)
            Note(SentStatus.label(message.status)?.let { "$time · $it" } ?: time)
        }
        when {
            message.outgoing && SentStatus.canRetry(message.status) -> item { WideChip("Retry") { onRetry() } }
            message.outgoing -> Unit
            message.conversation.startsWith("c:") && message.sender.isNotEmpty() ->
                item { WideChip("Reply to ${message.sender}") { onReply(message.sender) } }
            else -> item { WideChip("Reply") { onReply(null) } }
        }
    }
}

/** Settings › Quick replies: each one to change or remove, and Add. */
@Composable
fun QuickRepliesScreen(replies: List<String>, onOpen: (Int) -> Unit, onAdd: (String) -> Unit, onReset: () -> Unit) {
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    val keyboard = rememberKeyboard("New quick reply", onText = onAdd, onMissing = { notice = "This watch has no keyboard screen" })
    ScreenList {
        item { ListHeader { Text("Quick replies") } }
        item { Note("Each sends at once, without a preview.") }
        items(replies.size) { index -> WideChip(replies[index], secondary = true) { onOpen(index) } }
        val full = replies.size >= QuickReplies.MAX
        item { WideChip("Add", detail = if (full) "${QuickReplies.MAX} is the most" else null, enabled = !full) { keyboard.start() } }
        notice?.let { item { Note(it) } }
        if (replies != QuickReplies.DEFAULTS) item { WideChip("Back to the starting set", secondary = true) { onReset() } }
    }
}

/** One quick reply: change its words, or remove it. */
@Composable
fun QuickReplyScreen(reply: String, onChange: (String) -> Unit, onRemove: () -> Unit) {
    var notice by rememberSaveable { mutableStateOf<String?>(null) }
    val keyboard = rememberKeyboard("Change \"$reply\"", onText = onChange, onMissing = { notice = "This watch has no keyboard screen" })
    ScreenList {
        item { ListHeader { Text(reply, maxLines = 2) } }
        item { WideChip("Change") { keyboard.start() } }
        item { WideChip("Remove", secondary = true) { onRemove() } }
        notice?.let { item { Note(it) } }
    }
}
