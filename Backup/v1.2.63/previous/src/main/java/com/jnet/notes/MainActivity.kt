package com.jnet.notes

import android.os.Bundle
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.room.Room
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import com.jnet.notes.ui.theme.JNetNotesTheme
import com.jnet.notes.ui.*
import com.jnet.notes.repository.NotesRepository
import com.jnet.notes.data.local.AppDatabase
import com.jnet.notes.data.remote.RemoteNotesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var pendingImportUri by mutableStateOf<Uri?>(null)
    
    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { pendingImportUri = it }
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "notes-db"
        ).build()

        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.jnetai.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(RemoteNotesApi::class.java)
        val repository = NotesRepository(db.noteDao(), db.userDao(), api)
        
        setContent {
            var currentScreen by remember { mutableStateOf("login") }
            var editingNoteId by remember { mutableStateOf<Int?>(null) }
            var sessionPassword by remember { mutableStateOf("") }
            var importUri by remember { mutableStateOf<Uri?>(null) }
            var importTrigger by remember { mutableStateOf(0) }
            
            // Process import when URI changes
            val context = this@MainActivity
            LaunchedEffect(importTrigger) {
                if (importUri != null) {
                    try {
                        val json = withContext(Dispatchers.IO) {
                            context.contentResolver.openInputStream(importUri!!)?.bufferedReader()?.readText()
                                ?: throw Exception("Could not read file")
                        }
                        withContext(Dispatchers.IO) {
                            repository.importNotesFromJson(json, sessionPassword)
                        }
                        Toast.makeText(context, "Import complete!", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(context, "Import failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                    importUri = null
                }
            }
            
            JNetNotesTheme(darkTheme = true) {
                when (currentScreen) {
                    "login" -> LoginScreen(
                        userDao = db.userDao(),
                        onLoginSuccess = { password ->
                            sessionPassword = password
                            currentScreen = "list"
                        }
                    )
                    "list" -> NoteListScreen(
                        repository = repository,
                        password = sessionPassword,
                        onNoteClick = { id -> 
                            editingNoteId = id
                            currentScreen = "edit"
                        },
                        onAddNote = { 
                            editingNoteId = null
                            currentScreen = "edit"
                        },
                        onLogout = { 
                            sessionPassword = ""
                            currentScreen = "login"
                        },
                        onSettings = { currentScreen = "settings" }
                    )
                    "edit" -> NoteEditScreen(
                        repository = repository,
                        password = sessionPassword,
                        noteId = editingNoteId,
                        onSave = { currentScreen = "list" },
                        onCancel = { currentScreen = "list" }
                    )
                    "settings" -> SettingsScreen(
                        repository = repository,
                        password = sessionPassword,
                        onBack = { currentScreen = "list" },
                        onImportFile = {
                            importLauncher.launch("application/json")
                        }
                    )
                }
                
                // Watch for pending import URI from the activity result
                val pending = pendingImportUri
                if (pending != null) {
                    importUri = pending
                    importTrigger++
                    pendingImportUri = null
                }
            }
        }
    }
}