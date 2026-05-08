package com.jnet.notes.repository

import com.jnet.notes.data.local.*
import com.jnet.notes.data.remote.*
import com.jnet.notes.security.EncryptionManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Base64

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

    fun getAllNotes(): List<NoteEntity> = noteDao.getAllNotes()

    fun deleteNote(note: NoteEntity) = noteDao.deleteNote(note)

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

    /**
     * Export: decrypt notes with current password, store plaintext.
     * This makes cross-device import work with a different password.
     */
    fun exportNotesToJson(password: String): String {
        val notes = noteDao.getAllNotes()
        val exportData = notes.map { note ->
            try {
                val decrypted = getDecryptedNote(note, password)
                mapOf(
                    "title" to note.title,
                    "content" to decrypted,
                    "timestamp" to note.timestamp
                )
            } catch (e: Exception) {
                mapOf(
                    "title" to note.title,
                    "content" to note.encryptedContent,
                    "timestamp" to note.timestamp,
                    "encrypted" to true
                )
            }
        }
        return Gson().toJson(exportData)
    }

    /**
     * Import: handle both plaintext and legacy encrypted exports.
     * Re-encrypt with current device password + salt.
     */
    fun importNotesFromJson(json: String, password: String) {
        val type = object : TypeToken<List<Map<String, Any>>>() {}.type
        val importedNotes: List<Map<String, Any>> = Gson().fromJson(json, type)
        val user = userDao.getUser() ?: return

        importedNotes.forEach { data ->
            val title = data["title"] as? String ?: "Imported Note"
            val contentRaw = data["content"] as? String ?: ""
            val timestamp = (data["timestamp"] as? Double)?.toLong() ?: System.currentTimeMillis()
            val wasEncrypted = data["encrypted"] as? Boolean ?: false

            val plainContent = if (wasEncrypted) {
                try {
                    val salt = Base64.getDecoder().decode(user.salt)
                    EncryptionManager.decrypt(contentRaw, password, salt)
                } catch (e: Exception) {
                    contentRaw
                }
            } else {
                contentRaw
            }

            val salt = Base64.getDecoder().decode(user.salt)
            val reEncrypted = EncryptionManager.encrypt(plainContent, password, salt)

            val note = NoteEntity(
                title = title,
                encryptedContent = reEncrypted,
                timestamp = timestamp,
                syncStatus = 0
            )
            noteDao.insertNote(note)
        }
    }

    // --- Sync Logic ---
    fun syncWithRemote(password: String) {
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
            val salt = Base64.getDecoder().decode(user.salt)
            val encrypted = EncryptionManager.encrypt(dto.content, password, salt)
            noteDao.insertNote(NoteEntity(title = dto.title, encryptedContent = encrypted, timestamp = dto.timestamp, syncStatus = 1))
        }
    }
}
