package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.ProviderHealth
import com.example.core.UptimeStatusResult
import com.example.data.domain.BaddieDomainLogic
import com.example.data.model.ReminderItem
import com.example.ui.components.*
import com.example.ui.theme.LocalBaddieColors
import com.example.ui.viewmodel.BaddieDashboardData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationsAndUptimeScreen(
    data: BaddieDashboardData,
    savedTelegramToken: String,
    savedTelegramChatId: String,
    savedUptimeKey: String,
    uptimeStatus: UptimeStatusResult,
    dbHealth: ProviderHealth?,
    aiHealth: ProviderHealth?,
    onSaveTelegram: (String, String, Boolean, List<String>) -> Unit,
    onTestTelegram: () -> Unit,
    onSendBriefingNow: () -> Unit,
    onPollTelegramCommands: () -> Unit,
    onAddReminder: (String, String, String) -> Unit,
    onDeleteReminder: (String) -> Unit,
    onSaveUptimeKey: (String) -> Unit,
    onRefreshHealth: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val colors = LocalBaddieColors.current
    var tokenInput by remember(savedTelegramToken) { mutableStateOf(savedTelegramToken) }
    var chatIdInput by remember(savedTelegramChatId) { mutableStateOf(savedTelegramChatId) }
    var telegramEnabled by remember(data.profile.telegramEnabled) { mutableStateOf(data.profile.telegramEnabled) }
    var uptimeKeyInput by remember(savedUptimeKey) { mutableStateOf(savedUptimeKey) }
    val selectedPrefs = remember(data.profile.telegramPrefs) {
        mutableStateListOf<String>().apply { addAll(data.profile.telegramPrefs) }
    }

    var reminderTitle by remember { mutableStateOf("") }
    var reminderIso by remember {
        mutableStateOf(
            BaddieDomainLogic.nowInZone(data.profile.timezone).plusMinutes(30).withSecond(0).withNano(0).toString()
        )
    }

    val notificationPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) { onRefreshHealth() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        TopAppBar(
            title = { Text("Telegram, Reminders & Uptime") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            // 1. Telegram Bot Integration
            item {
                BaddieCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Telegram Bot Integration", style = MaterialTheme.typography.titleMedium)
                        Switch(checked = telegramEnabled, onCheckedChange = { telegramEnabled = it })
                    }
                    Text(
                        if (savedTelegramToken.isBlank()) "Status: Awaiting bot token" else "Status: Credentials saved locally",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = tokenInput, onValueChange = { tokenInput = it }, label = { Text("Bot Token") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = chatIdInput, onValueChange = { chatIdInput = it }, label = { Text("Chat ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    @OptIn(ExperimentalLayoutApi::class)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("MORNING_BRIEFING", "UPCOMING_TUITION", "STUDY_REMINDERS", "TASK_DEADLINES", "DAILY_REPORTS", "UPTIME_ALERTS").forEach { pref ->
                            FilterChip(
                                selected = pref in selectedPrefs,
                                onClick = { if (pref in selectedPrefs) selectedPrefs.remove(pref) else selectedPrefs.add(pref) },
                                label = { Text(pref.replace("_", " ")) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onSaveTelegram(tokenInput, chatIdInput, telegramEnabled, selectedPrefs.toList()) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Save") }
                        OutlinedButton(onClick = onTestTelegram, modifier = Modifier.weight(1f)) { Text("Test Bot") }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onSendBriefingNow, modifier = Modifier.weight(1f)) { Text("Send Briefing") }
                        OutlinedButton(onClick = onPollTelegramCommands, modifier = Modifier.weight(1f)) { Text("Poll /commands") }
                    }
                }
            }

            // 2. Smart Reminders
            item {
                BaddieCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Smart Reminders (${data.profile.timezone})", style = MaterialTheme.typography.titleMedium)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            TextButton(onClick = { notificationPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                                Text("Enable Alerts")
                            }
                        }
                    }
                    OutlinedTextField(value = reminderTitle, onValueChange = { reminderTitle = it }, label = { Text("Reminder Title") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(value = reminderIso, onValueChange = { reminderIso = it }, label = { Text("ISO Trigger Time") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (reminderTitle.isNotBlank()) {
                                onAddReminder(reminderTitle.trim(), reminderIso.trim(), "NONE")
                                reminderTitle = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Schedule Reminder") }
                }
            }

            items(data.reminders, key = { "rem_${it.id}" }) { rem ->
                BaddieCard(elevated = true) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rem.title, style = MaterialTheme.typography.titleSmall)
                            Text("${rem.triggerDateTimeIso} (${rem.timezone})", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { onDeleteReminder(rem.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete reminder")
                        }
                    }
                }
            }

            // 3. System Health & UptimeRobot
            item {
                BaddieCard {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("System Health & UptimeRobot", style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = onRefreshHealth) { Icon(Icons.Rounded.Refresh, contentDescription = "Refresh health") }
                    }
                    dbHealth?.let { h ->
                        Text("Database (${h.providerName}): ${h.statusDetail}", style = MaterialTheme.typography.bodySmall, color = if (h.healthy) colors.success else MaterialTheme.colorScheme.error)
                    }
                    aiHealth?.let { h ->
                        Text("AI (${h.providerName}): ${h.statusDetail}", style = MaterialTheme.typography.bodySmall, color = if (h.healthy) colors.success else MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = uptimeKeyInput, onValueChange = { uptimeKeyInput = it }, label = { Text("UptimeRobot Read-Only API Key") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { onSaveUptimeKey(uptimeKeyInput) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Save & Check Monitors")
                    }
                    Spacer(Modifier.height(8.dp))
                    when (uptimeStatus) {
                        is UptimeStatusResult.AwaitingCredentials -> {
                            BaddiePill("Awaiting UptimeRobot API Key", colors.warning)
                        }
                        is UptimeStatusResult.Error -> {
                            Text(uptimeStatus.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        is UptimeStatusResult.Live -> {
                            if (uptimeStatus.monitors.isEmpty()) {
                                Text("No monitors returned from UptimeRobot.", style = MaterialTheme.typography.bodySmall)
                            } else {
                                uptimeStatus.monitors.forEach { m ->
                                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Column(Modifier.weight(1f)) {
                                            Text(m.friendlyName, style = MaterialTheme.typography.titleSmall)
                                            Text("${m.averageResponseMs}ms • ${m.uptimeRatio}% • ${m.lastIncidentSummary}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        BaddiePill(m.statusLabel, if (m.statusCode == 2) colors.success else MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsAndBackupScreen(
    data: BaddieDashboardData,
    onUpdateSettings: (Map<String, Any>) -> Unit,
    onExportJson: () -> String,
    onExportTasksCsv: () -> String,
    onExportStudyCsv: () -> String,
    onRestoreBackup: (String) -> Unit,
    onRerunOnboarding: () -> Unit,
    onSignOut: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var pendingExportContent by remember { mutableStateOf<String?>(null) }

    val createJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        val content = pendingExportContent ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        }
        pendingExportContent = null
    }

    val createCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        val content = pendingExportContent ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray()) }
        }
        pendingExportContent = null
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            if (!raw.isNullOrBlank()) {
                onRestoreBackup(raw)
            }
        }
    }

    var studyTargetStr by remember(data.profile.dailyStudyTargetMinutes) {
        mutableStateOf(data.profile.dailyStudyTargetMinutes.toString())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        TopAppBar(
            title = { Text("Settings & Backup") },
            navigationIcon = {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
            },
            windowInsets = WindowInsets(0, 0, 0, 0),
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 28.dp)
        ) {
            item {
                BaddieCard {
                    Text("Account", style = MaterialTheme.typography.titleMedium)
                    Text("${data.profile.displayName} • ${data.profile.email}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onRerunOnboarding, modifier = Modifier.weight(1f)) {
                            Text("Setup Wizard")
                        }
                        Button(
                            onClick = onSignOut,
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("sign_out_button")
                        ) { Text("Sign Out") }
                    }
                }
            }

            item {
                BaddieCard {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("DARK", "LIGHT", "SYSTEM").forEach { mode ->
                            FilterChip(
                                selected = data.profile.themeMode == mode,
                                onClick = { onUpdateSettings(mapOf("themeMode" to mode)) },
                                label = { Text(mode) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Accent Color", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("LAVENDER", "SAGE", "SLATE", "AMBER").forEach { accent ->
                            FilterChip(
                                selected = data.profile.accentPreset == accent,
                                onClick = { onUpdateSettings(mapOf("accentPreset" to accent)) },
                                label = { Text(accent) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Compact Layout Density")
                        Switch(
                            checked = data.profile.compactLayout,
                            onCheckedChange = { onUpdateSettings(mapOf("compactLayout" to it)) }
                        )
                    }
                }
            }

            item {
                BaddieCard {
                    Text("Routine & Study Target", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text("Timezone", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Asia/Dhaka", "UTC", "Asia/Kolkata").forEach { tz ->
                            FilterChip(
                                selected = data.profile.timezone == tz,
                                onClick = { onUpdateSettings(mapOf("timezone" to tz)) },
                                label = { Text(tz) }
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = studyTargetStr,
                            onValueChange = { studyTargetStr = it },
                            label = { Text("Daily Study Goal (min)") },
                            modifier = Modifier.weight(1f)
                        )
                        Button(onClick = {
                            val mins = studyTargetStr.toIntOrNull()?.coerceIn(15, 1440) ?: 180
                            onUpdateSettings(mapOf("dailyStudyTargetMinutes" to mins))
                        }) { Text("Save") }
                    }
                }
            }

            item {
                BaddieCard {
                    Text("Export & Validated Backup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Export your workspace to JSON or CSV, or restore from a validated Baddie JSON backup.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                pendingExportContent = onExportJson()
                                createJsonLauncher.launch("baddie_backup.json")
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Export Full JSON Backup") }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    pendingExportContent = onExportTasksCsv()
                                    createCsvLauncher.launch("baddie_tasks.csv")
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Tasks CSV") }
                            OutlinedButton(
                                onClick = {
                                    pendingExportContent = onExportStudyCsv()
                                    createCsvLauncher.launch("baddie_study_tuition.csv")
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("Study/Tuition CSV") }
                        }
                        OutlinedButton(
                            onClick = { restoreLauncher.launch(arrayOf("application/json")) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Restore from JSON Backup") }
                    }
                }
            }
        }
    }
}
