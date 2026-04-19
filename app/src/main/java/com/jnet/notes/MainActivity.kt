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
        
        // Initialize Database
        val db = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "notes-db"
        ).build()

        // Initialize Retrofit API
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.jnetai.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        val api = retrofit.create(RemoteNotesApi::class.java)

        // Initialize Repository
        val repository = NotesRepository(db.noteDao(), db.userDao(), api)
        
        setContent {
            var isDark by remember { mutableStateOf(true) }
            var unlocked by remember { mutableStateOf(false) }
            
            JNetNotesTheme(darkTheme = isDark) {
                if (!unlocked) {
                    LoginScreen(
                        userDao = db.userDao(),
                        onLoginSuccess = { unlocked = true }
                    )
                } else {
                    NoteListScreen(
                        repository = repository,
                        onNoteClick = { id -> /* navigate to editor */ },
                        onAddNote = { /* navigate to add */ }
                    )
                }
            }
        }
    }
}