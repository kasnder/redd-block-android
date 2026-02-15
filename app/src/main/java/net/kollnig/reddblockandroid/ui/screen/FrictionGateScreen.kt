package net.kollnig.reddblockandroid.ui.screen

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kollnig.reddblockandroid.R

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
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(16.dp))

            // Progress indicator
            LinearProgressIndicator(
                progress = { (currentWordIndex.toFloat()) / words.size },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            )

            Text(
                stringResource(R.string.friction_gate_progress, currentWordIndex + 1, words.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            // Explanation
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Text(
                stringResource(R.string.friction_gate_desc),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(8.dp))

            // Word to type
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            ) {
                Text(
                    words[currentWordIndex],
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            // Input field
            OutlinedTextField(
                value = userInput,
                onValueChange = {
                    userInput = it
                    isError = false
                },
                label = { Text(stringResource(R.string.friction_gate_input_label)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                shape = RoundedCornerShape(16.dp),
                singleLine = true,
                isError = isError,
                supportingText = if (isError) {
                    { Text(stringResource(R.string.friction_gate_error)) }
                } else null,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { checkWord() })
            )

            Button(
                onClick = { checkWord() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = userInput.isNotBlank()
            ) {
                Text(
                    if (currentWordIndex >= words.lastIndex) stringResource(R.string.friction_gate_finish)
                    else stringResource(R.string.friction_gate_next),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
