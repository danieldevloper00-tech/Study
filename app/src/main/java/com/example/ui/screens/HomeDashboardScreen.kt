package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.domain.BaddieDomainLogic
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.LocalBaddieColors
import com.example.ui.theme.LocalBaddieSpacing
import com.example.ui.viewmodel.BaddieDashboardData
import com.example.ui.viewmodel.UiState
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboardScreen(
    data: BaddieDashboardData,
    nowClock: ZonedDateTime,
    aiRecommendation: UiState<String>,
    onRefreshAiRecommendation: () -> Unit,
    onToggleTask: (BaddieTask) -> Unit,
    onQuickAddTask: (String, TaskPriority, TaskCategory) -> Unit,
    onQuickAddNote: (String, String, Int) -> Unit,
    onToggleRoutineToday: (RoutineBlock) -> Unit,
    onToggleHabitToday: (Habit, String) -> Unit,
    onToggleWidgetVisibility: (String) -> Unit,
    onMoveWidget: (String, Int) -> Unit,
    onPostponeTask: (BaddieTask) -> Unit,
    onRescheduleTask: (BaddieTask, String) -> Unit,
    onMoveTaskToBacklog: (BaddieTask) -> Unit,
    onCancelTask: (BaddieTask) -> Unit,
    onNavigateToAi: () -> Unit
) {
    val colors = LocalBaddieColors.current
    val spacing = LocalBaddieSpacing.current
    val today = BaddieDomainLogic.todayInZoneDate(data.profile.timezone)
    val todayIso = today.toString()
    val dayShort = BaddieDomainLogic.dayOfWeekShort(today.dayOfWeek)

    var showMyDayTimeline by remember { mutableStateOf(false) }
    var showCustomizeWidgets by remember { mutableStateOf(false) }
    var showQuickTaskDialog by remember { mutableStateOf(false) }
    var showQuickNoteDialog by remember { mutableStateOf(false) }
    var showEndOfDayReview by remember { mutableStateOf(false) }

    val todayTasks = remember(data.tasks, todayIso) {
        data.tasks.filter {
            it.status != TaskStatus.CANCELLED.name && (it.dueDate.isBlank() || it.dueDate <= todayIso)
        }
    }
    val pendingTodayTasks = todayTasks.filter { !it.completed }
    val completedTodayCount = todayTasks.count { it.completed }
    val completionPct = if (todayTasks.isEmpty()) 0 else (completedTodayCount * 100) / todayTasks.size

    val todayTuitions = remember(data.tuitionSessions, todayIso) {
        data.tuitionSessions.filter {
            it.sessionDate == todayIso && it.status !in listOf(TuitionStatus.CANCELLED.name, TuitionStatus.RESCHEDULED.name)
        }.sortedBy { it.startTime }
    }
    val nextTuition = todayTuitions.firstOrNull {
        it.status in listOf(TuitionStatus.SCHEDULED.name, TuitionStatus.CONFIRMED.name)
    } ?: data.tuitionSessions.firstOrNull {
        it.sessionDate > todayIso && it.status in listOf(TuitionStatus.SCHEDULED.name, TuitionStatus.CONFIRMED.name)
    }

    val todayRoutine = remember(data.routineBlocks, dayShort) {
        data.routineBlocks.filter { dayShort in it.repeatDays }.sortedBy { it.startTime }
    }
    val nextRoutine = todayRoutine.firstOrNull { it.lastCompletedDate != todayIso }

    val todayStudyMinutes = remember(data.studySessions, todayIso) {
        data.studySessions.filter { it.sessionDate == todayIso }.sumOf { it.durationMinutes }
    }

    val activeHabits = data.habits.filter { it.active }
    val habitsDoneToday = activeHabits.count { todayIso in it.completedDates }
    val topStreak = activeHabits.maxOfOrNull { BaddieDomainLogic.computeHabitStreak(it, today) } ?: 0
    val latestSleep = data.sleepLogs.firstOrNull()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.sectionGap)
    ) {
        // Header: Greeting, Date, Live Clock, Overview / My Day Switcher
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "${BaddieDomainLogic.greetingForHour(nowClock.hour)}, ${data.profile.displayName}",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = nowClock.format(DateTimeFormatter.ofPattern("EEEE, d MMM yyyy", Locale.US)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = nowClock.format(DateTimeFormatter.ofPattern("h:mm:ss a", Locale.US)),
                            style = MaterialTheme.typography.titleMedium,
                            fontFamily = com.example.ui.theme.JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = data.profile.timezone,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Quick actions row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(
                        onClick = { showQuickTaskDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("quick_add_task_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.AddTask, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Quick Task", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { showQuickNoteDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("quick_add_note_button"),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(Icons.Rounded.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Quick Note", maxLines = 1)
                    }
                    IconButton(
                        onClick = { showCustomizeWidgets = true },
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("customize_dashboard_button")
                    ) {
                        Icon(Icons.Rounded.Tune, contentDescription = "Customize dashboard layout")
                    }
                }

                // Mode Switcher: Dashboard Overview vs My Day Timeline
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !showMyDayTimeline,
                        onClick = { showMyDayTimeline = false },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                        modifier = Modifier.testTag("tab_overview")
                    ) { Text("Overview") }
                    SegmentedButton(
                        selected = showMyDayTimeline,
                        onClick = { showMyDayTimeline = true },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                        modifier = Modifier.testTag("tab_my_day")
                    ) { Text("My Day") }
                }
            }
        }

        if (showMyDayTimeline) {
            item {
                BaddieSectionHeader(
                    title = "My Day Timeline",
                    subtitle = "$dayShort • ${todayRoutine.size} routine blocks • ${todayTuitions.size} tuitions",
                    trailing = {
                        if (pendingTodayTasks.isNotEmpty()) {
                            TextButton(
                                onClick = { showEndOfDayReview = true },
                                modifier = Modifier.testTag("eod_review_button")
                            ) {
                                Text("End-of-Day Review")
                            }
                        }
                    }
                )
            }

            if (todayRoutine.isEmpty() && todayTuitions.isEmpty() && todayTasks.isEmpty()) {
                item {
                    BaddieEmptyState(
                        title = "Your day is open",
                        subtitle = "Add routine blocks, tuition sessions, or tasks to build your adaptive timeline.",
                        actionLabel = "Add Quick Task",
                        onAction = { showQuickTaskDialog = true }
                    )
                }
            } else {
                items(todayRoutine, key = { "rb_${it.id}" }) { block ->
                    val done = block.lastCompletedDate == todayIso
                    BaddieCard(elevated = !done) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BaddieCheckbox(
                                checked = done,
                                onToggle = { onToggleRoutineToday(block) },
                                testTag = "myday_routine_${block.id}"
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = block.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                                )
                                Text(
                                    text = "${BaddieDomainLogic.formatDisplayTime(block.startTime)} – ${BaddieDomainLogic.formatDisplayTime(block.endTime)}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BaddiePill(block.blockType)
                        }
                    }
                }
                items(todayTuitions, key = { "tu_${it.id}" }) { session ->
                    BaddieCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("Tuition: ${session.subject}", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${BaddieDomainLogic.formatDisplayTime(session.startTime)} (${session.durationMinutes}m)" +
                                        if (session.teacherName.isNotBlank()) " • ${session.teacherName}" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BaddiePill(session.status, colors.info)
                        }
                    }
                }
                items(todayTasks, key = { "tk_${it.id}" }) { task ->
                    BaddieCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            BaddieCheckbox(
                                checked = task.completed,
                                onToggle = { onToggleTask(task) },
                                testTag = "myday_task_${task.id}"
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                                )
                                Text(
                                    text = "${task.category} • ${task.estimatedMinutes}m",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            BaddiePill(task.priority)
                        }
                    }
                }
            }
        } else {
            // Editable Dashboard Widgets
            val visibleWidgets = data.profile.widgetOrder.filter { it !in data.profile.hiddenWidgets }
            if (visibleWidgets.isEmpty()) {
                item {
                    BaddieEmptyState(
                        title = "All dashboard widgets hidden",
                        subtitle = "Tap the customize icon above to enable widgets.",
                        actionLabel = "Customize Layout",
                        onAction = { showCustomizeWidgets = true }
                    )
                }
            } else {
                items(visibleWidgets, key = { it }) { widgetId ->
                    when (widgetId) {
                        "PROGRESS" -> {
                            BaddieCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Today's Progress", style = MaterialTheme.typography.titleMedium)
                                        Text(
                                            if (todayTasks.isEmpty()) "No tasks scheduled yet"
                                            else "$completedTodayCount of ${todayTasks.size} completed • ${pendingTodayTasks.size} remaining",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = "$completionPct%",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                LinearProgressIndicator(
                                    progress = { completionPct / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp),
                                    trackColor = colors.borderSubtle
                                )
                                if (pendingTodayTasks.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = { showEndOfDayReview = true }) {
                                            Text("Review ${pendingTodayTasks.size} unfinished")
                                        }
                                    }
                                }
                            }
                        }

                        "NEXT_UP" -> {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                BaddieCard(modifier = Modifier.weight(1f), elevated = true) {
                                    Text("NEXT EVENT", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = nextRoutine?.title ?: "Free window",
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = nextRoutine?.let { BaddieDomainLogic.formatDisplayTime(it.startTime) } ?: "No pending block",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                BaddieCard(modifier = Modifier.weight(1f), elevated = true) {
                                    Text("NEXT TUITION", style = MaterialTheme.typography.labelSmall, color = colors.info)
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = nextTuition?.subject ?: "None scheduled",
                                        style = MaterialTheme.typography.titleSmall,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = nextTuition?.let { "${it.sessionDate} • ${BaddieDomainLogic.formatDisplayTime(it.startTime)}" } ?: "Tap Schedule to add",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        "AI_RECOMMENDATION" -> {
                            BaddieCard(onClick = onNavigateToAi) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                        Text("Daily AI Insight", style = MaterialTheme.typography.titleSmall)
                                    }
                                    IconButton(
                                        onClick = onRefreshAiRecommendation,
                                        modifier = Modifier.testTag("refresh_ai_daily_btn")
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = "Refresh AI insight", modifier = Modifier.size(18.dp))
                                    }
                                }
                                when (aiRecommendation) {
                                    is UiState.Loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                                    is UiState.Success -> Text(aiRecommendation.data, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    is UiState.Error -> Text(aiRecommendation.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }

                        "STUDY_TARGET" -> {
                            val target = data.profile.dailyStudyTargetMinutes.coerceAtLeast(1)
                            val pct = ((todayStudyMinutes * 100) / target).coerceIn(0, 100)
                            BaddieCard {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Today's Study Target", style = MaterialTheme.typography.titleSmall)
                                    Text("${todayStudyMinutes}m / ${target}m", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                Spacer(Modifier.height(8.dp))
                                LinearProgressIndicator(
                                    progress = { pct / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    trackColor = colors.borderSubtle
                                )
                            }
                        }

                        "HABITS_STREAK" -> {
                            BaddieCard {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("Habits & Streak", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            if (activeHabits.isEmpty()) "No habits configured yet"
                                            else "$habitsDoneToday/${activeHabits.size} checked today • Best streak: ${topStreak}d",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    BaddiePill("${topStreak}d streak", colors.success)
                                }
                                if (activeHabits.isNotEmpty()) {
                                    Spacer(Modifier.height(8.dp))
                                    activeHabits.take(3).forEach { habit ->
                                        val done = todayIso in habit.completedDates
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            BaddieCheckbox(
                                                checked = done,
                                                onToggle = { onToggleHabitToday(habit, todayIso) },
                                                testTag = "dash_habit_${habit.id}"
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(habit.title, style = MaterialTheme.typography.bodyMedium)
                                        }
                                    }
                                }
                            }
                        }

                        "SLEEP_WELLNESS" -> {
                            BaddieCard {
                                Text("Sleep & Wellness", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(4.dp))
                                if (latestSleep == null) {
                                    Text(
                                        "No sleep logs yet. Log manually under More → Journal & Sleep.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                } else {
                                    Text(
                                        "Last logged (${latestSleep.logDate}): ${latestSleep.durationMinutes / 60}h ${latestSleep.durationMinutes % 60}m • Energy ${latestSleep.energyLevel}/5",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }

                        "WEEKLY_OVERVIEW" -> {
                            val days = (6 downTo 0).map { offset ->
                                val d = today.minusDays(offset.toLong())
                                val count = data.tasks.count { it.completed && it.dueDate == d.toString() }
                                BarDatum(d.dayOfWeek.name.take(2), count.toFloat())
                            }
                            BaddieCard {
                                Text("7-Day Completed Tasks", style = MaterialTheme.typography.titleSmall)
                                Spacer(Modifier.height(10.dp))
                                BaddieBarChart(data = days)
                            }
                        }
                    }
                }
            }
        }
    }

    // Quick Task Dialog
    if (showQuickTaskDialog) {
        var title by remember { mutableStateOf("") }
        var priority by remember { mutableStateOf(TaskPriority.MEDIUM) }
        var category by remember { mutableStateOf(TaskCategory.STUDY) }
        AlertDialog(
            onDismissRequest = { showQuickTaskDialog = false },
            title = { Text("Quick Add Task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task title") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("quick_task_input")
                    )
                    Text("Priority", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TaskPriority.entries.forEach { p ->
                            FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p.label) })
                        }
                    }
                    Text("Category", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(TaskCategory.STUDY, TaskCategory.HOMEWORK, TaskCategory.TUITION, TaskCategory.PERSONAL).forEach { c ->
                            FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (title.isNotBlank()) {
                            onQuickAddTask(title.trim(), priority, category)
                            showQuickTaskDialog = false
                        }
                    },
                    modifier = Modifier.testTag("quick_task_save")
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showQuickTaskDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Quick Note Dialog
    if (showQuickNoteDialog) {
        var note by remember { mutableStateOf("") }
        var mood by remember { mutableStateOf("Calm") }
        var energy by remember { mutableIntStateOf(4) }
        AlertDialog(
            onDismissRequest = { showQuickNoteDialog = false },
            title = { Text("Quick Journal Note") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = note,
                        onValueChange = { note = it },
                        label = { Text("What's on your mind?") },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Calm", "Focused", "Tired", "Motivated").forEach { m ->
                            FilterChip(selected = mood == m, onClick = { mood = m }, label = { Text(m) })
                        }
                    }
                    Text("Energy: $energy/5", style = MaterialTheme.typography.labelLarge)
                    Slider(value = energy.toFloat(), onValueChange = { energy = it.toInt() }, valueRange = 1f..5f, steps = 3)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (note.isNotBlank()) {
                        onQuickAddNote(mood, note.trim(), energy)
                        showQuickNoteDialog = false
                    }
                }) { Text("Save Note") }
            },
            dismissButton = {
                TextButton(onClick = { showQuickNoteDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Customize Dashboard Widgets Dialog
    if (showCustomizeWidgets) {
        AlertDialog(
            onDismissRequest = { showCustomizeWidgets = false },
            title = { Text("Customize Dashboard") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    data.profile.widgetOrder.forEach { w ->
                        val visible = w !in data.profile.hiddenWidgets
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = colors.surfaceElevated,
                            border = BorderStroke(1.dp, colors.border)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(w.replace("_", " "), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { onMoveWidget(w, -1) }) {
                                        Icon(Icons.Rounded.KeyboardArrowUp, contentDescription = "Move up")
                                    }
                                    IconButton(onClick = { onMoveWidget(w, 1) }) {
                                        Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = "Move down")
                                    }
                                    Switch(checked = visible, onCheckedChange = { onToggleWidgetVisibility(w) })
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showCustomizeWidgets = false }) { Text("Done") }
            }
        )
    }

    // End-of-Day Smart Incomplete Task Handler
    if (showEndOfDayReview) {
        AlertDialog(
            onDismissRequest = { showEndOfDayReview = false },
            title = { Text("Unfinished Tasks Review") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        "Choose how to handle each unfinished task. Baddie never silently deletes unfinished work.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    pendingTodayTasks.take(5).forEach { task ->
                        BaddieCard(elevated = true) {
                            Text(task.title, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                TextButton(onClick = { onPostponeTask(task) }) { Text("Tomorrow") }
                                TextButton(onClick = { onRescheduleTask(task, today.plusDays(2).toString()) }) { Text("+2d") }
                                TextButton(onClick = { onMoveTaskToBacklog(task) }) { Text("Backlog") }
                                TextButton(onClick = { onCancelTask(task) }) { Text("Cancel") }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showEndOfDayReview = false }) { Text("Close") }
            }
        )
    }
}
