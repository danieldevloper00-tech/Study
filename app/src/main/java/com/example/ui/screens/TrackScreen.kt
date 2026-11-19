package com.example.ui.screens

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
import androidx.compose.ui.unit.dp
import com.example.data.domain.BaddieDomainLogic
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.JetBrainsMonoFamily
import com.example.ui.theme.LocalBaddieColors
import com.example.ui.theme.LocalBaddieSpacing
import com.example.ui.viewmodel.BaddieDashboardData
import com.example.ui.viewmodel.StudyTimerState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackScreen(
    data: BaddieDashboardData,
    timerState: StudyTimerState,
    onConfigureTimer: (String, String, String, Int) -> Unit,
    onToggleTimerRunning: () -> Unit,
    onFinishTimerAndSave: () -> Unit,
    onLogManualStudy: (String, String, Int, String) -> Unit,
    onAddSubject: (String, Int) -> Unit,
    onAddChapter: (StudySubject, String) -> Unit,
    onCycleChapterStatus: (StudySubject, Int) -> Unit,
    onUpdateConfidence: (StudySubject, Int) -> Unit,
    onAddHabit: (String, String, Int) -> Unit,
    onToggleHabitDate: (Habit, String) -> Unit,
    onDeleteHabit: (String) -> Unit,
    onAddGoal: (Goal) -> Unit,
    onUpdateGoalProgress: (Goal, Int) -> Unit,
    onToggleGoalMilestone: (Goal, Int) -> Unit,
    onDeleteGoal: (String) -> Unit
) {
    val colors = LocalBaddieColors.current
    val spacing = LocalBaddieSpacing.current
    val today = BaddieDomainLogic.todayInZoneDate(data.profile.timezone)
    val todayIso = today.toString()

    var subTab by remember { mutableStateOf("STUDY") } // STUDY, HABITS, GOALS
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var showManualStudyDialog by remember { mutableStateOf(false) }
    var chapterSubjectTarget by remember { mutableStateOf<StudySubject?>(null) }
    var showAddHabitDialog by remember { mutableStateOf(false) }
    var showAddGoalDialog by remember { mutableStateOf(false) }
    var goalFilter by remember { mutableStateOf<GoalTimeframe?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.itemGap)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Track", style = MaterialTheme.typography.headlineSmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("STUDY" to "Study", "HABITS" to "Habits", "GOALS" to "Goals").forEachIndexed { idx, (key, label) ->
                        SegmentedButton(
                            selected = subTab == key,
                            onClick = { subTab = key },
                            shape = SegmentedButtonDefaults.itemShape(idx, 3),
                            modifier = Modifier.testTag("track_tab_$key")
                        ) { Text(label) }
                    }
                }
            }
        }

        when (subTab) {
            "STUDY" -> {
                item {
                    val mins = timerState.remainingSeconds / 60
                    val secs = timerState.remainingSeconds % 60
                    BaddieCard(elevated = true) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text(if (timerState.breakPhase) "Break Timer" else "Study Focus Timer", style = MaterialTheme.typography.titleMedium)
                                Text(timerState.selectedSubjectName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            BaddiePill(timerState.mode)
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = String.format(Locale.US, "%02d:%02d", mins, secs),
                            style = MaterialTheme.typography.displaySmall,
                            fontFamily = JetBrainsMonoFamily,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(
                                selected = timerState.mode == "POMODORO" && timerState.targetMinutes == 25,
                                onClick = { onConfigureTimer(timerState.selectedSubjectId, timerState.selectedSubjectName, "POMODORO", 25) },
                                label = { Text("25m Pomodoro") }
                            )
                            FilterChip(
                                selected = timerState.mode == "CUSTOM" && timerState.targetMinutes == 50,
                                onClick = { onConfigureTimer(timerState.selectedSubjectId, timerState.selectedSubjectName, "CUSTOM", 50) },
                                label = { Text("50m Deep") }
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = onToggleTimerRunning,
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("study_timer_toggle")
                            ) {
                                Icon(if (timerState.running) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text(if (timerState.running) "Pause" else "Start")
                            }
                            OutlinedButton(
                                onClick = onFinishTimerAndSave,
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("study_timer_finish")
                            ) { Text("Finish & Log") }
                        }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            TextButton(onClick = { showManualStudyDialog = true }) { Text("+ Log Manual Session") }
                            TextButton(onClick = { showAddSubjectDialog = true }) { Text("+ Add Subject") }
                        }
                    }
                }

                item {
                    BaddieSectionHeader("Subjects & Chapters", "${data.subjects.size} subjects")
                }

                if (data.subjects.isEmpty()) {
                    item {
                        BaddieEmptyState(
                            title = "No study subjects yet",
                            subtitle = "Add Physics, Mathematics, Higher Math, Chemistry, Bangla, English, or custom subjects.",
                            actionLabel = "Add Subject",
                            onAction = { showAddSubjectDialog = true }
                        )
                    }
                } else {
                    items(data.subjects, key = { it.id }) { subject ->
                        BaddieCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(subject.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${subject.totalStudyMinutes}m logged • Confidence ${subject.confidenceLevel}/5 • ${subject.chapters.size} chapters",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Row {
                                    IconButton(onClick = {
                                        onConfigureTimer(subject.id, subject.name, timerState.mode, timerState.targetMinutes)
                                    }) {
                                        Icon(Icons.Rounded.Timer, contentDescription = "Focus on ${subject.name}")
                                    }
                                    IconButton(onClick = { chapterSubjectTarget = subject }) {
                                        Icon(Icons.Rounded.Add, contentDescription = "Add chapter")
                                    }
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text("Confidence:", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                (1..5).forEach { lvl ->
                                    FilterChip(
                                        selected = subject.confidenceLevel == lvl,
                                        onClick = { onUpdateConfidence(subject, lvl) },
                                        label = { Text("$lvl") }
                                    )
                                }
                            }
                            if (subject.chapters.isNotEmpty()) {
                                Spacer(Modifier.height(6.dp))
                                subject.chapters.forEachIndexed { idx, ch ->
                                    val chStatus = safeEnum(ch.status, ChapterStatus.NOT_STARTED)
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 3.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        color = colors.surfaceElevated,
                                        onClick = { onCycleChapterStatus(subject, idx) }
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(ch.title, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                            BaddiePill(chStatus.label)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "HABITS" -> {
                item {
                    BaddieSectionHeader(
                        "Habit Tracker",
                        "Gentle consistency without punishment",
                        trailing = {
                            Button(
                                onClick = { showAddHabitDialog = true },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("add_habit_btn")
                            ) { Text("+ Habit") }
                        }
                    )
                }

                if (data.habits.isEmpty()) {
                    item {
                        BaddieEmptyState(
                            title = "No habits added",
                            subtitle = "Track daily habits like drinking water, waking up on time, or focused revision.",
                            actionLabel = "Add Habit",
                            onAction = { showAddHabitDialog = true }
                        )
                    }
                } else {
                    items(data.habits, key = { it.id }) { habit ->
                        val streak = BaddieDomainLogic.computeHabitStreak(habit, today)
                        val last7 = (6 downTo 0).map { today.minusDays(it.toLong()) }
                        val done7 = last7.count { it.toString() in habit.completedDates }
                        val rate = (done7 * 100) / 7
                        val doneToday = todayIso in habit.completedDates

                        BaddieCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BaddieCheckbox(
                                    checked = doneToday,
                                    onToggle = { onToggleHabitDate(habit, todayIso) },
                                    testTag = "habit_check_${habit.id}"
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(habit.title, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        "${streak}d streak • $rate% this week • ${7 - done7} open days",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { onDeleteHabit(habit.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete habit")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                last7.forEach { d ->
                                    val dIso = d.toString()
                                    val checked = dIso in habit.completedDates
                                    FilterChip(
                                        selected = checked,
                                        onClick = { onToggleHabitDate(habit, dIso) },
                                        label = { Text(d.dayOfWeek.name.take(1)) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            "GOALS" -> {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        BaddieSectionHeader(
                            "Goals & Milestones",
                            "${data.goals.count { !it.completed }} active",
                            trailing = {
                                Button(
                                    onClick = { showAddGoalDialog = true },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("add_goal_btn")
                                ) { Text("+ Goal") }
                            }
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FilterChip(selected = goalFilter == null, onClick = { goalFilter = null }, label = { Text("All") })
                            GoalTimeframe.entries.forEach { tf ->
                                FilterChip(
                                    selected = goalFilter == tf,
                                    onClick = { goalFilter = if (goalFilter == tf) null else tf },
                                    label = { Text(tf.label) }
                                )
                            }
                        }
                    }
                }

                val filteredGoals = data.goals.filter { goalFilter == null || it.timeframe == goalFilter!!.name }
                if (filteredGoals.isEmpty()) {
                    item {
                        BaddieEmptyState(
                            title = "No goals in this view",
                            subtitle = "Add daily, weekly, monthly, or long-term goals and link tasks to auto-track progress.",
                            actionLabel = "Add Goal",
                            onAction = { showAddGoalDialog = true }
                        )
                    }
                } else {
                    items(filteredGoals, key = { it.id }) { goal ->
                        val linkedCount = data.tasks.count { it.goalId == goal.id }
                        BaddieCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(goal.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        BaddiePill(safeEnum(goal.timeframe, GoalTimeframe.WEEKLY).label)
                                        if (goal.deadline.isNotBlank()) {
                                            Text("Due ${goal.deadline}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (linkedCount > 0) {
                                            Text("$linkedCount linked tasks", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                IconButton(onClick = { onDeleteGoal(goal.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete goal")
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                LinearProgressIndicator(
                                    progress = { goal.progressPercent / 100f },
                                    modifier = Modifier.weight(1f).height(6.dp)
                                )
                                Text("${goal.progressPercent}%", style = MaterialTheme.typography.labelMedium)
                            }
                            Slider(
                                value = goal.progressPercent.toFloat(),
                                onValueChange = { onUpdateGoalProgress(goal, it.toInt()) },
                                valueRange = 0f..100f
                            )
                            goal.milestones.forEachIndexed { idx, m ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BaddieCheckbox(
                                        checked = m.completed,
                                        onToggle = { onToggleGoalMilestone(goal, idx) },
                                        testTag = "milestone_${goal.id}_$idx"
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(m.title, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddSubjectDialog) {
        var name by remember { mutableStateOf("") }
        var conf by remember { mutableIntStateOf(3) }
        AlertDialog(
            onDismissRequest = { showAddSubjectDialog = false },
            title = { Text("Add Subject") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Subject Name") }, modifier = Modifier.fillMaxWidth())
                    Text("Confidence: $conf/5", style = MaterialTheme.typography.labelLarge)
                    Slider(value = conf.toFloat(), onValueChange = { conf = it.toInt() }, valueRange = 1f..5f, steps = 3)
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank()) {
                        onAddSubject(name.trim(), conf)
                        showAddSubjectDialog = false
                    }
                }) { Text("Add") }
            },
            dismissButton = { TextButton(onClick = { showAddSubjectDialog = false }) { Text("Cancel") } }
        )
    }

    chapterSubjectTarget?.let { subj ->
        var chTitle by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { chapterSubjectTarget = null },
            title = { Text("Add Chapter to ${subj.name}") },
            text = {
                OutlinedTextField(value = chTitle, onValueChange = { chTitle = it }, label = { Text("Chapter Title") }, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = {
                    if (chTitle.isNotBlank()) {
                        onAddChapter(subj, chTitle.trim())
                        chapterSubjectTarget = null
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { chapterSubjectTarget = null }) { Text("Cancel") } }
        )
    }

    if (showManualStudyDialog) {
        var subjName by remember { mutableStateOf(timerState.selectedSubjectName) }
        var minsStr by remember { mutableStateOf("45") }
        var notes by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showManualStudyDialog = false },
            title = { Text("Log Study Session") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = subjName, onValueChange = { subjName = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = minsStr, onValueChange = { minsStr = it }, label = { Text("Minutes") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notes / Topics") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    val m = minsStr.toIntOrNull()?.coerceIn(1, 720) ?: 30
                    if (subjName.isNotBlank()) {
                        onLogManualStudy("", subjName.trim(), m, notes.trim())
                        showManualStudyDialog = false
                    }
                }) { Text("Log") }
            },
            dismissButton = { TextButton(onClick = { showManualStudyDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAddHabitDialog) {
        var title by remember { mutableStateOf("") }
        var freq by remember { mutableStateOf("DAILY") }
        AlertDialog(
            onDismissRequest = { showAddHabitDialog = false },
            title = { Text("New Habit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Habit Title") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("DAILY", "WEEKDAYS", "CUSTOM").forEach { f ->
                            FilterChip(selected = freq == f, onClick = { freq = f }, label = { Text(f) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank()) {
                        onAddHabit(title.trim(), freq, if (freq == "WEEKDAYS") 5 else 7)
                        showAddHabitDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddHabitDialog = false }) { Text("Cancel") } }
        )
    }

    if (showAddGoalDialog) {
        var title by remember { mutableStateOf("") }
        var timeframe by remember { mutableStateOf(GoalTimeframe.WEEKLY) }
        var deadline by remember { mutableStateOf(today.plusDays(7).toString()) }
        var milestoneDraft by remember { mutableStateOf("") }
        val milestones = remember { mutableStateListOf<MilestoneItem>() }
        AlertDialog(
            onDismissRequest = { showAddGoalDialog = false },
            title = { Text("New Goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Goal Title *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = deadline, onValueChange = { deadline = it }, label = { Text("Deadline (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        GoalTimeframe.entries.forEach { tf ->
                            FilterChip(selected = timeframe == tf, onClick = { timeframe = tf }, label = { Text(tf.label) })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = milestoneDraft, onValueChange = { milestoneDraft = it }, label = { Text("Milestone") }, modifier = Modifier.weight(1f))
                        Button(onClick = {
                            if (milestoneDraft.isNotBlank()) {
                                milestones += MilestoneItem(milestoneDraft.trim())
                                milestoneDraft = ""
                            }
                        }) { Text("Add") }
                    }
                    milestones.forEach { m -> Text("• ${m.title}", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank()) {
                        onAddGoal(Goal(title = title.trim(), timeframe = timeframe.name, deadline = deadline.trim(), milestones = milestones.toList()))
                        showAddGoalDialog = false
                    }
                }) { Text("Save Goal") }
            },
            dismissButton = { TextButton(onClick = { showAddGoalDialog = false }) { Text("Cancel") } }
        )
    }
}
