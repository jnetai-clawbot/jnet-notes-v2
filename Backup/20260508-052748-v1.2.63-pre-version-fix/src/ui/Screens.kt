package com.jnet.notes.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.util.Log
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jnet.notes.repository.NotesRepository
import com.jnet.notes.data.local.NoteEntity
import com.jnet.notes.data.local.UserCredsEntity
import com.jnet.notes.data.local.UserDao
import com.jnet.notes.security.EncryptionManager
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

private const val TAG = "JNetNotes"
private const val GITHUB_API_URL = "https://api.github.com/repos/jnetai-clawbot/jnet-notes-v2/releases/latest"
private const val GITHUB_RELEASES_URL = "https://github.com/jnetai-clawbot/jnet-notes-v2/releases"

object Err {
    const val E001 = "E001: DB_INIT_FAILED"
    const val E002 = "E002: USER_LOOKUP_FAILED"
    const val E003 = "E003: PASSWORD_HASH_FAILED"
    const val E004 = "E004: PASSWORD_SAVE_FAILED"
    const val E005 = "E005: PASSWORD_VERIFY_FAILED"
    const val E006 = "E006: NOTES_LOAD_FAILED"
    const val E007 = "E007: NOTE_SAVE_FAILED"
    const val E008 = "E008: ENCRYPTION_FAILED"
    const val E009 = "E009: DECRYPTION_FAILED"
    const val E010 = "E010: EXPORT_FAILED"
    const val E011 = "E011: IMPORT_FAILED"
    const val E012 = "E012: SYNC_AUTH_FAILED"
    const val E013 = "E013: SYNC_UPLOAD_FAILED"
    const val E014 = "E014: SYNC_PULL_FAILED"
    const val E015 = "E015: UNEXPECTED_ERROR"
}

fun logError(code: String, msg: String, e: Throwable? = null) {
    Log.e(TAG, "$code - $msg", e)
}

// ─── Clipboard Item Data ───
data class ClipboardItem(val text: String, val timestamp: Long)

// ─── Clipboard Manager Helper ───
class ClipboardHelper(private val context: Context) {
    companion object {
        private const val MAX_HISTORY = 20
    }

    private val prefs = context.getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)

    fun getSystemClipboardText(): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        if (clipboard.hasPrimaryClip()) {
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val item = clip.getItemAt(0)
                return item.text?.toString() ?: item.coerceToText(context)?.toString()
            }
        }
        return null
    }

    fun copyToClipboard(text: String, label: String = "Note"): Boolean {
        return try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText(label, text)
            clipboard.setPrimaryClip(clip)
            addToHistory(text)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun getHistory(): List<ClipboardItem> {
        val raw = prefs.getString("items", "") ?: ""
        if (raw.isEmpty()) return emptyList()
        return raw.split("|||").mapNotNull { entry ->
            val parts = entry.split(":::", limit = 2)
            if (parts.size == 2) {
                ClipboardItem(parts[1], parts[0].toLongOrNull() ?: 0L)
            } else null
        }.sortedByDescending { it.timestamp }
    }

    private fun addToHistory(text: String) {
        val existing = getHistory().toMutableList()
        existing.removeAll { it.text == text }
        existing.add(0, ClipboardItem(text, System.currentTimeMillis()))
        val trimmed = existing.take(MAX_HISTORY)
        val raw = trimmed.joinToString("|||") { "${it.timestamp}:::${it.text}" }
        prefs.edit().putString("items", raw).apply()
    }

    fun clearHistory() {
        prefs.edit().remove("items").apply()
    }
}

// ─── GitHub Update Check ───
data class UpdateCheckResult(
    val latestVersion: String = "",
    val hasUpdate: Boolean = false,
    val error: String = ""
)

suspend fun checkForUpdate(currentVersion: String): UpdateCheckResult {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL(GITHUB_API_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val reader = BufferedReader(InputStreamReader(conn.inputStream))
            val response = reader.readText()
            reader.close()

            val tagStart = response.indexOf("\"tag_name\":\"")
            if (tagStart == -1) return@withContext UpdateCheckResult(error = "Could not parse version")
            val tagEnd = response.indexOf("\"", tagStart + 12)
            val tagName = response.substring(tagStart + 12, tagEnd)
            val latestVer = tagName.removePrefix("v")

            val hasUpdate = try {
                val currentParts = currentVersion.split(".").map { it.toIntOrNull() ?: 0 }
                val latestParts = latestVer.split(".").map { it.toIntOrNull() ?: 0 }
                for (i in 0 until maxOf(currentParts.size, latestParts.size)) {
                    val cp = currentParts.getOrElse(i) { 0 }
                    val lp = latestParts.getOrElse(i) { 0 }
                    if (lp > cp) true
                    else if (lp < cp) false
                }
                false
            } catch (e: Exception) {
                false
            }

            UpdateCheckResult(latestVersion = tagName, hasUpdate = hasUpdate)
        } catch (e: Exception) {
            UpdateCheckResult(error = "Check failed: ${e.message?.take(50)}")
        }
    }
}

// ═══════════════════════════════════════════════
// CLIPBOARD DIALOG
// ═══════════════════════════════════════════════
@Composable
fun ClipboardDialog(
    clipboardHelper: ClipboardHelper,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val systemClipText = remember { clipboardHelper.getSystemClipboardText() }
    var history by remember { mutableStateOf(clipboardHelper.getHistory()) }
    var showClearConfirm by remember { mutableStateOf(false) }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Clipboard History?") },
            text = { Text("This will delete all stored clipboard items.") },
            confirmButton = {
                TextButton(onClick = {
                    clipboardHelper.clearHistory()
                    history = emptyList()
                    showClearConfirm = false
                    Toast.makeText(clipboardHelper.context, "History cleared", Toast.LENGTH_SHORT).show()
                }) { Text("Clear", color = MaterialTheme.colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("📋 Clipboard")
                Row {
                    TextButton(onClick = { selectedTab = 0 }) {
                        Text(if (selectedTab == 0) "● System" else "System", fontSize = 13.sp)
                    }
                    TextButton(onClick = { selectedTab = 1 }) {
                        Text(if (selectedTab == 1) "● History" else "History", fontSize = 13.sp)
                    }
                }
            }
        },
        text = {
            Column {
                if (selectedTab == 0) {
                    if (systemClipText != null && systemClipText.isNotBlank()) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onSelect(systemClipText); onDismiss() },
                            elevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("📋 Current System Clipboard", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.primary)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(systemClipText.take(200), style = MaterialTheme.typography.body2)
                                if (systemClipText.length > 200) {
                                    Text("... (${systemClipText.length} chars)", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
                                }
                            }
                        }
                    } else {
                        Text("System clipboard is empty", style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tap to paste into note editor", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f))
                } else {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${history.size} items", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
                        if (history.isNotEmpty()) {
                            TextButton(onClick = { showClearConfirm = true }) {
                                Text("Clear All", color = MaterialTheme.colors.error, fontSize = 12.sp)
                            }
                        }
                    }
                    if (history.isEmpty()) {
                        Text("No clipboard history yet", style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
                    } else {
                        LazyColumn(modifier = Modifier.height(300.dp)) {
                            itemsIndexed(history) { index, item ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable { onSelect(item.text); onDismiss() },
                                    elevation = 1.dp
                                ) {
                                    Column(modifier = Modifier.padding(10.dp)) {
                                        Text(item.text.take(150), style = MaterialTheme.typography.body2, maxLines = 3)
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("#${index + 1}", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f))
                                            Text(
                                                java.text.SimpleDateFormat("HH:mm dd/MM", java.util.Locale.getDefault()).format(java.util.Date(item.timestamp)),
                                                style = MaterialTheme.typography.caption,
                                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

// ═══════════════════════════════════════════════
// NOTE LIST SCREEN
// ═══════════════════════════════════════════════
@Composable
fun NoteListScreen(
    repository: NotesRepository,
    password: String,
    onNoteClick: (Int) -> Unit,
    onAddNote: () -> Unit,
    onLogout: () -> Unit,
    onSettings: () -> Unit
) {
    var notes by remember { mutableStateOf(listOf<NoteEntity>()) }
    var loadError by remember { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    var decryptedContentCache by remember { mutableStateOf(mapOf<Int, String>()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        try {
            notes = withContext(Dispatchers.IO) { repository.getAllNotes() }
        } catch (e: Exception) {
            logError(Err.E006, "Failed to load notes", e)
            loadError = "${Err.E006}: ${e.message}"
        }
    }

    val filteredNotes = if (searchQuery.isBlank()) {
        notes
    } else {
        val query = searchQuery.lowercase()
        val titleMatches = notes.filter { it.title.lowercase().contains(query) }
        if (titleMatches.isNotEmpty()) {
            titleMatches
        } else {
            scope.launch {
                val cache = mutableMapOf<Int, String>()
                for (note in notes) {
                    if (!decryptedContentCache.containsKey(note.id)) {
                        try {
                            val decrypted = withContext(Dispatchers.IO) { repository.getDecryptedNote(note, password) }
                            cache[note.id] = decrypted
                        } catch (e: Exception) {
                            cache[note.id] = ""
                        }
                    }
                }
                decryptedContentCache = decryptedContentCache + cache
            }
            notes.filter { note ->
                val cachedContent = decryptedContentCache[note.id] ?: ""
                cachedContent.lowercase().contains(query)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("J~Net Notes") },
                navigationIcon = {
                    TextButton(onClick = onSettings) {
                        Text("☰", color = MaterialTheme.colors.onPrimary, style = MaterialTheme.typography.h5)
                    }
                },
                actions = {
                    TextButton(onClick = onLogout) {
                        Text("Logout", color = MaterialTheme.colors.onPrimary)
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNote) {
                Text("+")
            }
        }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("🔍 Search notes...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true
            )

            if (searchQuery.isNotEmpty()) {
                Text(
                    text = "Showing ${filteredNotes.size} of ${notes.size} notes",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                )
            }

            if (loadError.isNotEmpty()) {
                Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                    Text("Error loading notes", style = MaterialTheme.typography.h6, color = MaterialTheme.colors.error)
                    Text(loadError, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.error)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { loadError = "" }) { Text("Dismiss") }
                }
            } else if (notes.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No notes yet", style = MaterialTheme.typography.h6, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Tap + to create one", style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f))
                    }
                }
            } else if (filteredNotes.isEmpty() && searchQuery.isNotEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("No matches found", style = MaterialTheme.typography.h6, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Try a different search term", style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f))
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    items(filteredNotes) { note ->
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onNoteClick(note.id) },
                            elevation = 2.dp
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = note.title, style = MaterialTheme.typography.subtitle1)
                                Text(
                                    text = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date(note.timestamp)),
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// NOTE EDIT SCREEN
// ═══════════════════════════════════════════════
@Composable
fun NoteEditScreen(
    repository: NotesRepository,
    password: String,
    noteId: Int?,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboardHelper = remember { ClipboardHelper(context) }

    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var originalTitle by remember { mutableStateOf("") }
    var originalContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(noteId != null) }
    var error by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showClipboardDialog by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    if (noteId != null && isLoading) {
        LaunchedEffect(noteId) {
            try {
                val notes = withContext(Dispatchers.IO) { repository.getAllNotes() }
                val note = notes.find { it.id == noteId }
                if (note != null) {
                    title = note.title
                    val decrypted = withContext(Dispatchers.IO) { repository.getDecryptedNote(note, password) }
                    content = decrypted
                    originalTitle = note.title
                    originalContent = decrypted
                }
            } catch (e: Exception) {
                logError(Err.E006, "Failed to load note", e)
                error = "${Err.E006}: ${e.message}"
            }
            isLoading = false
        }
    }

    // Clipboard dialog
    if (showClipboardDialog) {
        ClipboardDialog(
            clipboardHelper = clipboardHelper,
            onSelect = { selectedText -> content += selectedText },
            onDismiss = { showClipboardDialog = false }
        )
    }

    // Discard changes dialog
    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("You have unsaved changes.") },
            confirmButton = {
                TextButton(onClick = { showDiscardDialog = false; onCancel() }) { Text("Discard", color = MaterialTheme.colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("Keep editing") }
            }
        )
    }

    // Delete dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Note?") },
            text = { Text("This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                val notes = repository.getAllNotes()
                                val note = notes.find { it.id == noteId }
                                note?.let { repository.deleteNote(it) }
                            }
                            Toast.makeText(context, "Note deleted", Toast.LENGTH_SHORT).show()
                            onSave()
                        } catch (e: Exception) {
                            error = "${Err.E007}: ${e.message}"
                        }
                    }
                    showDeleteConfirm = false
                }) { Text("Delete", color = MaterialTheme.colors.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    // Context menu dialog
    if (showContextMenu) {
        AlertDialog(
            onDismissRequest = { showContextMenu = false },
            title = { Text("Edit") },
            text = {
                Column {
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        clipboardHelper.copyToClipboard(content)
                        Toast.makeText(context, "📋 Copied to clipboard + history", Toast.LENGTH_SHORT).show()
                        showContextMenu = false
                    }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📋 Copy", modifier = Modifier.weight(1f))
                        Text("to clipboard + history", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
                    }
                    Divider()
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        clipboardHelper.copyToClipboard(content)
                        content = ""
                        Toast.makeText(context, "✂️ Cut to clipboard", Toast.LENGTH_SHORT).show()
                        showContextMenu = false
                    }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("✂️ Cut", modifier = Modifier.weight(1f))
                        Text("copy + remove", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
                    }
                    Divider()
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        val clipText = clipboardHelper.getSystemClipboardText()
                        if (clipText != null) {
                            content += clipText
                            clipboardHelper.copyToClipboard(clipText, "Pasted")
                            Toast.makeText(context, "📄 Pasted from clipboard", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Clipboard is empty", Toast.LENGTH_SHORT).show()
                        }
                        showContextMenu = false
                    }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📄 Paste", modifier = Modifier.weight(1f))
                        Text("from system clipboard", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
                    }
                    Divider()
                    Row(modifier = Modifier.fillMaxWidth().clickable {
                        showContextMenu = false
                        showClipboardDialog = true
                    }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("📋 Clipboard Manager", modifier = Modifier.weight(1f))
                        Text("browse all items", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.primary)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showContextMenu = false }) { Text("Close") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (noteId == null) "New Note" else "Edit Note") },
                navigationIcon = {
                    TextButton(onClick = {
                        if (title != originalTitle || content != originalContent) {
                            showDiscardDialog = true
                        } else {
                            onCancel()
                        }
                    }) { Text("Close", color = Color.White) }
                },
                actions = {
                    IconButton(onClick = { showClipboardDialog = true }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Clipboard", tint = Color.White)
                    }
                    if (noteId != null) {
                        TextButton(onClick = { showDeleteConfirm = true }) {
                            Text("Delete", color = MaterialTheme.colors.error)
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        if (isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colors.error, style = MaterialTheme.typography.body2)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                Spacer(modifier = Modifier.height(12.dp))

                Box {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("Note content") },
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        maxLines = Int.MAX_VALUE
                    )
                    Box(
                        modifier = Modifier.matchParentSize()
                            .clickable(onClick = {}, onLongClick = { showContextMenu = true })
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                // Bottom toolbar
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                error = "Title cannot be empty"
                                return@Button
                            }
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        if (noteId != null) {
                                            val notes = repository.getAllNotes()
                                            notes.find { it.id == noteId }?.let { repository.deleteNote(it) }
                                        }
                                        repository.saveNote(title, content, password)
                                    }
                                    Toast.makeText(context, "Note saved", Toast.LENGTH_SHORT).show()
                                    onSave()
                                } catch (e: Exception) {
                                    logError(Err.E007, "Failed to save note", e)
                                    error = "${Err.E007}: ${e.message}"
                                }
                            }
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    ) { Text("💾 Save") }

                    if (noteId != null) {
                        OutlinedButton(
                            onClick = {
                                title = originalTitle
                                content = originalContent
                                error = ""
                                Toast.makeText(context, "Reverted to saved version", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        ) { Text("↩️ Revert") }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    OutlinedButton(
                        onClick = {
                            if (content.isNotEmpty()) {
                                if (clipboardHelper.copyToClipboard(content)) {
                                    Toast.makeText(context, "📋 Copied to clipboard + history", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Copy failed", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Nothing to copy", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    ) { Text("📋 Copy") }

                    OutlinedButton(
                        onClick = {
                            if (content.isNotEmpty()) {
                                clipboardHelper.copyToClipboard(content)
                                content = ""
                                Toast.makeText(context, "✂️ Cut to clipboard", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "Nothing to cut", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    ) { Text("✂️ Cut") }

                    OutlinedButton(
                        onClick = { showClipboardDialog = true },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    ) { Text("📋 Paste") }

                    OutlinedButton(
                        onClick = {
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                putExtra(Intent.EXTRA_SUBJECT, title)
                                putExtra(Intent.EXTRA_TEXT, "$title\n\n$content")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share via..."))
                        },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                    ) { Text("Share") }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// LOGIN SCREEN
// ═══════════════════════════════════════════════
@Composable
fun LoginScreen(userDao: UserDao, onLoginSuccess: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var isSetup by remember { mutableStateOf(false) }
    var checkedExisting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        try {
            withContext(Dispatchers.IO) {
                isSetup = (userDao.getUser() == null)
            }
        } catch (e: Exception) {
            logError(Err.E002, "Failed to check existing user", e)
            error = "${Err.E002}: ${e.message}"
        }
        checkedExisting = true
    }

    if (!checkedExisting) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Surface(color = MaterialTheme.colors.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isSetup) {
                Text("First Time Setup", style = MaterialTheme.typography.h5, color = MaterialTheme.colors.onBackground)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Default password: 12345678", style = MaterialTheme.typography.h6, color = MaterialTheme.colors.primary)
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Enter default password (12345678)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = { Text("Set your new password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = { Text("Confirm new password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colors.error, style = MaterialTheme.typography.body2)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        error = ""
                        if (password != "12345678") { error = "Default password is incorrect"; return@Button }
                        if (newPassword.isEmpty()) { error = "New password cannot be empty"; return@Button }
                        if (newPassword != confirmPassword) { error = "Passwords do not match"; return@Button }
                        scope.launch {
                            try {
                                withContext(Dispatchers.IO) {
                                    val salt = EncryptionManager.generateSalt()
                                    val hash = EncryptionManager.hashPassword(newPassword, salt)
                                    userDao.saveUser(UserCredsEntity(
                                        id = 1,
                                        passwordHash = hash,
                                        salt = android.util.Base64.encodeToString(salt, android.util.Base64.NO_WRAP)
                                    ))
                                }
                                Toast.makeText(context, "Password set!", Toast.LENGTH_SHORT).show()
                                onLoginSuccess(newPassword)
                            } catch (e: Exception) {
                                logError(Err.E004, "Failed to save new password", e)
                                error = "${Err.E004}: ${e.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Set Password & Continue") }
            } else {
                Text("Unlock Notes", style = MaterialTheme.typography.h5, color = MaterialTheme.colors.onBackground)
                Spacer(modifier = Modifier.height(24.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (error.isNotEmpty()) {
                    Text(error, color = MaterialTheme.colors.error, style = MaterialTheme.typography.body2)
                    Spacer(modifier = Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        error = ""
                        scope.launch {
                            try {
                                val match = withContext(Dispatchers.IO) {
                                    val user = userDao.getUser()
                                    if (user != null) {
                                        val salt = android.util.Base64.decode(user.salt, android.util.Base64.NO_WRAP)
                                        EncryptionManager.hashPassword(password, salt) == user.passwordHash
                                    } else false
                                }
                                if (match) onLoginSuccess(password)
                                else error = "Incorrect password"
                            } catch (e: Exception) {
                                logError(Err.E005, "Password verification failed", e)
                                error = "${Err.E005}: ${e.message}"
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Unlock") }
            }
        }
    }
}

// ═══════════════════════════════════════════════
// SETTINGS SCREEN
// ═══════════════════════════════════════════════
@Composable
fun SettingsScreen(
    repository: NotesRepository,
    password: String,
    currentVersion: String = "1.2.63",
    onBack: () -> Unit,
    onImportFile: () -> Unit = {},
    pendingImportJson: String? = null,
    onClearPendingImport: () -> Unit = {},
    isDarkTheme: Boolean = true,
    onThemeToggle: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var statusMessage by remember { mutableStateOf("") }
    var isExporting by remember { mutableStateOf(false) }

    // Import state
    var showImportConfirm by remember { mutableStateOf(false) }
    var importJson by remember { mutableStateOf(pendingImportJson) }

    // Update check state
    var updateInfo by remember { mutableStateOf<UpdateCheckResult?>(null) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    // Theme
    var darkMode by remember { mutableStateOf(isDarkTheme) }

    // Import confirmation dialog
    if (showImportConfirm && importJson != null) {
        AlertDialog(
            onDismissRequest = { showImportConfirm = false },
            title = { Text("Import Backup?") },
            text = { Text("This will merge notes from the backup file. Existing notes with the same title will be preserved.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { repository.importNotesFromJson(importJson!!, password) }
                            Toast.makeText(context, "Import complete!", Toast.LENGTH_SHORT).show()
                            onClearPendingImport()
                        } catch (e: Exception) {
                            statusMessage = "${Err.E011}: ${e.message}"
                        }
                    }
                    showImportConfirm = false
                }) { Text("Import") }
            },
            dismissButton = {
                TextButton(onClick = { showImportConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBack) {
                        Text("← Back", color = MaterialTheme.colors.onPrimary)
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ── Backup Section ──
            Text("Backup & Restore", style = MaterialTheme.typography.h6)

            Button(
                onClick = {
                    isExporting = true
                    statusMessage = ""
                    scope.launch {
                        try {
                            val json = withContext(Dispatchers.IO) { repository.exportNotesToJson(password) }
                            val fileName = "jnet-notes-backup-${java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.getDefault()).format(java.util.Date())}.json"
                            val file = java.io.File(context.cacheDir, fileName)
                            file.writeText(json)
                            val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                            val shareIntent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "application/json"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Export notes to..."))
                            statusMessage = "Export ready"
                        } catch (e: Exception) {
                            logError(Err.E010, "Export failed", e)
                            statusMessage = "${Err.E010}: ${e.message}"
                        }
                        isExporting = false
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                enabled = !isExporting
            ) {
                if (isExporting) CircularProgressIndicator(color = MaterialTheme.colors.onPrimary, modifier = Modifier.size(20.dp))
                else Text("📤 Export Encrypted Backup")
            }

            OutlinedButton(
                onClick = {
                    showImportConfirm = true
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) { Text("📥 Import Encrypted Backup") }

            if (statusMessage.isNotEmpty()) {
                Text(statusMessage, color = if (statusMessage.startsWith("E")) MaterialTheme.colors.error else MaterialTheme.colors.primary)
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // ── Theme Section ──
            Text("Appearance", style = MaterialTheme.typography.h6)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dark Theme", style = MaterialTheme.typography.body1)
                Switch(
                    checked = darkMode,
                    onCheckedChange = { checked ->
                        darkMode = checked
                        onThemeToggle(checked)
                    }
                )
            }

            Divider(modifier = Modifier.padding(vertical = 8.dp))

            // ── About Section ──
            Text("About", style = MaterialTheme.typography.h6)

            Card(modifier = Modifier.fillMaxWidth(), elevation = 1.dp) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Secure Notes", style = MaterialTheme.typography.subtitle1, fontWeight = FontWeight.Bold)
                        Text("v$currentVersion", style = MaterialTheme.typography.subtitle1, color = MaterialTheme.colors.primary)
                    }
                    Text("©️ J~Net 2026", style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f))
                    Text("jnetai.com", style = MaterialTheme.typography.body2, color = MaterialTheme.colors.primary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("GitHub Release:", style = MaterialTheme.typography.caption, color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f))
                            if (updateInfo != null) {
                                Text(
                                    updateInfo!!.latestVersion.ifEmpty { "checking..." },
                                    style = MaterialTheme.typography.body2,
                                    color = if (updateInfo!!.hasUpdate) MaterialTheme.colors.error else MaterialTheme.colors.onSurface
                                )
                            } else {
                                Text("tap Check for Updates", style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f))
                            }
                        }
                        if (checkingUpdate) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        } else {
                            TextButton(onClick = {
                                checkingUpdate = true
                                scope.launch {
                                    updateInfo = checkForUpdate(currentVersion)
                                    checkingUpdate = false
                                    if (updateInfo!!.hasUpdate) showUpdateDialog = true
                                    else if (updateInfo!!.error.isNotEmpty()) Toast.makeText(context, updateInfo!!.error, Toast.LENGTH_SHORT).show()
                                    else Toast.makeText(context, "You're up to date! v$currentVersion", Toast.LENGTH_SHORT).show()
                                }
                            }) { Text("Check Updates", fontSize = 12.sp) }
                        }
                    }
                }
            }

            if (showUpdateDialog && updateInfo != null) {
                AlertDialog(
                    onDismissRequest = { showUpdateDialog = false },
                    title = { Text("📥 Update Available!") },
                    text = {
                        Column {
                            Text("A new version is available:")
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Current: v$currentVersion", style = MaterialTheme.typography.body2)
                            Text("Latest: ${updateInfo!!.latestVersion}", style = MaterialTheme.typography.body2, color = MaterialTheme.colors.primary)
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showUpdateDialog = false
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL))
                            context.startActivity(intent)
                        }) { Text("Open GitHub") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showUpdateDialog = false }) { Text("Later") }
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            OutlinedButton(
                onClick = {
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "J~Net Secure Notes App")
                        putExtra(Intent.EXTRA_TEXT, "Check out J~Net Secure Notes v$currentVersion — encrypted notes app for Android\n$GITHUB_RELEASES_URL")
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share J~Net Notes via..."))
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) { Text("📤 Share App") }

            OutlinedButton(
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(GITHUB_RELEASES_URL))
                    context.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
            ) { Text("🌐 GitHub Releases") }
        }
    }
}
