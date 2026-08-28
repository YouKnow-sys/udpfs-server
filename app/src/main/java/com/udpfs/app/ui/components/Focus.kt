package com.udpfs.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

internal const val FOCUS_STIFFNESS = 2400f

@Composable
fun Modifier.focusRing(shape: Shape = CircleShape): Modifier {
    var focused by remember { mutableStateOf(false) }
    val spec = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = FOCUS_STIFFNESS)
    val width by animateDpAsState(
        targetValue = if (focused) 3.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = FOCUS_STIFFNESS),
        label = "focusBorder",
    )
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.04f else 1f,
        animationSpec = spec,
        label = "focusScale",
    )
    val color = MaterialTheme.colorScheme.primary
    return this
        .onFocusChanged { focused = it.hasFocus }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.drawBehind {
            if (width <= 0.dp) return@drawBehind
            val strokeInset = width.roundToPx() / 2
            if (strokeInset * 2 < size.width && strokeInset * 2 < size.height) {
                val outline =
                    shape.createOutline(
                        Size(size.width - strokeInset * 2, size.height - strokeInset * 2),
                        layoutDirection,
                        this,
                    )
                translate(strokeInset.toFloat(), strokeInset.toFloat()) {
                    drawPath(
                        outline.asPath(),
                        color,
                        style = Stroke(width.roundToPx().toFloat()),
                    )
                }
            }
        }
}

private fun Outline.asPath(): Path =
    when (this) {
        is Outline.Generic -> path
        is Outline.Rounded -> Path().apply { addRoundRect(roundRect) }
        is Outline.Rectangle -> Path().apply { addRect(rect) }
    }

@Composable
fun Modifier.focusHighlight(shape: Shape): Modifier {
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = FOCUS_STIFFNESS),
        label = "focusScale",
    )
    val color = MaterialTheme.colorScheme.secondaryContainer
    return this
        .onFocusChanged { focused = it.isFocused }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }.drawBehind {
            if (focused) {
                drawPath(shape.createOutline(size, layoutDirection, this).asPath(), color)
            }
        }
}

@Composable
fun Modifier.focusedClickable(
    shape: Shape,
    enabled: Boolean = true,
    onClick: () -> Unit,
): Modifier = focusHighlight(shape).clip(shape).clickable(enabled = enabled, onClick = onClick)
