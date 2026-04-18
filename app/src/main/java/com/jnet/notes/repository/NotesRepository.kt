package com.jnet.notes.repository

import com.jnet.notes.data.local.*
import com.jnet.notes.data.remote.*
import com.jnet.notes.security.EncryptionManager
import java.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class NotesRepository(
    private val noteDao: NoteDao,
    private val userDao: UserDao,
    private val api: RemoteNotesApi
) {
    private var sessionToken: String? = null

    // --- Security Logic ---
    fun setLocalPassword(password: String): Boolean {
        val salt = EncryptionManager.generateSalt()
        val hash = EncryptionManager.hashPassword(password, salt)
        userDao.saveUser(UserCredsEntity(passwordHash = hash, salt = Base64.getEncoder().encodeToString(salt)))
        return true
    }

    fun verifyPassword(password: String): Boolean {
        val user = userDao.getUser() ?: return false
        val salt = Base64.getDecoder().decode(user.salt)
        return EncryptionManager.hashPassword(password, salt) == user.passwordHash
    }

    // --- Note Management ---
    fun saveNote(title: String, content: String, password: String) {
        val user = userDao.getUser() ?: return
        val salt = Base64.getDecoder().decode(user.salt)
        val encrypted = EncryptionManager.encrypt(content, password, salt)
        
        val note = NoteEntity(
            title = title,
            encryptedContent = encrypted,
            timestamp = System.currentTimeMillis(),
            syncStatus = 0
        )
        noteDao.insertNote(note)
    }

    fun getDecryptedNote(note: NoteEntity, password: String): String {
        val user = userDao.getUser() ?: return "Error: No User"
        val salt = Base64.getDecoder().decode(user.salt)
        return EncryptionManager.decrypt(note.encryptedContent, password, salt)
    }

    // --- Export/Import Logic ---
    fun exportNotesToJson(): String {
        val notes = noteDao.getAllNotes()
        // Map to a simple list of JSON-compatible objects
        val exportData = notes.map { 
            mapOf(
                "title" to it.title, 
                "content" to it.encryptedContent, 
                "timestamp" to it.timestamp
            )
        }
        return Gson().toJson(exportData)
    }

    fun importNotesFromJson(json: String, password: String) {
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val importedNotes: List<Map<String, Any>> = Gson().fromJson(json, type)
        
        importedNotes.forEach { data ->
            val note = NoteEntity(
                title = data["title"] as String,
                encryptedContent = data["content"] as String,
                timestamp = (data["timestamp"] as Double).toLong(),
                syncStatus = 0
            )
            noteDao.insertNote(note)
        }
    }
    fun syncNotes(password: String) {
        // 1. Auth with JNetAI
        // Note: API usually requires username. Assuming stored or passed.
        // This is a simplified flow for the logic:
        val authResponse = api.authenticate(username = "user", password = password).body()
        sessionToken = authResponse?.token ?: return

        // 2. Push Local Changes
        val localNotes = noteDao.getAllNotes().filter { it.syncStatus != 1 }
        localNotes.forEach { note ->
            val decryptedContent = getDecryptedNote(note, password)
            val resp = api.uploadNote(token = sessionToken!!, title = note.title, content = decryptedContent)
            if (resp.isSuccessful) noteDao.updateSyncStatus(note.id, 1)
        }

        // 3. Pull Remote
        val remoteNotes = api.fetchNotes(token = sessionToken!!).body()
        remoteNotes?.forEach { dto ->
            val user = userDao.getUser() ?: return@forEach
            val salt = Base64.getDecoder().decode(user.salt)
            val encrypted = EncryptionManager.encrypt(dto.content, password, salt)
            noteDao.insertNote(NoteEntity(title = dto.title, encryptedContent = encrypted, timestamp = dto.timestamp, syncStatus = 1))
        }
    }
}
