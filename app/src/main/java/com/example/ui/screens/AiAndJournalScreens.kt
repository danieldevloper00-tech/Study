package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.AiScheduleProposal
import com.example.core.AiWeeklyReport
import com.example.data.domain.BaddieDomainLogic
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.LocalBaddieColors
import com.example.ui.theme.LocalBaddieSpacing
import com.example.ui.viewmodel.BaddieDashboardData
import com.example.ui.viewmodel.UiState

enum class MoreDestination(val title: String, val subtitle: String, val icon: ImageVector) {
    HUB("More", "Workspace modules, AI, wellness, analytics & settings", Icons.Rounded.GridView),
    AI("AI Assistant", "Gemini advisor, natural language scheduler & behaviour profile", Icons.Rounded.AutoAwesome),
    JOURNAL_SLEEP("Journal & Sleep", "Private daily check-in, reflections & sleep consistency", Icons.Rounded.Bedtime),
    ANALYTICS("Analytics", "Real progress charts, activity heatmap & weekly AI report", Icons.Rounded.BarChart),
    INTEGRATIONS("Telegram & Uptime", "Bot notifications, smart reminders & UptimeRobot monitors", Icons.Rounded.NotificationsActive),
    SETTINGS("Settings & Backup", "Appearance, timezone, JSON/CSV export & restore", Icons.Rounded.Settings)
}

@Composable
fun MoreHubScreen(
    data: BaddieDashboardData,
    onNavigate: (MoreDestination) -> Unit
) {
    val spacing = LocalBaddieSpacing.current
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.itemGap)
    ) {
        item {
            Column {
                Text("More", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Baddie modules • ${data.profile.displayName} • ${data.profile.timezone}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        items(MoreDestination.entries.filter { it != MoreDestination.HUB }) { dest ->
            BaddieCard(
                onClick = { onNavigate(dest) },
                modifier = Modifier.testTag("more_item_${dest.name}")
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(44.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(dest.icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(dest.title, style = MaterialTheme.typography.titleMedium)
                        Text(dest.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    data: BaddieDashboardData,
    messages: List<ChatMessage>,
    isStreaming: Boolean,
    onSendMessage: (String) -> Unit,
    onApproveProposal: (String, AiScheduleProposal) -> Unit,
    onChangeAiMode: (AiMode) -> Unit,
    onBack: () -> Unit,
    enableBackHandler: Boolean = true,
    showBackButton: Boolean = true
) {
    if (enableBackHandler) {
        BackHandler(onBack = onBack)
    }
    val colors = LocalBaddieColors.current
    var input by remember { mutableStateOf("") }
    val currentMode = safeEnum(data.profile.aiMode, AiMode.ADVISOR)
    val behaviour = remember(data.tasks, data.studySessions, data.habits, data.tuitionSessions, data.sleepLogs) {
        BaddieDomainLogic.computeBehaviourProfile(
            data.tasks,
            data.studySessions,
            data.habits,
            data.tuitionSessions,
            data.sleepLogs
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        TopAppBar(
            title = { Text("Baddie AI Assistant") },
            navigationIcon = {
                if (showBackButton) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            AiMode.entries.forEachIndexed { idx, mode ->
                SegmentedButton(
                    selected = currentMode == mode,
                    onClick = { onChangeAiMode(mode) },
                    shape = SegmentedButtonDefaults.itemShape(idx, 3)
                ) { Text(mode.label) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(currentMode.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp)
        ) {
            item {
                BaddieCard(elevated = true) {
                    Text("Behaviour Profile", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    if (!behaviour.hasSufficientData) {
                        Text(behaviour.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        Text(
                            buildString {
                                behaviour.taskCompletionRatePercent?.let { append("Task completion: $it%  ") }
                                behaviour.averageStudyMinutesPerDay?.let { append("• Avg study: ${it}m/day  ") }
                                behaviour.mostProductiveWeekday?.let { append("• Peak day: $it  ") }
                                behaviour.tuitionAttendanceRatePercent?.let { append("• Tuition attendance: $it%  ") }
                                if (behaviour.typicalWakeTime != null && behaviour.typicalSleepTime != null) {
                                    append("• Sleep: ${behaviour.typicalSleepTime}–${behaviour.typicalWakeTime}")
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (messages.isEmpty()) {
                item {
                    BaddieCard {
                        Text("Try asking Gemini:", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        listOf(
                            "My math teacher is coming tomorrow instead.",
                            "I have three physics chapters to finish this week.",
                            "Analyze my productivity today and suggest priorities."
                        ).forEach { prompt ->
                            AssistChip(
                                onClick = { onSendMessage(prompt) },
                                label = { Text(prompt) },
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            } else {
                items(messages, key = { it.id }) { msg ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = if (msg.isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, colors.border)
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(
                                text = if (msg.isUser) "You" else "Baddie AI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(msg.text, style = MaterialTheme.typography.bodyMedium)
                            msg.proposal?.let { prop ->
                                if (msg.proposalApplied) {
                                    BaddiePill("Applied & logged", colors.success)
                                } else if (currentMode != AiMode.OBSERVER) {
                                    Button(
                                        onClick = { onApproveProposal(msg.id, prop) },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text("Approve & Apply Change")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (data.aiLogs.isNotEmpty()) {
                item {
                    BaddieSectionHeader("Recent AI Activity Log", "${data.aiLogs.size} entries")
                }
                items(data.aiLogs.take(4), key = { "ailog_${it.id}" }) { log ->
                    BaddieCard(elevated = true) {
                        Text("${log.actionType} (${log.mode})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(log.summary, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Message Baddie AI...") },
                modifier = Modifier
                    .weight(1f)
                    .testTag("ai_chat_input"),
                shape = RoundedCornerShape(12.dp)
            )
            IconButton(
                onClick = {
                    if (input.isNotBlank() && !isStreaming) {
                        onSendMessage(input.trim())
                        input = ""
                    }
                },
                enabled = !isStreaming,
                modifier = Modifier
                    .minimumInteractiveComponentSize()
                    .testTag("ai_chat_send")
            ) {
                if (isStreaming) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JournalAndSleepScreen(
    data: BaddieDashboardData,
    onAddJournal: (String, Int, String, String, String, Boolean) -> Unit,
    onDeleteJournal: (String) -> Unit,
    onAddSleepLog: (String, String, Int, String, Int, String) -> Unit,
    onDeleteSleepLog: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var tab by remember { mutableStateOf("JOURNAL") }
    var showJournalDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        TopAppBar(
            title = { Text("Journal & Wellness") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(selected = tab == "JOURNAL", onClick = { tab = "JOURNAL" }, shape = SegmentedButtonDefaults.itemShape(0, 2)) {
                Text("Private Journal")
            }
            SegmentedButton(selected = tab == "SLEEP", onClick = { tab = "SLEEP" }, shape = SegmentedButtonDefaults.itemShape(1, 2)) {
                Text("Sleep & Wellness")
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            if (tab == "JOURNAL") {
                item {
                    Button(onClick = { showJournalDialog = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("+ New Private Journal Check-in")
                    }
                }
                if (data.journalEntries.isEmpty()) {
                    item {
                        BaddieEmptyState(
                            title = "Private journal is empty",
                            subtitle = "Record daily mood, energy, wins, and difficulties. AI summaries run only if you opt in.",
                            actionLabel = "Write Today's Entry",
                            onAction = { showJournalDialog = true }
                        )
                    }
                } else {
                    items(data.journalEntries, key = { it.id }) { entry ->
                        BaddieCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${entry.entryDate} • ${entry.mood} (Energy ${entry.energyLevel}/5)", style = MaterialTheme.typography.titleSmall)
                                IconButton(onClick = { onDeleteJournal(entry.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete entry")
                                }
                            }
                            if (entry.content.isNotBlank()) Text(entry.content, style = MaterialTheme.typography.bodyMedium)
                            if (entry.wins.isNotBlank()) Text("Win: ${entry.wins}", style = MaterialTheme.typography.bodySmall, color = LocalBaddieColors.current.success)
                            if (entry.difficulties.isNotBlank()) Text("Difficulty: ${entry.difficulties}", style = MaterialTheme.typography.bodySmall, color = LocalBaddieColors.current.warning)
                            if (entry.aiSummary.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                Text("AI Reflection: ${entry.aiSummary}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            } else {
                item {
                    Button(onClick = { showSleepDialog = true }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                        Text("+ Log Sleep & Exercise")
                    }
                }
                if (data.sleepLogs.isNotEmpty()) {
                    item {
                        val bars = data.sleepLogs.take(7).reversed().map {
                            BarDatum(it.logDate.takeLast(2), it.durationMinutes / 60f)
                        }
                        BaddieCard {
                            Text("Sleep Consistency (Hours)", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(8.dp))
                            BaddieBarChart(bars)
                        }
                    }
                    items(data.sleepLogs, key = { it.id }) { log ->
                        BaddieCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${log.logDate} • ${log.durationMinutes / 60}h ${log.durationMinutes % 60}m", style = MaterialTheme.typography.titleSmall)
                                    Text("${log.sleepTime} → ${log.wakeTime} • Energy ${log.energyLevel}/5 • Exercise ${log.exerciseMinutes}m", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { onDeleteSleepLog(log.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete sleep log")
                                }
                            }
                        }
                    }
                } else {
                    item {
                        BaddieEmptyState(
                            title = "No manual sleep logs yet",
                            subtitle = "Log sleep and wake times manually. Baddie never fabricates physiological telemetry.",
                            actionLabel = "Log Sleep",
                            onAction = { showSleepDialog = true }
                        )
                    }
                }
            }
        }
    }

    if (showJournalDialog) {
        var mood by remember { mutableStateOf("Calm") }
        var energy by remember { mutableIntStateOf(4) }
        var content by remember { mutableStateOf("") }
        var wins by remember { mutableStateOf("") }
        var diff by remember { mutableStateOf("") }
        var summarizeAi by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { showJournalDialog = false },
            title = { Text("Daily Journal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("Calm", "Focused", "Tired", "Stressed").forEach { m ->
                            FilterChip(selected = mood == m, onClick = { mood = m }, label = { Text(m) })
                        }
                    }
                    Text("Energy: $energy/5", style = MaterialTheme.typography.labelLarge)
                    Slider(value = energy.toFloat(), onValueChange = { energy = it.toInt() }, valueRange = 1f..5f, steps = 3)
                    OutlinedTextField(value = content, onValueChange = { content = it }, label = { Text("Reflection") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = wins, onValueChange = { wins = it }, label = { Text("Today's Wins") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = diff, onValueChange = { diff = it }, label = { Text("Difficulties") }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Summarize with AI", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = summarizeAi, onCheckedChange = { summarizeAi = it })
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAddJournal(mood, energy, content.trim(), wins.trim(), diff.trim(), summarizeAi)
                    showJournalDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showJournalDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSleepDialog) {
        var sleepTime by remember { mutableStateOf("23:30") }
        var wakeTime by remember { mutableStateOf("06:30") }
        var energy by remember { mutableIntStateOf(4) }
        var exerciseStr by remember { mutableStateOf("20") }
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("Log Sleep & Wellness") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = sleepTime, onValueChange = { sleepTime = it }, label = { Text("Sleep (HH:mm)") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = wakeTime, onValueChange = { wakeTime = it }, label = { Text("Wake (HH:mm)") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = exerciseStr, onValueChange = { exerciseStr = it }, label = { Text("Exercise (minutes)") }, modifier = Modifier.fillMaxWidth())
                    Text("Morning Energy: $energy/5")
                    Slider(value = energy.toFloat(), onValueChange = { energy = it.toInt() }, valueRange = 1f..5f, steps = 3)
                }
            },
            confirmButton = {
                Button(onClick = {
                    onAddSleepLog(sleepTime, wakeTime, energy, "Rested", exerciseStr.toIntOrNull() ?: 0, "")
                    showSleepDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSleepDialog = false }) { Text("Cancel") } }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    data: BaddieDashboardData,
    weeklyReport: UiState<AiWeeklyReport>?,
    onGenerateWeeklyReport: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var rangeDays by remember { mutableIntStateOf(7) }
    val today = BaddieDomainLogic.todayInZoneDate(data.profile.timezone)
    val startDateIso = today.minusDays((rangeDays - 1).toLong()).toString()

    val filteredStudy = data.studySessions.filter { it.sessionDate >= startDateIso }
    val totalStudyMin = filteredStudy.sumOf { it.durationMinutes }
    val completedTasks = data.tasks.count { it.completed }
    val tuitionCompleted = data.tuitionSessions.count { it.status == TuitionStatus.COMPLETED.name }
    val tuitionMissed = data.tuitionSessions.count { it.status in listOf(TuitionStatus.TEACHER_ABSENT.name, TuitionStatus.STUDENT_ABSENT.name, TuitionStatus.CANCELLED.name) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        TopAppBar(
            title = { Text("Analytics & Reports") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(1 to "Today", 7 to "7d", 30 to "30d", 90 to "90d").forEach { (days, label) ->
                FilterChip(selected = rangeDays == days, onClick = { rangeDays = days }, label = { Text(label) })
            }
        }

        Spacer(Modifier.height(10.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            if (data.tasks.isEmpty() && data.studySessions.isEmpty() && data.tuitionSessions.isEmpty()) {
                item {
                    BaddieEmptyState(
                        title = "No analytics recorded yet",
                        subtitle = "Complete tasks, study timer sessions, or tuition check-ins to populate real insights."
                    )
                }
            } else {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        BaddieCard(Modifier.weight(1f), elevated = true) {
                            Text("STUDY TIME", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            Text("${totalStudyMin / 60}h ${totalStudyMin % 60}m", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        }
                        BaddieCard(Modifier.weight(1f), elevated = true) {
                            Text("TASKS DONE", style = MaterialTheme.typography.labelSmall, color = LocalBaddieColors.current.success)
                            Text("$completedTasks / ${data.tasks.size}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        }
                        BaddieCard(Modifier.weight(1f), elevated = true) {
                            Text("TUITION", style = MaterialTheme.typography.labelSmall, color = LocalBaddieColors.current.info)
                            Text("$tuitionCompleted / ${tuitionCompleted + tuitionMissed}", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                item {
                    val subjectBars = filteredStudy.groupBy { it.subjectName }
                        .map { (name, list) -> BarDatum(name.take(5), list.sumOf { it.durationMinutes }.toFloat()) }
                        .take(6)
                    BaddieCard {
                        Text("Study Minutes by Subject", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(10.dp))
                        if (subjectBars.isEmpty()) {
                            Text("No study sessions logged in this range.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            BaddieBarChart(subjectBars)
                        }
                    }
                }

                item {
                    val heatCounts = (27 downTo 0).map { offset ->
                        val dIso = today.minusDays(offset.toLong()).toString()
                        data.habits.count { dIso in it.completedDates } + data.studySessions.count { it.sessionDate == dIso }
                    }
                    BaddieCard {
                        Text("28-Day Activity Heatmap", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(10.dp))
                        BaddieActivityHeatmap(heatCounts)
                    }
                }
            }

            item {
                BaddieCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("AI Weekly Report", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = onGenerateWeeklyReport) { Text("Generate") }
                    }
                    Spacer(Modifier.height(8.dp))
                    when (weeklyReport) {
                        null -> Text("Generates an honest weekly analysis strictly from your logged data.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        is UiState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                        is UiState.Error -> Text(weeklyReport.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        is UiState.Success -> {
                            val r = weeklyReport.data
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Went well: ${r.whatWentWell}", style = MaterialTheme.typography.bodySmall)
                                Text("Missed: ${r.whatWasMissed}", style = MaterialTheme.typography.bodySmall)
                                Text("Peak days: ${r.mostProductiveDays}", style = MaterialTheme.typography.bodySmall)
                                Text("Weak subjects: ${r.weakSubjects}", style = MaterialTheme.typography.bodySmall)
                                Text("Patterns: ${r.behaviourPatterns}", style = MaterialTheme.typography.bodySmall)
                                Text("Next step: ${r.recommendations}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
