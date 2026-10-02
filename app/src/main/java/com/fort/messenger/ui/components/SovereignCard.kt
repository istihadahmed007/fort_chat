package com.fort.messenger.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fort.messenger.ui.theme.SurfaceSubtleBorder

@Composable
fun SovereignCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    elevation: Dp = 0.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier
            .then(
                if (onClick != null) {
                    Modifier
                        .clip(shape)
                        .clickable { onClick() }
                } else {
                    Modifier
                }
            ),
        shape = shape,
        color = backgroundColor,
        tonalElevation = elevation,
        shadowElevation = elevation,
        border = BorderStroke(0.75.dp, borderColor)
    ) {
        Box(modifier = Modifier.padding(16.dp)) {
            content()
        }
    }
}
