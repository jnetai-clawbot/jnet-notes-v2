package com.jnet.notes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import com.jnet.notes.ui.theme.JNetNotesTheme
import com.jnet.notes.ui.*
import com.jnet.notes.repository.NotesRepository

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Repository (Simplified for demo, usually use Hilt/Koin)
        val repository = NotesRepository(/* inject deps */)
        
        setContent {
            var isDark by remember { mutableStateOf(true) }
            var unlockedPassword by remember { mutableStateOf<String?>(null) }
            
            JNetNotesTheme(darkTheme = isDark) {
                if (unlockedPassword == null) {
                    LoginScreen(onLoginSuccess = { password ->
                        unlockedPassword = password
                    })
                } else {
                    // Navigation Logic here (e.g., Compose Navigation)
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
