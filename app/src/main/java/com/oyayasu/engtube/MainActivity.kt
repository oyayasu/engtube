package com.oyayasu.engtube

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.oyayasu.engtube.data.Phrase
import com.oyayasu.engtube.data.SourceTools
import kotlinx.coroutines.launch

internal val Lavender = Color(0xFFF1E7F8)
internal val Sage = Color(0xFFD8E8C7)
internal val DeepGreen = Color(0xFF456C42)
internal val Ink = Color(0xFF292335)
internal val Cream = Color(0xFFFFFBF8)

private val Scheme = lightColorScheme(
    primary = DeepGreen, onPrimary = Color.White, primaryContainer = Sage,
    onPrimaryContainer = Ink, secondary = Color(0xFF795C91),
    secondaryContainer = Lavender, background = Color(0xFFFDF9FE),
    surface = Cream, surfaceVariant = Lavender, onBackground = Ink, onSurface = Ink
)

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<String?>(null)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        incoming = extractInput(intent)
        window.statusBarColor = android.graphics.Color.rgb(241, 231, 248)
        window.navigationBarColor = android.graphics.Color.rgb(241, 231, 248)
        window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
            android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        setContent {
            MaterialTheme(colorScheme = Scheme, shapes = Shapes(
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(24.dp)
            )) {
                EngTubeShell(incoming = incoming, consumed = { incoming = null })
            }
        }
    }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incoming = extractInput(intent)
    }
    private fun extractInput(intent: Intent?): String? {
        if (intent?.getBooleanExtra("open_capture", false) == true) return ""
        return if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain")
            intent.getStringExtra(Intent.EXTRA_TEXT) else null
    }
}

private enum class Screen { Home, Capture, Library, Study, Settings }

@Composable
private fun EngTubeShell(incoming: String?, consumed: () -> Unit) {
    val context = LocalContext.current
    val dao = remember { (context.applicationContext as EngTubeApplication).database.phraseDao() }
    val phrases by dao.observeAll().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var screen by remember { mutableStateOf(Screen.Home) }
    var captureSeed by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<Phrase?>(null) }
    var studying by remember { mutableStateOf<Phrase?>(null) }
    val preferences = remember { context.getSharedPreferences("engtube", 0) }
    var accent by remember { mutableStateOf(preferences.getString("accent", "UK") ?: "UK") }
    fun capture(seed: String = "", phrase: Phrase? = null) {
        captureSeed = seed; editing = phrase; screen = Screen.Capture
    }
    LaunchedEffect(incoming) {
        if (incoming != null) { capture(incoming); consumed() }
    }
    Scaffold(
        containerColor = Scheme.background,
        topBar = {
            if (screen != Screen.Capture && screen != Screen.Study) {
                Surface(color = Lavender) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 19.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.MenuBook, null, tint = DeepGreen)
                        Spacer(Modifier.width(9.dp))
                        Text("EngTube", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.weight(1f))
                        Text("EN · " + accent, color = DeepGreen)
                    }
                }
            }
        },
        bottomBar = {
            if (screen != Screen.Capture && screen != Screen.Study) {
                NavigationBar(containerColor = Lavender) {
                    listOf(
                        Triple(Screen.Home, Icons.Default.Home, "Home"),
                        Triple(Screen.Library, Icons.Default.Bookmarks, "Library"),
                        Triple(Screen.Settings, Icons.Default.Settings, "Settings")
                    ).forEach { (destination, icon, label) ->
                        NavigationBarItem(
                            selected = screen == destination,
                            onClick = { screen = destination },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(indicatorColor = Sage)
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when (screen) {
                Screen.Home -> HomeScreen(phrases.size, onCapture = { capture() },
                    onLibrary = { screen = Screen.Library })
                Screen.Capture -> CaptureScreen(
                    seed = captureSeed, editing = editing, defaultAccent = accent,
                    onBack = { screen = Screen.Library },
                    onSave = { phrase ->
                        scope.launch { dao.save(phrase); screen = Screen.Library }
                    }
                )
                Screen.Library -> LibraryScreen(
                    phrases = phrases, onCapture = { capture() },
                    onStudy = { studying = it; screen = Screen.Study },
                    onEdit = { capture(phrase = it) },
                    onDelete = { phrase -> scope.launch { dao.delete(phrase) } }
                )
                Screen.Study -> {
                    val current = phrases.firstOrNull { it.id == studying?.id } ?: studying
                    if (current == null) screen = Screen.Library
                    else StudyScreen(
                        phrase = current, defaultAccent = accent,
                        onBack = { screen = Screen.Library },
                        onEdit = { capture(phrase = current) },
                        onUpdate = { updated ->
                            studying = updated
                            scope.launch { dao.save(updated) }
                        }
                    )
                }
                Screen.Settings -> SettingsScreen(
                    accent = accent,
                    onAccent = {
                        accent = it
                        preferences.edit().putString("accent", it).apply()
                    }
                )
            }
        }
    }
}

@Composable
private fun HomeScreen(count: Int, onCapture: () -> Unit, onLibrary: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Spacer(Modifier.height(12.dp))
        Text("Make spoken English yours.", style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold)
        Text("Save real phrases from the videos you watch. Study pronunciation and keep every source.",
            color = Color.DarkGray)
        Surface(shape = RoundedCornerShape(24.dp), color = Sage) {
            Column(Modifier.fillMaxWidth().padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Default.OndemandVideo, null, tint = DeepGreen)
                Text("Capture from YouTube", style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold)
                Text("Paste a link or use Share → EngTube in the YouTube app.")
                Button(onClick = onCapture, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Capture a phrase")
                }
            }
        }
        Surface(shape = RoundedCornerShape(24.dp), color = Lavender,
            modifier = Modifier.fillMaxWidth().clickable(onClick = onLibrary)) {
            Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Bookmarks, null, tint = Color(0xFF795C91))
                Spacer(Modifier.width(14.dp))
                Column {
                    Text("$count saved phrases", fontWeight = FontWeight.Bold)
                    Text("Open your phrase library", style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.weight(1f))
                Icon(Icons.Default.ChevronRight, null)
            }
        }
        Text("British pronunciation first · American IPA available",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}

@Composable
private fun LibraryScreen(
    phrases: List<Phrase>,
    onCapture: () -> Unit,
    onStudy: (Phrase) -> Unit,
    onEdit: (Phrase) -> Unit,
    onDelete: (Phrase) -> Unit
) {
    var search by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("All") }
    var collection by remember { mutableStateOf("All") }
    var deleteTarget by remember { mutableStateOf<Phrase?>(null) }
    val collections = remember(phrases) {
        listOf("All") + phrases.map { it.collection }.distinct().sorted()
    }
    val visible = phrases.filter { phrase ->
        (status == "All" || phrase.status == status) &&
        (collection == "All" || phrase.collection == collection) &&
        (search.isBlank() || listOf(phrase.text, phrase.meaning, phrase.notes, phrase.title, phrase.collection)
            .any { it.contains(search, ignoreCase = true) })
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 12.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("My Library", style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onCapture) { Icon(Icons.Default.Add, "Capture") }
        }
        OutlinedTextField(
            search, { search = it }, label = { Text("Search phrases, words or notes") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            listOf("All", "New", "Studying", "Learned").forEach {
                FilterChip(selected = status == it, onClick = { status = it }, label = { Text(it) })
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text(visible.size.toString() + " phrases", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            var expanded by remember { mutableStateOf(false) }
            Box {
                TextButton(onClick = { expanded = true }) {
                    Text(if (collection == "All") "All collections" else collection, maxLines = 1)
                    Icon(Icons.Default.ArrowDropDown, null)
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    collections.forEach {
                        DropdownMenuItem(text = { Text(it) },
                            onClick = { collection = it; expanded = false })
                    }
                }
            }
        }
        if (visible.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Icon(Icons.Default.Bookmarks, null, modifier = Modifier.size(42.dp), tint = DeepGreen)
                Spacer(Modifier.height(10.dp))
                Text("No phrases here yet")
                Spacer(Modifier.height(10.dp))
                Button(onClick = onCapture) { Text("Capture a phrase") }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(visible, key = { it.id }) { phrase ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Cream),
                        onClick = { onStudy(phrase) }, modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Sage, shape = RoundedCornerShape(14.dp)) {
                                Icon(Icons.Default.PlayArrow, null,
                                    modifier = Modifier.padding(10.dp), tint = DeepGreen)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(phrase.text, maxLines = 2,
                                    overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                                Text(phrase.collection + " · " + phrase.status,
                                    style = MaterialTheme.typography.labelSmall, color = DeepGreen)
                                if (phrase.videoId.isNotBlank()) {
                                    Text("YouTube · " + SourceTools.clock(phrase.startMs),
                                        style = MaterialTheme.typography.bodySmall, color = Color.Gray)
                                }
                            }
                            var expanded by remember { mutableStateOf(false) }
                            Box {
                                IconButton(onClick = { expanded = true }) {
                                    Icon(Icons.Default.MoreVert, "Phrase options")
                                }
                                DropdownMenu(expanded, onDismissRequest = { expanded = false }) {
                                    DropdownMenuItem(text = { Text("Edit") }, onClick = {
                                        expanded = false; onEdit(phrase)
                                    })
                                    DropdownMenuItem(text = { Text("Delete") }, onClick = {
                                        expanded = false; deleteTarget = phrase
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    deleteTarget?.let { phrase ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Delete phrase?") },
            text = { Text(phrase.text) },
            confirmButton = {
                TextButton(onClick = { onDelete(phrase); deleteTarget = null }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun SettingsScreen(accent: String, onAccent: (String) -> Unit) {
    val context = LocalContext.current
    val notifications = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    Column(Modifier.fillMaxSize().padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Surface(shape = RoundedCornerShape(20.dp), color = Cream) {
            Column(Modifier.fillMaxWidth().padding(18.dp)) {
                Text("Reference pronunciation", fontWeight = FontWeight.Bold)
                Text("The original speaker's accent is independent of this setting.",
                    style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    FilterChip(selected = accent == "UK", onClick = { onAccent("UK") },
                        label = { Text("UK · British") })
                    FilterChip(selected = accent == "US", onClick = { onAccent("US") },
                        label = { Text("US · American") })
                }
            }
        }
        Surface(shape = RoundedCornerShape(20.dp), color = Cream) {
            Column(Modifier.fillMaxWidth().padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)) {
                Text("Floating study button", fontWeight = FontWeight.Bold)
                Text("Optional quick access over YouTube. The bubble cannot read a video's audio, captions or playback position.",
                    style = MaterialTheme.typography.bodySmall)
                Button(onClick = {
                    if (!Settings.canDrawOverlays(context)) {
                        val request = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            Uri.parse("package:" + context.packageName))
                        context.startActivity(request)
                    } else {
                        if (Build.VERSION.SDK_INT >= 33 &&
                            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                            android.content.pm.PackageManager.PERMISSION_GRANTED) {
                            notifications.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        androidx.core.content.ContextCompat.startForegroundService(
                            context, Intent(context, OverlayService::class.java))
                        Toast.makeText(context, "Floating button started", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Enable floating button") }
                OutlinedButton(onClick = {
                    context.stopService(Intent(context, OverlayService::class.java))
                }) { Text("Stop floating button") }
            }
        }
        Text("Privacy: phrases stay on this device. IPA lookup sends words to a public dictionary API; YouTube media is not downloaded.",
            style = MaterialTheme.typography.bodySmall, color = Color.Gray)
    }
}
