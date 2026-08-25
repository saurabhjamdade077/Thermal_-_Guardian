package com.thermalguardian.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: SessionEntity): Long

    @Query("SELECT * FROM gaming_sessions ORDER BY startTimeMs DESC")
    fun getAllSessionsFlow(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM gaming_sessions ORDER BY startTimeMs DESC")
    suspend fun getAllSessions(): List<SessionEntity>

    @Query("SELECT * FROM gaming_sessions WHERE id = :id LIMIT 1")
    suspend fun getSessionById(id: Long): SessionEntity?

    @Query("SELECT * FROM gaming_sessions ORDER BY startTimeMs DESC LIMIT 1")
    suspend fun getLatestSession(): SessionEntity?

    @Query("DELETE FROM gaming_sessions WHERE id = :id")
    suspend fun deleteSessionById(id: Long)

    @Query("DELETE FROM gaming_sessions")
    suspend fun clearAllSessions()
}
