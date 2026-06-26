package net.kollnig.reddblockandroid.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.speech.tts.TextToSpeech
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kollnig.reddblockandroid.BuildConfig
import net.kollnig.reddblockandroid.R
import net.kollnig.reddblockandroid.data.CHINESE_VOCABULARY
import net.kollnig.reddblockandroid.ui.theme.*
import net.kollnig.reddblockandroid.util.ChineseTypingStats

// Common English words for the friction gate
private val WORD_LIST = listOf(
    "apple", "bridge", "candle", "desert", "eagle", "forest", "garden",
    "harbor", "island", "jungle", "kitchen", "lemon", "mirror", "needle",
    "orange", "palace", "garden", "river", "silver", "temple", "under",
    "valley", "winter", "yellow", "anchor", "basket", "castle", "dragon",
    "engine", "flower", "guitar", "hammer", "insect", "jacket", "kitten",
    "lantern", "marble", "nature", "ocean", "pencil", "rabbit", "saddle",
    "timber", "umbrella", "velvet", "walnut", "zenith", "branch", "copper",
    "danger", "eleven", "falcon", "gentle", "hollow", "ivory", "jigsaw",
    "kettle", "lumber", "mango", "narrow", "oyster", "pepper", "quartz",
    "rocket", "sunset", "trophy", "unfold", "voyage", "window", "absent",
    "butter", "circle", "dinner", "elbow", "finger", "gravel", "helmet",
    "indent", "jumble", "kernel", "ladder", "mental", "notice", "offset",
    "planet", "riddle", "spiral", "thread", "unique", "vertex", "wander",
    "ballet", "carbon", "differ", "effort", "fabric", "global", "hidden",
    "impact", "jungle", "knight", "linear", "method", "normal", "obtain",
    "parent", "random", "simple", "travel", "update", "vision", "weekly"
)


/**
 * @param unlockDurationText When non-null, shown in the block mode title to
 *   tell the user how long the block will be lifted (e.g. "10 minutes").
 * @param scheduleName       Name of the schedule that caused the block (shown in block mode).
 * @param isBlockMode        True when launched by the blocker service (shows close button
 *   instead of back, different title/instruction). False for in-app friction gate.
 * @param onPassed           Called when the gate is passed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrictionGateScreen(
    wordCount: Int,
    onPassed: () -> Unit,
    onBackPressed: () -> Unit,
    unlockDurationText: String? = null,
    scheduleName: String? = null,
    blockedTargetLabel: String? = null,
    isBlockMode: Boolean = false,
) {
    val useChineseMode = BuildConfig.DEBUG

    // TTS for Chinese pronunciation
    val context = LocalContext.current
    val tts = remember {
        var engine: TextToSpeech? = null
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine?.language = java.util.Locale.CHINESE
            }
        }
        engine
    }
    DisposableEffect(Unit) {
        onDispose { tts?.shutdown() }
    }

    // English words mode
    val words = remember {
        WORD_LIST.shuffled().take(wordCount)
    }
    // Chinese mode — bump this to unlock harder words
    val chineseHskLevel = 1
    val chineseWords = remember {
        CHINESE_VOCABULARY.filter { it.hskLevel <= chineseHskLevel }.shuffled().take(wordCount)
    }

    val totalCount = if (useChineseMode) chineseWords.size else words.size

    var currentWordIndex by remember { mutableIntStateOf(0) }
    var userInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    var pinyinManuallyRevealed by remember { mutableStateOf(false) }
    var wordStartMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var weeklyStats by remember { mutableStateOf(ChineseTypingStats.getWeeklyStats()) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Build the challenge phrase (all remaining words)
    val challengePhrase = remember {
        if (useChineseMode) chineseWords.joinToString("  ") { it.character }
        else words.joinToString(" ")
    }

    LaunchedEffect(Unit) {
        // The text field's node may not be attached on the first frame (e.g.
        // right after the activity is created/recreated), which makes
        // requestFocus() throw "FocusRequester is not initialized". Retry on
        // subsequent frames until it succeeds.
        repeat(10) {
            if (runCatching { focusRequester.requestFocus() }.isSuccess) {
                keyboardController?.show()
                return@LaunchedEffect
            }
            withFrameNanos { }
        }
    }

    fun normalizeUserPinyin(input: String): String =
        java.text.Normalizer.normalize(input.trim().lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("[\\p{InCombiningDiacriticalMarks}]"), "")
            .replace(Regex("[^a-z]"), "")

    fun checkWord() {
        val isCorrect = if (useChineseMode) {
            val expected = chineseWords[currentWordIndex].pinyinNormalized.replace(Regex("[^a-z]"), "")
            normalizeUserPinyin(userInput) == expected
        } else {
            userInput.trim().equals(words[currentWordIndex], ignoreCase = true)
        }
        if (isCorrect) {
            isError = false
            if (useChineseMode) {
                ChineseTypingStats.recordWord(System.currentTimeMillis() - wordStartMillis)
                weeklyStats = ChineseTypingStats.getWeeklyStats()
            }
            if (currentWordIndex >= totalCount - 1) {
                onPassed()
            } else {
                currentWordIndex++
                userInput = ""
                pinyinManuallyRevealed = false
                wordStartMillis = System.currentTimeMillis()
            }
        } else {
            isError = true
        }
    }

    // Build contextual title for block mode
    val topBarTitle = if (isBlockMode && blockedTargetLabel != null) {
        if (unlockDurationText != null)
            stringResource(R.string.block_gate_title_duration, blockedTargetLabel, unlockDurationText)
        else
            stringResource(R.string.block_gate_title, blockedTargetLabel)
    } else if (isBlockMode) {
        stringResource(R.string.app_name)
    } else {
        stringResource(R.string.friction_gate_title)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        topBarTitle,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        if (isBlockMode) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.close))
                        } else {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(8.dp))

                // Progress indicator
                LinearProgressIndicator(
                    progress = { (currentWordIndex.toFloat()) / totalCount },
                    modifier = Modifier.fillMaxWidth(),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    color = IndigoPrimary,
                )

                Text(
                    if (useChineseMode)
                        stringResource(R.string.friction_gate_progress_chinese, currentWordIndex + 1, totalCount)
                    else
                        stringResource(R.string.friction_gate_progress, currentWordIndex + 1, totalCount),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // -- Override card (iOS-style dialog look) --
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp)),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (!isBlockMode) {
                            // In-app mode: show generic title
                            Text(
                                stringResource(R.string.friction_gate_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Instruction with context
                        Text(
                            if (useChineseMode) {
                                if (isBlockMode && scheduleName != null)
                                    stringResource(R.string.block_gate_instruction_chinese, scheduleName)
                                else
                                    stringResource(R.string.override_instruction_chinese)
                            } else {
                                if (isBlockMode && scheduleName != null)
                                    stringResource(R.string.block_gate_instruction, scheduleName)
                                else
                                    stringResource(R.string.override_instruction)
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // Challenge phrase in monospace code block
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                challengePhrase,
                                modifier = Modifier.padding(14.dp),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 14.sp,
                                lineHeight = 22.sp,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }

                        // Current word highlight
                        Text(
                            if (useChineseMode)
                                stringResource(R.string.friction_gate_progress_chinese, currentWordIndex + 1, totalCount)
                            else
                                stringResource(R.string.friction_gate_progress, currentWordIndex + 1, totalCount),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline
                        )

                        // Word to type
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            color = IndigoPrimary.copy(alpha = 0.08f)
                        ) {
                            if (useChineseMode) {
                                val cw = chineseWords[currentWordIndex]
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        cw.meaning,
                                        style = MaterialTheme.typography.displaySmall,
                                        fontWeight = FontWeight.Bold,
                                        textAlign = TextAlign.Center,
                                        color = IndigoPrimary
                                    )
                                    Text(
                                        cw.character,
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    if (isError || pinyinManuallyRevealed) {
                                        Text(
                                            cw.pinyin,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            textAlign = TextAlign.Center,
                                            color = IndigoPrimary
                                        )
                                    } else {
                                        TextButton(onClick = { pinyinManuallyRevealed = true }) {
                                            Text(stringResource(R.string.friction_gate_reveal_pinyin))
                                        }
                                    }
                                    IconButton(onClick = {
                                        tts?.speak(cw.character, TextToSpeech.QUEUE_FLUSH, null, null)
                                    }) {
                                        Icon(
                                            Icons.Rounded.VolumeUp,
                                            contentDescription = "Listen",
                                            tint = IndigoPrimary
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    words[currentWordIndex],
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center,
                                    color = IndigoPrimary
                                )
                            }
                        }

                        // Input field
                        OutlinedTextField(
                            value = userInput,
                            onValueChange = {
                                userInput = it
                                isError = false
                            },
                            placeholder = {
                                Text(
                                    if (useChineseMode) stringResource(R.string.friction_gate_pinyin_hint)
                                    else stringResource(R.string.type_here_hint),
                                    color = TextHint
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            isError = isError,
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Done,
                                autoCorrectEnabled = false,
                                keyboardType = KeyboardType.Password
                            ),
                            keyboardActions = KeyboardActions(onDone = { checkWord() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                            )
                        )

                        // Buttons row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = onBackPressed,
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            ) {
                                Text(
                                    stringResource(if (isBlockMode) R.string.keep_blocked else R.string.cancel),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            Button(
                                onClick = { checkWord() },
                                modifier = Modifier.weight(1f).height(48.dp),
                                shape = RoundedCornerShape(10.dp),
                                enabled = userInput.isNotBlank(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            ) {
                                Text(
                                    if (currentWordIndex >= totalCount - 1) stringResource(R.string.override_button)
                                    else stringResource(R.string.friction_gate_next),
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                if (useChineseMode) {
                    val minutes = (weeklyStats.totalDurationMs / 60_000L).toInt()
                    Text(
                        text = stringResource(
                            R.string.friction_gate_weekly_stats,
                            weeklyStats.wordCount,
                            minutes
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(32.dp))
            }
    }
}
