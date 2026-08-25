package com.thermalguardian.app.data.repository

import com.thermalguardian.app.data.db.SessionDao
import com.thermalguardian.app.data.db.SessionEntity
import com.thermalguardian.app.data.model.SessionSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class SessionRepository(private val sessionDao: SessionDao) {

    val allSessionsFlow: Flow<List<SessionSummary>> = sessionDao.getAllSessionsFlow().map { list ->
        list.map { it.toSessionSummary() }
    }

    suspend fun saveSession(summary: SessionSummary): Long = withContext(Dispatchers.IO) {
        val entity = SessionEntity.fromSessionSummary(summary)
        sessionDao.insertSession(entity)
    }

    suspend fun getSessionById(id: Long): SessionSummary? = withContext(Dispatchers.IO) {
        sessionDao.getSessionById(id)?.toSessionSummary()
    }

    suspend fun getLatestSession(): SessionSummary? = withContext(Dispatchers.IO) {
        sessionDao.getLatestSession()?.toSessionSummary()
    }

    suspend fun deleteSession(id: Long) = withContext(Dispatchers.IO) {
        sessionDao.deleteSessionById(id)
    }

    suspend fun clearHistory() = withContext(Dispatchers.IO) {
        sessionDao.clearAllSessions()
    }
}
