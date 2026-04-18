package com.jnet.notes.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jnet.notes.repository.NotesRepository
import com.jnet.notes.data.local.NoteEntity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue

@Composable
fun NoteListScreen(repository: NotesRepository, onNoteClick: (Int) -> Unit, onAddNote: () -> Unit) {
    var notes by remember { mutableStateOf(repository.getAllNotes()) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("J~Net Notes") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddNote) {
                Text("+")
            }
        }
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            items(notes) { note ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    onClick = { onNoteClick(note.id) }
                ) {
                    Text(text = note.title, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
fun LoginScreen(onLoginSuccess: (String) -> Unit) {
    var password by remember { mutableStateOf("") }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Enter Password", style = MaterialTheme.typography.h5)
        TextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Password") },
            modifier = Modifier.padding(16.dp)
        )
        Button(onClick = { onLoginSuccess(password) }) {
            Text("Unlock Notes")
        }
    }
}

@Composable
fun SettingsScreen(
    currentTheme: Boolean, 
    onThemeToggle: (Boolean) -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.h5)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Dark Theme")
            Spacer(modifier = Modifier.weight(1f))
            Switch(checked = currentTheme, onCheckedChange = onThemeToggle)
        }
        
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Export Local Backup (JSON)")
        }
        
        Button(onClick = onImport, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
            Text("Import Backup File")
        }
    }
}
