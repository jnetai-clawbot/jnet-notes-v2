package com.jnet.notes

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import com.jnet.notes.ui.theme.JNetNotesTheme
import com.jnet.notes.ui.*
import com.jnet.notes.repository.NotesRepository
import com.jnet.notes.data.local.AppDatabase
import com.jnet.notes.data.remote.createApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var database: AppDatabase
    private lateinit var repository: NotesRepository

    private val importFileLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        uri?.let { onImportFileUri(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        database = AppDatabase.getInstance(this)
        repository = NotesRepository(
            noteDao = database.noteDao(),
            userDao = database.userDao(),
            api = createApi()
        )

        setContent {
            var isDark by remember { mutableStateOf(true) }
            var unlockedPassword by remember { mutableStateOf<String?>(null) }
            var currentScreen by remember { mutableStateOf("login") }
            var editingNoteId by remember { mutableStateOf<Int?>(null) }
            var pendingImportJson by remember { mutableStateOf<String?>(null) }

            JNetNotesTheme(darkTheme = isDark) {
                when (currentScreen) {
                    "login" -> {
                        LoginScreen(
                            userDao = database.userDao(),
                            onLoginSuccess = { password ->
                                unlockedPassword = password
                                currentScreen = "list"
                            }
                        )
                    }
                    "list" -> {
                        NoteListScreen(
                            repository = repository,
                            password = unlockedPassword!!,
                            onNoteClick = { id ->
                                editingNoteId = id
                                currentScreen = "editor"
                            },
                            onAddNote = {
                                editingNoteId = null
                                currentScreen = "editor"
                            },
                            onLogout = {
                                unlockedPassword = null
                                currentScreen = "login"
                            },
                            onSettings = {
                                currentScreen = "settings"
                            }
                        )
                    }
                    "editor" -> {
                        NoteEditScreen(
                            repository = repository,
                            password = unlockedPassword!!,
                            noteId = editingNoteId,
                            onSave = { currentScreen = "list" },
                            onCancel = { currentScreen = "list" }
                        )
                    }
                    "settings" -> {
                        SettingsScreen(
                            repository = repository,
                            password = unlockedPassword!!,
                            onBack = { currentScreen = "list" },
                            onImportFile = { importFileLauncher.launch("*/*") },
                            pendingImportJson = pendingImportJson,
                            onClearPendingImport = { pendingImportJson = null },
                            isDarkTheme = isDark,
                            onThemeToggle = { isDark = it }
                        )
                    }
                }
            }
        }
    }

    private fun onImportFileUri(uri: Uri) {
        try {
            val inputStream = contentResolver.openInputStream(uri)
            val json = inputStream?.bufferedReader()?.use { it.readText() } ?: return
            Toast.makeText(this, "Import file selected. Go to Settings to complete import.", Toast.LENGTH_LONG).show()
            // The import will be handled in SettingsScreen with the password available
        } catch (e: Exception) {
            Toast.makeText(this, "Import failed: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}
