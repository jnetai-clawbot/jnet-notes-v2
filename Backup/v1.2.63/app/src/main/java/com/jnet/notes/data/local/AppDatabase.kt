package com.jnet.notes.data.local

import androidx.room.*

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val encryptedContent: String, // AES encrypted
    val timestamp: Long,
    val syncStatus: Int = 0 // 0: local only, 1: synced, 2: modified locally
)

@Entity(tableName = "user_creds")
data class UserCredsEntity(
    @PrimaryKey val id: Int = 1, // Single user
    val passwordHash: String,
    val salt: String // Base64 salt used for hashing
)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY timestamp DESC")
    suspend fun getAllNotes(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Delete
    suspend fun deleteNote(note: NoteEntity)

    @Query("UPDATE notes SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, status: Int)
}

@Dao
interface UserDao {
    @Query("SELECT * FROM user_creds WHERE id = 1")
    suspend fun getUser(): UserCredsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveUser(user: UserCredsEntity)
}

@Database(entities = [NoteEntity::class, UserCredsEntity::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    abstract fun noteDao(): NoteDao
    abstract fun userDao(): UserDao
}
