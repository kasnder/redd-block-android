package net.kollnig.reddblockandroid.ui.screen

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kollnig.reddblockandroid.R

@Composable
fun AccessibilityConsentDialog(
    onDismiss: () -> Unit,
    onAgree: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = stringResource(id = R.string.accessibility_consent_title))
        },
        text = {
            Text(text = stringResource(id = R.string.accessibility_consent_text))
        },
        confirmButton = {
            Button(onClick = onAgree) {
                Text(text = stringResource(id = R.string.accessibility_consent_agree))
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(text = stringResource(id = R.string.accessibility_consent_deny))
            }
        },
        modifier = Modifier.padding(16.dp)
    )
}
