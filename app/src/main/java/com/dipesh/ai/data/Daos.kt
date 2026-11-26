package com.dipesh.ai.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun get(id: Long): ConversationEntity?

    @Insert
    suspend fun insert(c: ConversationEntity): Long

    @Query("UPDATE conversations SET title = :title, updatedAt = :now WHERE id = :id")
    suspend fun update(id: Long, title: String, now: Long)

    @Query("UPDATE conversations SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: Long, now: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM conversations")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM conversations")
    fun observeCount(): Flow<Int>
}

@Dao
interface MessageDao {
    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY id ASC")
    fun observe(id: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE conversationId = :id ORDER BY id ASC")
    suspend fun list(id: Long): List<MessageEntity>

    @Insert
    suspend fun insert(m: MessageEntity): Long

    @Query("UPDATE messages SET text = :text, isError = :isError WHERE id = :id")
    suspend fun update(id: Long, text: String, isError: Boolean)

    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM messages")
    fun observeCount(): Flow<Int>

    @Query("SELECT createdAt FROM messages WHERE role = 'user' AND createdAt >= :since")
    fun observeUserTimes(since: Long): Flow<List<Long>>
}

/** Facts, notes and routines. Plain (non-suspend) methods: call from a background thread. */
@Dao
interface MemoryDao {
    @Query("SELECT * FROM facts ORDER BY id DESC")
    fun observeFacts(): Flow<List<FactEntity>>

    @Query("SELECT text FROM facts ORDER BY id ASC")
    fun factTexts(): List<String>

    @Query("SELECT COUNT(*) FROM facts WHERE text = :t")
    fun countFact(t: String): Int

    @Insert
    fun insertFact(f: FactEntity)

    @Query("DELETE FROM facts WHERE id = :id")
    fun deleteFact(id: Long)

    @Query("DELETE FROM facts")
    fun clearFacts()

    @Query("SELECT * FROM notes ORDER BY id DESC")
    fun observeNotes(): Flow<List<NoteEntity>>

    @Query("SELECT text FROM notes ORDER BY id ASC")
    fun noteTexts(): List<String>

    @Insert
    fun insertNote(n: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    fun deleteNote(id: Long)

    @Query("DELETE FROM notes")
    fun clearNotes()

    @Query("SELECT * FROM routines ORDER BY name ASC")
    fun routines(): List<RoutineEntity>

    @Query("SELECT * FROM routines ORDER BY name ASC")
    fun observeRoutines(): Flow<List<RoutineEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun saveRoutine(r: RoutineEntity)

    @Query("DELETE FROM routines WHERE name = :name")
    fun deleteRoutine(name: String)
}
