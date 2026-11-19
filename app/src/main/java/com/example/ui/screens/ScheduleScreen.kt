package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.data.domain.BaddieDomainLogic
import com.example.data.model.*
import com.example.ui.components.*
import com.example.ui.theme.LocalBaddieColors
import com.example.ui.theme.LocalBaddieSpacing
import com.example.ui.viewmodel.BaddieDashboardData
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    data: BaddieDashboardData,
    onAddTuitionSession: (TuitionSession) -> Unit,
    onUpdateTuitionStatus: (String, TuitionStatus) -> Unit,
    onRescheduleTuition: (TuitionSession, String, String) -> Unit,
    onDeleteTuitionSession: (String) -> Unit,
    onAddTeacher: (TeacherProfile) -> Unit,
    onToggleTeacherActive: (TeacherProfile) -> Unit,
    onDeleteTeacher: (String) -> Unit,
    onAddRoutineBlock: (RoutineBlock) -> Unit,
    onToggleRoutineToday: (RoutineBlock) -> Unit,
    onDeleteRoutineBlock: (String) -> Unit
) {
    val colors = LocalBaddieColors.current
    val spacing = LocalBaddieSpacing.current
    val today = BaddieDomainLogic.todayInZoneDate(data.profile.timezone)
    val todayIso = today.toString()

    var subTab by remember { mutableStateOf("TUITION") } // TUITION, ROUTINE, CALENDAR
    var selectedDate by remember { mutableStateOf(today) }
    var calendarMode by remember { mutableStateOf("WEEK") } // DAY, WEEK, MONTH

    var showAddTuitionDialog by remember { mutableStateOf(false) }
    var showAddTeacherDialog by remember { mutableStateOf(false) }
    var rescheduleTarget by remember { mutableStateOf<TuitionSession?>(null) }
    var showAddRoutineDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(spacing.itemGap)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Schedule", style = MaterialTheme.typography.headlineSmall)
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    listOf("TUITION" to "Tuition", "ROUTINE" to "Routine", "CALENDAR" to "Calendar").forEachIndexed { idx, (key, label) ->
                        SegmentedButton(
                            selected = subTab == key,
                            onClick = { subTab = key },
                            shape = SegmentedButtonDefaults.itemShape(idx, 3),
                            modifier = Modifier.testTag("schedule_tab_$key")
                        ) { Text(label) }
                    }
                }
            }
        }

        when (subTab) {
            "TUITION" -> {
                val todayTuitions = data.tuitionSessions.filter {
                    it.sessionDate == todayIso && it.status != TuitionStatus.RESCHEDULED.name
                }
                val upcomingTuitions = data.tuitionSessions.filter {
                    it.sessionDate > todayIso && it.status in listOf(TuitionStatus.SCHEDULED.name, TuitionStatus.CONFIRMED.name)
                }
                val historicalTuitions = data.tuitionSessions.filter {
                    it.status in listOf(
                        TuitionStatus.COMPLETED.name,
                        TuitionStatus.RESCHEDULED.name,
                        TuitionStatus.CANCELLED.name,
                        TuitionStatus.TEACHER_ABSENT.name,
                        TuitionStatus.STUDENT_ABSENT.name
                    )
                }.sortedByDescending { it.sessionDate }

                val completedWeek = data.tuitionSessions.count {
                    it.status == TuitionStatus.COMPLETED.name && it.sessionDate >= today.minusDays(6).toString()
                }
                val absentWeek = data.tuitionSessions.count {
                    it.status in listOf(TuitionStatus.TEACHER_ABSENT.name, TuitionStatus.STUDENT_ABSENT.name, TuitionStatus.CANCELLED.name) &&
                        it.sessionDate >= today.minusDays(6).toString()
                }

                item {
                    BaddieCard(elevated = true) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("Weekly Attendance", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "$completedWeek completed • $absentWeek missed/cancelled (7d)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                OutlinedButton(
                                    onClick = { showAddTeacherDialog = true },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) { Text("Teachers") }
                                Button(
                                    onClick = { showAddTuitionDialog = true },
                                    modifier = Modifier
                                        .heightIn(min = 48.dp)
                                        .testTag("add_tuition_btn")
                                ) { Text("+ Session") }
                            }
                        }
                    }
                }

                item {
                    BaddieSectionHeader("Today's Tuition ($todayIso)", "${todayTuitions.size} session(s)")
                }
                if (todayTuitions.isEmpty()) {
                    item {
                        BaddieEmptyState(
                            title = "No tuition scheduled today",
                            subtitle = "Add an extra session or reschedule an upcoming one anytime.",
                            actionLabel = "Add Tuition Session",
                            onAction = { showAddTuitionDialog = true },
                            showArtwork = false
                        )
                    }
                } else {
                    items(todayTuitions, key = { "today_${it.id}" }) { session ->
                        TuitionSessionCard(
                            session = session,
                            onStatusChange = { status -> onUpdateTuitionStatus(session.id, status) },
                            onReschedule = { rescheduleTarget = session },
                            onDelete = { onDeleteTuitionSession(session.id) }
                        )
                    }
                }

                item {
                    BaddieSectionHeader("Upcoming Sessions", "${upcomingTuitions.size} upcoming")
                }
                if (upcomingTuitions.isEmpty()) {
                    item {
                        BaddieCard {
                            Text("No upcoming tuition sessions queued.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(upcomingTuitions, key = { "up_${it.id}" }) { session ->
                        TuitionSessionCard(
                            session = session,
                            onStatusChange = { status -> onUpdateTuitionStatus(session.id, status) },
                            onReschedule = { rescheduleTarget = session },
                            onDelete = { onDeleteTuitionSession(session.id) }
                        )
                    }
                }

                item {
                    BaddieSectionHeader("Teacher Profiles", "${data.teachers.size} configured")
                }
                if (data.teachers.isEmpty()) {
                    item {
                        BaddieCard {
                            Text(
                                "No teachers added yet. Add teachers with fixed weekdays or flexible ~5 days/week cadence.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(data.teachers, key = { "t_${it.id}" }) { teacher ->
                        BaddieCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${teacher.name} • ${teacher.subject}", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (teacher.flexibleWeekly) "Flexible ~${teacher.targetSessionsPerWeek}x/week • ${teacher.preferredTime}"
                                        else "${teacher.defaultWeekdays.joinToString(", ")} • ${teacher.preferredTime} (${teacher.expectedDurationMinutes}m)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(checked = teacher.active, onCheckedChange = { onToggleTeacherActive(teacher) })
                                IconButton(onClick = { onDeleteTeacher(teacher.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete teacher")
                                }
                            }
                        }
                    }
                }

                if (historicalTuitions.isNotEmpty()) {
                    item {
                        BaddieSectionHeader("History & Rescheduled Log", "Preserved for analytics")
                    }
                    items(historicalTuitions.take(10), key = { "hist_${it.id}" }) { session ->
                        BaddieCard(elevated = true) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text("${session.subject} (${session.sessionDate})", style = MaterialTheme.typography.bodyMedium)
                                    if (session.notes.isNotBlank()) {
                                        Text(session.notes, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                BaddiePill(session.status)
                            }
                        }
                    }
                }
            }

            "ROUTINE" -> {
                val activeDayShort = BaddieDomainLogic.dayOfWeekShort(selectedDate.dayOfWeek)
                val dayBlocks = data.routineBlocks.filter { activeDayShort in it.repeatDays }.sortedBy { it.startTime }

                item {
                    BaddieCard {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column {
                                Text("Adaptive Daily Routine", style = MaterialTheme.typography.titleMedium)
                                Text("Viewing $activeDayShort (${selectedDate})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Button(
                                onClick = { showAddRoutineDialog = true },
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("add_routine_btn")
                            ) { Text("+ Block") }
                        }
                        Spacer(Modifier.height(10.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            (0..6).forEach { offset ->
                                val d = today.plusDays(offset.toLong())
                                val short = BaddieDomainLogic.dayOfWeekShort(d.dayOfWeek)
                                FilterChip(
                                    selected = selectedDate == d,
                                    onClick = { selectedDate = d },
                                    label = { Text(short.take(2)) }
                                )
                            }
                        }
                    }
                }

                if (dayBlocks.isEmpty()) {
                    item {
                        BaddieEmptyState(
                            title = "No routine blocks on $activeDayShort",
                            subtitle = "Add customizable blocks for study, tuition, meals, exercise, or sleep. Overlaps are checked automatically.",
                            actionLabel = "Add Routine Block",
                            onAction = { showAddRoutineDialog = true }
                        )
                    }
                } else {
                    items(dayBlocks, key = { "rb_${it.id}" }) { block ->
                        val done = block.lastCompletedDate == todayIso
                        BaddieCard {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                BaddieCheckbox(
                                    checked = done,
                                    onToggle = { onToggleRoutineToday(block) },
                                    testTag = "routine_check_${block.id}"
                                )
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        block.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        textDecoration = if (done) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                    Text(
                                        "${BaddieDomainLogic.formatDisplayTime(block.startTime)} – ${BaddieDomainLogic.formatDisplayTime(block.endTime)} (${block.durationMinutes}m)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                BaddiePill(block.blockType)
                                IconButton(onClick = { onDeleteRoutineBlock(block.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete block")
                                }
                            }
                        }
                    }
                }
            }

            "CALENDAR" -> {
                item {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        listOf("DAY" to "Day", "WEEK" to "Week", "MONTH" to "Month").forEachIndexed { idx, (key, label) ->
                            SegmentedButton(
                                selected = calendarMode == key,
                                onClick = { calendarMode = key },
                                shape = SegmentedButtonDefaults.itemShape(idx, 3)
                            ) { Text(label) }
                        }
                    }
                }

                if (calendarMode == "MONTH") {
                    val ym = YearMonth.from(selectedDate)
                    val daysInMonth = ym.lengthOfMonth()
                    item {
                        BaddieCard {
                            Text("${ym.month.name.lowercase().replaceFirstChar { it.uppercase() }} ${ym.year}", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(10.dp))
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (dayNum in 1..daysInMonth) {
                                    val date = ym.atDay(dayNum)
                                    val dIso = date.toString()
                                    val hasEvents = data.tuitionSessions.any { it.sessionDate == dIso } ||
                                        data.tasks.any { it.dueDate == dIso }
                                    Surface(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clickable { selectedDate = date },
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (selectedDate == date) MaterialTheme.colorScheme.primary else colors.surfaceElevated,
                                        border = if (hasEvents) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, colors.borderSubtle)
                                    ) {
                                        Box(contentAlignment = Alignment.Center) {
                                            Text(
                                                text = "$dayNum",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (selectedDate == date) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else if (calendarMode == "WEEK") {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            (-1..5).forEach { offset ->
                                val d = today.plusDays(offset.toLong())
                                FilterChip(
                                    selected = selectedDate == d,
                                    onClick = { selectedDate = d },
                                    label = { Text("${d.dayOfMonth}") }
                                )
                            }
                        }
                    }
                }

                val selIso = selectedDate.toString()
                val selShort = BaddieDomainLogic.dayOfWeekShort(selectedDate.dayOfWeek)
                val selTuitions = data.tuitionSessions.filter { it.sessionDate == selIso && it.status != TuitionStatus.RESCHEDULED.name }
                val selTasks = data.tasks.filter { it.dueDate == selIso }
                val selRoutine = data.routineBlocks.filter { selShort in it.repeatDays }

                item {
                    BaddieSectionHeader(
                        "Agenda for $selIso ($selShort)",
                        "${selRoutine.size} routine • ${selTuitions.size} tuition • ${selTasks.size} tasks"
                    )
                }
                if (selTuitions.isEmpty() && selTasks.isEmpty() && selRoutine.isEmpty()) {
                    item {
                        BaddieCard {
                            Text("Nothing scheduled for $selIso.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                } else {
                    items(selRoutine, key = { "cal_r_${it.id}" }) { b ->
                        BaddieCard(elevated = true) {
                            Text("${BaddieDomainLogic.formatDisplayTime(b.startTime)} • Routine: ${b.title}", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    items(selTuitions, key = { "cal_tu_${it.id}" }) { t ->
                        BaddieCard {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${BaddieDomainLogic.formatDisplayTime(t.startTime)} • Tuition: ${t.subject}", style = MaterialTheme.typography.titleSmall)
                                BaddiePill(t.status)
                            }
                        }
                    }
                    items(selTasks, key = { "cal_tk_${it.id}" }) { task ->
                        BaddieCard {
                            Text("Task: ${task.title} (${task.priority})", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }

    // Add Tuition Session Dialog (with live conflict check!)
    if (showAddTuitionDialog) {
        var subject by remember { mutableStateOf(data.teachers.firstOrNull()?.subject ?: "Mathematics") }
        var teacherName by remember { mutableStateOf(data.teachers.firstOrNull()?.name.orEmpty()) }
        var teacherId by remember { mutableStateOf(data.teachers.firstOrNull()?.id.orEmpty()) }
        var date by remember { mutableStateOf(todayIso) }
        var startTime by remember { mutableStateOf("16:00") }
        var durationStr by remember { mutableStateOf("90") }
        var extra by remember { mutableStateOf(false) }
        val conflicts = remember(date, startTime, durationStr, data.tuitionSessions, data.routineBlocks) {
            BaddieDomainLogic.detectTuitionConflicts(
                date,
                startTime,
                durationStr.toIntOrNull() ?: 90,
                data.tuitionSessions,
                data.routineBlocks
            )
        }
        AlertDialog(
            onDismissRequest = { showAddTuitionDialog = false },
            title = { Text("Schedule Tuition Session") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (data.teachers.isNotEmpty()) {
                        Text("Select Teacher", style = MaterialTheme.typography.labelLarge)
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            data.teachers.forEach { t ->
                                FilterChip(
                                    selected = teacherId == t.id,
                                    onClick = {
                                        teacherId = t.id
                                        teacherName = t.name
                                        subject = t.subject
                                        startTime = t.preferredTime.ifBlank { "16:00" }
                                        durationStr = t.expectedDurationMinutes.toString()
                                    },
                                    label = { Text("${t.name} (${t.subject})") }
                                )
                            }
                        }
                    }
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = teacherName, onValueChange = { teacherName = it }, label = { Text("Teacher Name (optional)") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = date, onValueChange = { date = it }, label = { Text("Date (YYYY-MM-DD)") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text("Time (HH:mm)") }, modifier = Modifier.weight(1f))
                    }
                    OutlinedTextField(value = durationStr, onValueChange = { durationStr = it }, label = { Text("Duration (minutes)") }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Extra / Flexible session", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = extra, onCheckedChange = { extra = it })
                    }
                    if (conflicts.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.warning.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, colors.warning.copy(alpha = 0.4f))
                        ) {
                            Text(
                                "Overlap Warning: ${conflicts.joinToString("; ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.warning,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (subject.isNotBlank()) {
                        onAddTuitionSession(
                            TuitionSession(
                                teacherId = teacherId,
                                teacherName = teacherName.trim(),
                                subject = subject.trim(),
                                sessionDate = date.trim(),
                                startTime = startTime.trim(),
                                durationMinutes = durationStr.toIntOrNull()?.coerceIn(15, 480) ?: 90,
                                originalDate = date.trim(),
                                originalTime = startTime.trim(),
                                extraSession = extra
                            )
                        )
                        showAddTuitionDialog = false
                    }
                }) { Text(if (conflicts.isNotEmpty()) "Save Anyway" else "Schedule") }
            },
            dismissButton = { TextButton(onClick = { showAddTuitionDialog = false }) { Text("Cancel") } }
        )
    }

    // Reschedule Tuition Dialog (with conflict warning & historical preservation!)
    rescheduleTarget?.let { target ->
        var newDate by remember { mutableStateOf(LocalDate.parse(target.sessionDate).plusDays(1).toString()) }
        var newTime by remember { mutableStateOf(target.startTime) }
        val conflicts = remember(newDate, newTime, data.tuitionSessions, data.routineBlocks) {
            BaddieDomainLogic.detectTuitionConflicts(
                newDate,
                newTime,
                target.durationMinutes,
                data.tuitionSessions,
                data.routineBlocks,
                ignoreSessionId = target.id
            )
        }
        AlertDialog(
            onDismissRequest = { rescheduleTarget = null },
            title = { Text("Reschedule ${target.subject}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Original slot (${target.sessionDate} at ${target.startTime}) will be preserved in history as Rescheduled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(value = newDate, onValueChange = { newDate = it }, label = { Text("New Date (YYYY-MM-DD)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = newTime, onValueChange = { newTime = it }, label = { Text("New Time (HH:mm)") }, modifier = Modifier.fillMaxWidth())
                    if (conflicts.isNotEmpty()) {
                        Text("Conflict detected: ${conflicts.joinToString("; ")}", style = MaterialTheme.typography.bodySmall, color = colors.warning)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    onRescheduleTuition(target, newDate.trim(), newTime.trim())
                    rescheduleTarget = null
                }) { Text("Confirm Reschedule") }
            },
            dismissButton = { TextButton(onClick = { rescheduleTarget = null }) { Text("Cancel") } }
        )
    }

    // Add Teacher Dialog
    if (showAddTeacherDialog) {
        var name by remember { mutableStateOf("") }
        var subject by remember { mutableStateOf("") }
        var time by remember { mutableStateOf("16:00") }
        var flexible by remember { mutableStateOf(false) }
        var contact by remember { mutableStateOf("") }
        val days = remember { mutableStateListOf("SUN", "TUE", "THU") }
        AlertDialog(
            onDismissRequest = { showAddTeacherDialog = false },
            title = { Text("Add Teacher Profile") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Teacher Name *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = subject, onValueChange = { subject = it }, label = { Text("Subject *") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = time, onValueChange = { time = it }, label = { Text("Preferred Time (HH:mm)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = contact, onValueChange = { contact = it }, label = { Text("Contact / Phone") }, modifier = Modifier.fillMaxWidth())
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Flexible ~5 days/week", style = MaterialTheme.typography.bodySmall)
                        Switch(checked = flexible, onCheckedChange = { flexible = it })
                    }
                    if (!flexible) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("SAT", "SUN", "MON", "TUE", "WED", "THU", "FRI").forEach { d ->
                                FilterChip(
                                    selected = d in days,
                                    onClick = { if (d in days) days.remove(d) else days.add(d) },
                                    label = { Text(d) }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (name.isNotBlank() && subject.isNotBlank()) {
                        onAddTeacher(
                            TeacherProfile(
                                name = name.trim(),
                                subject = subject.trim(),
                                preferredTime = time.trim(),
                                flexibleWeekly = flexible,
                                targetSessionsPerWeek = if (flexible) 5 else days.size,
                                defaultWeekdays = if (flexible) emptyList() else days.toList(),
                                contactInfo = contact.trim()
                            )
                        )
                        showAddTeacherDialog = false
                    }
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showAddTeacherDialog = false }) { Text("Cancel") } }
        )
    }

    // Add Routine Block Dialog (with overlap warning!)
    if (showAddRoutineDialog) {
        var title by remember { mutableStateOf("") }
        var blockType by remember { mutableStateOf(RoutineBlockType.STUDY) }
        var startTime by remember { mutableStateOf("08:00") }
        var endTime by remember { mutableStateOf("09:00") }
        val repeatDays = remember { mutableStateListOf("SAT", "SUN", "MON", "TUE", "WED", "THU") }
        val conflicts = remember(startTime, endTime, repeatDays.toList(), data.routineBlocks) {
            BaddieDomainLogic.detectRoutineConflicts(startTime, endTime, repeatDays, data.routineBlocks)
        }
        AlertDialog(
            onDismissRequest = { showAddRoutineDialog = false },
            title = { Text("Add Routine Block") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("Block Title *") }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = startTime, onValueChange = { startTime = it }, label = { Text("Start (HH:mm)") }, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = endTime, onValueChange = { endTime = it }, label = { Text("End (HH:mm)") }, modifier = Modifier.weight(1f))
                    }
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        RoutineBlockType.entries.forEach { t ->
                            FilterChip(selected = blockType == t, onClick = { blockType = t }, label = { Text(t.label) })
                        }
                    }
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf("SAT", "SUN", "MON", "TUE", "WED", "THU", "FRI").forEach { d ->
                            FilterChip(selected = d in repeatDays, onClick = { if (d in repeatDays) repeatDays.remove(d) else repeatDays.add(d) }, label = { Text(d) })
                        }
                    }
                    if (conflicts.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = colors.warning.copy(alpha = 0.14f),
                            border = BorderStroke(1.dp, colors.warning.copy(alpha = 0.4f))
                        ) {
                            Text(
                                "Overlap detected with: ${conflicts.joinToString("; ")}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.warning,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (title.isNotBlank()) {
                        onAddRoutineBlock(
                            RoutineBlock(
                                title = title.trim(),
                                blockType = blockType.name,
                                startTime = startTime.trim(),
                                endTime = endTime.trim(),
                                durationMinutes = BaddieDomainLogic.computeDurationMinutes(startTime, endTime),
                                repeatDays = repeatDays.toList()
                            )
                        )
                        showAddRoutineDialog = false
                    }
                }) { Text(if (conflicts.isNotEmpty()) "Save Anyway" else "Save Block") }
            },
            dismissButton = { TextButton(onClick = { showAddRoutineDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun TuitionSessionCard(
    session: TuitionSession,
    onStatusChange: (TuitionStatus) -> Unit,
    onReschedule: () -> Unit,
    onDelete: () -> Unit
) {
    val colors = LocalBaddieColors.current
    var menuOpen by remember { mutableStateOf(false) }
    val statusColor = when (safeEnum(session.status, TuitionStatus.SCHEDULED)) {
        TuitionStatus.COMPLETED -> colors.success
        TuitionStatus.CONFIRMED -> MaterialTheme.colorScheme.primary
        TuitionStatus.CANCELLED, TuitionStatus.TEACHER_ABSENT, TuitionStatus.STUDENT_ABSENT -> MaterialTheme.colorScheme.error
        TuitionStatus.RESCHEDULED -> colors.warning
        TuitionStatus.SCHEDULED -> colors.info
    }

    BaddieCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(session.subject, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "${session.sessionDate} • ${BaddieDomainLogic.formatDisplayTime(session.startTime)} (${session.durationMinutes}m)" +
                        if (session.teacherName.isNotBlank()) " • ${session.teacherName}" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (session.rescheduledFromId.isNotBlank()) {
                    Text(
                        "Moved from ${session.originalDate} ${session.originalTime}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.warning
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                BaddiePill(safeEnum(session.status, TuitionStatus.SCHEDULED).label, statusColor)
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = "Tuition status actions")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text("Confirm") }, onClick = { menuOpen = false; onStatusChange(TuitionStatus.CONFIRMED) })
                        DropdownMenuItem(text = { Text("Mark Completed") }, onClick = { menuOpen = false; onStatusChange(TuitionStatus.COMPLETED) })
                        DropdownMenuItem(text = { Text("Reschedule Day / Time") }, onClick = { menuOpen = false; onReschedule() })
                        DropdownMenuItem(text = { Text("Teacher Absent") }, onClick = { menuOpen = false; onStatusChange(TuitionStatus.TEACHER_ABSENT) })
                        DropdownMenuItem(text = { Text("Student Absent") }, onClick = { menuOpen = false; onStatusChange(TuitionStatus.STUDENT_ABSENT) })
                        DropdownMenuItem(text = { Text("Cancel Session") }, onClick = { menuOpen = false; onStatusChange(TuitionStatus.CANCELLED) })
                        DropdownMenuItem(text = { Text("Delete") }, onClick = { menuOpen = false; onDelete() })
                    }
                }
            }
        }
    }
}
