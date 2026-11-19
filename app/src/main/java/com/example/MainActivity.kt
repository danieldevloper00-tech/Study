package com.example

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.core.configureAppCheck
import com.example.data.model.BaddieTask
import com.example.data.model.JournalEntry
import com.example.data.model.TaskCategory
import com.example.data.model.TaskPriority
import com.example.data.prefs.LocalSecretsStore
import com.example.data.provider.*
import com.example.ui.auth.AuthScreen
import com.example.ui.auth.signOutUser
import com.example.ui.onboarding.OnboardingWizard
import com.example.ui.screens.*
import com.example.ui.theme.BaddieTheme
import com.example.ui.theme.LocalBaddieColors
import com.example.ui.viewmodel.BaddieDashboardData
import com.example.ui.viewmodel.BaddieViewModel
import com.example.ui.viewmodel.UiState
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

enum class PrimaryTab(val label: String, val icon: ImageVector) {
    TODAY("Today", Icons.Rounded.Home),
    TASKS("Tasks", Icons.Rounded.CheckCircleOutline),
    SCHEDULE("Schedule", Icons.Rounded.CalendarMonth),
    TRACK("Track", Icons.Rounded.Timeline),
    MORE("More", Icons.Rounded.GridView)
}

private fun FirebaseAuth.authStateFlow(): Flow<FirebaseUser?> = callbackFlow {
    val listener = FirebaseAuth.AuthStateListener { auth ->
        trySend(auth.currentUser)
    }
    addAuthStateListener(listener)
    awaitClose { removeAuthStateListener(listener) }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // App Check MUST be configured before any Firebase service or ViewModel is initialized.
        configureAppCheck(this, intent)
        enableEdgeToEdge()

        setContent {
            val currentUser by remember { Firebase.auth.authStateFlow() }
                .collectAsStateWithLifecycle(initialValue = Firebase.auth.currentUser)

            val user = currentUser
            if (user == null) {
                BaddieTheme {
                    AuthScreen(onAuthSuccess = {})
                }
            } else {
                key(user.uid) {
                    AuthenticatedBaddieRoot(
                        application = application,
                        user = user
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthenticatedBaddieRoot(
    application: Application,
    user: FirebaseUser
) {
    val vm: BaddieViewModel = viewModel(
        key = user.uid,
        factory = viewModelFactory {
            initializer {
                val dbId = application.getString(R.string.firestore_database_id)
                val firestore = FirebaseFirestore.getInstance(dbId)
                val dbProvider = FirestoreDatabaseProvider(firestore, Firebase.auth)
                val repository = BaddieRepository(dbProvider, Firebase.auth)
                val secretsStore = LocalSecretsStore(application)
                val localScheduler = LocalReminderScheduler(application)
                BaddieViewModel(
                    repository = repository,
                    aiProvider = GeminiAIProvider(),
                    telegramProvider = TelegramNotificationProvider(secretsStore, localScheduler),
                    uptimeProvider = UptimeRobotProvider(secretsStore),
                    secretsStore = secretsStore,
                    currentUserId = user.uid,
                    userDisplayName = user.displayName,
                    userEmail = user.email
                )
            }
        }
    )

    val dataState by vm.dataState.collectAsStateWithLifecycle()
    val nowClock by vm.nowClock.collectAsStateWithLifecycle()
    val timerState by vm.timerState.collectAsStateWithLifecycle()
    val dailyRecommendation by vm.dailyRecommendation.collectAsStateWithLifecycle()
    val chatMessages by vm.chatMessages.collectAsStateWithLifecycle()
    val isAiStreaming by vm.isAiStreaming.collectAsStateWithLifecycle()
    val weeklyReport by vm.weeklyReport.collectAsStateWithLifecycle()
    val uptimeStatus by vm.uptimeStatus.collectAsStateWithLifecycle()
    val dbHealth by vm.dbHealth.collectAsStateWithLifecycle()
    val aiHealth by vm.aiHealth.collectAsStateWithLifecycle()
    val savedTelegramToken by vm.savedTelegramToken.collectAsStateWithLifecycle()
    val savedTelegramChatId by vm.savedTelegramChatId.collectAsStateWithLifecycle()
    val savedUptimeKey by vm.savedUptimeKey.collectAsStateWithLifecycle()
    val feedbackMessage by vm.feedbackMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(feedbackMessage) {
        val msg = feedbackMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        vm.clearFeedback()
    }

    val loadedProfile = (dataState as? UiState.Success)?.data?.profile
    BaddieTheme(
        themeMode = loadedProfile?.themeMode ?: "DARK",
        accentPreset = loadedProfile?.accentPreset ?: "LAVENDER",
        compactLayout = loadedProfile?.compactLayout ?: false
    ) {
        when (val state = dataState) {
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            "Loading Baddie workspace...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            is UiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.background)
                        .windowInsetsPadding(WindowInsets.safeDrawing)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, LocalBaddieColors.current.border),
                        modifier = Modifier.widthIn(max = 440.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text("Sync Error", style = MaterialTheme.typography.titleLarge)
                            Text(
                                state.message,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { signOutUser(credentialManager, scope) },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Sign Out") }
                                Button(
                                    onClick = { vm.refreshSystemHealth() },
                                    modifier = Modifier.weight(1f)
                                ) { Text("Retry") }
                            }
                        }
                    }
                }
            }

            is UiState.Success -> {
                val data = state.data
                var forceOnboarding by rememberSaveable { mutableStateOf(false) }

                if (!data.profile.onboardingComplete || forceOnboarding) {
                    OnboardingWizard(
                        initialName = data.profile.displayName.ifBlank { user.displayName.orEmpty() },
                        onFinish = { name, tz, teachers, seedTuition, blocks, subjects, goals, aiMode, token, chatId, tgEnabled ->
                            vm.completeOnboarding(
                                displayName = name,
                                timezone = tz,
                                teachersToCreate = teachers,
                                seedDefaultTuitionSlots = seedTuition,
                                routineBlocks = blocks,
                                subjectNames = subjects,
                                initialGoals = goals,
                                aiMode = aiMode,
                                telegramToken = token,
                                telegramChatId = chatId,
                                telegramEnabled = tgEnabled
                            )
                            forceOnboarding = false
                        },
                        onSkipAll = {
                            vm.updateProfileSettings(mapOf("onboardingComplete" to true))
                            forceOnboarding = false
                        }
                    )
                } else {
                    BaddieMainWorkspace(
                        data = data,
                        vm = vm,
                        nowClock = nowClock,
                        timerState = timerState,
                        dailyRecommendation = dailyRecommendation,
                        chatMessages = chatMessages,
                        isAiStreaming = isAiStreaming,
                        weeklyReport = weeklyReport,
                        uptimeStatus = uptimeStatus,
                        dbHealth = dbHealth,
                        aiHealth = aiHealth,
                        savedTelegramToken = savedTelegramToken,
                        savedTelegramChatId = savedTelegramChatId,
                        savedUptimeKey = savedUptimeKey,
                        snackbarHostState = snackbarHostState,
                        onRerunOnboarding = { forceOnboarding = true },
                        onSignOut = { signOutUser(credentialManager, scope) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BaddieMainWorkspace(
    data: BaddieDashboardData,
    vm: BaddieViewModel,
    nowClock: java.time.ZonedDateTime,
    timerState: com.example.ui.viewmodel.StudyTimerState,
    dailyRecommendation: UiState<String>,
    chatMessages: List<com.example.data.model.ChatMessage>,
    isAiStreaming: Boolean,
    weeklyReport: UiState<com.example.core.AiWeeklyReport>?,
    uptimeStatus: com.example.core.UptimeStatusResult,
    dbHealth: com.example.core.ProviderHealth?,
    aiHealth: com.example.core.ProviderHealth?,
    savedTelegramToken: String,
    savedTelegramChatId: String,
    savedUptimeKey: String,
    snackbarHostState: SnackbarHostState,
    onRerunOnboarding: () -> Unit,
    onSignOut: () -> Unit
) {
    var currentTab by rememberSaveable { mutableStateOf(PrimaryTab.TODAY) }
    var moreDestination by rememberSaveable { mutableStateOf(MoreDestination.HUB) }

    val navigateToAi = {
        currentTab = PrimaryTab.MORE
        moreDestination = MoreDestination.AI
    }

    val tabContent: @Composable (Modifier) -> Unit = { contentModifier ->
        Box(modifier = contentModifier) {
            when (currentTab) {
                PrimaryTab.TODAY -> HomeDashboardScreen(
                    data = data,
                    nowClock = nowClock,
                    aiRecommendation = dailyRecommendation,
                    onRefreshAiRecommendation = { vm.refreshDailyAiRecommendation() },
                    onToggleTask = { vm.toggleTask(it) },
                    onQuickAddTask = { title, priority, category ->
                        vm.addTask(
                            BaddieTask(
                                title = title,
                                priority = priority.name,
                                category = category.name,
                                dueDate = com.example.data.domain.BaddieDomainLogic.todayIso(data.profile.timezone)
                            )
                        )
                    },
                    onQuickAddNote = { mood, content, energy ->
                        vm.addJournalEntry(
                            mood = mood,
                            energy = energy,
                            content = content,
                            wins = "",
                            difficulties = "",
                            summarizeWithAi = false
                        )
                    },
                    onToggleRoutineToday = { vm.toggleRoutineBlockToday(it) },
                    onToggleHabitToday = { habit, dateIso -> vm.toggleHabitDate(habit, dateIso) },
                    onToggleWidgetVisibility = { vm.toggleWidgetVisibility(it) },
                    onMoveWidget = { widgetId, delta -> vm.moveWidget(widgetId, delta) },
                    onPostponeTask = { vm.postponeTask(it) },
                    onRescheduleTask = { task, newDate -> vm.rescheduleTaskToDate(task, newDate) },
                    onMoveTaskToBacklog = { vm.moveToBacklog(it) },
                    onCancelTask = { vm.cancelTask(it) },
                    onNavigateToAi = navigateToAi
                )

                PrimaryTab.TASKS -> TasksScreen(
                    data = data,
                    onAddTask = { vm.addTask(it) },
                    onUpdateTask = { id, updates -> vm.updateTask(id, updates) },
                    onToggleTask = { vm.toggleTask(it) },
                    onToggleSubtask = { task, idx -> vm.toggleSubtask(task, idx) },
                    onDuplicateTask = { vm.duplicateTask(it) },
                    onPostponeTask = { vm.postponeTask(it) },
                    onBulkComplete = { vm.bulkCompleteTasks(it) },
                    onDeleteTask = { vm.deleteTask(it) }
                )

                PrimaryTab.SCHEDULE -> ScheduleScreen(
                    data = data,
                    onAddTuitionSession = { vm.addTuitionSession(it) },
                    onUpdateTuitionStatus = { id, status -> vm.updateTuitionStatus(id, status) },
                    onRescheduleTuition = { session, date, time -> vm.rescheduleTuition(session, date, time) },
                    onDeleteTuitionSession = { vm.deleteTuitionSession(it) },
                    onAddTeacher = { vm.addTeacher(it) },
                    onToggleTeacherActive = { vm.toggleTeacherActive(it) },
                    onDeleteTeacher = { vm.deleteTeacher(it) },
                    onAddRoutineBlock = { vm.addRoutineBlock(it) },
                    onToggleRoutineToday = { vm.toggleRoutineBlockToday(it) },
                    onDeleteRoutineBlock = { vm.deleteRoutineBlock(it) }
                )

                PrimaryTab.TRACK -> TrackScreen(
                    data = data,
                    timerState = timerState,
                    onConfigureTimer = { subjId, subjName, mode, mins -> vm.configureTimer(subjId, subjName, mode, mins) },
                    onToggleTimerRunning = { vm.toggleTimerRunning() },
                    onFinishTimerAndSave = { vm.finishTimerAndSave() },
                    onLogManualStudy = { subjId, subjName, mins, notes -> vm.logStudySession(subjId, subjName, mins, "MANUAL", notes) },
                    onAddSubject = { name, conf -> vm.addSubject(name, conf) },
                    onAddChapter = { subj, title -> vm.addChapterToSubject(subj, title) },
                    onCycleChapterStatus = { subj, idx -> vm.cycleChapterStatus(subj, idx) },
                    onUpdateConfidence = { subj, conf -> vm.updateSubjectConfidence(subj, conf) },
                    onAddHabit = { title, freq, target -> vm.addHabit(title, freq, target) },
                    onToggleHabitDate = { habit, dateIso -> vm.toggleHabitDate(habit, dateIso) },
                    onDeleteHabit = { vm.deleteHabit(it) },
                    onAddGoal = { vm.addGoal(it) },
                    onUpdateGoalProgress = { goal, pct -> vm.updateGoalProgress(goal, pct) },
                    onToggleGoalMilestone = { goal, idx -> vm.toggleGoalMilestone(goal, idx) },
                    onDeleteGoal = { vm.deleteGoal(it) }
                )

                PrimaryTab.MORE -> when (moreDestination) {
                    MoreDestination.HUB -> MoreHubScreen(
                        data = data,
                        onNavigate = { moreDestination = it }
                    )

                    MoreDestination.AI -> AiAssistantScreen(
                        data = data,
                        messages = chatMessages,
                        isStreaming = isAiStreaming,
                        onSendMessage = { vm.sendAiMessage(it) },
                        onApproveProposal = { msgId, prop -> vm.approveProposal(msgId, prop) },
                        onChangeAiMode = { mode -> vm.updateProfileSettings(mapOf("aiMode" to mode.name)) },
                        onBack = { moreDestination = MoreDestination.HUB }
                    )

                    MoreDestination.JOURNAL_SLEEP -> JournalAndSleepScreen(
                        data = data,
                        onAddJournal = { mood, energy, content, wins, diff, summarize ->
                            vm.addJournalEntry(mood, energy, content, wins, diff, summarize)
                        },
                        onDeleteJournal = { vm.deleteJournalEntry(it) },
                        onAddSleepLog = { sleep, wake, energy, mood, exercise, notes ->
                            vm.addSleepLog(sleep, wake, energy, mood, exercise, notes)
                        },
                        onDeleteSleepLog = { vm.deleteSleepLog(it) },
                        onBack = { moreDestination = MoreDestination.HUB }
                    )

                    MoreDestination.ANALYTICS -> AnalyticsScreen(
                        data = data,
                        weeklyReport = weeklyReport,
                        onGenerateWeeklyReport = { vm.generateWeeklyReport() },
                        onBack = { moreDestination = MoreDestination.HUB }
                    )

                    MoreDestination.INTEGRATIONS -> IntegrationsAndUptimeScreen(
                        data = data,
                        savedTelegramToken = savedTelegramToken,
                        savedTelegramChatId = savedTelegramChatId,
                        savedUptimeKey = savedUptimeKey,
                        uptimeStatus = uptimeStatus,
                        dbHealth = dbHealth,
                        aiHealth = aiHealth,
                        onSaveTelegram = { token, chatId, enabled, prefs -> vm.saveTelegramConfig(token, chatId, enabled, prefs) },
                        onTestTelegram = { vm.testTelegramConnection() },
                        onSendBriefingNow = { vm.sendMorningBriefingNow() },
                        onPollTelegramCommands = { vm.pollAndReplyTelegramCommands() },
                        onAddReminder = { title, iso, repeat -> vm.addReminder(title, iso, repeat) },
                        onDeleteReminder = { vm.deleteReminder(it) },
                        onSaveUptimeKey = { vm.saveUptimeKeyAndRefresh(it) },
                        onRefreshHealth = { vm.refreshSystemHealth() },
                        onBack = { moreDestination = MoreDestination.HUB }
                    )

                    MoreDestination.SETTINGS -> SettingsAndBackupScreen(
                        data = data,
                        onUpdateSettings = { vm.updateProfileSettings(it) },
                        onExportJson = { vm.exportFullBackupJson() },
                        onExportTasksCsv = { vm.exportTasksCsv() },
                        onExportStudyCsv = { vm.exportStudyAndTuitionCsv() },
                        onRestoreBackup = { vm.restoreFromBackupJson(it) },
                        onRerunOnboarding = onRerunOnboarding,
                        onSignOut = onSignOut,
                        onBack = { moreDestination = MoreDestination.HUB }
                    )
                }
            }
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val isWide = maxWidth >= 600.dp
        val showSideAiPane = maxWidth >= 960.dp && !(currentTab == PrimaryTab.MORE && moreDestination == MoreDestination.AI)

        if (!isWide) {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing,
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        windowInsets = WindowInsets.navigationBars
                    ) {
                        PrimaryTab.entries.forEach { tab ->
                            NavigationBarItem(
                                selected = currentTab == tab,
                                onClick = {
                                    currentTab = tab
                                    if (tab == PrimaryTab.MORE) moreDestination = MoreDestination.HUB
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_${tab.name}")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                tabContent(Modifier.fillMaxSize().padding(innerPadding))
            }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                contentWindowInsets = WindowInsets.safeDrawing
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        Spacer(Modifier.height(12.dp))
                        PrimaryTab.entries.forEach { tab ->
                            NavigationRailItem(
                                selected = currentTab == tab,
                                onClick = {
                                    currentTab = tab
                                    if (tab == PrimaryTab.MORE) moreDestination = MoreDestination.HUB
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag("nav_${tab.name}")
                            )
                        }
                    }

                    VerticalDivider(color = LocalBaddieColors.current.borderSubtle)

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        tabContent(
                            Modifier
                                .fillMaxHeight()
                                .widthIn(max = 760.dp)
                                .fillMaxWidth()
                        )
                    }

                    if (showSideAiPane) {
                        VerticalDivider(color = LocalBaddieColors.current.borderSubtle)
                        Box(
                            modifier = Modifier
                                .width(360.dp)
                                .fillMaxHeight()
                        ) {
                            AiAssistantScreen(
                                data = data,
                                messages = chatMessages,
                                isStreaming = isAiStreaming,
                                onSendMessage = { vm.sendAiMessage(it) },
                                onApproveProposal = { msgId, prop -> vm.approveProposal(msgId, prop) },
                                onChangeAiMode = { mode -> vm.updateProfileSettings(mapOf("aiMode" to mode.name)) },
                                onBack = {},
                                enableBackHandler = false,
                                showBackButton = false
                            )
                        }
                    }
                }
            }
        }
    }
}
