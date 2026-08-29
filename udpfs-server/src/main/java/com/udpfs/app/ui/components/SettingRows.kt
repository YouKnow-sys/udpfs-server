package com.udpfs.app.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.udpfs.app.R

private val StepperValueWidth = 92.dp

@Composable
fun Section(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        content()
    }
}

@Composable
fun PathRow(
    icon: Painter,
    title: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    clearLabel: String = "",
    onClear: (() -> Unit)? = null,
) {
    val trash = remember { FocusRequester() }
    val row = remember { FocusRequester() }
    Row(
        Modifier
            .fillMaxWidth()
            .focusRequester(row)
            .focusProperties { if (onClear != null) right = trash }
            .focusedClickable(MaterialTheme.shapes.medium, enabled, onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(painter = icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (onClear != null) {
            IconButton(
                onClick = onClear,
                enabled = enabled,
                modifier =
                    Modifier
                        .focusProperties { left = row }
                        .focusRequester(trash)
                        .focusRing(),
            ) {
                Icon(painterResource(R.drawable.ic_delete), contentDescription = clearLabel)
            }
        }
        if (enabled) {
            Icon(painterResource(R.drawable.ic_keyboard_arrow_right), contentDescription = null)
        }
    }
}

@Composable
fun StepperRow(
    title: String,
    value: Int,
    step: Int,
    min: Int,
    max: Int,
    enabled: Boolean,
    suffix: String = "",
    onValueClick: (() -> Unit)? = null,
    onChange: (Int) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = { onChange((value - step).coerceAtLeast(min)) }, enabled = enabled) {
            Icon(painterResource(R.drawable.ic_remove), contentDescription = stringResource(R.string.action_decrease, title))
        }
        val valueModifier =
            if (onValueClick != null && enabled) {
                Modifier
                    .width(StepperValueWidth)
                    .focusedClickable(MaterialTheme.shapes.small) { onValueClick() }
            } else {
                Modifier.width(StepperValueWidth)
            }
        Text(
            "$value$suffix",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            modifier = valueModifier,
        )
        IconButton(onClick = { onChange((value + step).coerceAtMost(max)) }, enabled = enabled) {
            Icon(painterResource(R.drawable.ic_add), contentDescription = stringResource(R.string.action_increase, title))
        }
    }
}

@Composable
fun SwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onChange,
            ).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            interactionSource = interactionSource,
        )
    }
}

@Composable
fun <T> SegmentedRow(
    options: List<T>,
    selected: T,
    enabled: Boolean,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                enabled = enabled,
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
                label = { Text(optionLabel(option)) },
            )
        }
    }
}
