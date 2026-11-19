package com.example.data.export

import com.example.data.model.*
import com.example.data.provider.BaddieRepository
import org.json.JSONArray
import org.json.JSONObject

data class FullBackupSnapshot(
    val profile: UserProfile?,
    val tasks: List<BaddieTask>,
    val routineBlocks: List<RoutineBlock>,
    val teachers: List<TeacherProfile>,
    val tuitionSessions: List<TuitionSession>,
    val subjects: List<StudySubject>,
    val studySessions: List<StudySession>,
    val habits: List<Habit>,
    val goals: List<Goal>,
    val journalEntries: List<JournalEntry>,
    val sleepLogs: List<SleepLog>
)

object BackupManager {
    private const val BACKUP_SCHEMA_VERSION = 1

    fun exportFullJson(snapshot: FullBackupSnapshot): String {
        val root = JSONObject()
        root.put("app", "Baddie")
        root.put("schemaVersion", BACKUP_SCHEMA_VERSION)
        root.put("exportedAtMillis", System.currentTimeMillis())

        snapshot.profile?.let { p ->
            root.put("profile", JSONObject().apply {
                put("displayName", p.displayName)
                put("timezone", p.timezone)
                put("weekStartDay", p.weekStartDay)
                put("aiMode", p.aiMode)
                put("dailyStudyTargetMinutes", p.dailyStudyTargetMinutes)
            })
        }

        root.put("tasks", JSONArray().apply {
            snapshot.tasks.forEach { t ->
                put(JSONObject().apply {
                    put("title", t.title)
                    put("description", t.description)
                    put("category", t.category)
                    put("subject", t.subject)
                    put("priority", t.priority)
                    put("status", t.status)
                    put("completed", t.completed)
                    put("dueDate", t.dueDate)
                    put("startTime", t.startTime)
                    put("estimatedMinutes", t.estimatedMinutes)
                    put("recurrence", t.recurrence)
                })
            }
        })

        root.put("routineBlocks", JSONArray().apply {
            snapshot.routineBlocks.forEach { b ->
                put(JSONObject().apply {
                    put("title", b.title)
                    put("blockType", b.blockType)
                    put("startTime", b.startTime)
                    put("endTime", b.endTime)
                    put("durationMinutes", b.durationMinutes)
                    put("repeatDays", JSONArray(b.repeatDays))
                })
            }
        })

        root.put("teachers", JSONArray().apply {
            snapshot.teachers.forEach { tc ->
                put(JSONObject().apply {
                    put("name", tc.name)
                    put("subject", tc.subject)
                    put("defaultWeekdays", JSONArray(tc.defaultWeekdays))
                    put("flexibleWeekly", tc.flexibleWeekly)
                    put("targetSessionsPerWeek", tc.targetSessionsPerWeek)
                    put("preferredTime", tc.preferredTime)
                    put("expectedDurationMinutes", tc.expectedDurationMinutes)
                })
            }
        })

        root.put("tuitionSessions", JSONArray().apply {
            snapshot.tuitionSessions.forEach { s ->
                put(JSONObject().apply {
                    put("teacherName", s.teacherName)
                    put("subject", s.subject)
                    put("sessionDate", s.sessionDate)
                    put("startTime", s.startTime)
                    put("durationMinutes", s.durationMinutes)
                    put("status", s.status)
                    put("originalDate", s.originalDate)
                    put("originalTime", s.originalTime)
                })
            }
        })

        root.put("studySessions", JSONArray().apply {
            snapshot.studySessions.forEach { s ->
                put(JSONObject().apply {
                    put("subjectName", s.subjectName)
                    put("sessionDate", s.sessionDate)
                    put("durationMinutes", s.durationMinutes)
                    put("mode", s.mode)
                    put("notes", s.notes)
                })
            }
        })

        root.put("habits", JSONArray().apply {
            snapshot.habits.forEach { h ->
                put(JSONObject().apply {
                    put("title", h.title)
                    put("frequency", h.frequency)
                    put("targetDaysPerWeek", h.targetDaysPerWeek)
                    put("completedDates", JSONArray(h.completedDates))
                })
            }
        })

        root.put("goals", JSONArray().apply {
            snapshot.goals.forEach { g ->
                put(JSONObject().apply {
                    put("title", g.title)
                    put("timeframe", g.timeframe)
                    put("priority", g.priority)
                    put("deadline", g.deadline)
                    put("progressPercent", g.progressPercent)
                    put("completed", g.completed)
                })
            }
        })

        root.put("journalEntries", JSONArray().apply {
            snapshot.journalEntries.forEach { j ->
                put(JSONObject().apply {
                    put("entryDate", j.entryDate)
                    put("mood", j.mood)
                    put("energyLevel", j.energyLevel)
                    put("content", j.content)
                    put("wins", j.wins)
                    put("difficulties", j.difficulties)
                })
            }
        })

        root.put("sleepLogs", JSONArray().apply {
            snapshot.sleepLogs.forEach { sl ->
                put(JSONObject().apply {
                    put("logDate", sl.logDate)
                    put("sleepTime", sl.sleepTime)
                    put("wakeTime", sl.wakeTime)
                    put("durationMinutes", sl.durationMinutes)
                    put("energyLevel", sl.energyLevel)
                    put("exerciseMinutes", sl.exerciseMinutes)
                })
            }
        })

        return root.toString(2)
    }

    fun exportTasksCsv(tasks: List<BaddieTask>): String {
        val sb = StringBuilder("Title,Category,Subject,Priority,Status,DueDate,EstimatedMin,Completed\n")
        tasks.forEach { t ->
            sb.append(csvEscape(t.title)).append(',')
                .append(t.category).append(',')
                .append(csvEscape(t.subject)).append(',')
                .append(t.priority).append(',')
                .append(t.status).append(',')
                .append(t.dueDate).append(',')
                .append(t.estimatedMinutes).append(',')
                .append(t.completed).append('\n')
        }
        return sb.toString()
    }

    fun exportStudyAndTuitionCsv(
        studySessions: List<StudySession>,
        tuitionSessions: List<TuitionSession>
    ): String {
        val sb = StringBuilder("Type,Subject,TeacherOrMode,Date,TimeOrDuration,StatusOrNotes\n")
        studySessions.forEach { s ->
            sb.append("Study,")
                .append(csvEscape(s.subjectName)).append(',')
                .append(s.mode).append(',')
                .append(s.sessionDate).append(',')
                .append("${s.durationMinutes}m").append(',')
                .append(csvEscape(s.notes)).append('\n')
        }
        tuitionSessions.forEach { t ->
            sb.append("Tuition,")
                .append(csvEscape(t.subject)).append(',')
                .append(csvEscape(t.teacherName)).append(',')
                .append(t.sessionDate).append(',')
                .append("${t.startTime} (${t.durationMinutes}m)").append(',')
                .append(t.status).append('\n')
        }
        return sb.toString()
    }

    fun validateBackupJson(rawJson: String): Result<JSONObject> {
        return try {
            val obj = JSONObject(rawJson)
            if (obj.optString("app") != "Baddie" || !obj.has("schemaVersion")) {
                return Result.failure(IllegalArgumentException("Invalid Baddie backup file format."))
            }
            Result.success(obj)
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Malformed JSON backup: ${e.localizedMessage}"))
        }
    }

    suspend fun restoreBackup(
        uid: String,
        rawJson: String,
        repository: BaddieRepository
    ): Result<Int> {
        val validated = validateBackupJson(rawJson).getOrElse { return Result.failure(it) }
        var restoredCount = 0

        val tasksArr = validated.optJSONArray("tasks")
        if (tasksArr != null) {
            for (i in 0 until tasksArr.length()) {
                val item = tasksArr.getJSONObject(i)
                val title = item.optString("title", "").trim()
                if (title.isNotEmpty()) {
                    repository.addTask(
                        uid,
                        BaddieTask(
                            title = title,
                            description = item.optString("description", ""),
                            category = item.optString("category", TaskCategory.STUDY.name),
                            subject = item.optString("subject", ""),
                            priority = item.optString("priority", TaskPriority.MEDIUM.name),
                            status = item.optString("status", TaskStatus.PENDING.name),
                            completed = item.optBoolean("completed", false),
                            dueDate = item.optString("dueDate", ""),
                            startTime = item.optString("startTime", ""),
                            estimatedMinutes = item.optInt("estimatedMinutes", 30),
                            recurrence = item.optString("recurrence", TaskRecurrence.NONE.name)
                        )
                    )
                    restoredCount++
                }
            }
        }

        val habitsArr = validated.optJSONArray("habits")
        if (habitsArr != null) {
            for (i in 0 until habitsArr.length()) {
                val item = habitsArr.getJSONObject(i)
                val title = item.optString("title", "").trim()
                if (title.isNotEmpty()) {
                    val datesArr = item.optJSONArray("completedDates")
                    val dates = if (datesArr != null) (0 until datesArr.length()).map { datesArr.getString(it) } else emptyList()
                    repository.addHabit(
                        uid,
                        Habit(
                            title = title,
                            frequency = item.optString("frequency", "DAILY"),
                            targetDaysPerWeek = item.optInt("targetDaysPerWeek", 7),
                            completedDates = dates
                        )
                    )
                    restoredCount++
                }
            }
        }

        val goalsArr = validated.optJSONArray("goals")
        if (goalsArr != null) {
            for (i in 0 until goalsArr.length()) {
                val item = goalsArr.getJSONObject(i)
                val title = item.optString("title", "").trim()
                if (title.isNotEmpty()) {
                    repository.addGoal(
                        uid,
                        Goal(
                            title = title,
                            timeframe = item.optString("timeframe", GoalTimeframe.WEEKLY.name),
                            priority = item.optString("priority", TaskPriority.MEDIUM.name),
                            deadline = item.optString("deadline", ""),
                            progressPercent = item.optInt("progressPercent", 0),
                            completed = item.optBoolean("completed", false)
                        )
                    )
                    restoredCount++
                }
            }
        }

        return Result.success(restoredCount)
    }

    private fun csvEscape(value: String): String {
        val clean = value.replace("\"", "\"\"").replace("\n", " ")
        return "\"$clean\""
    }
}
