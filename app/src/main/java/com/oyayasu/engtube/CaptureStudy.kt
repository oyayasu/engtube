package com.oyayasu.engtube

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.speech.tts.TextToSpeech
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.oyayasu.engtube.data.AudioSegmentPlayer
import com.oyayasu.engtube.data.Phrase
import com.oyayasu.engtube.data.Pronunciation
import com.oyayasu.engtube.data.SourceTools
import kotlinx.coroutines.launch
import java.util.Locale

@Composable
internal fun CaptureScreen(
    seed: String,
    editing: Phrase?,
    defaultAccent: String,
    onBack: () -> Unit,
    onSave: (Phrase) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val key = editing?.id ?: seed
    var url by remember(key) { mutableStateOf(editing?.videoUrl ?: seed) }
    var title by remember(key) { mutableStateOf(editing?.title ?: "") }
    var phrase by remember(key) { mutableStateOf(editing?.text ?: "") }
    var meaning by remember(key) { mutableStateOf(editing?.meaning ?: "") }
    var notes by remember(key) { mutableStateOf(editing?.notes ?: "") }
    var category by remember(key) { mutableStateOf(editing?.collection ?: "General") }
    var ipaUk by remember(key) { mutableStateOf(editing?.ipaUk ?: "") }
    var ipaUs by remember(key) { mutableStateOf(editing?.ipaUs ?: "") }
    var accent by remember(key) { mutableStateOf(defaultAccent) }
    var transcript by remember(key) { mutableStateOf("") }
    val source = remember(url) { SourceTools.youtube(url) }
    var from by remember(key) { mutableStateOf(SourceTools.clock(editing?.startMs ?: (SourceTools.youtube(seed)?.startMs ?: 0))) }
    var to by remember(key) { mutableStateOf(SourceTools.clock(editing?.endMs ?: ((SourceTools.youtube(seed)?.startMs ?: 0) + 3000))) }
    var audioUri by remember(key) { mutableStateOf(editing?.audioUri) }
    var audioFrom by remember(key) { mutableStateOf(SourceTools.clock(editing?.audioStartMs ?: 0)) }
    var audioTo by remember(key) { mutableStateOf(SourceTools.clock(editing?.audioEndMs ?: 3000)) }
    var lookingUp by remember { mutableStateOf(false) }
    var lookupMessage by remember { mutableStateOf("") }
    var webPlayer by remember(source?.id) { mutableStateOf<WebView?>(null) }
    var error by remember { mutableStateOf("") }

    val audioPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            audioUri = uri.toString()
        }
    }

    fun chooseCurrentTime(setEnd: Boolean) {
        val player = webPlayer ?: run {
            error = "Player isn't ready. Enter the time manually."; return
        }
        player.evaluateJavascript(
            "Math.floor(window.engtubePlayer && window.engtubePlayer.getCurrentTime() || 0)"
        ) { result ->
            val seconds = result.removeSurrounding("\"").toLongOrNull()
            if (seconds == null) error = "Could not read player time; enter it manually."
            else if (setEnd) to = SourceTools.clock(seconds * 1000)
            else from = SourceTools.clock(seconds * 1000)
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text(if (editing == null) "Phrase Capture" else "Edit phrase",
                style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        Surface(color = Lavender, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Source video", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    url, { url = it }, label = { Text("YouTube URL") },
                    modifier = Modifier.fillMaxWidth(), maxLines = 2
                )
                OutlinedTextField(
                    title, { title = it }, label = { Text("Video title (optional)") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true
                )
                if (source != null) {
                    Text("Video recognised · " + source.id, style = MaterialTheme.typography.bodySmall,
                        color = DeepGreen)
                    EmbeddedPlayer(source.id, onCreated = { webPlayer = it })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { openAt(context, source.id, SourceTools.parseClock(from) ?: 0) }) {
                            Icon(Icons.Default.OpenInNew, null)
                            Spacer(Modifier.width(4.dp))
                            Text("YouTube")
                        }
                    }
                } else if (url.isNotBlank()) {
                    Text("Paste a valid youtube.com or youtu.be link.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        }

        Surface(color = Cream, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Select a phrase", fontWeight = FontWeight.Bold)
                Text("Paste SRT/WebVTT or plain transcript text. Only timed captions include synchronised intervals.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(transcript, { transcript = it },
                    label = { Text("Transcript (optional)") },
                    minLines = 2, maxLines = 5, modifier = Modifier.fillMaxWidth())
                val segments = remember(transcript) { SourceTools.transcript(transcript) }
                if (segments.isNotEmpty()) {
                    Text("Tap a line to choose it (" + segments.size + ")",
                        style = MaterialTheme.typography.labelMedium)
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        items(segments) { segment ->
                            Surface(
                                onClick = {
                                    phrase = segment.text
                                    if (segment.startMs != null && segment.endMs != null) {
                                        from = SourceTools.clock(segment.startMs)
                                        to = SourceTools.clock(segment.endMs)
                                    }
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (segment.text == phrase) Sage else Lavender
                            ) {
                                Column(Modifier.fillMaxWidth().padding(10.dp)) {
                                    Text(segment.text)
                                    Text(if (segment.startMs == null) "No timestamp — adjust manually" else
                                        SourceTools.clock(segment.startMs) + " – " +
                                            SourceTools.clock(segment.endMs ?: segment.startMs),
                                        style = MaterialTheme.typography.labelSmall)
                                }
                            }
                        }
                    }
                }
                OutlinedTextField(phrase, { phrase = it },
                    label = { Text("Phrase (editable)") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(from, { from = it }, modifier = Modifier.weight(1f),
                        label = { Text("Start · mm:ss") }, singleLine = true)
                    OutlinedTextField(to, { to = it }, modifier = Modifier.weight(1f),
                        label = { Text("End · mm:ss") }, singleLine = true)
                }
                if (source != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TextButton(onClick = { chooseCurrentTime(false) }) { Text("Set start to now") }
                        TextButton(onClick = { chooseCurrentTime(true) }) { Text("Set end to now") }
                    }
                }
            }
        }

        Surface(color = Lavender, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Pronunciation (dictionary reference)", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = accent == "UK", onClick = { accent = "UK" },
                        label = { Text("UK · British") })
                    FilterChip(selected = accent == "US", onClick = { accent = "US" },
                        label = { Text("US · American") })
                }
                OutlinedTextField(if (accent == "UK") ipaUk else ipaUs,
                    { if (accent == "UK") ipaUk = it else ipaUs = it },
                    label = { Text("IPA · " + accent) }, modifier = Modifier.fillMaxWidth())
                Button(
                    enabled = phrase.isNotBlank() && !lookingUp,
                    onClick = {
                        lookingUp = true
                        scope.launch {
                            try {
                                val result = Pronunciation.phrase(phrase)
                                if (result.uk.isNotBlank()) ipaUk = result.uk
                                if (result.us.isNotBlank()) ipaUs = result.us
                                lookupMessage = if (result.missing.isEmpty())
                                    "Dictionary pronunciations found; review against the audio."
                                else "Incomplete accent-labelled IPA for: " +
                                    result.missing.joinToString(", ") +
                                    ". '?' means unavailable; edit manually."
                            } finally { lookingUp = false }
                        }
                    }
                ) {
                    if (lookingUp) CircularProgressIndicator(Modifier.size(16.dp),
                        strokeWidth = 2.dp)
                    else Icon(Icons.Default.Translate, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Look up UK / US IPA")
                }
                if (lookupMessage.isNotBlank()) Text(lookupMessage,
                    style = MaterialTheme.typography.bodySmall)
            }
        }
        Surface(color = Cream, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Meaning & notes", fontWeight = FontWeight.Bold)
                OutlinedTextField(meaning, { meaning = it }, label = { Text("Meaning in Portuguese") },
                    modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes / connected speech") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2)
                OutlinedTextField(category, { category = it }, label = { Text("Collection") },
                    modifier = Modifier.fillMaxWidth(), singleLine = true)
                Text("User-owned audio (optional)", fontWeight = FontWeight.Bold)
                Text("Select a recording you have permission to use. No YouTube audio is downloaded.",
                    style = MaterialTheme.typography.bodySmall)
                if (audioUri != null) {
                    Text("Imported-audio timestamps are independent of YouTube timestamps.",
                        style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(audioFrom, { audioFrom = it }, modifier = Modifier.weight(1f),
                            label = { Text("Audio start") }, singleLine = true)
                        OutlinedTextField(audioTo, { audioTo = it }, modifier = Modifier.weight(1f),
                            label = { Text("Audio end") }, singleLine = true)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { audioPicker.launch(arrayOf("audio/*")) }) {
                        Icon(Icons.Default.AudioFile, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (audioUri == null) "Choose audio file" else "Change audio file")
                    }
                    if (audioUri != null) {
                        IconButton(onClick = { audioUri = null }) {
                            Icon(Icons.Default.Close, "Remove audio")
                        }
                    }
                }
            }
        }
        if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        Button(
            onClick = {
                val start = SourceTools.parseClock(from)
                val end = SourceTools.parseClock(to)
                error = when {
                    phrase.isBlank() -> "Enter a phrase before saving."
                    url.isNotBlank() && source == null -> "The source must be a valid YouTube link."
                    start == null || end == null -> "Use a valid timestamp such as 01:23."
                    end <= start -> "End must be after start."
                    audioUri != null && (SourceTools.parseClock(audioFrom) == null || SourceTools.parseClock(audioTo) == null) ->
                        "Use valid imported-audio timestamps."
                    audioUri != null && (SourceTools.parseClock(audioTo) ?: 0L) <= (SourceTools.parseClock(audioFrom) ?: 0L) ->
                        "Imported-audio end must be after start."
                    else -> ""
                }
                if (error.isBlank() && start != null && end != null) {
                    onSave((editing ?: Phrase(text = phrase.trim())).copy(
                        text = phrase.trim(), meaning = meaning.trim(), notes = notes.trim(),
                        videoId = source?.id.orEmpty(), videoUrl = source?.url.orEmpty(),
                        title = title.trim(), startMs = start, endMs = end,
                        ipaUk = ipaUk.trim(), ipaUs = ipaUs.trim(), audioUri = audioUri,
                        audioStartMs = SourceTools.parseClock(audioFrom) ?: 0L,
                        audioEndMs = SourceTools.parseClock(audioTo) ?: 3000L,
                        collection = category.trim().ifBlank { "General" }
                    ))
                }
            }, modifier = Modifier.fillMaxWidth().height(54.dp)
        ) {
            Icon(Icons.Default.BookmarkAdd, null)
            Spacer(Modifier.width(8.dp))
            Text("Save phrase")
        }
        Spacer(Modifier.height(14.dp))
    }
}

@Composable
private fun EmbeddedPlayer(videoId: String, onCreated: (WebView) -> Unit) {
    val context = LocalContext.current
    val webview = remember(videoId) {
        WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.mediaPlaybackRequiresUserGesture = true
            webChromeClient = WebChromeClient()
            webViewClient = WebViewClient()
            val html = """
                <!doctype html><html><head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <style>html,body,#player{width:100%;height:100%;margin:0;background:#111;overflow:hidden}</style>
                </head><body><div id="player"></div>
                <script src="https://www.youtube.com/iframe_api"></script>
                <script>
                  window.engtubePlayer=null;
                  function onYouTubeIframeAPIReady(){
                    window.engtubePlayer=new YT.Player('player',{
                      width:'100%',height:'100%',videoId:'$videoId',
                      playerVars:{playsinline:1,origin:'https://www.youtube.com'}
                    });
                  }
                </script></body></html>
            """.trimIndent()
            loadDataWithBaseURL("https://www.youtube.com/", html, "text/html", "UTF-8", null)
        }
    }
    DisposableEffect(webview) {
        onDispose { webview.stopLoading(); webview.destroy() }
    }
    SideEffect { onCreated(webview) }
    AndroidView(factory = { webview }, modifier = Modifier.fillMaxWidth().height(205.dp))
}

internal fun openAt(context: android.content.Context, videoId: String, atMs: Long) {
    val seconds = atMs.coerceAtLeast(0) / 1000
    val link = Uri.parse("https://www.youtube.com/watch?v=" + videoId + "&t=" + seconds + "s")
    val intent = Intent(Intent.ACTION_VIEW, link).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }.onFailure {
        Toast.makeText(context, "Unable to open YouTube", Toast.LENGTH_LONG).show()
    }
}

@Composable
internal fun StudyScreen(
    phrase: Phrase,
    defaultAccent: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onUpdate: (Phrase) -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var accent by remember(phrase.id) { mutableStateOf(defaultAccent) }
    var speed by remember { mutableFloatStateOf(0.85f) }
    var repeat by remember { mutableStateOf(true) }
    var playing by remember { mutableStateOf(false) }
    var ttsReady by remember { mutableStateOf(false) }
    val speaker = remember {
        TextToSpeech(context) { status -> ttsReady = status == TextToSpeech.SUCCESS }
    }
    val clipPlayer = remember { AudioSegmentPlayer(context) { playing = it } }
    DisposableEffect(Unit) {
        onDispose { speaker.shutdown(); clipPlayer.stop() }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") }
            Text("Phrase Study", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Edit phrase") }
        }
        Surface(color = Sage, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text(phrase.text, style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold)
                if (phrase.meaning.isNotBlank()) {
                    Spacer(Modifier.height(7.dp)); Text(phrase.meaning)
                }
                Text(phrase.collection, style = MaterialTheme.typography.labelSmall, color = DeepGreen)
            }
        }
        Surface(color = Cream, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Pronunciation · dictionary reference", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FilterChip(selected = accent == "UK", onClick = { accent = "UK" },
                        label = { Text("UK · British") })
                    FilterChip(selected = accent == "US", onClick = { accent = "US" },
                        label = { Text("US · American") })
                }
                Text((if (accent == "UK") phrase.ipaUk else phrase.ipaUs)
                    .ifBlank { "IPA not available. Edit or look up in Capture." },
                    style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    enabled = ttsReady,
                    onClick = {
                        val language = if (accent == "UK") Locale.UK else Locale.US
                        val result = speaker.setLanguage(language)
                        if (result < 0) {
                            Toast.makeText(context, "Install an " + accent + " TTS voice in Android settings",
                                Toast.LENGTH_LONG).show()
                        } else speaker.speak(phrase.text, TextToSpeech.QUEUE_FLUSH, null, "engtube")
                    }
                ) {
                    Icon(Icons.Default.RecordVoiceOver, null)
                    Spacer(Modifier.width(7.dp))
                    Text("Speak · " + accent + " (synthetic)")
                }
                Text("This IPA describes dictionary pronunciations, not the exact sounds produced by the video's speaker.",
                    style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
        }
        Surface(color = Lavender, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Audio & original context", fontWeight = FontWeight.Bold)
                Text("Range: " + SourceTools.clock(phrase.startMs) + " – " +
                    SourceTools.clock(phrase.endMs),
                    style = MaterialTheme.typography.bodyMedium)
                if (phrase.videoId.isNotBlank()) {
                    OutlinedButton(onClick = { openAt(context, phrase.videoId, phrase.startMs) }) {
                        Icon(Icons.Default.OpenInNew, null)
                        Spacer(Modifier.width(6.dp))
                        Text("Open in YouTube at this moment")
                    }
                    YouTubeClipPlayer(phrase.videoId, phrase.startMs, phrase.endMs)
                }
                if (phrase.audioUri != null) {
                    Text("Imported local audio · playback follows the file's timeline",
                        style = MaterialTheme.typography.bodySmall)
                    Text("Local clip: " + SourceTools.clock(phrase.audioStartMs) + " – " +
                        SourceTools.clock(phrase.audioEndMs),
                        style = MaterialTheme.typography.bodySmall)
                    Row(verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        Button(onClick = {
                            if (playing) clipPlayer.stop() else clipPlayer.play(
                                Uri.parse(phrase.audioUri),
                                phrase.audioStartMs, phrase.audioEndMs, speed, repeat)
                        }) {
                            Icon(if (playing) Icons.Default.Stop else Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(3.dp))
                            Text(if (playing) "Stop" else "Play")
                        }
                        FilterChip(selected = repeat, onClick = { repeat = !repeat },
                            label = { Text("A–B repeat") })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Speed: " + speed.toString() + "×")
                        Slider(speed, { speed = it }, valueRange = 0.5f..1.5f,
                            steps = 9, modifier = Modifier.weight(1f))
                    }
                } else {
                    Text("No audio file saved. You can replay the original video via YouTube, or attach a recording you own in Edit.",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Surface(color = Cream, shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Study notes", fontWeight = FontWeight.Bold)
                Text(phrase.notes.ifBlank { "Add your observations about linking, rhythm and reductions in Edit." })
                Text("Progress", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("New", "Studying", "Learned").forEach { status ->
                        FilterChip(selected = phrase.status == status,
                            onClick = { onUpdate(phrase.copy(status = status)) },
                            label = { Text(status) })
                    }
                }
            }
        }
    }
}
