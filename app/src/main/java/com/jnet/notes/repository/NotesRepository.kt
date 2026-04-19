package com.jnet.notes.repository

import com.jnet.notes.data.local.*
import com.jnet.notes.data.remote.*
import com.jnet.notes.security.EncryptionManager
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class NotesRepository(
    private val noteDao: NoteDao,
    private val userDao: UserDao,
    private val api: RemoteNotesApi
) {
    private var sessionToken: String? = null

    // --- Note Management (suspend for Room) ---
    suspend fun getAllNotes(): List<NoteEntity> {
        return noteDao.getAllNotes()
    }

    suspend fun saveNote(title: String, content: String, password: String) {
        val user = userDao.getUser() ?: return
        val salt = Base64.decode(user.salt, Base64.NO_WRAP)
        val encrypted = EncryptionManager.encrypt(content, password, salt)
        
        val note = NoteEntity(
            title = title,
            encryptedContent = encrypted,
            timestamp = System.currentTimeMillis(),
            syncStatus = 0
        )
        noteDao.insertNote(note)
    }

    suspend fun getDecryptedNote(note: NoteEntity, password: String): String {
        val user = userDao.getUser() ?: return "Error: No User"
        val salt = Base64.decode(user.salt, Base64.NO_WRAP)
        return EncryptionManager.decrypt(note.encryptedContent, password, salt)
    }

    // --- Export/Import Logic ---
    suspend fun exportNotesToJson(): String {
        val notes = noteDao.getAllNotes()
        val exportData = notes.map { 
            mapOf(
                "title" to it.title, 
                "content" to it.encryptedContent, 
                "timestamp" to it.timestamp
            )
        }
        return Gson().toJson(exportData)
    }

    suspend fun importNotesFromJson(json: String, password: String) {
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

    suspend fun syncNotes(password: String) {
        val authResponse = api.authenticate(username = "user", password = password).body()
        sessionToken = authResponse?.token ?: return

        val localNotes = noteDao.getAllNotes().filter { it.syncStatus != 1 }
        localNotes.forEach { note ->
            val decryptedContent = getDecryptedNote(note, password)
            val resp = api.uploadNote(token = sessionToken!!, title = note.title, content = decryptedContent)
            if (resp.isSuccessful) noteDao.updateSyncStatus(note.id, 1)
        }

        val remoteNotes = api.fetchNotes(token = sessionToken!!).body()
        remoteNotes?.forEach { dto ->
            val user = userDao.getUser() ?: return@forEach
            val salt = Base64.decode(user.salt, Base64.NO_WRAP)
            val encrypted = EncryptionManager.encrypt(dto.content, password, salt)
            noteDao.insertNote(NoteEntity(title = dto.title, encryptedContent = encrypted, timestamp = dto.timestamp, syncStatus = 1))
        }
    }
}