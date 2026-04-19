package com.jnet.notes.ui

import android.widget.Toast
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
    ) { paddingValues ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(paddingValues).padding(16.dp)) {
            items(notes) { note ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp).clickable { onNoteClick(note.id) }
                ) {
                    Text(text = note.title, modifier = Modifier.padding(16.dp))
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
    
    // Check if user already exists on first load
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val existing = userDao.getUser()
            isSetup = (existing == null)
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
            // First-time setup - default password is 12345678, user must set their own
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
                    
                    // Save new password
                    scope.launch {
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
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Set Password & Continue")
            }
        } else {
            // Returning user - just enter password
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
