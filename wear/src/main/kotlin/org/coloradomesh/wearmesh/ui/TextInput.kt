package org.coloradomesh.wearmesh.ui

import android.app.Activity
import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.wear.input.RemoteInputIntentHelper

/** Starts one way of getting text from the user; [onText] gets it, unless the user backs out. */
fun interface TextSource {
    fun start()
}

private const val INPUT_KEY = "text"

/**
 * Dictation, through the watch's speech recognizer. M0 found that offline dictation needs a
 * confirmation tap before it returns the text; the recognizer's own screen asks for it. [onMissing]
 * runs when the watch has no recognizer.
 */
@Composable
fun rememberDictation(prompt: String, onText: (String) -> Unit, onMissing: () -> Unit): TextSource {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (result.resultCode == Activity.RESULT_OK && !text.isNullOrBlank()) onText(text)
    }
    return TextSource {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            onMissing()
        }
    }
}

/**
 * The watch's own text input screen: keyboard, with voice and emoji as the watch offers them.
 * It starts empty; Wear OS gives no way to hand it text to edit.
 */
@Composable
fun rememberKeyboard(label: String, onText: (String) -> Unit, onMissing: () -> Unit): TextSource {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val data = result.data ?: return@rememberLauncherForActivityResult
        val text = RemoteInput.getResultsFromIntent(data)?.getCharSequence(INPUT_KEY)?.toString()
        if (!text.isNullOrBlank()) onText(text)
    }
    return TextSource {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(RemoteInput.Builder(INPUT_KEY).setLabel(label).build()))
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            onMissing()
        }
    }
}
