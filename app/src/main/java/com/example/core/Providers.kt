package com.example.core

import kotlinx.coroutines.flow.Flow

/**
 * Modular provider interfaces decoupling Baddie from specific database, AI,
 * notification, and monitoring backends.
 */

data class ProviderHealth(
    val providerName: String,
    val healthy: Boolean,
    val latencyMs: Long,
    val statusDetail: String
)

interface DatabaseProvider {
    val providerName: String
    suspend fun initialize(): Result<Unit>
    fun currentUserId(): String?
    suspend fun <T> get(collectionPath: String, documentId: String, type: Class<T>): Result<T?>
    suspend fun create(collectionPath: String, documentId: String?, payload: Map<String, Any>): Result<String>
    suspend fun update(collectionPath: String, documentId: String, updates: Map<String, Any>): Result<Unit>
    suspend fun delete(collectionPath: String, documentId: String): Result<Unit>
    fun <T> observeCollection(collectionPath: String, type: Class<T>): Flow<List<T>>
    suspend fun healthCheck(): ProviderHealth
}

data class AiScheduleProposal(
    val actionType: String, // "RESCHEDULE_TUITION", "CREATE_STUDY_PLAN", "CREATE_TASK", "NONE"
    val summary: String,
    val teacherName: String = "",
    val targetDate: String = "",
    val targetTime: String = "",
    val subject: String = "",
    val proposedTaskTitles: List<String> = emptyList(),
    val conflictWarning: String = "",
    val requiresApproval: Boolean = true
)

data class AiWeeklyReport(
    val whatWentWell: String,
    val whatWasMissed: String,
    val mostProductiveDays: String,
    val weakSubjects: String,
    val behaviourPatterns: String,
    val recommendations: String
)

interface AIProvider {
    val providerName: String
    suspend fun generate(prompt: String): Result<String>
    fun stream(prompt: String, systemContext: String = ""): Flow<String>
    suspend fun analyzeRoutine(contextSnapshot: String): Result<String>
    suspend fun suggestSchedule(userRequest: String, contextSnapshot: String): Result<AiScheduleProposal>
    suspend fun summarizeDay(contextSnapshot: String): Result<String>
    suspend fun generateWeeklyReport(contextSnapshot: String): Result<AiWeeklyReport>
    suspend fun healthCheck(): ProviderHealth
}

interface NotificationProvider {
    val providerName: String
    suspend fun send(recipient: String, title: String, message: String): Result<Unit>
    suspend fun schedule(id: String, triggerAtMillis: Long, title: String, message: String): Result<Unit>
    suspend fun cancel(id: String): Result<Unit>
    suspend fun testConnection(): Result<String>
}

data class UptimeMonitorItem(
    val id: String,
    val friendlyName: String,
    val url: String,
    val statusCode: Int, // 2 = Up, 8 = Seems down, 9 = Down, 0 = Paused
    val statusLabel: String,
    val uptimeRatio: String,
    val averageResponseMs: Int,
    val lastIncidentSummary: String
)

sealed interface UptimeStatusResult {
    data object AwaitingCredentials : UptimeStatusResult
    data class Live(val monitors: List<UptimeMonitorItem>, val checkedAtEpochMillis: Long) : UptimeStatusResult
    data class Error(val message: String) : UptimeStatusResult
}

interface UptimeMonitoringProvider {
    val providerName: String
    suspend fun fetchMonitors(): UptimeStatusResult
}
