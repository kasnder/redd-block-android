package net.kollnig.reddblockandroid.ui.screen

import android.Manifest
import android.content.ActivityNotFoundException
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.kollnig.reddblockandroid.assistant.AssistantMessage
import net.kollnig.reddblockandroid.assistant.AssistantResult
import net.kollnig.reddblockandroid.assistant.AssistantViewModel
import net.kollnig.reddblockandroid.assistant.OpenAIModels
import net.kollnig.reddblockandroid.assistant.ScheduleProposal

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AssistantScreen(
    messages: SnapshotStateList<AssistantMessage>,
    onReviewProposal: (ScheduleProposal) -> Unit
) {
    val context = LocalContext.current
    val viewModel = remember { AssistantViewModel(context) }
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var hasApiKey by remember { mutableStateOf(viewModel.hasApiKey()) }
    var showSettings by remember { mutableStateOf(!hasApiKey) }
    var apiKey by remember { mutableStateOf(viewModel.apiKeyDraft) }
    var model by remember { mutableStateOf(viewModel.modelDraft) }
    var goals by remember { mutableStateOf(viewModel.goalsDraft) }
    var usageSharing by remember { mutableStateOf(viewModel.usageSharingEnabled) }
    var motionSharing by remember { mutableStateOf(viewModel.motionSharingEnabled) }
    var wifiSharing by remember { mutableStateOf(viewModel.wifiSharingEnabled) }
    var isSaving by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var messageDraft by remember { mutableStateOf("") }
    val isFreshChat = messages.size == 1 && messages.firstOrNull()?.role == AssistantMessage.Role.ASSISTANT
    val examplePrompts = remember {
        listOf(
            "Analyze my schedules",
            "Help me stop morning scrolling.",
            "Block commute scrolling.",
            "Suggest a bedtime schedule.",
            "Use Home Wi-Fi."
        )
    }
    fun syncSettingsDrafts() {
        viewModel.apiKeyDraft = apiKey
        viewModel.modelDraft = model.ifBlank { OpenAIModels.DEFAULT_MODEL }
        viewModel.goalsDraft = goals
        viewModel.usageSharingEnabled = usageSharing
        viewModel.motionSharingEnabled = motionSharing
        viewModel.wifiSharingEnabled = wifiSharing
    }

    val motionPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        motionSharing = granted
        if (granted) {
            viewModel.motionSharingEnabled = true
            viewModel.startMotionUpdates()
        } else {
            scope.launch { snackbarHostState.showSnackbar("Motion permission was not granted.") }
        }
    }

    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        wifiSharing = granted
        if (!granted) {
            scope.launch { snackbarHostState.showSnackbar("Wi-Fi context permission was not granted.") }
        }
    }

    fun sendMessage(text: String) {
        if (text.isBlank() || isSending) return
        messageDraft = ""
        messages.add(AssistantMessage(AssistantMessage.Role.USER, text))
        scope.launch {
            isSending = true
            try {
                val result = viewModel.sendMessage(text)
                when (result) {
                    is AssistantResult.Message ->
                        messages.add(AssistantMessage(AssistantMessage.Role.ASSISTANT, result.text))
                    is AssistantResult.Proposal ->
                        messages.add(
                            AssistantMessage(
                                role = AssistantMessage.Role.ASSISTANT,
                                text = result.text,
                                proposal = result.proposal
                            )
                        )
                }
            } catch (e: Exception) {
                messages.add(
                    AssistantMessage(
                        AssistantMessage.Role.ASSISTANT,
                        e.message ?: "I could not reach OpenAI. Check your key and network connection."
                    )
                )
            } finally {
                isSending = false
            }
        }
    }

    fun sendCurrentMessage() {
        sendMessage(messageDraft.trim())
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Assistant", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(
                        onClick = {
                            messages.clear()
                            messages.add(assistantWelcomeMessage())
                        }
                    ) {
                        Icon(Icons.Rounded.Delete, contentDescription = "Reset chat")
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "Assistant settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(vertical = 12.dp)
            ) {
                items(messages) { message ->
                    AssistantMessageCard(
                        message = message,
                        onReviewProposal = onReviewProposal
                    )
                }
                if (isSending) {
                    item {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            if (isFreshChat) {
                FlowRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    examplePrompts.forEach { prompt ->
                        AssistChip(
                            onClick = { sendMessage(prompt) },
                            enabled = hasApiKey && !isSending,
                            label = {
                                Text(
                                    prompt,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = messageDraft,
                    onValueChange = { messageDraft = it },
                    modifier = Modifier.weight(1f),
                    enabled = hasApiKey && !isSending,
                    placeholder = { Text(if (hasApiKey) "What problem should ReDD help with?" else "Add an OpenAI key first") },
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { sendCurrentMessage() }),
                    maxLines = 4
                )
                FilledIconButton(
                    onClick = { sendCurrentMessage() },
                    enabled = hasApiKey && messageDraft.isNotBlank() && !isSending
                ) {
                    Icon(Icons.Rounded.Send, contentDescription = "Send")
                }
            }
        }
    }

    if (showSettings) {
        AlertDialog(
            onDismissRequest = { showSettings = false },
            title = { Text("Assistant setup", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("OpenAI API key") },
                        leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Model") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = goals,
                        onValueChange = { goals = it },
                        label = { Text("Goal notes") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = usageSharing,
                            onCheckedChange = { usageSharing = it }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Share coarse Usage Stats")
                            Text(
                                "Labels, packages, duration buckets only.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (usageSharing && !viewModel.hasUsageStatsPermission()) {
                        OutlinedButton(
                            onClick = {
                                try {
                                    context.startActivity(viewModel.usageSettingsIntent())
                                } catch (_: ActivityNotFoundException) {
                                    scope.launch { snackbarHostState.showSnackbar("Could not open Usage Access settings.") }
                                }
                            }
                        ) {
                            Icon(Icons.Rounded.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Open Usage Access settings")
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = motionSharing,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    motionSharing = false
                                } else if (viewModel.hasMotionPermission()) {
                                    motionSharing = true
                                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    motionPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                                } else {
                                    motionSharing = true
                                }
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Share motion context")
                            Text(
                                "Uses Android Activity Recognition: still, walking, cycling, running, or in vehicle.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = wifiSharing,
                            onCheckedChange = { enabled ->
                                if (!enabled) {
                                    wifiSharing = false
                                } else if (viewModel.hasWifiPermission()) {
                                    wifiSharing = true
                                } else {
                                    wifiPermissionLauncher.launch(viewModel.wifiRuntimePermission())
                                }
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Share Wi-Fi context")
                            Text(
                                "Reads the currently connected Wi-Fi network for home, work, and campus schedules. Android requires location permission for SSID access.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !isSaving,
                    onClick = {
                        syncSettingsDrafts()
                        scope.launch {
                            isSaving = true
                            try {
                                viewModel.saveSettings()
                                hasApiKey = viewModel.hasApiKey()
                                showSettings = false
                                snackbarHostState.showSnackbar("Assistant settings saved.")
                            } catch (e: Exception) {
                                snackbarHostState.showSnackbar(e.message ?: "Could not save assistant settings.")
                            } finally {
                                isSaving = false
                            }
                        }
                    }
                ) {
                    Text(if (isSaving) "Saving..." else "Save")
                }
            },
            dismissButton = {
                Row {
                    if (hasApiKey) {
                        TextButton(onClick = {
                            viewModel.clearApiKey()
                            apiKey = ""
                            hasApiKey = false
                        }) {
                            Icon(Icons.Rounded.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Clear key")
                        }
                    }
                    TextButton(onClick = { showSettings = false }) {
                        Text("Cancel")
                    }
                }
            }
        )
    }
}

@Composable
private fun AssistantMessageCard(
    message: AssistantMessage,
    onReviewProposal: (ScheduleProposal) -> Unit
) {
    val isUser = message.role == AssistantMessage.Role.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(if (isUser) 0.86f else 0.96f),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surface
            )
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isUser) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        if (isUser) "You" else "Ulrik",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(message.text, style = MaterialTheme.typography.bodyMedium)
                message.proposal?.let { proposal ->
                    HorizontalDivider()
                    Text(
                        proposal.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        proposalSummary(proposal),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 5,
                        overflow = TextOverflow.Ellipsis
                    )
                    Button(onClick = { onReviewProposal(proposal) }) {
                        Text("Review and create")
                    }
                }
            }
        }
    }
}

fun assistantWelcomeMessage(): AssistantMessage {
    return AssistantMessage(
        AssistantMessage.Role.ASSISTANT,
        "Tell me what is going wrong with your phone use. I can draft a new schedule, but I cannot weaken existing blocks."
    )
}

private fun proposalSummary(proposal: ScheduleProposal): String {
    val targets = (proposal.blockedApps + proposal.blockedWebsites).joinToString(", ")
    val conditions = listOfNotNull(
        proposal.timing.motionCondition?.let { "when ${it.activity.label.lowercase()}" },
        proposal.timing.wifiCondition?.let { "on ${it.label} Wi-Fi" }
    )
    val timing = when (proposal.timing.type) {
        net.kollnig.reddblockandroid.data.ScheduleTiming.ScheduleType.MANUAL -> "Manual"
        net.kollnig.reddblockandroid.data.ScheduleTiming.ScheduleType.DAILY ->
            "Daily ${proposal.timing.timeHour.twoDigits()}:${proposal.timing.timeMinute.twoDigits()}-${proposal.timing.endTimeHour.twoDigits()}:${proposal.timing.endTimeMinute.twoDigits()}"
        net.kollnig.reddblockandroid.data.ScheduleTiming.ScheduleType.WEEKLY ->
            "Weekly ${proposal.timing.daysOfWeek.joinToString { it.name.take(3) }} ${proposal.timing.timeHour.twoDigits()}:${proposal.timing.timeMinute.twoDigits()}-${proposal.timing.endTimeHour.twoDigits()}:${proposal.timing.endTimeMinute.twoDigits()}"
    }
    val activeWhen = if (conditions.isEmpty()) "" else "\nActive ${conditions.joinToString(" and ")}"
    return "$timing$activeWhen\nBlocks: $targets\nFriction: ${proposal.frictionWordCount} words\nPause: ${proposal.autoReenableMinutes.pauseLabel()}"
}

private fun Int?.twoDigits(): String = String.format("%02d", this ?: 0)

private fun Int.pauseLabel(): String = when (this) {
    0 -> "until manually re-enabled"
    5, 10, 15, 30 -> "$this minutes"
    60 -> "1 hour"
    120 -> "2 hours"
    240 -> "4 hours"
    480 -> "8 hours"
    1440 -> "24 hours"
    else -> "$this minutes"
}
