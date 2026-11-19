package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.domain.BaddieDomainLogic
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.LocalBaddieColors
import com.example.ui.theme.LocalBaddieSpacing
import com.example.ui.viewmodel.BaddieDashboardData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    data: BaddieDashboardData,
    onAddTask: (BaddieTask) -> Unit,
    onUpdateTask: (String, Map<String, Any>) -> Unit,
    onToggleTask: (BaddieTask) -> Unit,
    onToggleSubtask: (BaddieTask, Int) -> Unit,
    onDuplicateTask: (BaddieTask) -> Unit,
    onPostponeTask: (BaddieTask) -> Unit,
    onBulkComplete: (List<String>) -> Unit,
    onDeleteTask: (String) -> Unit
) {
    val colors = LocalBaddieColors.current
    val spacing = LocalBaddieSpacing.current
    val todayIso = BaddieDomainLogic.todayIso(data.profile.timezone)

    var searchQuery by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf("ACTIVE") } // ACTIVE, COMPLETED, CANCELLED
    var categoryFilter by remember { mutableStateOf<TaskCategory?>(null) }
    var bulkMode by remember { mutableStateOf(false) }
    val selectedIds = remember { mutableStateListOf<String>() }
    var showTaskDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<BaddieTask?>(null) }
    var expandedTaskIds by remember { mutableStateOf(setOf<String>()) }

    val filteredTasks = remember(data.tasks, searchQuery, statusFilter, categoryFilter) {
        data.tasks.filter { t ->
            val matchesStatus = when (statusFilter) {
                "COMPLETED" -> t.completed
                "CANCELLED" -> t.status == TaskStatus.CANCELLED.name
                else -> !t.completed && t.status != TaskStatus.CANCELLED.name
            }
            val matchesCat = categoryFilter == null || t.category == categoryFilter!!.name
            val matchesQuery = searchQuery.isBlank() ||
                t.title.contains(searchQuery, true) ||
                t.subject.contains(searchQuery, true)
            matchesStatus && matchesCat && matchesQuery
        }.sortedWith(compareByDescending<BaddieTask> { BaddieDomainLogic.priorityWeight(it.priority) }.thenBy { it.dueDate })
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    editingTask = null
                    showTaskDialog = true
                },
                modifier = Modifier.testTag("add_task_fab")
            ) {
                Icon(Icons.Rounded.Add, contentDescription = "Create task")
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(top = 12.dp, bottom = 90.dp),
            verticalArrangement = Arrangement.spacedBy(spacing.itemGap)
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Tasks", style = MaterialTheme.typography.headlineSmall)
                            Text(
                                "${data.tasks.count { !it.completed && it.status != TaskStatus.CANCELLED.name }} pending • ${data.tasks.count { it.completed }} done",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row {
                            FilterChip(
                                selected = bulkMode,
                                onClick = {
                                    bulkMode = !bulkMode
                                    if (!bulkMode) selectedIds.clear()
                                },
                                label = { Text(if (bulkMode) "Done Selecting" else "Bulk") }
                            )
                        }
                    }

                    if (bulkMode && selectedIds.isNotEmpty()) {
                        Button(
                            onClick = {
                                onBulkComplete(selectedIds.toList())
                                selectedIds.clear()
                                bulkMode = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Complete ${selectedIds.size} Selected")
                        }
                    }

                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search tasks or subjects...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("task_search_input"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf("ACTIVE" to "Active", "COMPLETED" to "Done", "CANCELLED" to "Cancelled").forEachIndexed { idx, (key, label) ->
                            SegmentedButton(
                                selected = statusFilter == key,
                                onClick = { statusFilter = key },
                                shape = SegmentedButtonDefaults.itemShape(idx, 3)
                            ) { Text(label) }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = categoryFilter == null,
                            onClick = { categoryFilter = null },
                            label = { Text("All") }
                        )
                        listOf(TaskCategory.STUDY, TaskCategory.HOMEWORK, TaskCategory.TUITION, TaskCategory.PERSONAL).forEach { cat ->
                            FilterChip(
                                selected = categoryFilter == cat,
                                onClick = { categoryFilter = if (categoryFilter == cat) null else cat },
                                label = { Text(cat.label) }
                            )
                        }
                    }

                    // Quick Task Templates
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AssistChip(
                            onClick = {
                                onAddTask(
                                    BaddieTask(
                                        title = "Revise today's tuition notes",
                                        category = TaskCategory.TUITION.name,
                                        priority = TaskPriority.HIGH.name,
                                        dueDate = todayIso,
                                        estimatedMinutes = 40
                                    )
                                )
                            },
                            label = { Text("+ Tuition Revision") }
                        )
                        AssistChip(
                            onClick = {
                                onAddTask(
                                    BaddieTask(
                                        title = "Solve 15 practice problems",
                                        category = TaskCategory.HOMEWORK.name,
                                        priority = TaskPriority.MEDIUM.name,
                                        dueDate = todayIso,
                                        estimatedMinutes = 45,
                                        subtasks = listOf(SubtaskItem("Formula review"), SubtaskItem("Problems 1-15"))
                                    )
                                )
                            },
                            label = { Text("+ Practice Set") }
                        )
                    }
                }
            }

            if (filteredTasks.isEmpty()) {
                item {
                    BaddieEmptyState(
                        title = "No matching tasks",
                        subtitle = "Create a task with subtasks, priority, and due date, or adjust filters.",
                        actionLabel = "Create Task",
                        onAction = {
                            editingTask = null
                            showTaskDialog = true
                        }
                    )
                }
            } else {
                items(filteredTasks, key = { it.id }) { task ->
                    var menuOpen by remember { mutableStateOf(false) }
                    val expanded = task.id in expandedTaskIds
                    val priorityColor = when (safeEnum(task.priority, TaskPriority.MEDIUM)) {
                        TaskPriority.URGENT -> MaterialTheme.colorScheme.error
                        TaskPriority.HIGH -> colors.warning
                        TaskPriority.MEDIUM -> MaterialTheme.colorScheme.primary
                        TaskPriority.LOW -> MaterialTheme.colorScheme.onSurfaceVariant
                    }

                    BaddieCard(
                        onClick = {
                            expandedTaskIds = if (expanded) expandedTaskIds - task.id else expandedTaskIds + task.id
                        }
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (bulkMode) {
                                Checkbox(
                                    checked = task.id in selectedIds,
                                    onCheckedChange = { checked ->
                                        if (checked) selectedIds.add(task.id) else selectedIds.remove(task.id)
                                    }
                                )
                            } else {
                                BaddieCheckbox(
                                    checked = task.completed,
                                    onToggle = { onToggleTask(task) },
                                    testTag = "task_checkbox_${task.id}"
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = task.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    textDecoration = if (task.completed) TextDecoration.LineThrough else TextDecoration.None
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                    BaddiePill(task.priority, priorityColor)
                                    BaddiePill(task.category)
                                    if (task.subject.isNotBlank()) {
                                        Text(task.subject, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                if (task.dueDate.isNotBlank() || task.subtasks.isNotEmpty()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = buildString {
                                            if (task.dueDate.isNotBlank()) append("Due ${task.dueDate} ")
                                            if (task.startTime.isNotBlank()) append("at ${task.startTime} ")
                                            append("• ${task.estimatedMinutes}m")
                                            if (task.subtasks.isNotEmpty()) {
                                                append(" • ${task.subtasks.count { it.completed }}/${task.subtasks.size} subtasks")
                                            }
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Box {
                                IconButton(onClick = { menuOpen = true }) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "Task actions")
                                }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(text = { Text("Edit") }, onClick = {
                                        menuOpen = false
                                        editingTask = task
                                        showTaskDialog = true
                                    })
                                    DropdownMenuItem(text = { Text("Postpone +1 day") }, onClick = {
                                        menuOpen = false
                                        onPostponeTask(task)
                                    })
                                    DropdownMenuItem(text = { Text("Duplicate") }, onClick = {
                                        menuOpen = false
                                        onDuplicateTask(task)
                                    })
                                    DropdownMenuItem(text = { Text("Delete") }, onClick = {
                                        menuOpen = false
                                        onDeleteTask(task.id)
                                    })
                                }
                            }
                        }

                        if (expanded) {
                            Spacer(Modifier.height(8.dp))
                            HorizontalDivider(color = colors.borderSubtle)
                            Spacer(Modifier.height(8.dp))
                            if (task.description.isNotBlank()) {
                                Text(task.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.height(6.dp))
                            }
                            if (task.progressPercent in 1..99) {
                                LinearProgressIndicator(
                                    progress = { task.progressPercent / 100f },
                                    modifier = Modifier.fillMaxWidth().height(4.dp)
                                )
                                Spacer(Modifier.height(6.dp))
                            }
                            task.subtasks.forEachIndexed { idx, sub ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    BaddieCheckbox(
                                        checked = sub.completed,
                                        onToggle = { onToggleSubtask(task, idx) },
                                        testTag = "subtask_${task.id}_$idx"
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        sub.title,
                                        style = MaterialTheme.typography.bodySmall,
                                        textDecoration = if (sub.completed) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showTaskDialog) {
        TaskEditorDialog(
            initial = editingTask,
            defaultDate = todayIso,
            subjects = data.subjects,
            goals = data.goals,
            onDismiss = { showTaskDialog = false },
            onSave = { built ->
                val cur = editingTask
                if (cur == null) {
                    onAddTask(built)
                } else {
                    onUpdateTask(
                        cur.id,
                        mapOf(
                            "title" to built.title,
                            "description" to built.description,
                            "category" to built.category,
                            "subject" to built.subject,
                            "priority" to built.priority,
                            "dueDate" to built.dueDate,
                            "startTime" to built.startTime,
                            "estimatedMinutes" to built.estimatedMinutes,
                            "recurrence" to built.recurrence,
                            "goalId" to built.goalId,
                            "notes" to built.notes,
                            "subtasks" to built.subtasks.map { it.toMap() }
                        )
                    )
                }
                showTaskDialog = false
            }
        )
    }
}

@Composable
private fun TaskEditorDialog(
    initial: BaddieTask?,
    defaultDate: String,
    subjects: List<StudySubject>,
    goals: List<Goal>,
    onDismiss: () -> Unit,
    onSave: (BaddieTask) -> Unit
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }
    var category by remember { mutableStateOf(safeEnum(initial?.category, TaskCategory.STUDY)) }
    var priority by remember { mutableStateOf(safeEnum(initial?.priority, TaskPriority.MEDIUM)) }
    var subject by remember { mutableStateOf(initial?.subject.orEmpty()) }
    var dueDate by remember { mutableStateOf(initial?.dueDate ?: defaultDate) }
    var startTime by remember { mutableStateOf(initial?.startTime.orEmpty()) }
    var estimatedStr by remember { mutableStateOf((initial?.estimatedMinutes ?: 30).toString()) }
    var recurrence by remember { mutableStateOf(safeEnum(initial?.recurrence, TaskRecurrence.NONE)) }
    var selectedGoalId by remember { mutableStateOf(initial?.goalId.orEmpty()) }
    var subtaskDraft by remember { mutableStateOf("") }
    val subtasks = remember { mutableStateListOf<SubtaskItem>().apply { addAll(initial?.subtasks.orEmpty()) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "New Task" else "Edit Task") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Title *") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = dueDate, onValueChange = { dueDate = it }, label = { Text("Due (YYYY-MM-DD)") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text("Time (HH:mm)") }, modifier = Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = estimatedStr, onValueChange = { estimatedStr = it }, label = { Text("Est. Min") }, modifier = Modifier.width(100.dp))
                }
                if (subjects.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        subjects.take(4).forEach { s ->
                            AssistChip(onClick = { subject = s.name }, label = { Text(s.name) })
                        }
                    }
                }
                Text("Priority", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TaskPriority.entries.forEach { p ->
                        FilterChip(selected = priority == p, onClick = { priority = p }, label = { Text(p.label) })
                    }
                }
                Text("Category", style = MaterialTheme.typography.labelLarge)
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TaskCategory.entries.forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c.label) })
                    }
                }
                Text("Recurrence", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TaskRecurrence.entries.forEach { r ->
                        FilterChip(selected = recurrence == r, onClick = { recurrence = r }, label = { Text(r.label) })
                    }
                }
                if (goals.isNotEmpty()) {
                    Text("Link to Goal", style = MaterialTheme.typography.labelLarge)
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        FilterChip(selected = selectedGoalId.isBlank(), onClick = { selectedGoalId = "" }, label = { Text("None") })
                        goals.take(4).forEach { g ->
                            FilterChip(selected = selectedGoalId == g.id, onClick = { selectedGoalId = g.id }, label = { Text(g.title.take(18)) })
                        }
                    }
                }
                Text("Subtasks", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(value = subtaskDraft, onValueChange = { subtaskDraft = it }, label = { Text("Subtask title") }, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        if (subtaskDraft.isNotBlank()) {
                            subtasks += SubtaskItem(subtaskDraft.trim())
                            subtaskDraft = ""
                        }
                    }) { Text("Add") }
                }
                subtasks.forEachIndexed { idx, s ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("• ${s.title}", style = MaterialTheme.typography.bodySmall)
                        IconButton(onClick = { subtasks.removeAt(idx) }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Remove subtask", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isNotBlank()) {
                    onSave(
                        BaddieTask(
                            title = title.trim(),
                            description = description.trim(),
                            category = category.name,
                            subject = subject.trim(),
                            priority = priority.name,
                            dueDate = dueDate.trim(),
                            startTime = startTime.trim(),
                            estimatedMinutes = estimatedStr.toIntOrNull()?.coerceIn(1, 1440) ?: 30,
                            recurrence = recurrence.name,
                            goalId = selectedGoalId,
                            subtasks = subtasks.toList()
                        )
                    )
                }
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
