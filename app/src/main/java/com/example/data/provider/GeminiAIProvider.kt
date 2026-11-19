package com.example.data.provider

import android.util.Log
import com.example.core.AIProvider
import com.example.core.AiScheduleProposal
import com.example.core.AiWeeklyReport
import com.example.core.ProviderHealth
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

private const val TAG = "GeminiAIProvider"
private const val MODEL_DEFAULT = "gemini-3.8-flash"
private const val MODEL_LITE = "gemini-3.5-flash-lite"

class GeminiAIProvider : AIProvider {

    override val providerName: String = "Google Gemini (Firebase AI Logic)"

    private val chatModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_DEFAULT,
            systemInstruction = content {
                text(
                    "You are Baddie, a calm, intelligent, practical personal productivity assistant. " +
                        "You help the user manage their daily routine, unpredictable teacher/tuition schedules, " +
                        "study plans, tasks, habits, and goals. Base every recommendation strictly on the " +
                        "provided user data. Never invent fake patterns or fake historical records. Keep responses concise, structured, and actionable."
                )
            }
        )
    }

    private val fastSummaryModel by lazy {
        Firebase.ai.generativeModel(modelName = MODEL_LITE)
    }

    private val proposalSchema = Schema.obj(
        properties = mapOf(
            "actionType" to Schema.enumeration(
                values = listOf("RESCHEDULE_TUITION", "CREATE_STUDY_PLAN", "CREATE_TASK", "NONE"),
                description = "Detected schedule or task action"
            ),
            "summary" to Schema.string(description = "Human-readable explanation of proposed change and conflict check"),
            "teacherName" to Schema.string(description = "Matching teacher name if applicable"),
            "targetDate" to Schema.string(description = "Target ISO date YYYY-MM-DD if applicable"),
            "targetTime" to Schema.string(description = "Target time HH:mm if applicable"),
            "subject" to Schema.string(description = "Subject name if applicable"),
            "proposedTaskTitles" to Schema.array(
                items = Schema.string(),
                description = "Individual tasks to add if creating a study plan or task"
            ),
            "conflictWarning" to Schema.string(description = "Any scheduling overlap or conflict detected, empty if none"),
            "requiresApproval" to Schema.boolean(description = "True when user approval is recommended before modifying saved data")
        ),
        optionalProperties = listOf("teacherName", "targetDate", "targetTime", "subject", "proposedTaskTitles", "conflictWarning")
    )

    private val proposalModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_DEFAULT,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = proposalSchema
            }
        )
    }

    private val weeklyReportSchema = Schema.obj(
        properties = mapOf(
            "whatWentWell" to Schema.string(),
            "whatWasMissed" to Schema.string(),
            "mostProductiveDays" to Schema.string(),
            "weakSubjects" to Schema.string(),
            "behaviourPatterns" to Schema.string(),
            "recommendations" to Schema.string()
        )
    )

    private val weeklyReportModel by lazy {
        Firebase.ai.generativeModel(
            modelName = MODEL_DEFAULT,
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = weeklyReportSchema
            }
        )
    }

    override suspend fun generate(prompt: String): Result<String> {
        return try {
            val response = withTimeout(60_000L) {
                chatModel.generateContent(prompt)
            }
            val text = response.text?.trim()
            if (text.isNullOrBlank()) {
                Result.failure(IllegalStateException("Gemini returned an empty response."))
            } else {
                Result.success(text)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini generate timed out", e)
            Result.failure(Exception("AI request timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Gemini generate failed", e)
            Result.failure(e)
        }
    }

    override fun stream(prompt: String, systemContext: String): Flow<String> = flow {
        val fullPrompt = if (systemContext.isBlank()) prompt else "$systemContext\n\nUser message: $prompt"
        val accumulated = StringBuilder()
        withTimeout(60_000L) {
            chatModel.generateContentStream(fullPrompt).collect { chunk ->
                val part = chunk.text.orEmpty()
                if (part.isNotEmpty()) {
                    accumulated.append(part)
                    emit(accumulated.toString())
                }
            }
        }
        if (accumulated.isBlank()) {
            throw IllegalStateException("Gemini stream completed with empty text.")
        }
    }

    override suspend fun analyzeRoutine(contextSnapshot: String): Result<String> {
        val prompt = """
            Analyze the user's actual routine, tasks, tuition schedule, and study logs below.
            Give one concise, practical daily recommendation (max 3 sentences). If data is empty or sparse, acknowledge that and suggest a simple starting step without inventing statistics.
            
            DATA:
            $contextSnapshot
        """.trimIndent()
        return try {
            val response = withTimeout(45_000L) {
                fastSummaryModel.generateContent(prompt)
            }
            val text = response.text?.trim()
            if (text.isNullOrBlank()) Result.failure(IllegalStateException("Empty analysis")) else Result.success(text)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "analyzeRoutine failed", e)
            Result.failure(e)
        }
    }

    override suspend fun suggestSchedule(
        userRequest: String,
        contextSnapshot: String
    ): Result<AiScheduleProposal> {
        val prompt = """
            Interpret the user's natural language request against their real saved schedule below.
            - If they mention a teacher coming on a different day/time (e.g., "My math teacher is coming tomorrow instead"), set actionType=RESCHEDULE_TUITION, match teacherName/subject, compute targetDate (YYYY-MM-DD) & targetTime (HH:mm), and check conflictWarning against existing sessions/routine blocks.
            - If they ask to finish chapters or create a study plan, set actionType=CREATE_STUDY_PLAN and list concrete individual tasks in proposedTaskTitles.
            - If they want a single task created, set actionType=CREATE_TASK.
            - Otherwise set actionType=NONE and explain in summary.
            
            CURRENT SCHEDULE & DATA:
            $contextSnapshot
            
            USER REQUEST:
            $userRequest
        """.trimIndent()
        return try {
            val response = withTimeout(60_000L) {
                proposalModel.generateContent(prompt)
            }
            val jsonStr = response.text ?: return Result.failure(IllegalStateException("Empty JSON proposal"))
            val obj = JSONObject(jsonStr)
            val tasksArr = obj.optJSONArray("proposedTaskTitles")
            val titles = if (tasksArr != null) {
                (0 until tasksArr.length()).mapNotNull { idx -> tasksArr.optString(idx).takeIf { it.isNotBlank() } }
            } else emptyList()
            Result.success(
                AiScheduleProposal(
                    actionType = obj.optString("actionType", "NONE"),
                    summary = obj.optString("summary", ""),
                    teacherName = obj.optString("teacherName", ""),
                    targetDate = obj.optString("targetDate", ""),
                    targetTime = obj.optString("targetTime", ""),
                    subject = obj.optString("subject", ""),
                    proposedTaskTitles = titles,
                    conflictWarning = obj.optString("conflictWarning", ""),
                    requiresApproval = obj.optBoolean("requiresApproval", true)
                )
            )
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("AI planning timed out after 60 seconds."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "suggestSchedule failed", e)
            Result.failure(e)
        }
    }

    override suspend fun summarizeDay(contextSnapshot: String): Result<String> {
        val prompt = """
            Summarize this journal & daily activity context in 2-3 calm, reflective sentences without inventing any facts:
            $contextSnapshot
        """.trimIndent()
        return generate(prompt)
    }

    override suspend fun generateWeeklyReport(contextSnapshot: String): Result<AiWeeklyReport> {
        val prompt = """
            Generate an honest weekly productivity report strictly from the real data below.
            Do NOT fabricate numbers or patterns if there is insufficient history; state clearly where data is still building.
            
            DATA:
            $contextSnapshot
        """.trimIndent()
        return try {
            val response = withTimeout(60_000L) {
                weeklyReportModel.generateContent(prompt)
            }
            val jsonStr = response.text ?: return Result.failure(IllegalStateException("Empty weekly report JSON"))
            val obj = JSONObject(jsonStr)
            Result.success(
                AiWeeklyReport(
                    whatWentWell = obj.optString("whatWentWell", "No completed items logged yet."),
                    whatWasMissed = obj.optString("whatWasMissed", "No missed items recorded."),
                    mostProductiveDays = obj.optString("mostProductiveDays", "Insufficient data yet."),
                    weakSubjects = obj.optString("weakSubjects", "Insufficient data yet."),
                    behaviourPatterns = obj.optString("behaviourPatterns", "Insufficient history to infer patterns."),
                    recommendations = obj.optString("recommendations", "Keep logging daily tasks and study sessions.")
                )
            )
        } catch (e: TimeoutCancellationException) {
            Result.failure(Exception("Weekly report generation timed out."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "generateWeeklyReport failed", e)
            Result.failure(e)
        }
    }

    override suspend fun healthCheck(): ProviderHealth {
        val start = System.currentTimeMillis()
        return try {
            val res = withTimeout(20_000L) { fastSummaryModel.generateContent("Reply with OK") }
            val latency = System.currentTimeMillis() - start
            val ok = !res.text.isNullOrBlank()
            ProviderHealth(providerName, ok, latency, if (ok) "Ready (${latency}ms)" else "Empty reply")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            ProviderHealth(providerName, false, latency, e.localizedMessage ?: "Attestation/Network error")
        }
    }
}
