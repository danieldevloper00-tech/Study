package com.example.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.*
import com.example.ui.components.BaddieCard
import com.example.ui.components.BaddiePill
import com.example.ui.theme.LocalBaddieColors

@Composable
fun OnboardingWizard(
    initialName: String,
    onFinish: (
        displayName: String,
        timezone: String,
        teachers: List<TeacherProfile>,
        seedDefaultTuition: Boolean,
        routineBlocks: List<RoutineBlock>,
        subjects: List<String>,
        goals: List<Goal>,
        aiMode: AiMode,
        telegramToken: String,
        telegramChatId: String,
        telegramEnabled: Boolean
    ) -> Unit,
    onSkipAll: () -> Unit
) {
    val colors = LocalBaddieColors.current
    var step by remember { mutableIntStateOf(1) }

    // Step 1: Profile & timezone
    var displayName by remember { mutableStateOf(initialName.ifBlank { "" }) }
    var timezone by remember { mutableStateOf("Asia/Dhaka") }

    // Step 2: Teachers (never invented; user adds)
    val teachers = remember { mutableStateListOf<TeacherProfile>() }
    var teacherName by remember { mutableStateOf("") }
    var teacherSubject by remember { mutableStateOf("Mathematics") }
    var teacherTime by remember { mutableStateOf("16:00") }
    var teacherFlexible by remember { mutableStateOf(false) }

    // Step 3: Tuition template opt-in
    var seedTuitionTemplate by remember { mutableStateOf(true) }

    // Step 4: Routine blocks
    val routineBlocks = remember {
        mutableStateListOf(
            RoutineBlock(title = "Wake-up & Morning Prep", blockType = RoutineBlockType.MORNING_PREP.name, startTime = "06:30", endTime = "07:15", durationMinutes = 45),
            RoutineBlock(title = "Focused Morning Study", blockType = RoutineBlockType.STUDY.name, startTime = "07:30", endTime = "09:00", durationMinutes = 90),
            RoutineBlock(title = "Evening Tuition Window", blockType = RoutineBlockType.TUITION.name, startTime = "16:00", endTime = "19:00", durationMinutes = 180, repeatDays = listOf("SAT", "SUN", "MON", "TUE", "WED", "THU")),
            RoutineBlock(title = "Sleep Wind-down", blockType = RoutineBlockType.SLEEP.name, startTime = "23:00", endTime = "23:30", durationMinutes = 30)
        )
    }

    // Step 5: Subjects
    val allDefaultSubjects = listOf("Bangla", "English", "Mathematics", "Higher Mathematics", "Physics", "Chemistry", "Biology", "ICT")
    val selectedSubjects = remember { mutableStateListOf("Mathematics", "Higher Mathematics", "Physics", "Chemistry", "English") }
    var customSubject by remember { mutableStateOf("") }

    // Step 6: Initial goals
    val initialGoals = remember { mutableStateListOf<Goal>() }
    var goalTitle by remember { mutableStateOf("") }
    var goalTimeframe by remember { mutableStateOf(GoalTimeframe.WEEKLY) }

    // Step 7: Telegram
    var telegramToken by remember { mutableStateOf("") }
    var telegramChatId by remember { mutableStateOf("") }
    var telegramEnabled by remember { mutableStateOf(false) }

    // Step 8: AI mode
    var aiMode by remember { mutableStateOf(AiMode.ADVISOR) }

    // Step 9: Reminder summary preference
    var reminderPrefNote by remember { mutableStateOf("15 minutes before tuition & high-priority tasks") }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentWindowInsets = WindowInsets.safeDrawing
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Setup Baddie",
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = "Step $step of 9",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                TextButton(
                    onClick = onSkipAll,
                    modifier = Modifier.testTag("onboarding_skip_all")
                ) {
                    Text("Skip Setup")
                }
            }

            LinearProgressIndicator(
                progress = { step / 9f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp),
                trackColor = colors.borderSubtle
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (step) {
                    1 -> {
                        BaddieCard {
                            Text("Profile & Local Timezone", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Everything in Baddie aligns to your actual local clock.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(
                                value = displayName,
                                onValueChange = { displayName = it },
                                label = { Text("Your Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            Text("Timezone", style = MaterialTheme.typography.labelLarge)
                            Spacer(Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Asia/Dhaka", "UTC", "Asia/Kolkata").forEach { tz ->
                                    FilterChip(
                                        selected = timezone == tz,
                                        onClick = { timezone = tz },
                                        label = { Text(tz) }
                                    )
                                }
                            }
                        }
                    }

                    2 -> {
                        BaddieCard {
                            Text("Teachers (Optional)", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Add your real teachers. Baddie never invents teacher names.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = teacherName,
                                onValueChange = { teacherName = it },
                                label = { Text("Teacher Name") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(
                                    value = teacherSubject,
                                    onValueChange = { teacherSubject = it },
                                    label = { Text("Subject") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedTextField(
                                    value = teacherTime,
                                    onValueChange = { teacherTime = it },
                                    label = { Text("Time (HH:mm)") },
                                    singleLine = true,
                                    modifier = Modifier.width(120.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Flexible ~5 days/week (Other subjects)", style = MaterialTheme.typography.bodySmall)
                                Switch(checked = teacherFlexible, onCheckedChange = { teacherFlexible = it })
                            }
                            Button(
                                onClick = {
                                    if (teacherName.isNotBlank() && teacherSubject.isNotBlank()) {
                                        teachers += TeacherProfile(
                                            name = teacherName.trim(),
                                            subject = teacherSubject.trim(),
                                            preferredTime = teacherTime.ifBlank { "16:00" },
                                            flexibleWeekly = teacherFlexible,
                                            targetSessionsPerWeek = if (teacherFlexible) 5 else 3,
                                            defaultWeekdays = if (teacherFlexible) listOf("SAT", "MON", "WED") else listOf("SUN", "TUE", "THU")
                                        )
                                        teacherName = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Add Teacher")
                            }
                        }
                        teachers.forEachIndexed { idx, t ->
                            BaddieCard(elevated = true) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("${t.name} • ${t.subject}", style = MaterialTheme.typography.titleSmall)
                                        Text("${t.preferredTime} (${if (t.flexibleWeekly) "Flexible 5x/wk" else "Sun/Tue/Thu"})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { teachers.removeAt(idx) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove teacher")
                                    }
                                }
                            }
                        }
                    }

                    3 -> {
                        BaddieCard {
                            Text("Weekly Tuition Structure", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Use your flexible default tuition cadence for the next 7 days (fully editable anytime):",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            listOf(
                                "Saturday: Other subjects",
                                "Sunday: Mathematics / Higher Math + Physics / Chemistry",
                                "Monday: Other subjects",
                                "Tuesday: Mathematics / Higher Math + Physics / Chemistry",
                                "Wednesday: Other subjects",
                                "Thursday: Mathematics / Higher Math + Physics / Chemistry",
                                "Friday: No regular tuition"
                            ).forEach { line ->
                                Text("• $line", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Seed this 7-day starting schedule", style = MaterialTheme.typography.titleSmall)
                                Switch(checked = seedTuitionTemplate, onCheckedChange = { seedTuitionTemplate = it })
                            }
                        }
                    }

                    4 -> {
                        BaddieCard {
                            Text("Daily Routine Blocks", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Starter adaptive blocks (never rigid—adjust or delete anytime).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        routineBlocks.forEachIndexed { idx, b ->
                            BaddieCard(elevated = true) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(b.title, style = MaterialTheme.typography.titleSmall)
                                        Text("${b.startTime} – ${b.endTime}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { routineBlocks.removeAt(idx) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove block")
                                    }
                                }
                            }
                        }
                    }

                    5 -> {
                        BaddieCard {
                            Text("Study Subjects", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                (allDefaultSubjects + selectedSubjects).distinct().forEach { subj ->
                                    val selected = subj in selectedSubjects
                                    FilterChip(
                                        selected = selected,
                                        onClick = {
                                            if (selected) selectedSubjects.remove(subj) else selectedSubjects.add(subj)
                                        },
                                        label = { Text(subj) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = customSubject,
                                    onValueChange = { customSubject = it },
                                    label = { Text("Custom Subject") },
                                    singleLine = true,
                                    modifier = Modifier.weight(1f)
                                )
                                Button(onClick = {
                                    if (customSubject.isNotBlank()) {
                                        selectedSubjects.add(customSubject.trim())
                                        customSubject = ""
                                    }
                                }) { Text("Add") }
                            }
                        }
                    }

                    6 -> {
                        BaddieCard {
                            Text("Initial Goals (Optional)", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = goalTitle,
                                onValueChange = { goalTitle = it },
                                label = { Text("Goal title (e.g., Complete 3 Physics chapters)") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GoalTimeframe.entries.forEach { tf ->
                                    FilterChip(
                                        selected = goalTimeframe == tf,
                                        onClick = { goalTimeframe = tf },
                                        label = { Text(tf.label) }
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    if (goalTitle.isNotBlank()) {
                                        initialGoals += Goal(title = goalTitle.trim(), timeframe = goalTimeframe.name)
                                        goalTitle = ""
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Add Goal") }
                        }
                        initialGoals.forEachIndexed { idx, g ->
                            BaddieCard(elevated = true) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text(g.title, style = MaterialTheme.typography.titleSmall)
                                        BaddiePill(g.timeframe)
                                    }
                                    IconButton(onClick = { initialGoals.removeAt(idx) }) {
                                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove goal")
                                    }
                                }
                            }
                        }
                    }

                    7 -> {
                        BaddieCard {
                            Text("Telegram Bot Notifications (Optional)", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Bot tokens are stored strictly in private device preferences. Skip if you want to configure later in Settings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = telegramToken,
                                onValueChange = { telegramToken = it },
                                label = { Text("Bot Token") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = telegramChatId,
                                onValueChange = { telegramChatId = it },
                                label = { Text("Chat ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Enable Telegram alerts")
                                Switch(checked = telegramEnabled, onCheckedChange = { telegramEnabled = it })
                            }
                        }
                    }

                    8 -> {
                        BaddieCard {
                            Text("Gemini AI Assistant Mode", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Powered securely through Firebase AI Logic (no manual API key required).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(10.dp))
                            AiMode.entries.forEach { mode ->
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (aiMode == mode) MaterialTheme.colorScheme.primaryContainer else colors.surfaceElevated,
                                    border = BorderStroke(1.dp, if (aiMode == mode) MaterialTheme.colorScheme.primary else colors.border),
                                    onClick = { aiMode = mode }
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Text(mode.label, style = MaterialTheme.typography.titleSmall)
                                        Text(mode.subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }

                    9 -> {
                        BaddieCard {
                            Text("Reminder Preferences & Ready", style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(8.dp))
                            OutlinedTextField(
                                value = reminderPrefNote,
                                onValueChange = { reminderPrefNote = it },
                                label = { Text("Default reminder lead-time note") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Summary: ${displayName.ifBlank { "Owner" }} • $timezone • ${selectedSubjects.size} subjects • ${teachers.size} teachers • AI: ${aiMode.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (step > 1) {
                    OutlinedButton(
                        onClick = { step-- },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) { Text("Back") }
                }
                if (step < 9) {
                    Button(
                        onClick = { step++ },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("onboarding_next")
                    ) { Text(if (step in listOf(2, 6, 7)) "Next / Skip" else "Next") }
                } else {
                    Button(
                        onClick = {
                            onFinish(
                                displayName.ifBlank { "Owner" },
                                timezone,
                                teachers.toList(),
                                seedTuitionTemplate,
                                routineBlocks.toList(),
                                selectedSubjects.toList(),
                                initialGoals.toList(),
                                aiMode,
                                telegramToken,
                                telegramChatId,
                                telegramEnabled
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                            .testTag("onboarding_finish")
                    ) { Text("Launch Baddie") }
                }
            }
        }
    }
}
