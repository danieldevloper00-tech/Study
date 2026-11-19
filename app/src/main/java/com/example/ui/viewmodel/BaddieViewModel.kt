package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AIProvider
import com.example.core.AiScheduleProposal
import com.example.core.AiWeeklyReport
import com.example.core.ProviderHealth
import com.example.core.UptimeStatusResult
import com.example.data.domain.BaddieDomainLogic
import com.example.data.export.BackupManager
import com.example.data.export.FullBackupSnapshot
import com.example.data.model.*
import com.example.data.prefs.LocalSecretsStore
import com.example.data.provider.BaddieRepository
import com.example.data.provider.TelegramNotificationProvider
import com.example.data.provider.UptimeRobotProvider
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.ZonedDateTime

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

data class StudyTimerState(
    val selectedSubjectId: String = "",
    val selectedSubjectName: String = "Physics",
    val mode: String = "POMODORO", // POMODORO, CUSTOM
    val targetMinutes: Int = 25,
    val remainingSeconds: Int = 25 * 60,
    val running: Boolean = false,
    val breakPhase: Boolean = false,
    val elapsedStudySeconds: Int = 0
)

data class BaddieDashboardData(
    val profile: UserProfile = UserProfile(),
    val tasks: List<BaddieTask> = emptyList(),
    val routineBlocks: List<RoutineBlock> = emptyList(),
    val teachers: List<TeacherProfile> = emptyList(),
    val tuitionSessions: List<TuitionSession> = emptyList(),
    val subjects: List<StudySubject> = emptyList(),
    val studySessions: List<StudySession> = emptyList(),
    val habits: List<Habit> = emptyList(),
    val goals: List<Goal> = emptyList(),
    val journalEntries: List<JournalEntry> = emptyList(),
    val sleepLogs: List<SleepLog> = emptyList(),
    val reminders: List<ReminderItem> = emptyList(),
    val aiLogs: List<AiActionLog> = emptyList()
)

class BaddieViewModel(
    private val repository: BaddieRepository,
    private val aiProvider: AIProvider,
    private val telegramProvider: TelegramNotificationProvider,
    private val uptimeProvider: UptimeRobotProvider,
    private val secretsStore: LocalSecretsStore,
    private val currentUserId: String,
    private val userDisplayName: String?,
    private val userEmail: String?
) : ViewModel() {

    private companion object {
        const val TAG = "BaddieVM"
        const val STOP_TIMEOUT_MS = 5000L
    }

    private val _feedbackMessage = MutableStateFlow<String?>(null)
    val feedbackMessage: StateFlow<String?> = _feedbackMessage.asStateFlow()

    private val _nowClock = MutableStateFlow(ZonedDateTime.now(BaddieDomainLogic.safeZoneId("Asia/Dhaka")))
    val nowClock: StateFlow<ZonedDateTime> = _nowClock.asStateFlow()

    private val _timerState = MutableStateFlow(StudyTimerState())
    val timerState: StateFlow<StudyTimerState> = _timerState.asStateFlow()

    private val _dailyRecommendation = MutableStateFlow<UiState<String>>(UiState.Success("Tap refresh to generate a personalized recommendation from your real schedule."))
    val dailyRecommendation: StateFlow<UiState<String>> = _dailyRecommendation.asStateFlow()

    private val _chatMessages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val chatMessages: StateFlow<List<ChatMessage>> = _chatMessages.asStateFlow()

    private val _isAiStreaming = MutableStateFlow(false)
    val isAiStreaming: StateFlow<Boolean> = _isAiStreaming.asStateFlow()

    private val _weeklyReport = MutableStateFlow<UiState<AiWeeklyReport>?>(null)
    val weeklyReport: StateFlow<UiState<AiWeeklyReport>?> = _weeklyReport.asStateFlow()

    private val _uptimeStatus = MutableStateFlow<UptimeStatusResult>(UptimeStatusResult.AwaitingCredentials)
    val uptimeStatus: StateFlow<UptimeStatusResult> = _uptimeStatus.asStateFlow()

    private val _dbHealth = MutableStateFlow<ProviderHealth?>(null)
    val dbHealth: StateFlow<ProviderHealth?> = _dbHealth.asStateFlow()

    private val _aiHealth = MutableStateFlow<ProviderHealth?>(null)
    val aiHealth: StateFlow<ProviderHealth?> = _aiHealth.asStateFlow()

    val savedTelegramToken: StateFlow<String> = secretsStore.telegramBotToken
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")
    val savedTelegramChatId: StateFlow<String> = secretsStore.telegramChatId
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")
    val savedUptimeKey: StateFlow<String> = secretsStore.uptimeApiKeyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), "")

    private fun <T> Flow<T>.unwrapFirestoreErrors(): Flow<T> = this.catch { error ->
        val firestoreError = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (firestoreError == null && error is CancellationException) throw error
        throw (firestoreError ?: error)
    }

    private val corePart1 = combine(
        repository.observeProfile(currentUserId).unwrapFirestoreErrors(),
        repository.observeTasks(currentUserId).unwrapFirestoreErrors(),
        repository.observeRoutineBlocks(currentUserId).unwrapFirestoreErrors(),
        repository.observeTeachers(currentUserId).unwrapFirestoreErrors(),
        repository.observeTuitionSessions(currentUserId).unwrapFirestoreErrors()
    ) { profile, tasks, routine, teachers, tuitions ->
        listOf(profile, tasks, routine, teachers, tuitions)
    }

    private val corePart2 = combine(
        repository.observeSubjects(currentUserId).unwrapFirestoreErrors(),
        repository.observeStudySessions(currentUserId).unwrapFirestoreErrors(),
        repository.observeHabits(currentUserId).unwrapFirestoreErrors(),
        repository.observeGoals(currentUserId).unwrapFirestoreErrors(),
        repository.observeJournalEntries(currentUserId).unwrapFirestoreErrors()
    ) { subjects, studySessions, habits, goals, journal ->
        listOf(subjects, studySessions, habits, goals, journal)
    }

    private val corePart3 = combine(
        repository.observeSleepLogs(currentUserId).unwrapFirestoreErrors(),
        repository.observeReminders(currentUserId).unwrapFirestoreErrors(),
        repository.observeAiLogs(currentUserId).unwrapFirestoreErrors()
    ) { sleepLogs, reminders, aiLogs ->
        Triple(sleepLogs, reminders, aiLogs)
    }

    @Suppress("UNCHECKED_CAST")
    val dataState: StateFlow<UiState<BaddieDashboardData>> = combine(corePart1, corePart2, corePart3) { p1, p2, p3 ->
        val profile = (p1[0] as? UserProfile) ?: UserProfile(
            userId = currentUserId,
            displayName = userDisplayName ?: "Owner",
            email = userEmail.orEmpty()
        )
        BaddieDashboardData(
            profile = profile,
            tasks = (p1[1] as List<BaddieTask>).sortedBy { it.sortOrder },
            routineBlocks = (p1[2] as List<RoutineBlock>).sortedBy { it.startTime },
            teachers = p1[3] as List<TeacherProfile>,
            tuitionSessions = (p1[4] as List<TuitionSession>).sortedBy { "${it.sessionDate} ${it.startTime}" },
            subjects = p2[0] as List<StudySubject>,
            studySessions = p2[1] as List<StudySession>,
            habits = p2[2] as List<Habit>,
            goals = p2[3] as List<Goal>,
            journalEntries = (p2[4] as List<JournalEntry>).sortedByDescending { it.entryDate },
            sleepLogs = p3.first.sortedByDescending { it.logDate },
            reminders = p3.second,
            aiLogs = p3.third.sortedByDescending { it.createdAt?.seconds ?: 0L }
        )
    }
        .map<BaddieDashboardData, UiState<BaddieDashboardData>> { UiState.Success(it) }
        .catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val root = firestoreError ?: error
            Log.e(TAG, "Stream error", root)
            emit(UiState.Error(root.localizedMessage ?: "Failed to sync data"))
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = UiState.Loading
        )

    init {
        viewModelScope.launch {
            repository.ensureUserProfile(currentUserId, userDisplayName, userEmail)
        }
        viewModelScope.launch {
            while (true) {
                val tz = (dataState.value as? UiState.Success)?.data?.profile?.timezone ?: "Asia/Dhaka"
                _nowClock.value = BaddieDomainLogic.nowInZone(tz)
                delay(1000L)
            }
        }
        viewModelScope.launch {
            while (true) {
                if (_timerState.value.running) {
                    delay(1000L)
                    val cur = _timerState.value
                    if (cur.running) {
                        if (cur.remainingSeconds > 1) {
                            _timerState.value = cur.copy(
                                remainingSeconds = cur.remainingSeconds - 1,
                                elapsedStudySeconds = if (!cur.breakPhase) cur.elapsedStudySeconds + 1 else cur.elapsedStudySeconds
                            )
                        } else {
                            handleTimerSegmentComplete()
                        }
                    }
                } else {
                    delay(400L)
                }
            }
        }
    }

    fun clearFeedback() { _feedbackMessage.value = null }
    private fun showMsg(msg: String) { _feedbackMessage.value = msg }

    private fun currentData(): BaddieDashboardData? = (dataState.value as? UiState.Success)?.data

    // --- Profile & Settings ---

    fun updateProfileSettings(updates: Map<String, Any>) {
        viewModelScope.launch {
            repository.updateProfile(currentUserId, updates)
                .onFailure { showMsg(it.localizedMessage ?: "Failed to update settings") }
        }
    }

    fun toggleWidgetVisibility(widgetId: String) {
        val profile = currentData()?.profile ?: return
        val nextHidden = if (widgetId in profile.hiddenWidgets) {
            profile.hiddenWidgets - widgetId
        } else {
            profile.hiddenWidgets + widgetId
        }
        updateProfileSettings(mapOf("hiddenWidgets" to nextHidden))
    }

    fun moveWidget(widgetId: String, delta: Int) {
        val profile = currentData()?.profile ?: return
        val list = profile.widgetOrder.toMutableList()
        val idx = list.indexOf(widgetId)
        if (idx == -1) return
        val target = (idx + delta).coerceIn(0, list.lastIndex)
        if (target == idx) return
        list.removeAt(idx)
        list.add(target, widgetId)
        updateProfileSettings(mapOf("widgetOrder" to list))
    }

    // --- Onboarding ---

    fun completeOnboarding(
        displayName: String,
        timezone: String,
        teachersToCreate: List<TeacherProfile>,
        seedDefaultTuitionSlots: Boolean,
        routineBlocks: List<RoutineBlock>,
        subjectNames: List<String>,
        initialGoals: List<Goal>,
        aiMode: AiMode,
        telegramToken: String,
        telegramChatId: String,
        telegramEnabled: Boolean
    ) {
        viewModelScope.launch {
            if (telegramToken.isNotBlank() || telegramChatId.isNotBlank()) {
                secretsStore.saveTelegramCredentials(telegramToken, telegramChatId)
            }
            val tz = timezone.ifBlank { "Asia/Dhaka" }
            val createdTeachers = mutableListOf<Pair<String, TeacherProfile>>()
            teachersToCreate.forEach { t ->
                val res = repository.addTeacher(currentUserId, t)
                res.getOrNull()?.let { id -> createdTeachers += id to t }
            }

            if (seedDefaultTuitionSlots) {
                val today = BaddieDomainLogic.todayInZoneDate(tz)
                val defaultSlots = listOf(
                    DayOfWeek.SATURDAY to listOf("Other Subjects"),
                    DayOfWeek.SUNDAY to listOf("Mathematics", "Physics / Chemistry"),
                    DayOfWeek.MONDAY to listOf("Other Subjects"),
                    DayOfWeek.TUESDAY to listOf("Mathematics", "Physics / Chemistry"),
                    DayOfWeek.WEDNESDAY to listOf("Other Subjects"),
                    DayOfWeek.THURSDAY to listOf("Mathematics", "Physics / Chemistry")
                )
                for (offset in 0..6) {
                    val d = today.plusDays(offset.toLong())
                    val matching = defaultSlots.firstOrNull { it.first == d.dayOfWeek }?.second.orEmpty()
                    matching.forEachIndexed { idx, subj ->
                        val matchedTeacher = createdTeachers.firstOrNull {
                            it.second.subject.contains(subj.substringBefore(" "), ignoreCase = true)
                        }
                        repository.addTuitionSession(
                            currentUserId,
                            TuitionSession(
                                teacherId = matchedTeacher?.first.orEmpty(),
                                teacherName = matchedTeacher?.second?.name.orEmpty(),
                                subject = subj,
                                sessionDate = d.toString(),
                                startTime = if (idx == 0) "16:00" else "18:00",
                                durationMinutes = 90,
                                status = TuitionStatus.SCHEDULED.name,
                                originalDate = d.toString(),
                                originalTime = if (idx == 0) "16:00" else "18:00"
                            )
                        )
                    }
                }
            }

            routineBlocks.forEach { block ->
                repository.addRoutineBlock(currentUserId, block)
            }

            subjectNames.distinct().forEach { name ->
                repository.addSubject(currentUserId, StudySubject(name = name))
            }

            initialGoals.forEach { g ->
                repository.addGoal(currentUserId, g)
            }

            // Add default editable habits only if user has none yet
            if (currentData()?.habits.isNullOrEmpty()) {
                listOf("Wake up on time", "Focused study block", "Drink water", "Sleep on time").forEach { title ->
                    repository.addHabit(currentUserId, Habit(title = title))
                }
            }

            repository.updateProfile(
                currentUserId,
                mapOf(
                    "displayName" to displayName.ifBlank { "Owner" },
                    "timezone" to tz,
                    "aiMode" to aiMode.name,
                    "telegramEnabled" to telegramEnabled,
                    "telegramChatId" to telegramChatId.trim(),
                    "onboardingComplete" to true
                )
            )
            showMsg("Workspace initialized for Baddie")
        }
    }

    // --- Tasks ---

    fun addTask(task: BaddieTask) {
        viewModelScope.launch {
            repository.addTask(currentUserId, task)
                .onSuccess { showMsg("Task added") }
                .onFailure { showMsg(it.localizedMessage ?: "Failed to add task") }
        }
    }

    fun updateTask(taskId: String, updates: Map<String, Any>) {
        viewModelScope.launch {
            repository.updateTask(currentUserId, taskId, updates)
                .onFailure { showMsg(it.localizedMessage ?: "Failed to update task") }
        }
    }

    fun toggleTask(task: BaddieTask) {
        viewModelScope.launch {
            val all = currentData()?.tasks.orEmpty()
            repository.toggleTaskCompletion(currentUserId, task, all)
                .onFailure { showMsg(it.localizedMessage ?: "Failed to update task") }
        }
    }

    fun toggleSubtask(task: BaddieTask, index: Int) {
        viewModelScope.launch {
            repository.toggleSubtask(currentUserId, task, index)
        }
    }

    fun duplicateTask(task: BaddieTask) {
        viewModelScope.launch {
            repository.duplicateTask(currentUserId, task)
                .onSuccess { showMsg("Task duplicated") }
        }
    }

    fun postponeTask(task: BaddieTask) {
        viewModelScope.launch {
            val tz = currentData()?.profile?.timezone ?: "Asia/Dhaka"
            repository.postponeTaskOneDay(currentUserId, task, tz)
                .onSuccess { showMsg("Moved to next day") }
        }
    }

    fun rescheduleTaskToDate(task: BaddieTask, newDate: String) {
        viewModelScope.launch {
            repository.updateTask(currentUserId, task.id, mapOf("dueDate" to newDate, "status" to TaskStatus.PENDING.name))
                .onSuccess { showMsg("Task rescheduled to $newDate") }
        }
    }

    fun moveToBacklog(task: BaddieTask) {
        viewModelScope.launch {
            repository.updateTask(currentUserId, task.id, mapOf("dueDate" to "", "status" to TaskStatus.PENDING.name))
                .onSuccess { showMsg("Kept in backlog") }
        }
    }

    fun cancelTask(task: BaddieTask) {
        viewModelScope.launch {
            repository.updateTask(currentUserId, task.id, mapOf("status" to TaskStatus.CANCELLED.name, "completed" to false))
                .onSuccess { showMsg("Task cancelled") }
        }
    }

    fun bulkCompleteTasks(ids: List<String>) {
        if (ids.isEmpty()) return
        viewModelScope.launch {
            repository.bulkCompleteTasks(currentUserId, ids)
                .onSuccess { showMsg("Completed ${ids.size} tasks") }
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteTask(currentUserId, taskId)
                .onSuccess { showMsg("Task deleted") }
        }
    }

    // --- Routine ---

    fun addRoutineBlock(block: RoutineBlock) {
        viewModelScope.launch {
            repository.addRoutineBlock(currentUserId, block)
                .onSuccess { showMsg("Routine block saved") }
                .onFailure { showMsg(it.localizedMessage ?: "Failed to save block") }
        }
    }

    fun toggleRoutineBlockToday(block: RoutineBlock) {
        viewModelScope.launch {
            val tz = currentData()?.profile?.timezone ?: "Asia/Dhaka"
            repository.toggleRoutineCompletedToday(currentUserId, block, BaddieDomainLogic.todayIso(tz))
        }
    }

    fun deleteRoutineBlock(blockId: String) {
        viewModelScope.launch { repository.deleteRoutineBlock(currentUserId, blockId) }
    }

    // --- Teachers & Tuition ---

    fun addTeacher(teacher: TeacherProfile) {
        viewModelScope.launch {
            repository.addTeacher(currentUserId, teacher)
                .onSuccess { showMsg("Teacher profile saved") }
                .onFailure { showMsg(it.localizedMessage ?: "Failed to save teacher") }
        }
    }

    fun toggleTeacherActive(teacher: TeacherProfile) {
        viewModelScope.launch {
            repository.updateTeacher(currentUserId, teacher.id, mapOf("active" to !teacher.active))
        }
    }

    fun deleteTeacher(teacherId: String) {
        viewModelScope.launch { repository.deleteTeacher(currentUserId, teacherId) }
    }

    fun addTuitionSession(session: TuitionSession) {
        viewModelScope.launch {
            repository.addTuitionSession(currentUserId, session)
                .onSuccess { showMsg("Tuition session scheduled") }
                .onFailure { showMsg(it.localizedMessage ?: "Failed to schedule tuition") }
        }
    }

    fun updateTuitionStatus(sessionId: String, status: TuitionStatus) {
        viewModelScope.launch {
            repository.updateTuitionStatus(currentUserId, sessionId, status)
                .onSuccess { showMsg("Tuition marked ${status.label}") }
        }
    }

    fun rescheduleTuition(session: TuitionSession, newDate: String, newTime: String) {
        viewModelScope.launch {
            repository.rescheduleTuitionSession(currentUserId, session, newDate, newTime)
                .onSuccess { showMsg("Rescheduled to $newDate at $newTime (history preserved)") }
                .onFailure { showMsg(it.localizedMessage ?: "Reschedule failed") }
        }
    }

    fun deleteTuitionSession(sessionId: String) {
        viewModelScope.launch { repository.deleteTuitionSession(currentUserId, sessionId) }
    }

    // --- Study Timer & Subjects ---

    fun configureTimer(subjectId: String, subjectName: String, mode: String, minutes: Int) {
        val safeMins = minutes.coerceIn(1, 180)
        _timerState.value = StudyTimerState(
            selectedSubjectId = subjectId,
            selectedSubjectName = subjectName,
            mode = mode,
            targetMinutes = safeMins,
            remainingSeconds = safeMins * 60,
            running = false,
            breakPhase = false,
            elapsedStudySeconds = 0
        )
    }

    fun toggleTimerRunning() {
        val cur = _timerState.value
        _timerState.value = cur.copy(running = !cur.running)
    }

    fun finishTimerAndSave() {
        val cur = _timerState.value
        val mins = (cur.elapsedStudySeconds / 60).coerceAtLeast(1)
        _timerState.value = cur.copy(running = false, remainingSeconds = cur.targetMinutes * 60, elapsedStudySeconds = 0, breakPhase = false)
        logStudySession(cur.selectedSubjectId, cur.selectedSubjectName, mins, cur.mode, "Timer completed")
    }

    private fun handleTimerSegmentComplete() {
        val cur = _timerState.value
        if (!cur.breakPhase) {
            logStudySession(cur.selectedSubjectId, cur.selectedSubjectName, cur.targetMinutes, cur.mode, "Completed timer cycle")
            if (cur.mode == "POMODORO") {
                _timerState.value = cur.copy(running = false, breakPhase = true, remainingSeconds = 5 * 60, elapsedStudySeconds = 0)
                showMsg("Study block finished! 5-min break ready.")
            } else {
                _timerState.value = cur.copy(running = false, remainingSeconds = cur.targetMinutes * 60, elapsedStudySeconds = 0)
                showMsg("Study session logged!")
            }
        } else {
            _timerState.value = cur.copy(running = false, breakPhase = false, remainingSeconds = cur.targetMinutes * 60)
            showMsg("Break complete.")
        }
    }

    fun addSubject(name: String, confidence: Int) {
        viewModelScope.launch {
            repository.addSubject(currentUserId, StudySubject(name = name, confidenceLevel = confidence))
                .onSuccess { showMsg("Subject added") }
                .onFailure { showMsg(it.localizedMessage ?: "Failed to add subject") }
        }
    }

    fun addChapterToSubject(subject: StudySubject, chapterTitle: String) {
        if (chapterTitle.isBlank()) return
        viewModelScope.launch {
            val next = subject.chapters + ChapterItem(title = chapterTitle.trim())
            repository.updateSubject(currentUserId, subject.id, mapOf("chapters" to next.map { it.toMap() }))
        }
    }

    fun cycleChapterStatus(subject: StudySubject, index: Int) {
        if (index !in subject.chapters.indices) return
        val order = ChapterStatus.entries
        val cur = safeEnum(subject.chapters[index].status, ChapterStatus.NOT_STARTED)
        val nextStatus = order[(order.indexOf(cur) + 1) % order.size]
        val nextPct = when (nextStatus) {
            ChapterStatus.NOT_STARTED -> 0
            ChapterStatus.LEARNING -> 35
            ChapterStatus.PRACTICING -> 70
            ChapterStatus.COMPLETED -> 100
            ChapterStatus.NEEDS_REVISION -> 85
        }
        val nextList = subject.chapters.mapIndexed { idx, ch ->
            if (idx == index) ch.copy(status = nextStatus.name, progressPercent = nextPct) else ch
        }
        viewModelScope.launch {
            repository.updateSubject(currentUserId, subject.id, mapOf("chapters" to nextList.map { it.toMap() }))
        }
    }

    fun updateSubjectConfidence(subject: StudySubject, confidence: Int) {
        viewModelScope.launch {
            repository.updateSubject(currentUserId, subject.id, mapOf("confidenceLevel" to confidence.coerceIn(1, 5)))
        }
    }

    fun logStudySession(subjectId: String, subjectName: String, minutes: Int, mode: String, notes: String) {
        viewModelScope.launch {
            val tz = currentData()?.profile?.timezone ?: "Asia/Dhaka"
            val matched = currentData()?.subjects?.firstOrNull { it.id == subjectId || it.name.equals(subjectName, true) }
            val session = StudySession(
                subjectId = matched?.id ?: subjectId,
                subjectName = matched?.name ?: subjectName,
                sessionDate = BaddieDomainLogic.todayIso(tz),
                durationMinutes = minutes.coerceIn(1, 720),
                mode = mode,
                notes = notes
            )
            repository.logStudySession(currentUserId, session, matched?.totalStudyMinutes)
                .onSuccess { showMsg("Logged ${session.durationMinutes}m for ${session.subjectName}") }
        }
    }

    // --- Habits ---

    fun addHabit(title: String, frequency: String, targetDays: Int) {
        viewModelScope.launch {
            repository.addHabit(currentUserId, Habit(title = title, frequency = frequency, targetDaysPerWeek = targetDays))
                .onSuccess { showMsg("Habit added") }
        }
    }

    fun toggleHabitDate(habit: Habit, dateIso: String) {
        viewModelScope.launch { repository.toggleHabitForDate(currentUserId, habit, dateIso) }
    }

    fun deleteHabit(habitId: String) {
        viewModelScope.launch { repository.deleteHabit(currentUserId, habitId) }
    }

    // --- Goals ---

    fun addGoal(goal: Goal) {
        viewModelScope.launch {
            repository.addGoal(currentUserId, goal)
                .onSuccess { showMsg("Goal saved") }
        }
    }

    fun updateGoalProgress(goal: Goal, progress: Int) {
        val safe = progress.coerceIn(0, 100)
        viewModelScope.launch {
            repository.updateGoal(currentUserId, goal.id, mapOf("progressPercent" to safe, "completed" to (safe == 100)))
        }
    }

    fun toggleGoalMilestone(goal: Goal, index: Int) {
        if (index !in goal.milestones.indices) return
        val next = goal.milestones.mapIndexed { idx, m -> if (idx == index) m.copy(completed = !m.completed) else m }
        val pct = if (next.isEmpty()) goal.progressPercent else (next.count { it.completed } * 100) / next.size
        viewModelScope.launch {
            repository.updateGoal(
                currentUserId,
                goal.id,
                mapOf("milestones" to next.map { it.toMap() }, "progressPercent" to pct, "completed" to (pct == 100))
            )
        }
    }

    fun deleteGoal(goalId: String) {
        viewModelScope.launch { repository.deleteGoal(currentUserId, goalId) }
    }

    // --- Journal & Sleep ---

    fun addJournalEntry(mood: String, energy: Int, content: String, wins: String, difficulties: String, summarizeWithAi: Boolean) {
        viewModelScope.launch {
            val tz = currentData()?.profile?.timezone ?: "Asia/Dhaka"
            val summary = if (summarizeWithAi) {
                aiProvider.summarizeDay("Mood: $mood, Energy: $energy/5\nWins: $wins\nDifficulties: $difficulties\nEntry: $content")
                    .getOrElse { "" }
            } else ""
            val entry = JournalEntry(
                entryDate = BaddieDomainLogic.todayIso(tz),
                mood = mood,
                energyLevel = energy,
                content = content,
                wins = wins,
                difficulties = difficulties,
                aiSummary = summary,
                aiSummaryEnabled = summarizeWithAi
            )
            repository.addJournalEntry(currentUserId, entry)
                .onSuccess { showMsg("Journal entry saved") }
        }
    }

    fun deleteJournalEntry(entryId: String) {
        viewModelScope.launch { repository.deleteJournalEntry(currentUserId, entryId) }
    }

    fun addSleepLog(sleepTime: String, wakeTime: String, energy: Int, mood: String, exerciseMinutes: Int, notes: String) {
        viewModelScope.launch {
            val tz = currentData()?.profile?.timezone ?: "Asia/Dhaka"
            val s = BaddieDomainLogic.parseMinutesOrNull(sleepTime) ?: 23 * 60
            val w = BaddieDomainLogic.parseMinutesOrNull(wakeTime) ?: 7 * 60
            val dur = if (w >= s) w - s else (1440 - s + w)
            val log = SleepLog(
                logDate = BaddieDomainLogic.todayIso(tz),
                sleepTime = sleepTime,
                wakeTime = wakeTime,
                durationMinutes = dur,
                energyLevel = energy,
                mood = mood,
                exerciseMinutes = exerciseMinutes,
                notes = notes
            )
            repository.addSleepLog(currentUserId, log)
                .onSuccess { showMsg("Wellness & sleep logged (${dur / 60}h ${dur % 60}m)") }
        }
    }

    fun deleteSleepLog(logId: String) {
        viewModelScope.launch { repository.deleteSleepLog(currentUserId, logId) }
    }

    // --- Reminders ---

    fun addReminder(title: String, dateTimeIso: String, repeatRule: String) {
        viewModelScope.launch {
            val tz = currentData()?.profile?.timezone ?: "Asia/Dhaka"
            val item = ReminderItem(
                title = title,
                triggerDateTimeIso = dateTimeIso,
                timezone = tz,
                repeatRule = repeatRule
            )
            repository.addReminder(currentUserId, item)
                .onSuccess { id ->
                    val millis = runCatching { ZonedDateTime.parse(dateTimeIso).toInstant().toEpochMilli() }.getOrNull()
                    if (millis != null && millis > System.currentTimeMillis()) {
                        telegramProvider.schedule(id, millis, "Baddie Reminder", title)
                    }
                    showMsg("Reminder scheduled")
                }
        }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch {
            telegramProvider.cancel(id)
            repository.deleteReminder(currentUserId, id)
        }
    }

    // --- Gemini AI Assistant ---

    private fun buildAiSnapshot(): String {
        val d = currentData() ?: return "No user data loaded yet."
        return BaddieDomainLogic.buildContextSnapshot(
            timezone = d.profile.timezone,
            tasks = d.tasks,
            routineBlocks = d.routineBlocks,
            teachers = d.teachers,
            tuitionSessions = d.tuitionSessions,
            subjects = d.subjects,
            habits = d.habits,
            goals = d.goals,
            studySessions = d.studySessions,
            sleepLogs = d.sleepLogs
        )
    }

    fun refreshDailyAiRecommendation() {
        viewModelScope.launch {
            _dailyRecommendation.value = UiState.Loading
            aiProvider.analyzeRoutine(buildAiSnapshot())
                .onSuccess { _dailyRecommendation.value = UiState.Success(it) }
                .onFailure { _dailyRecommendation.value = UiState.Error(it.localizedMessage ?: "AI unavailable") }
        }
    }

    fun sendAiMessage(userText: String) {
        if (userText.isBlank() || _isAiStreaming.value) return
        val data = currentData() ?: return
        val snapshot = buildAiSnapshot()
        val userMsg = ChatMessage(id = "u_${System.currentTimeMillis()}", isUser = true, text = userText)
        val assistantMsgId = "a_${System.currentTimeMillis()}"
        _chatMessages.value = _chatMessages.value + userMsg + ChatMessage(id = assistantMsgId, isUser = false, text = "...")

        viewModelScope.launch {
            _isAiStreaming.value = true
            try {
                // First detect structured schedule/task intent if Advisor or Autopilot
                val proposalRes = aiProvider.suggestSchedule(userText, snapshot)
                val proposal = proposalRes.getOrNull()?.takeIf { it.actionType != "NONE" }

                if (proposal != null) {
                    val mode = safeEnum(data.profile.aiMode, AiMode.ADVISOR)
                    var autoApplied = false
                    if (mode == AiMode.AUTOPILOT && proposal.actionType == "CREATE_TASK" && proposal.conflictWarning.isBlank()) {
                        applyProposalInternal(proposal, data)
                        autoApplied = true
                    }
                    val replyText = buildString {
                        append(proposal.summary)
                        if (proposal.conflictWarning.isNotBlank()) append("\n\nConflict Check: ${proposal.conflictWarning}")
                        if (autoApplied) append("\n\n(Autopilot mode applied this low-risk task automatically.)")
                    }
                    _chatMessages.value = _chatMessages.value.map {
                        if (it.id == assistantMsgId) it.copy(text = replyText, proposal = proposal, proposalApplied = autoApplied) else it
                    }
                } else {
                    aiProvider.stream(userText, "USER DATA CONTEXT:\n$snapshot").collect { partial ->
                        _chatMessages.value = _chatMessages.value.map {
                            if (it.id == assistantMsgId) it.copy(text = partial) else it
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _chatMessages.value = _chatMessages.value.map {
                    if (it.id == assistantMsgId) it.copy(text = "Error: ${e.localizedMessage ?: "AI request failed"}") else it
                }
            } finally {
                _isAiStreaming.value = false
            }
        }
    }

    fun approveProposal(messageId: String, proposal: AiScheduleProposal) {
        val data = currentData() ?: return
        viewModelScope.launch {
            applyProposalInternal(proposal, data)
            _chatMessages.value = _chatMessages.value.map {
                if (it.id == messageId) it.copy(proposalApplied = true) else it
            }
        }
    }

    private suspend fun applyProposalInternal(proposal: AiScheduleProposal, data: BaddieDashboardData) {
        val today = BaddieDomainLogic.todayIso(data.profile.timezone)
        when (proposal.actionType) {
            "RESCHEDULE_TUITION" -> {
                val targetSession = data.tuitionSessions.firstOrNull { s ->
                    s.status in listOf(TuitionStatus.SCHEDULED.name, TuitionStatus.CONFIRMED.name) &&
                        (s.subject.contains(proposal.subject, true) ||
                            (proposal.teacherName.isNotBlank() && s.teacherName.contains(proposal.teacherName, true)))
                }
                val nextDate = proposal.targetDate.ifBlank { BaddieDomainLogic.todayInZoneDate(data.profile.timezone).plusDays(1).toString() }
                val nextTime = proposal.targetTime.ifBlank { targetSession?.startTime ?: "16:00" }
                if (targetSession != null) {
                    repository.rescheduleTuitionSession(currentUserId, targetSession, nextDate, nextTime)
                } else {
                    repository.addTuitionSession(
                        currentUserId,
                        TuitionSession(
                            teacherName = proposal.teacherName,
                            subject = proposal.subject.ifBlank { "Tuition" },
                            sessionDate = nextDate,
                            startTime = nextTime
                        )
                    )
                }
            }
            "CREATE_STUDY_PLAN", "CREATE_TASK" -> {
                val titles = proposal.proposedTaskTitles.ifEmpty { listOf(proposal.summary.take(80)) }
                titles.forEach { title ->
                    repository.addTask(
                        currentUserId,
                        BaddieTask(
                            title = title,
                            category = TaskCategory.STUDY.name,
                            subject = proposal.subject,
                            priority = TaskPriority.HIGH.name,
                            dueDate = proposal.targetDate.ifBlank { today }
                        )
                    )
                }
            }
        }
        repository.logAiAction(
            currentUserId,
            AiActionLog(
                mode = data.profile.aiMode,
                actionType = proposal.actionType,
                summary = proposal.summary.take(500),
                approved = true
            )
        )
        showMsg("AI change applied & logged")
    }

    fun generateWeeklyReport() {
        viewModelScope.launch {
            _weeklyReport.value = UiState.Loading
            aiProvider.generateWeeklyReport(buildAiSnapshot())
                .onSuccess { _weeklyReport.value = UiState.Success(it) }
                .onFailure { _weeklyReport.value = UiState.Error(it.localizedMessage ?: "Failed to generate report") }
        }
    }

    // --- Telegram, Uptime & Health ---

    fun saveTelegramConfig(token: String, chatId: String, enabled: Boolean, prefs: List<String>) {
        viewModelScope.launch {
            secretsStore.saveTelegramCredentials(token, chatId)
            repository.updateProfile(
                currentUserId,
                mapOf(
                    "telegramEnabled" to enabled,
                    "telegramChatId" to chatId.trim(),
                    "telegramPrefs" to prefs
                )
            )
            showMsg("Telegram settings saved")
        }
    }

    fun testTelegramConnection() {
        viewModelScope.launch {
            telegramProvider.testConnection()
                .onSuccess { bot ->
                    val chatId = secretsStore.getTelegramChatId()
                    if (chatId.isNotBlank()) {
                        telegramProvider.send(chatId, "Baddie Connected", "Telegram integration verified for $bot.")
                    }
                    showMsg("Connected to $bot")
                }
                .onFailure { showMsg(it.localizedMessage ?: "Telegram test failed") }
        }
    }

    fun sendMorningBriefingNow() {
        val data = currentData() ?: return
        viewModelScope.launch {
            val chatId = secretsStore.getTelegramChatId()
            val msg = BaddieDomainLogic.buildMorningBriefingMessage(
                data.profile.timezone,
                data.tasks,
                data.tuitionSessions,
                data.goals
            )
            telegramProvider.send(chatId, "Baddie Briefing", msg)
                .onSuccess { showMsg("Briefing sent to Telegram") }
                .onFailure { showMsg(it.localizedMessage ?: "Failed to send briefing") }
        }
    }

    fun pollAndReplyTelegramCommands() {
        val data = currentData() ?: return
        viewModelScope.launch {
            telegramProvider.pollCommands()
                .onSuccess { cmds ->
                    if (cmds.isEmpty()) {
                        showMsg("No new Telegram commands")
                    } else {
                        val chatId = secretsStore.getTelegramChatId()
                        cmds.forEach { cmd ->
                            val reply = BaddieDomainLogic.answerTelegramCommand(
                                cmd.command,
                                data.profile.timezone,
                                data.tasks,
                                data.tuitionSessions,
                                data.routineBlocks,
                                data.goals,
                                data.studySessions
                            )
                            telegramProvider.send(chatId, "", reply)
                        }
                        showMsg("Processed ${cmds.size} Telegram command(s)")
                    }
                }
                .onFailure { showMsg(it.localizedMessage ?: "Failed to poll Telegram") }
        }
    }

    fun saveUptimeKeyAndRefresh(apiKey: String) {
        viewModelScope.launch {
            secretsStore.saveUptimeApiKey(apiKey)
            repository.updateProfile(currentUserId, mapOf("uptimeEnabled" to apiKey.isNotBlank()))
            refreshSystemHealth()
        }
    }

    fun refreshSystemHealth() {
        viewModelScope.launch {
            _dbHealth.value = repository.healthCheck()
            _aiHealth.value = aiProvider.healthCheck()
            _uptimeStatus.value = uptimeProvider.fetchMonitors()
        }
    }

    // --- Export & Backup ---

    fun exportFullBackupJson(): String {
        val d = currentData() ?: BaddieDashboardData()
        return BackupManager.exportFullJson(
            FullBackupSnapshot(
                profile = d.profile,
                tasks = d.tasks,
                routineBlocks = d.routineBlocks,
                teachers = d.teachers,
                tuitionSessions = d.tuitionSessions,
                subjects = d.subjects,
                studySessions = d.studySessions,
                habits = d.habits,
                goals = d.goals,
                journalEntries = d.journalEntries,
                sleepLogs = d.sleepLogs
            )
        )
    }

    fun exportTasksCsv(): String = BackupManager.exportTasksCsv(currentData()?.tasks.orEmpty())

    fun exportStudyAndTuitionCsv(): String {
        val d = currentData() ?: return ""
        return BackupManager.exportStudyAndTuitionCsv(d.studySessions, d.tuitionSessions)
    }

    fun restoreFromBackupJson(rawJson: String) {
        viewModelScope.launch {
            BackupManager.restoreBackup(currentUserId, rawJson, repository)
                .onSuccess { count -> showMsg("Restored $count items cleanly") }
                .onFailure { showMsg(it.localizedMessage ?: "Invalid backup") }
        }
    }
}
