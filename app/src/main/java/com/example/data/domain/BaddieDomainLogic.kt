package com.example.data.domain

import com.example.data.model.*
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object BaddieDomainLogic {

    fun safeZoneId(timezone: String): ZoneId =
        runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.of("Asia/Dhaka") }

    fun nowInZone(timezone: String): ZonedDateTime = ZonedDateTime.now(safeZoneId(timezone))

    fun todayIso(timezone: String): String = nowInZone(timezone).toLocalDate().toString()

    fun greetingForHour(hour: Int): String = when (hour) {
        in 5..11 -> "Good morning"
        in 12..16 -> "Good afternoon"
        in 17..21 -> "Good evening"
        else -> "Late night focus"
    }

    fun dayOfWeekShort(day: DayOfWeek): String = when (day) {
        DayOfWeek.SATURDAY -> "SAT"
        DayOfWeek.SUNDAY -> "SUN"
        DayOfWeek.MONDAY -> "MON"
        DayOfWeek.TUESDAY -> "TUE"
        DayOfWeek.WEDNESDAY -> "WED"
        DayOfWeek.THURSDAY -> "THU"
        DayOfWeek.FRIDAY -> "FRI"
    }

    fun parseMinutesOrNull(hhmm: String): Int? {
        val parts = hhmm.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return h * 60 + m
    }

    fun formatMinutesAsTime(totalMinutes: Int): String {
        val wrapped = ((totalMinutes % 1440) + 1440) % 1440
        val h = wrapped / 60
        val m = wrapped % 60
        return String.format(Locale.US, "%02d:%02d", h, m)
    }

    fun formatDisplayTime(hhmm: String): String {
        val mins = parseMinutesOrNull(hhmm) ?: return hhmm
        val local = LocalTime.of(mins / 60, mins % 60)
        return local.format(DateTimeFormatter.ofPattern("h:mm a", Locale.US))
    }

    fun computeDurationMinutes(startTime: String, endTime: String): Int {
        val s = parseMinutesOrNull(startTime) ?: return 60
        val e = parseMinutesOrNull(endTime) ?: return 60
        return if (e > s) e - s else (1440 - s + e).coerceAtLeast(15)
    }

    fun detectRoutineConflicts(
        startTime: String,
        endTime: String,
        repeatDays: List<String>,
        existingBlocks: List<RoutineBlock>,
        ignoreBlockId: String = ""
    ): List<String> {
        val start = parseMinutesOrNull(startTime) ?: return emptyList()
        val endRaw = parseMinutesOrNull(endTime) ?: return emptyList()
        val end = if (endRaw > start) endRaw else start + 60
        val conflicts = mutableListOf<String>()
        existingBlocks.filter { it.id != ignoreBlockId }.forEach { block ->
            val sharedDays = block.repeatDays.intersect(repeatDays.toSet())
            if (sharedDays.isNotEmpty()) {
                val bStart = parseMinutesOrNull(block.startTime) ?: return@forEach
                val bEndRaw = parseMinutesOrNull(block.endTime) ?: return@forEach
                val bEnd = if (bEndRaw > bStart) bEndRaw else bStart + block.durationMinutes
                if (start < bEnd && end > bStart) {
                    conflicts += "${block.title} (${block.startTime}–${block.endTime} on ${sharedDays.joinToString(",")})"
                }
            }
        }
        return conflicts
    }

    fun detectTuitionConflicts(
        dateIso: String,
        startTime: String,
        durationMinutes: Int,
        sessions: List<TuitionSession>,
        routineBlocks: List<RoutineBlock>,
        ignoreSessionId: String = ""
    ): List<String> {
        val start = parseMinutesOrNull(startTime) ?: return emptyList()
        val end = start + durationMinutes
        val conflicts = mutableListOf<String>()

        sessions.filter {
            it.id != ignoreSessionId &&
                it.sessionDate == dateIso &&
                it.status !in listOf(TuitionStatus.CANCELLED.name, TuitionStatus.RESCHEDULED.name)
        }.forEach { s ->
            val sStart = parseMinutesOrNull(s.startTime) ?: return@forEach
            val sEnd = sStart + s.durationMinutes
            if (start < sEnd && end > sStart) {
                conflicts += "Tuition: ${s.subject} (${s.startTime})"
            }
        }

        val parsedDate = runCatching { LocalDate.parse(dateIso) }.getOrNull()
        if (parsedDate != null) {
            val dayShort = dayOfWeekShort(parsedDate.dayOfWeek)
            routineBlocks.filter {
                dayShort in it.repeatDays && it.blockType != RoutineBlockType.TUITION.name
            }.forEach { b ->
                val bStart = parseMinutesOrNull(b.startTime) ?: return@forEach
                val bEnd = bStart + b.durationMinutes
                if (start < bEnd && end > bStart) {
                    conflicts += "Routine: ${b.title} (${b.startTime}–${b.endTime})"
                }
            }
        }
        return conflicts
    }

    fun computeHabitStreak(habit: Habit, today: LocalDate): Int {
        if (habit.completedDates.isEmpty()) return 0
        val dateSet = habit.completedDates.toSet()
        var streak = 0
        var cursor = if (today.toString() in dateSet) today else today.minusDays(1)
        while (cursor.toString() in dateSet) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun computeBehaviourProfile(
        tasks: List<BaddieTask>,
        studySessions: List<StudySession>,
        habits: List<Habit>,
        tuitionSessions: List<TuitionSession>,
        sleepLogs: List<SleepLog>
    ): BehaviourProfile {
        val totalRecords = tasks.size + studySessions.size + sleepLogs.size + tuitionSessions.size
        if (totalRecords < 3) {
            return BehaviourProfile(
                hasSufficientData = false,
                typicalWakeTime = null,
                typicalSleepTime = null,
                averageStudyMinutesPerDay = null,
                taskCompletionRatePercent = null,
                mostProductiveWeekday = null,
                habitConsistencyPercent = null,
                tuitionAttendanceRatePercent = null,
                notes = "Keep using Baddie for a few days so patterns are learned strictly from real logs."
            )
        }

        val avgWake = if (sleepLogs.size >= 2) {
            val mins = sleepLogs.mapNotNull { parseMinutesOrNull(it.wakeTime) }
            if (mins.isNotEmpty()) formatMinutesAsTime(mins.average().toInt()) else null
        } else null

        val avgSleep = if (sleepLogs.size >= 2) {
            val mins = sleepLogs.mapNotNull { parseMinutesOrNull(it.sleepTime) }
            if (mins.isNotEmpty()) formatMinutesAsTime(mins.average().toInt()) else null
        } else null

        val avgStudy = if (studySessions.isNotEmpty()) {
            val distinctDays = studySessions.map { it.sessionDate }.distinct().size.coerceAtLeast(1)
            studySessions.sumOf { it.durationMinutes } / distinctDays
        } else null

        val completionRate = if (tasks.isNotEmpty()) {
            (tasks.count { it.completed } * 100) / tasks.size
        } else null

        val productiveDay = tasks.filter { it.completed && it.dueDate.isNotBlank() }
            .mapNotNull { runCatching { LocalDate.parse(it.dueDate).dayOfWeek }.getOrNull() }
            .groupingBy { it }
            .eachCount()
            .maxByOrNull { it.value }
            ?.key?.name?.lowercase()?.replaceFirstChar { it.uppercase() }

        val habitConsistency = if (habits.isNotEmpty()) {
            val totalLogs = habits.sumOf { it.completedDates.size }
            ((totalLogs * 100) / (habits.size * 7)).coerceIn(0, 100)
        } else null

        val nonScheduledTuitions = tuitionSessions.filter {
            it.status in listOf(
                TuitionStatus.COMPLETED.name,
                TuitionStatus.TEACHER_ABSENT.name,
                TuitionStatus.STUDENT_ABSENT.name
            )
        }
        val attendanceRate = if (nonScheduledTuitions.isNotEmpty()) {
            (nonScheduledTuitions.count { it.status == TuitionStatus.COMPLETED.name } * 100) / nonScheduledTuitions.size
        } else null

        return BehaviourProfile(
            hasSufficientData = true,
            typicalWakeTime = avgWake,
            typicalSleepTime = avgSleep,
            averageStudyMinutesPerDay = avgStudy,
            taskCompletionRatePercent = completionRate,
            mostProductiveWeekday = productiveDay,
            habitConsistencyPercent = habitConsistency,
            tuitionAttendanceRatePercent = attendanceRate,
            notes = "Computed directly from ${tasks.size} tasks, ${studySessions.size} study sessions, and ${sleepLogs.size} sleep logs."
        )
    }

    fun buildContextSnapshot(
        timezone: String,
        tasks: List<BaddieTask>,
        routineBlocks: List<RoutineBlock>,
        teachers: List<TeacherProfile>,
        tuitionSessions: List<TuitionSession>,
        subjects: List<StudySubject>,
        habits: List<Habit>,
        goals: List<Goal>,
        studySessions: List<StudySession>,
        sleepLogs: List<SleepLog>
    ): String {
        val today = todayInZoneDate(timezone)
        val tomorrow = today.plusDays(1)
        val dayShort = dayOfWeekShort(today.dayOfWeek)
        val pendingTasks = tasks.filter { !it.completed && it.status != TaskStatus.CANCELLED.name }
        val todayTuitions = tuitionSessions.filter {
            it.sessionDate == today.toString() && it.status != TuitionStatus.CANCELLED.name
        }
        val upcomingTuitions = tuitionSessions.filter {
            it.sessionDate >= today.toString() && it.status in listOf(TuitionStatus.SCHEDULED.name, TuitionStatus.CONFIRMED.name)
        }.sortedBy { "${it.sessionDate} ${it.startTime}" }.take(8)
        val todayRoutine = routineBlocks.filter { dayShort in it.repeatDays }.sortedBy { it.startTime }

        return buildString {
            appendLine("Timezone: $timezone | Today: $today (${today.dayOfWeek}) | Tomorrow: $tomorrow (${tomorrow.dayOfWeek})")
            appendLine("Pending Tasks (${pendingTasks.size}): " + pendingTasks.take(12).joinToString("; ") {
                "[${it.priority}] ${it.title} (due=${it.dueDate.ifBlank { "none" }}, subj=${it.subject.ifBlank { it.category }})"
            })
            appendLine("Teachers (${teachers.size}): " + teachers.joinToString("; ") {
                "${it.name} (${it.subject}, days=${if (it.flexibleWeekly) "${it.targetSessionsPerWeek}x flexible" else it.defaultWeekdays.joinToString(",")}, pref=${it.preferredTime})"
            })
            appendLine("Today's Tuitions (${todayTuitions.size}): " + todayTuitions.joinToString("; ") {
                "${it.subject} w/ ${it.teacherName} at ${it.startTime} [${it.status}]"
            })
            appendLine("Upcoming Tuitions: " + upcomingTuitions.joinToString("; ") {
                "${it.sessionDate} ${it.startTime} ${it.subject} (${it.teacherName})"
            })
            appendLine("Today's Routine Blocks: " + todayRoutine.joinToString("; ") {
                "${it.startTime}-${it.endTime} ${it.title}"
            })
            appendLine("Subjects (${subjects.size}): " + subjects.joinToString("; ") {
                "${it.name} (${it.totalStudyMinutes}m logged, conf=${it.confidenceLevel}/5)"
            })
            appendLine("Recent Study (7d): ${studySessions.takeLast(10).sumOf { it.durationMinutes }} mins across ${studySessions.takeLast(10).size} sessions")
            appendLine("Active Habits (${habits.size}): " + habits.take(8).joinToString("; ") { it.title })
            appendLine("Goals (${goals.size}): " + goals.take(6).joinToString("; ") { "${it.title} (${it.progressPercent}%)" })
            appendLine("Sleep Logs (${sleepLogs.size}): " + sleepLogs.takeLast(3).joinToString("; ") { "${it.logDate}: ${it.durationMinutes}m" })
        }
    }

    fun todayInZoneDate(timezone: String): LocalDate = nowInZone(timezone).toLocalDate()

    fun buildMorningBriefingMessage(
        timezone: String,
        tasks: List<BaddieTask>,
        tuitionSessions: List<TuitionSession>,
        goals: List<Goal>
    ): String {
        val today = todayIso(timezone)
        val topTasks = tasks.filter { !it.completed && it.status != TaskStatus.CANCELLED.name }
            .sortedByDescending { priorityWeight(it.priority) }
            .take(3)
        val nextTuition = tuitionSessions
            .filter { it.sessionDate == today && it.status in listOf(TuitionStatus.SCHEDULED.name, TuitionStatus.CONFIRMED.name) }
            .minByOrNull { it.startTime }
        val dailyGoal = goals.firstOrNull { !it.completed }

        return buildString {
            appendLine("Good morning!")
            if (topTasks.isNotEmpty()) {
                appendLine("Your priorities today:")
                topTasks.forEach { appendLine("- ${it.title}") }
            } else {
                appendLine("No pending tasks yet for today.")
            }
            if (nextTuition != null) {
                appendLine("Next tuition: ${nextTuition.subject} at ${formatDisplayTime(nextTuition.startTime)}.")
            } else {
                appendLine("No scheduled tuition today.")
            }
            if (dailyGoal != null) {
                appendLine("Focus goal: ${dailyGoal.title} (${dailyGoal.progressPercent}%).")
            }
        }.trim()
    }

    fun answerTelegramCommand(
        command: String,
        timezone: String,
        tasks: List<BaddieTask>,
        tuitionSessions: List<TuitionSession>,
        routineBlocks: List<RoutineBlock>,
        goals: List<Goal>,
        studySessions: List<StudySession>
    ): String {
        val today = todayIso(timezone)
        val dayShort = dayOfWeekShort(todayInZoneDate(timezone).dayOfWeek)
        return when (command.lowercase()) {
            "/start", "/help" -> "Baddie Bot Commands:\n/today - Daily briefing\n/tasks - Pending tasks\n/schedule - Today's routine\n/tuition - Upcoming tuitions\n/goals - Active goals\n/study - Recent study minutes\n/report - Quick summary"
            "/today" -> buildMorningBriefingMessage(timezone, tasks, tuitionSessions, goals)
            "/tasks" -> {
                val pending = tasks.filter { !it.completed && it.status != TaskStatus.CANCELLED.name }.take(8)
                if (pending.isEmpty()) "All tasks completed!" else pending.joinToString("\n") { "- [${it.priority}] ${it.title}" }
            }
            "/schedule" -> {
                val blocks = routineBlocks.filter { dayShort in it.repeatDays }.sortedBy { it.startTime }
                if (blocks.isEmpty()) "No routine blocks set for $dayShort." else blocks.joinToString("\n") { "${formatDisplayTime(it.startTime)} - ${it.title}" }
            }
            "/tuition" -> {
                val upcoming = tuitionSessions.filter { it.sessionDate >= today && it.status != TuitionStatus.CANCELLED.name }
                    .sortedBy { "${it.sessionDate} ${it.startTime}" }.take(6)
                if (upcoming.isEmpty()) "No upcoming tuition sessions." else upcoming.joinToString("\n") { "${it.sessionDate} ${formatDisplayTime(it.startTime)} • ${it.subject} (${it.status})" }
            }
            "/goals" -> {
                val active = goals.filter { !it.completed }.take(6)
                if (active.isEmpty()) "No active goals." else active.joinToString("\n") { "- ${it.title}: ${it.progressPercent}%" }
            }
            "/study" -> {
                val todayMins = studySessions.filter { it.sessionDate == today }.sumOf { it.durationMinutes }
                val totalMins = studySessions.sumOf { it.durationMinutes }
                "Study logged today: ${todayMins}m\nTotal recorded: ${totalMins}m across ${studySessions.size} sessions."
            }
            "/report" -> {
                val done = tasks.count { it.completed }
                "Baddie Quick Report:\nTasks completed: $done/${tasks.size}\nStudy sessions: ${studySessions.size}\nActive goals: ${goals.count { !it.completed }}"
            }
            else -> "Unknown command. Send /help to see supported commands."
        }
    }

    fun priorityWeight(priority: String): Int = when (priority.uppercase()) {
        "URGENT" -> 4
        "HIGH" -> 3
        "MEDIUM" -> 2
        else -> 1
    }
}
