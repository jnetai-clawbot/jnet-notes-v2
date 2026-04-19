package com.jnet.notes.ui

import android.widget.Toast
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
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

private const val TAG = "JNetNotes"

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

@Composable
fun NoteListScreen(
    repository: NotesRepository, 
    onNoteClick: (Int) -> Unit, 
    onAddNote: () -> Unit,
    onLogout: () -> Unit
) {
    var notes by remember { mutableStateOf(listOf<NoteEntity>()) }
    var loadError by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        try {
            notes = withContext(Dispatchers.IO) { repository.getAllNotes() }
        } catch (e: Exception) {
            logError(Err.E006, "Failed to load notes", e)
            loadError = "${Err.E006}: ${e.message}"
        }
    }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("J~Net Notes") },
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
        if (loadError.isNotEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
                Text("Error loading notes", style = MaterialTheme.typography.h6, color = MaterialTheme.colors.error)
                Text(loadError, style = MaterialTheme.typography.body2, color = MaterialTheme.colors.error)
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { loadError = "" }) { Text("Dismiss") }
            }
        } else if (notes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("No notes yet", style = MaterialTheme.typography.h6, color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Tap + to create one", style = MaterialTheme.typography.body2, color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f))
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
                items(notes) { note ->
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

@Composable
fun NoteEditScreen(
    repository: NotesRepository,
    noteId: Int?,
    onSave: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf("") }
    var content by remember { mutableStateOf("") }
    var originalTitle by remember { mutableStateOf("") }
    var originalContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(noteId != null) }
    var error by remember { mutableStateOf("") }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    // Load existing note if editing
    if (noteId != null && isLoading) {
        LaunchedEffect(noteId) {
            try {
                val notes = withContext(Dispatchers.IO) { repository.getAllNotes() }
                val note = notes.find { it.id == noteId }
                if (note != null) {
                    title = note.title
                    content = note.encryptedContent // Will be decrypted later when password is integrated
                    originalTitle = note.title
                    originalContent = note.encryptedContent
                }
            } catch (e: Exception) {
                logError(Err.E006, "Failed to load note", e)
                error = "${Err.E006}: ${e.message}"
            }
            isLoading = false
        }
    }
    
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
                                if (note != null) repository.deleteNote(note)
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
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (noteId == null) "New Note" else "Edit Note") },
                navigationIcon = {
                    TextButton(onClick = onCancel) {
                        Text("Cancel", color = MaterialTheme.colors.onPrimary)
                    }
                },
                actions = {
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
                
                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    label = { Text("Note content") },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    maxLines = Int.MAX_VALUE
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    // Save button
                    Button(
                        onClick = {
                            if (title.isBlank()) {
                                error = "Title cannot be empty"
                                return@Button
                            }
                            scope.launch {
                                try {
                                    withContext(Dispatchers.IO) {
                                        repository.saveNote(title, content, "local")
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
                    ) {
                        Text("Save")
                    }
                    
                    // Revert button (only when editing)
                    if (noteId != null) {
                        OutlinedButton(
                            onClick = {
                                title = originalTitle
                                content = originalContent
                                error = ""
                            },
                            modifier = Modifier.weight(1f).padding(horizontal = 4.dp)
                        ) {
                            Text("Revert")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LoginScreen(userDao: UserDao, onLoginSuccess: () -> Unit) {
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
                val existing = userDao.getUser()
                isSetup = (existing == null)
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
    
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (isSetup) {
            Text("First Time Setup", style = MaterialTheme.typography.h5)
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                "Default password: 12345678",
                style = MaterialTheme.typography.h6,
                color = MaterialTheme.colors.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "You must set your own password to continue",
                style = MaterialTheme.typography.body2,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
            )
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
                    if (password != "12345678") {
                        error = "Default password is incorrect"
                        return@Button
                    }
                    if (newPassword.isEmpty()) {
                        error = "New password cannot be empty"
                        return@Button
                    }
                    if (newPassword != confirmPassword) {
                        error = "Passwords do not match"
                        return@Button
                    }
                    
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
                            onLoginSuccess()
                        } catch (e: Exception) {
                            logError(Err.E004, "Failed to save new password", e)
                            error = "${Err.E004}: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set Password & Continue")
            }
        } else {
            Text("Unlock Notes", style = MaterialTheme.typography.h5)
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
                                    val hash = EncryptionManager.hashPassword(password, salt)
                                    hash == user.passwordHash
                                } else false
                            }
                            if (match) {
                                onLoginSuccess()
                            } else {
                                error = "Incorrect password"
                            }
                        } catch (e: Exception) {
                            logError(Err.E005, "Password verification failed", e)
                            error = "${Err.E005}: ${e.message}"
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Unlock")
            }
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