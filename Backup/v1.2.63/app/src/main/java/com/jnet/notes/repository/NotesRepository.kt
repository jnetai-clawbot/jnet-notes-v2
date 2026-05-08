package com.jnet.notes.repository

import android.util.Log
import com.jnet.notes.data.local.*
import com.jnet.notes.data.remote.*
import com.jnet.notes.security.EncryptionManager
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

private const val TAG = "JNetNotes"

class NotesRepository(
    private val noteDao: NoteDao,
    private val userDao: UserDao,
    private val api: RemoteNotesApi
) {
    private var sessionToken: String? = null

    // --- Note Management ---
    suspend fun getAllNotes(): List<NoteEntity> {
        return try {
            noteDao.getAllNotes()
        } catch (e: Exception) {
            Log.e(TAG, "E006: Failed to load notes - ${e.message}", e)
            emptyList()
        }
    }

    suspend fun saveNote(title: String, content: String, password: String): NoteEntity {
        val user = userDao.getUser() ?: throw Exception("E002: No user found")
        val salt = Base64.decode(user.salt, Base64.NO_WRAP)
        val encrypted = EncryptionManager.encrypt(content, password, salt)
        
        val note = NoteEntity(
            title = title,
            encryptedContent = encrypted,
            timestamp = System.currentTimeMillis(),
            syncStatus = 0
        )
        noteDao.insertNote(note)
        return note
    }

    suspend fun deleteNote(note: NoteEntity) {
        noteDao.deleteNote(note)
    }

    suspend fun getDecryptedNote(note: NoteEntity, password: String): String {
        return try {
            val user = userDao.getUser() ?: return "E002: No user found"
            val salt = Base64.decode(user.salt, Base64.NO_WRAP)
            EncryptionManager.decrypt(note.encryptedContent, password, salt)
        } catch (e: Exception) {
            Log.e(TAG, "E009: Decryption failed - ${e.message}", e)
            "E009: Decryption failed - ${e.message}"
        }
    }

    // --- Export/Import Logic ---
    suspend fun exportNotesToJson(): String {
        return try {
            val user = userDao.getUser() ?: throw Exception("E002: No user found")
            val notes = noteDao.getAllNotes()
            val exportData = mapOf(
                "version" to 2,
                "salt" to user.salt,
                "notes" to notes.map { 
                    mapOf(
                        "title" to it.title, 
                        "content" to it.encryptedContent, 
                        "timestamp" to it.timestamp
                    )
                }
            )
            Gson().toJson(exportData)
        } catch (e: Exception) {
            Log.e(TAG, "E010: Export failed - ${e.message}", e)
            "[]"
        }
    }

    suspend fun importNotesFromJson(json: String, password: String) {
        try {
            val user = userDao.getUser() ?: throw Exception("E002: No user found")
            val localSalt = Base64.decode(user.salt, Base64.NO_WRAP)
            val type = object : TypeToken<Map<String, Any>>() {}.type
            val data: Map<String, Any> = Gson().fromJson(json, type)
            
            // Version 2 format: includes salt, decrypt with source salt then re-encrypt with local salt
            val sourceSaltBase64 = data["salt"] as? String
            val notesList = (data["notes"] as? List<Map<String, Any>>)
                ?: (data["notes"] as? List<*>)?.filterIsInstance<Map<String, Any>>()
                ?: emptyList()
            
            // If we have a source salt (v2 format), decrypt with it then re-encrypt with local salt
            // If no salt (v1 format), content was already stored with local salt — just insert as-is
            val needsReEncrypt = sourceSaltBase64 != null
            val sourceSalt = sourceSaltBase64?.let { Base64.decode(it, Base64.NO_WRAP) }
            
            notesList.forEach { noteData ->
                val title = noteData["title"] as String
                val content = noteData["content"] as String
                val timestamp = when (val ts = noteData["timestamp"]) {
                    is Double -> ts.toLong()
                    is Number -> ts.toLong()
                    else -> System.currentTimeMillis()
                }
                
                val encryptedContent = if (needsReEncrypt && sourceSalt != null) {
                    // Decrypt with source phone's salt, then re-encrypt with local phone's salt
                    try {
                        val plaintext = EncryptionManager.decrypt(content, password, sourceSalt)
                        EncryptionManager.encrypt(plaintext, password, localSalt)
                    } catch (e: Exception) {
                        Log.e(TAG, "E009: Re-encryption failed for note '$title' - ${e.message}", e)
                        // Try inserting as-is (might be v1 format on same phone)
                        content
                    }
                } else {
                    content
                }
                
                val note = NoteEntity(
                    title = title,
                    encryptedContent = encryptedContent,
                    timestamp = timestamp,
                    syncStatus = 0
                )
                noteDao.insertNote(note)
            }
        } catch (e: Exception) {
            Log.e(TAG, "E011: Import failed - ${e.message}", e)
            throw Exception("E011: Import failed - ${e.message}")
        }
    }

    suspend fun syncNotes(password: String) {
        try {
            val authResponse = api.authenticate(username = "user", password = password).body()
            sessionToken = authResponse?.token ?: throw Exception("E012: Auth failed - no token")

            val localNotes = noteDao.getAllNotes().filter { it.syncStatus != 1 }
            localNotes.forEach { note ->
                try {
                    val decryptedContent = getDecryptedNote(note, password)
                    val resp = api.uploadNote(token = sessionToken!!, title = note.title, content = decryptedContent)
                    if (resp.isSuccessful) noteDao.updateSyncStatus(note.id, 1)
                } catch (e: Exception) {
                    Log.e(TAG, "E013: Upload failed for note ${note.id} - ${e.message}", e)
                }
            }

            val remoteNotes = api.fetchNotes(token = sessionToken!!).body()
            remoteNotes?.forEach { dto ->
                try {
                    val user = userDao.getUser() ?: return@forEach
                    val salt = Base64.decode(user.salt, Base64.NO_WRAP)
                    val encrypted = EncryptionManager.encrypt(dto.content, password, salt)
                    noteDao.insertNote(NoteEntity(title = dto.title, encryptedContent = encrypted, timestamp = dto.timestamp, syncStatus = 1))
                } catch (e: Exception) {
                    Log.e(TAG, "E014: Pull failed for remote note - ${e.message}", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "E012: Sync auth failed - ${e.message}", e)
            throw Exception("E012: Sync failed - ${e.message}")
        }
    }
}