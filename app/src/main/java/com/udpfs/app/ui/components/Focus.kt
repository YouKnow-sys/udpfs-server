package com.udpfs.app.ui.components

import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.draw.scale
import androidx.compose.ui.composed
import androidx.compose.ui.Modifier
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState

fun Modifier.focusRing(shape: Shape = CircleShape): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val width by animateDpAsState(if (focused) 3.dp else 0.dp, label = "focusBorder")
    val scale by animateFloatAsState(if (focused) 1.04f else 1f, label = "focusScale")
    this
        .onFocusChanged { focused = it.hasFocus }
        .scale(scale)
        .border(width, MaterialTheme.colorScheme.primary, shape)
}

fun Modifier.focusHighlight(shape: Shape): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) 1.02f else 1f, label = "focusScale")
    val color = MaterialTheme.colorScheme.secondaryContainer
    this
        .onFocusChanged { focused = it.hasFocus }
        .scale(scale)
        .background(if (focused) color else androidx.compose.ui.graphics.Color.Transparent, shape)
}
