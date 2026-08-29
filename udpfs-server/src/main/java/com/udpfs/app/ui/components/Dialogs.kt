package com.udpfs.app.ui.components

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.udpfs.app.R

@Composable
fun InputDialog(
    title: String,
    initial: String,
    placeholder: String? = null,
    keyboardType: KeyboardType = KeyboardType.Uri,
    transform: (String) -> String = { it },
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = transform(it) },
                singleLine = true,
                placeholder = placeholder?.let { { Text(it) } },
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
