package com.jnet.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
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

class MainActivity : ComponentActivity() {
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
                        onBack = { currentScreen = "list" }
                    )
                }
            }
        }
    }
}