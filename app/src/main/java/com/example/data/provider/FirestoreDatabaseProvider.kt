package com.example.data.provider

import android.content.Context
import com.example.R
import com.example.core.DatabaseProvider
import com.example.core.OperationType
import com.example.core.ProviderHealth
import com.example.core.handleFirestoreError
import com.example.data.model.*
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.time.LocalDate
import java.time.ZoneId

class FirestoreDatabaseProvider(
    val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) : DatabaseProvider {

    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    override val providerName: String = "Firebase Firestore"

    override suspend fun initialize(): Result<Unit> = Result.success(Unit)

    override fun currentUserId(): String? = auth.currentUser?.uid?.takeIf { it.isNotBlank() }

    private fun requireUserId(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    override suspend fun <T> get(
        collectionPath: String,
        documentId: String,
        type: Class<T>
    ): Result<T?> {
        return try {
            requireUserId()
            val snap = db.collection(collectionPath).document(documentId).get().await()
            Result.success(snap.toObject(type, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, "$collectionPath/$documentId")
            Result.failure(e)
        }
    }

    override suspend fun create(
        collectionPath: String,
        documentId: String?,
        payload: Map<String, Any>
    ): Result<String> {
        var docPath = collectionPath
        return try {
            requireUserId()
            val docRef = if (documentId.isNullOrBlank()) {
                db.collection(collectionPath).document()
            } else {
                db.collection(collectionPath).document(documentId)
            }
            docPath = docRef.path
            docRef.set(payload).await()
            Result.success(docRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun update(
        collectionPath: String,
        documentId: String,
        updates: Map<String, Any>
    ): Result<Unit> {
        val docPath = "$collectionPath/$documentId"
        return try {
            requireUserId()
            require(documentId.isNotBlank()) { "Document ID must not be blank" }
            val withTimestamp = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            db.collection(collectionPath).document(documentId).update(withTimestamp).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docPath)
            Result.failure(e)
        }
    }

    override suspend fun delete(collectionPath: String, documentId: String): Result<Unit> {
        val docPath = "$collectionPath/$documentId"
        return try {
            requireUserId()
            require(documentId.isNotBlank()) { "Document ID must not be blank" }
            db.collection(collectionPath).document(documentId).delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docPath)
            Result.failure(e)
        }
    }

    override fun <T> observeCollection(collectionPath: String, type: Class<T>): Flow<List<T>> = flow {
        requireUserId()
        emitAll(
            db.collection(collectionPath)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(type, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                }
        )
    }

    override suspend fun healthCheck(): ProviderHealth {
        val start = System.currentTimeMillis()
        return try {
            val uid = currentUserId()
            if (uid == null) {
                ProviderHealth(providerName, false, 0L, "Unauthenticated")
            } else {
                db.collection("users").document(uid).get().await()
                val latency = System.currentTimeMillis() - start
                ProviderHealth(providerName, true, latency, "Connected (${latency}ms)")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val latency = System.currentTimeMillis() - start
            ProviderHealth(providerName, false, latency, e.localizedMessage ?: "Connection failed")
        }
    }
}

class BaddieRepository(
    private val dbProvider: FirestoreDatabaseProvider,
    private val auth: FirebaseAuth = Firebase.auth
) {
    private val db: FirebaseFirestore get() = dbProvider.db

    private fun requireUid(): String {
        return auth.currentUser?.uid?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("User must be signed in with Google.")
    }

    private fun userSub(uid: String, sub: String) = "users/$uid/$sub"

    // --- Observers ---

    fun observeProfile(uid: String): Flow<UserProfile?> = flow {
        val checkUid = requireUid()
        check(checkUid == uid) { "User mismatch" }
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap -> snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
        )
    }

    fun observeTasks(uid: String): Flow<List<BaddieTask>> =
        dbProvider.observeCollection(userSub(uid, "tasks"), BaddieTask::class.java)

    fun observeRoutineBlocks(uid: String): Flow<List<RoutineBlock>> =
        dbProvider.observeCollection(userSub(uid, "routine_blocks"), RoutineBlock::class.java)

    fun observeTeachers(uid: String): Flow<List<TeacherProfile>> =
        dbProvider.observeCollection(userSub(uid, "teachers"), TeacherProfile::class.java)

    fun observeTuitionSessions(uid: String): Flow<List<TuitionSession>> =
        dbProvider.observeCollection(userSub(uid, "tuition_sessions"), TuitionSession::class.java)

    fun observeSubjects(uid: String): Flow<List<StudySubject>> =
        dbProvider.observeCollection(userSub(uid, "subjects"), StudySubject::class.java)

    fun observeStudySessions(uid: String): Flow<List<StudySession>> =
        dbProvider.observeCollection(userSub(uid, "study_sessions"), StudySession::class.java)

    fun observeHabits(uid: String): Flow<List<Habit>> =
        dbProvider.observeCollection(userSub(uid, "habits"), Habit::class.java)

    fun observeGoals(uid: String): Flow<List<Goal>> =
        dbProvider.observeCollection(userSub(uid, "goals"), Goal::class.java)

    fun observeJournalEntries(uid: String): Flow<List<JournalEntry>> =
        dbProvider.observeCollection(userSub(uid, "journal_entries"), JournalEntry::class.java)

    fun observeSleepLogs(uid: String): Flow<List<SleepLog>> =
        dbProvider.observeCollection(userSub(uid, "sleep_logs"), SleepLog::class.java)

    fun observeReminders(uid: String): Flow<List<ReminderItem>> =
        dbProvider.observeCollection(userSub(uid, "reminders"), ReminderItem::class.java)

    fun observeAiLogs(uid: String): Flow<List<AiActionLog>> =
        dbProvider.observeCollection(userSub(uid, "ai_action_logs"), AiActionLog::class.java)

    // --- Profile ---

    suspend fun ensureUserProfile(uid: String, displayName: String?, email: String?): Result<Unit> {
        val docPath = "users/$uid"
        return try {
            requireUid()
            val ref = db.collection("users").document(uid)
            val snap = ref.get().await()
            if (!snap.exists()) {
                val profile = UserProfile(
                    userId = uid,
                    displayName = displayName?.takeIf { it.isNotBlank() } ?: "Owner",
                    email = email.orEmpty(),
                    timezone = "Asia/Dhaka"
                )
                ref.set(profile.toCreateMap()).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun updateProfile(uid: String, updates: Map<String, Any>): Result<Unit> =
        dbProvider.update("users", uid, updates)

    // --- Tasks ---

    suspend fun addTask(uid: String, task: BaddieTask): Result<String> {
        if (task.title.isBlank()) return Result.failure(IllegalArgumentException("Task title cannot be empty"))
        return dbProvider.create(userSub(uid, "tasks"), null, task.toCreateMap(uid))
    }

    suspend fun updateTask(uid: String, taskId: String, updates: Map<String, Any>): Result<Unit> =
        dbProvider.update(userSub(uid, "tasks"), taskId, updates)

    suspend fun toggleTaskCompletion(uid: String, task: BaddieTask, allTasks: List<BaddieTask>): Result<Unit> {
        val nextCompleted = !task.completed
        val updates = mapOf<String, Any>(
            "completed" to nextCompleted,
            "status" to if (nextCompleted) TaskStatus.COMPLETED.name else TaskStatus.PENDING.name,
            "progressPercent" to if (nextCompleted) 100 else 0,
            "completedAtMillis" to if (nextCompleted) System.currentTimeMillis() else 0L
        )
        val res = dbProvider.update(userSub(uid, "tasks"), task.id, updates)
        if (res.isSuccess && task.goalId.isNotBlank()) {
            val updatedList = allTasks.map { if (it.id == task.id) it.copy(completed = nextCompleted) else it }
            syncGoalFromTasks(uid, task.goalId, updatedList)
        }
        return res
    }

    suspend fun toggleSubtask(uid: String, task: BaddieTask, subtaskIndex: Int): Result<Unit> {
        if (subtaskIndex !in task.subtasks.indices) return Result.failure(IndexOutOfBoundsException("Invalid subtask index"))
        val updatedSubtasks = task.subtasks.mapIndexed { idx, item ->
            if (idx == subtaskIndex) item.copy(completed = !item.completed) else item
        }
        val completedCount = updatedSubtasks.count { it.completed }
        val pct = if (updatedSubtasks.isEmpty()) task.progressPercent else (completedCount * 100) / updatedSubtasks.size
        return dbProvider.update(
            userSub(uid, "tasks"),
            task.id,
            mapOf(
                "subtasks" to updatedSubtasks.map { it.toMap() },
                "progressPercent" to pct
            )
        )
    }

    suspend fun duplicateTask(uid: String, task: BaddieTask): Result<String> {
        val copy = task.copy(
            id = "",
            title = "${task.title} (Copy)",
            completed = false,
            status = TaskStatus.PENDING.name,
            progressPercent = 0,
            completedAtMillis = 0L
        )
        return addTask(uid, copy)
    }

    suspend fun postponeTaskOneDay(uid: String, task: BaddieTask, timezone: String): Result<Unit> {
        val zone = runCatching { ZoneId.of(timezone) }.getOrElse { ZoneId.of("Asia/Dhaka") }
        val current = runCatching { LocalDate.parse(task.dueDate) }.getOrElse { LocalDate.now(zone) }
        val nextDate = current.plusDays(1).toString()
        return dbProvider.update(userSub(uid, "tasks"), task.id, mapOf("dueDate" to nextDate))
    }

    suspend fun bulkCompleteTasks(uid: String, taskIds: List<String>): Result<Unit> {
        return try {
            requireUid()
            val batch = db.batch()
            val now = System.currentTimeMillis()
            taskIds.forEach { tid ->
                val ref = db.collection("users").document(uid).collection("tasks").document(tid)
                batch.update(
                    ref,
                    mapOf(
                        "completed" to true,
                        "status" to TaskStatus.COMPLETED.name,
                        "progressPercent" to 100,
                        "completedAtMillis" to now,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }
            batch.commit().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userSub(uid, "tasks"))
            Result.failure(e)
        }
    }

    suspend fun deleteTask(uid: String, taskId: String): Result<Unit> =
        dbProvider.delete(userSub(uid, "tasks"), taskId)

    // --- Routine Blocks ---

    suspend fun addRoutineBlock(uid: String, block: RoutineBlock): Result<String> {
        if (block.title.isBlank()) return Result.failure(IllegalArgumentException("Routine block title cannot be empty"))
        return dbProvider.create(userSub(uid, "routine_blocks"), null, block.toCreateMap(uid))
    }

    suspend fun updateRoutineBlock(uid: String, blockId: String, updates: Map<String, Any>): Result<Unit> =
        dbProvider.update(userSub(uid, "routine_blocks"), blockId, updates)

    suspend fun toggleRoutineCompletedToday(uid: String, block: RoutineBlock, todayIso: String): Result<Unit> {
        val nextDate = if (block.lastCompletedDate == todayIso) "" else todayIso
        return dbProvider.update(
            userSub(uid, "routine_blocks"),
            block.id,
            mapOf("lastCompletedDate" to nextDate)
        )
    }

    suspend fun deleteRoutineBlock(uid: String, blockId: String): Result<Unit> =
        dbProvider.delete(userSub(uid, "routine_blocks"), blockId)

    // --- Teachers & Flexible Tuition Engine ---

    suspend fun addTeacher(uid: String, teacher: TeacherProfile): Result<String> {
        if (teacher.name.isBlank() || teacher.subject.isBlank()) {
            return Result.failure(IllegalArgumentException("Teacher name and subject are required"))
        }
        return dbProvider.create(userSub(uid, "teachers"), null, teacher.toCreateMap(uid))
    }

    suspend fun updateTeacher(uid: String, teacherId: String, updates: Map<String, Any>): Result<Unit> =
        dbProvider.update(userSub(uid, "teachers"), teacherId, updates)

    suspend fun deleteTeacher(uid: String, teacherId: String): Result<Unit> =
        dbProvider.delete(userSub(uid, "teachers"), teacherId)

    suspend fun addTuitionSession(uid: String, session: TuitionSession): Result<String> {
        if (session.subject.isBlank() || session.sessionDate.isBlank()) {
            return Result.failure(IllegalArgumentException("Subject and date are required"))
        }
        return dbProvider.create(userSub(uid, "tuition_sessions"), null, session.toCreateMap(uid))
    }

    suspend fun updateTuitionStatus(uid: String, sessionId: String, status: TuitionStatus, notes: String? = null): Result<Unit> {
        val updates = mutableMapOf<String, Any>("status" to status.name)
        if (notes != null) updates["notes"] = notes.take(1000)
        return dbProvider.update(userSub(uid, "tuition_sessions"), sessionId, updates)
    }

    /**
     * Preserves original session for historical analytics by marking it RESCHEDULED
     * and creating the new session pointing back to originalDate / originalTime / rescheduledFromId.
     */
    suspend fun rescheduleTuitionSession(
        uid: String,
        original: TuitionSession,
        newDate: String,
        newTime: String,
        newDurationMinutes: Int = original.durationMinutes
    ): Result<String> {
        return try {
            requireUid()
            val col = db.collection("users").document(uid).collection("tuition_sessions")
            val newRef = col.document()
            val origRef = col.document(original.id)
            val batch = db.batch()
            batch.update(
                origRef,
                mapOf(
                    "status" to TuitionStatus.RESCHEDULED.name,
                    "notes" to (original.notes.ifBlank { "Rescheduled to $newDate $newTime" }).take(1000),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            val newSession = original.copy(
                id = "",
                sessionDate = newDate,
                startTime = newTime,
                durationMinutes = newDurationMinutes,
                status = TuitionStatus.SCHEDULED.name,
                originalDate = original.originalDate.ifBlank { original.sessionDate },
                originalTime = original.originalTime.ifBlank { original.startTime },
                rescheduledFromId = original.id
            )
            batch.set(newRef, newSession.toCreateMap(uid))
            batch.commit().await()
            Result.success(newRef.id)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userSub(uid, "tuition_sessions"))
            Result.failure(e)
        }
    }

    suspend fun deleteTuitionSession(uid: String, sessionId: String): Result<Unit> =
        dbProvider.delete(userSub(uid, "tuition_sessions"), sessionId)

    // --- Study Subjects & Sessions ---

    suspend fun addSubject(uid: String, subject: StudySubject): Result<String> {
        if (subject.name.isBlank()) return Result.failure(IllegalArgumentException("Subject name is required"))
        return dbProvider.create(userSub(uid, "subjects"), null, subject.toCreateMap(uid))
    }

    suspend fun updateSubject(uid: String, subjectId: String, updates: Map<String, Any>): Result<Unit> =
        dbProvider.update(userSub(uid, "subjects"), subjectId, updates)

    suspend fun deleteSubject(uid: String, subjectId: String): Result<Unit> =
        dbProvider.delete(userSub(uid, "subjects"), subjectId)

    suspend fun logStudySession(uid: String, session: StudySession, currentSubjectTotal: Int? = null): Result<String> {
        val createRes = dbProvider.create(userSub(uid, "study_sessions"), null, session.toCreateMap(uid))
        if (createRes.isSuccess && session.subjectId.isNotBlank() && currentSubjectTotal != null) {
            dbProvider.update(
                userSub(uid, "subjects"),
                session.subjectId,
                mapOf("totalStudyMinutes" to (currentSubjectTotal + session.durationMinutes).coerceIn(0, 1000000))
            )
        }
        return createRes
    }

    // --- Habits ---

    suspend fun addHabit(uid: String, habit: Habit): Result<String> {
        if (habit.title.isBlank()) return Result.failure(IllegalArgumentException("Habit title required"))
        return dbProvider.create(userSub(uid, "habits"), null, habit.toCreateMap(uid))
    }

    suspend fun toggleHabitForDate(uid: String, habit: Habit, dateIso: String): Result<Unit> {
        val nextDates = if (dateIso in habit.completedDates) {
            habit.completedDates - dateIso
        } else {
            (habit.completedDates + dateIso).distinct().takeLast(400)
        }
        return dbProvider.update(userSub(uid, "habits"), habit.id, mapOf("completedDates" to nextDates))
    }

    suspend fun updateHabit(uid: String, habitId: String, updates: Map<String, Any>): Result<Unit> =
        dbProvider.update(userSub(uid, "habits"), habitId, updates)

    suspend fun deleteHabit(uid: String, habitId: String): Result<Unit> =
        dbProvider.delete(userSub(uid, "habits"), habitId)

    // --- Goals ---

    suspend fun addGoal(uid: String, goal: Goal): Result<String> {
        if (goal.title.isBlank()) return Result.failure(IllegalArgumentException("Goal title required"))
        return dbProvider.create(userSub(uid, "goals"), null, goal.toCreateMap(uid))
    }

    suspend fun updateGoal(uid: String, goalId: String, updates: Map<String, Any>): Result<Unit> =
        dbProvider.update(userSub(uid, "goals"), goalId, updates)

    suspend fun deleteGoal(uid: String, goalId: String): Result<Unit> =
        dbProvider.delete(userSub(uid, "goals"), goalId)

    suspend fun syncGoalFromTasks(uid: String, goalId: String, tasks: List<BaddieTask>) {
        val linked = tasks.filter { it.goalId == goalId && it.status != TaskStatus.CANCELLED.name }
        if (linked.isEmpty()) return
        val completedCount = linked.count { it.completed }
        val pct = (completedCount * 100) / linked.size
        dbProvider.update(
            userSub(uid, "goals"),
            goalId,
            mapOf("progressPercent" to pct, "completed" to (pct == 100))
        )
    }

    // --- Journal & Sleep ---

    suspend fun addJournalEntry(uid: String, entry: JournalEntry): Result<String> =
        dbProvider.create(userSub(uid, "journal_entries"), null, entry.toCreateMap(uid))

    suspend fun updateJournalEntry(uid: String, entryId: String, updates: Map<String, Any>): Result<Unit> =
        dbProvider.update(userSub(uid, "journal_entries"), entryId, updates)

    suspend fun deleteJournalEntry(uid: String, entryId: String): Result<Unit> =
        dbProvider.delete(userSub(uid, "journal_entries"), entryId)

    suspend fun addSleepLog(uid: String, log: SleepLog): Result<String> =
        dbProvider.create(userSub(uid, "sleep_logs"), null, log.toCreateMap(uid))

    suspend fun deleteSleepLog(uid: String, logId: String): Result<Unit> =
        dbProvider.delete(userSub(uid, "sleep_logs"), logId)

    // --- Reminders & AI Action Logs ---

    suspend fun addReminder(uid: String, reminder: ReminderItem): Result<String> =
        dbProvider.create(userSub(uid, "reminders"), null, reminder.toCreateMap(uid))

    suspend fun updateReminder(uid: String, reminderId: String, updates: Map<String, Any>): Result<Unit> =
        dbProvider.update(userSub(uid, "reminders"), reminderId, updates)

    suspend fun deleteReminder(uid: String, reminderId: String): Result<Unit> =
        dbProvider.delete(userSub(uid, "reminders"), reminderId)

    suspend fun logAiAction(uid: String, log: AiActionLog): Result<String> =
        dbProvider.create(userSub(uid, "ai_action_logs"), null, log.toCreateMap(uid))

    suspend fun healthCheck(): ProviderHealth = dbProvider.healthCheck()
}
