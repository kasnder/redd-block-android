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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kollnig.reddblockandroid.R
import net.kollnig.reddblockandroid.ui.theme.*

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FrictionGateScreen(
    wordCount: Int,
    onPassed: () -> Unit,
    onBackPressed: () -> Unit
) {
    val words = remember {
        WORD_LIST.shuffled().take(wordCount)
    }

    var currentWordIndex by remember { mutableIntStateOf(0) }
    var userInput by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Build the challenge phrase (all remaining words)
    val challengePhrase = remember { words.joinToString(" ") }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    fun checkWord() {
        if (userInput.trim().equals(words[currentWordIndex], ignoreCase = true)) {
            isError = false
            if (currentWordIndex >= words.lastIndex) {
                onPassed()
            } else {
                currentWordIndex++
                userInput = ""
            }
        } else {
            isError = true
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.friction_gate_title),
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackPressed) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
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
                progress = { (currentWordIndex.toFloat()) / words.size },
                modifier = Modifier.fillMaxWidth(),
                trackColor = DayChipUnselected,
                color = IndigoPrimary,
            )

            Text(
                stringResource(R.string.friction_gate_progress, currentWordIndex + 1, words.size),
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary
            )

            // ── Override card (iOS-style dialog look) ──
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
                    // Title
                    Text(
                        stringResource(R.string.friction_gate_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )

                    // Instruction
                    Text(
                        stringResource(R.string.override_instruction),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )

                    // Challenge phrase in monospace code block
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = CoolGrey
                    ) {
                        Text(
                            challengePhrase,
                            modifier = Modifier.padding(14.dp),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            lineHeight = 22.sp,
                            color = TextPrimary
                        )
                    }

                    // Current word highlight
                    Text(
                        stringResource(R.string.friction_gate_progress, currentWordIndex + 1, words.size),
                        style = MaterialTheme.typography.labelSmall,
                        color = TextHint
                    )

                    // Word to type
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp),
                        color = IndigoPrimary.copy(alpha = 0.08f)
                    ) {
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

                    // Input field
                    OutlinedTextField(
                        value = userInput,
                        onValueChange = {
                            userInput = it
                            isError = false
                        },
                        placeholder = { Text(stringResource(R.string.type_here_hint), color = TextHint) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        shape = RoundedCornerShape(10.dp),
                        singleLine = true,
                        isError = isError,
                        supportingText = if (isError) {
                            { Text(stringResource(R.string.friction_gate_error)) }
                        } else null,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                            autoCorrectEnabled = false,
                            keyboardType = KeyboardType.Password
                        ),
                        keyboardActions = KeyboardActions(onDone = { checkWord() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            unfocusedContainerColor = SurfaceLight,
                            focusedContainerColor = SurfaceLight
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
                                contentColor = TextPrimary
                            )
                        ) {
                            Text(
                                stringResource(R.string.cancel),
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        Button(
                            onClick = { checkWord() },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            enabled = userInput.isNotBlank(),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = DarkNavy,
                                contentColor = White
                            )
                        ) {
                            Text(
                                if (currentWordIndex >= words.lastIndex) stringResource(R.string.override_button)
                                else stringResource(R.string.friction_gate_next),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
