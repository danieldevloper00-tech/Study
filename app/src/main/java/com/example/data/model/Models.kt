package com.example.data.model

import androidx.annotation.Keep
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

inline fun <reified T : Enum<T>> safeEnum(raw: String?, default: T): T {
    if (raw.isNullOrBlank()) return default
    return enumValues<T>().firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: default
}

enum class TaskPriority(val label: String) {
    LOW("Low"),
    MEDIUM("Medium"),
    HIGH("High"),
    URGENT("Urgent")
}

enum class TaskCategory(val label: String) {
    STUDY("Study"),
    TUITION("Tuition"),
    HOMEWORK("Homework"),
    PERSONAL("Personal"),
    HEALTH("Health"),
    ROUTINE("Routine"),
    GOALS("Goals"),
    OTHER("Other")
}

enum class TaskStatus(val label: String) {
    PENDING("Pending"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled")
}

enum class TaskRecurrence(val label: String) {
    NONE("One-time"),
    DAILY("Daily"),
    WEEKLY("Weekly"),
    CUSTOM("Custom")
}

enum class RoutineBlockType(val label: String) {
    WAKE_UP("Wake-up"),
    MORNING_PREP("Morning Prep"),
    STUDY("Study"),
    TUITION("Tuition"),
    MEAL("Meal"),
    BREAK("Break"),
    EXERCISE("Exercise"),
    PERSONAL("Personal"),
    SLEEP("Sleep")
}

enum class TuitionStatus(val label: String) {
    SCHEDULED("Scheduled"),
    CONFIRMED("Confirmed"),
    RESCHEDULED("Rescheduled"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    TEACHER_ABSENT("Teacher Absent"),
    STUDENT_ABSENT("Student Absent")
}

enum class ChapterStatus(val label: String) {
    NOT_STARTED("Not started"),
    LEARNING("Learning"),
    PRACTICING("Practicing"),
    COMPLETED("Completed"),
    NEEDS_REVISION("Needs revision")
}

enum class GoalTimeframe(val label: String) {
    DAILY("Daily"),
    WEEKLY("Weekly"),
    MONTHLY("Monthly"),
    LONG_TERM("Long-term")
}

enum class AiMode(val label: String, val subtitle: String) {
    OBSERVER("Observer", "Analyzes & learns quietly without making changes"),
    ADVISOR("Advisor", "Suggests adjustments and asks approval before saving"),
    AUTOPILOT("Autopilot", "Applies low-risk schedule tweaks; confirms deletions")
}

@Keep
@IgnoreExtraProperties
data class SubtaskItem(
    val title: String = "",
    val completed: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "title" to title,
        "completed" to completed
    )
}

@Keep
@IgnoreExtraProperties
data class ChapterItem(
    val title: String = "",
    val status: String = ChapterStatus.NOT_STARTED.name,
    val progressPercent: Int = 0,
    val notes: String = ""
) {
    fun toMap(): Map<String, Any> = mapOf(
        "title" to title,
        "status" to status,
        "progressPercent" to progressPercent.coerceIn(0, 100),
        "notes" to notes
    )
}

@Keep
@IgnoreExtraProperties
data class MilestoneItem(
    val title: String = "",
    val completed: Boolean = false
) {
    fun toMap(): Map<String, Any> = mapOf(
        "title" to title,
        "completed" to completed
    )
}

@Keep
@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val timezone: String = "Asia/Dhaka",
    val weekStartDay: String = "SATURDAY",
    val themeMode: String = "DARK",
    val accentPreset: String = "LAVENDER",
    val compactLayout: Boolean = false,
    val aiMode: String = AiMode.ADVISOR.name,
    val dailyStudyTargetMinutes: Int = 180,
    val onboardingComplete: Boolean = false,
    val widgetOrder: List<String> = DEFAULT_WIDGET_ORDER,
    val hiddenWidgets: List<String> = emptyList(),
    val telegramEnabled: Boolean = false,
    val telegramChatId: String = "",
    val telegramPrefs: List<String> = listOf("MORNING_BRIEFING", "UPCOMING_TUITION", "TASK_DEADLINES"),
    val uptimeEnabled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "displayName" to displayName.take(80).ifBlank { "Student" },
        "email" to email.take(120),
        "timezone" to timezone.take(60).ifBlank { "Asia/Dhaka" },
        "weekStartDay" to weekStartDay,
        "themeMode" to themeMode,
        "accentPreset" to accentPreset,
        "compactLayout" to compactLayout,
        "aiMode" to aiMode,
        "dailyStudyTargetMinutes" to dailyStudyTargetMinutes.coerceIn(0, 1440),
        "onboardingComplete" to onboardingComplete,
        "widgetOrder" to widgetOrder.take(20),
        "hiddenWidgets" to hiddenWidgets.take(20),
        "telegramEnabled" to telegramEnabled,
        "telegramChatId" to telegramChatId.take(64),
        "telegramPrefs" to telegramPrefs.take(20),
        "uptimeEnabled" to uptimeEnabled,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )

    companion object {
        val DEFAULT_WIDGET_ORDER = listOf(
            "PROGRESS",
            "NEXT_UP",
            "AI_RECOMMENDATION",
            "STUDY_TARGET",
            "HABITS_STREAK",
            "SLEEP_WELLNESS",
            "WEEKLY_OVERVIEW"
        )
    }
}

@Keep
@IgnoreExtraProperties
data class BaddieTask(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val category: String = TaskCategory.STUDY.name,
    val subject: String = "",
    val priority: String = TaskPriority.MEDIUM.name,
    val status: String = TaskStatus.PENDING.name,
    val completed: Boolean = false,
    val dueDate: String = "",
    val startTime: String = "",
    val estimatedMinutes: Int = 30,
    val actualMinutes: Int = 0,
    val progressPercent: Int = 0,
    val recurrence: String = TaskRecurrence.NONE.name,
    val tags: List<String> = emptyList(),
    val notes: String = "",
    val reminderMinutesBefore: Int = 15,
    val goalId: String = "",
    val sortOrder: Int = 0,
    val subtasks: List<SubtaskItem> = emptyList(),
    val completedAtMillis: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "title" to title.trim().take(200),
        "description" to description.take(2000),
        "category" to safeEnum(category, TaskCategory.OTHER).name,
        "subject" to subject.take(80),
        "priority" to safeEnum(priority, TaskPriority.MEDIUM).name,
        "status" to safeEnum(status, TaskStatus.PENDING).name,
        "completed" to completed,
        "dueDate" to dueDate.take(20),
        "startTime" to startTime.take(10),
        "estimatedMinutes" to estimatedMinutes.coerceIn(0, 1440),
        "actualMinutes" to actualMinutes.coerceIn(0, 1440),
        "progressPercent" to progressPercent.coerceIn(0, 100),
        "recurrence" to safeEnum(recurrence, TaskRecurrence.NONE).name,
        "tags" to tags.take(15),
        "notes" to notes.take(2000),
        "reminderMinutesBefore" to reminderMinutesBefore.coerceIn(-1, 1440),
        "goalId" to goalId.take(128),
        "sortOrder" to sortOrder.coerceIn(0, 1000000),
        "subtasks" to subtasks.take(30).map { it.toMap() },
        "completedAtMillis" to completedAtMillis.coerceAtLeast(0L),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class RoutineBlock(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val blockType: String = RoutineBlockType.STUDY.name,
    val startTime: String = "07:00",
    val endTime: String = "08:00",
    val durationMinutes: Int = 60,
    val repeatDays: List<String> = listOf("SAT", "SUN", "MON", "TUE", "WED", "THU", "FRI"),
    val reminderEnabled: Boolean = true,
    val notes: String = "",
    val sortOrder: Int = 0,
    val lastCompletedDate: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "title" to title.trim().take(120),
        "blockType" to safeEnum(blockType, RoutineBlockType.PERSONAL).name,
        "startTime" to startTime.take(10),
        "endTime" to endTime.take(10),
        "durationMinutes" to durationMinutes.coerceIn(1, 1440),
        "repeatDays" to repeatDays.take(7),
        "reminderEnabled" to reminderEnabled,
        "notes" to notes.take(500),
        "sortOrder" to sortOrder.coerceIn(0, 100000),
        "lastCompletedDate" to lastCompletedDate.take(20),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class TeacherProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val subject: String = "",
    val defaultWeekdays: List<String> = emptyList(),
    val flexibleWeekly: Boolean = false,
    val targetSessionsPerWeek: Int = 3,
    val preferredTime: String = "16:00",
    val expectedDurationMinutes: Int = 90,
    val contactInfo: String = "",
    val notes: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.trim().take(120),
        "subject" to subject.trim().take(80),
        "defaultWeekdays" to defaultWeekdays.take(7),
        "flexibleWeekly" to flexibleWeekly,
        "targetSessionsPerWeek" to targetSessionsPerWeek.coerceIn(0, 14),
        "preferredTime" to preferredTime.take(10),
        "expectedDurationMinutes" to expectedDurationMinutes.coerceIn(15, 480),
        "contactInfo" to contactInfo.take(120),
        "notes" to notes.take(1000),
        "active" to active,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class TuitionSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val teacherId: String = "",
    val teacherName: String = "",
    val subject: String = "",
    val sessionDate: String = "",
    val startTime: String = "16:00",
    val durationMinutes: Int = 90,
    val status: String = TuitionStatus.SCHEDULED.name,
    val originalDate: String = "",
    val originalTime: String = "",
    val rescheduledFromId: String = "",
    val extraSession: Boolean = false,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "teacherId" to teacherId.take(128),
        "teacherName" to teacherName.take(120),
        "subject" to subject.trim().take(80),
        "sessionDate" to sessionDate.take(20),
        "startTime" to startTime.take(10),
        "durationMinutes" to durationMinutes.coerceIn(15, 480),
        "status" to safeEnum(status, TuitionStatus.SCHEDULED).name,
        "originalDate" to originalDate.take(20),
        "originalTime" to originalTime.take(10),
        "rescheduledFromId" to rescheduledFromId.take(128),
        "extraSession" to extraSession,
        "notes" to notes.take(1000),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class StudySubject(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val colorHex: String = "#C4B5FD",
    val confidenceLevel: Int = 3,
    val totalStudyMinutes: Int = 0,
    val pendingTopics: String = "",
    val examPrepNotes: String = "",
    val chapters: List<ChapterItem> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "name" to name.trim().take(80),
        "colorHex" to colorHex.take(16),
        "confidenceLevel" to confidenceLevel.coerceIn(1, 5),
        "totalStudyMinutes" to totalStudyMinutes.coerceAtLeast(0),
        "pendingTopics" to pendingTopics.take(1000),
        "examPrepNotes" to examPrepNotes.take(1000),
        "chapters" to chapters.take(60).map { it.toMap() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class StudySession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val subjectId: String = "",
    val subjectName: String = "",
    val sessionDate: String = "",
    val durationMinutes: Int = 25,
    val mode: String = "POMODORO",
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "subjectId" to subjectId.take(128),
        "subjectName" to subjectName.trim().take(80),
        "sessionDate" to sessionDate.take(20),
        "durationMinutes" to durationMinutes.coerceIn(1, 720),
        "mode" to if (mode in listOf("POMODORO", "CUSTOM", "MANUAL")) mode else "CUSTOM",
        "notes" to notes.take(500),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Habit(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val frequency: String = "DAILY",
    val targetDaysPerWeek: Int = 7,
    val reminderTime: String = "",
    val completedDates: List<String> = emptyList(),
    val notes: String = "",
    val active: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "title" to title.trim().take(120),
        "frequency" to if (frequency in listOf("DAILY", "WEEKDAYS", "CUSTOM")) frequency else "DAILY",
        "targetDaysPerWeek" to targetDaysPerWeek.coerceIn(1, 7),
        "reminderTime" to reminderTime.take(10),
        "completedDates" to completedDates.take(400),
        "notes" to notes.take(500),
        "active" to active,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class Goal(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val description: String = "",
    val timeframe: String = GoalTimeframe.WEEKLY.name,
    val priority: String = TaskPriority.MEDIUM.name,
    val deadline: String = "",
    val progressPercent: Int = 0,
    val autoCalculateFromTasks: Boolean = true,
    val completed: Boolean = false,
    val milestones: List<MilestoneItem> = emptyList(),
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "title" to title.trim().take(160),
        "description" to description.take(1000),
        "timeframe" to safeEnum(timeframe, GoalTimeframe.WEEKLY).name,
        "priority" to safeEnum(priority, TaskPriority.MEDIUM).name,
        "deadline" to deadline.take(20),
        "progressPercent" to progressPercent.coerceIn(0, 100),
        "autoCalculateFromTasks" to autoCalculateFromTasks,
        "completed" to completed,
        "milestones" to milestones.take(30).map { it.toMap() },
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class JournalEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val entryDate: String = "",
    val mood: String = "Calm",
    val energyLevel: Int = 3,
    val content: String = "",
    val wins: String = "",
    val difficulties: String = "",
    val aiSummary: String = "",
    val aiSummaryEnabled: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "entryDate" to entryDate.take(20),
        "mood" to mood.take(40).ifBlank { "Calm" },
        "energyLevel" to energyLevel.coerceIn(1, 5),
        "content" to content.take(5000),
        "wins" to wins.take(1000),
        "difficulties" to difficulties.take(1000),
        "aiSummary" to aiSummary.take(2000),
        "aiSummaryEnabled" to aiSummaryEnabled,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class SleepLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val logDate: String = "",
    val sleepTime: String = "23:30",
    val wakeTime: String = "06:30",
    val durationMinutes: Int = 420,
    val energyLevel: Int = 3,
    val mood: String = "Rested",
    val exerciseMinutes: Int = 0,
    val notes: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "logDate" to logDate.take(20),
        "sleepTime" to sleepTime.take(10),
        "wakeTime" to wakeTime.take(10),
        "durationMinutes" to durationMinutes.coerceIn(0, 1440),
        "energyLevel" to energyLevel.coerceIn(1, 5),
        "mood" to mood.take(40),
        "exerciseMinutes" to exerciseMinutes.coerceIn(0, 600),
        "notes" to notes.take(1000),
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class ReminderItem(
    @DocumentId val id: String = "",
    val userId: String = "",
    val title: String = "",
    val triggerDateTimeIso: String = "",
    val timezone: String = "Asia/Dhaka",
    val offsetMinutes: Int = 15,
    val repeatRule: String = "NONE",
    val sourceType: String = "CUSTOM",
    val sourceId: String = "",
    val enabled: Boolean = true,
    val delivered: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "title" to title.trim().take(160),
        "triggerDateTimeIso" to triggerDateTimeIso.take(40),
        "timezone" to timezone.take(60),
        "offsetMinutes" to offsetMinutes.coerceIn(0, 1440),
        "repeatRule" to if (repeatRule in listOf("NONE", "DAILY", "WEEKLY")) repeatRule else "NONE",
        "sourceType" to if (sourceType in listOf("TASK", "ROUTINE", "TUITION", "HABIT", "SLEEP", "CUSTOM")) sourceType else "CUSTOM",
        "sourceId" to sourceId.take(128),
        "enabled" to enabled,
        "delivered" to delivered,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@Keep
@IgnoreExtraProperties
data class AiActionLog(
    @DocumentId val id: String = "",
    val userId: String = "",
    val mode: String = AiMode.ADVISOR.name,
    val actionType: String = "",
    val summary: String = "",
    val approved: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(uid: String): Map<String, Any> = mapOf(
        "userId" to uid,
        "mode" to safeEnum(mode, AiMode.ADVISOR).name,
        "actionType" to actionType.take(60),
        "summary" to summary.take(500),
        "approved" to approved,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

/**
 * Computed strictly from real historical logs; never invents patterns when data is sparse.
 */
data class BehaviourProfile(
    val hasSufficientData: Boolean,
    val typicalWakeTime: String?,
    val typicalSleepTime: String?,
    val averageStudyMinutesPerDay: Int?,
    val taskCompletionRatePercent: Int?,
    val mostProductiveWeekday: String?,
    val habitConsistencyPercent: Int?,
    val tuitionAttendanceRatePercent: Int?,
    val notes: String
)

data class ChatMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val proposal: com.example.core.AiScheduleProposal? = null,
    val proposalApplied: Boolean = false
)
